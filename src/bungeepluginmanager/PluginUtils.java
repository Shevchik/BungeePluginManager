package bungeepluginmanager;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Handler;

import org.yaml.snakeyaml.Yaml;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.api.plugin.PluginManager;

public class PluginUtils {

	@SuppressWarnings("deprecation")
	public static void unloadPlugin(Plugin plugin) {
		IllegalStateException error = new IllegalStateException("Errors occured while unloading plugin " + plugin.getDescription().getName()) {
			private static final long serialVersionUID = 1L;

			@Override
			public synchronized Throwable fillInStackTrace() {
				return this;
			}
		};

		PluginManager pluginmanager = ProxyServer.getInstance().getPluginManager();
		ClassLoader pluginclassloader = plugin.getClass().getClassLoader();

		//call onDisable
		try {
			plugin.onDisable();
		} catch (Throwable t) {
			error.addSuppressed(t);
		}

		//close all log handlers
		try {
			for (Handler handler : plugin.getLogger().getHandlers()) {
				handler.close();
			}
		} catch (Throwable t) {
			error.addSuppressed(t);
		}

		//unregister event handlers
		try {
			pluginmanager.unregisterListeners(plugin);
		} catch (Throwable t) {
			error.addSuppressed(t);
		}

		//unregister commands
		try {
			pluginmanager.unregisterCommands(plugin);
		} catch (Throwable t) {
			error.addSuppressed(t);
		}

		//cancel tasks
		try {
			ProxyServer.getInstance().getScheduler().cancel(plugin);
		} catch (Throwable t) {
			error.addSuppressed(t);
		}

		//shutdown plugin executor
		try {
			plugin.getExecutorService().shutdownNow();
		} catch (Throwable t) {
			error.addSuppressed(t);
		}

		//stop all still active threads that belong to a plugin
		for (Thread thread : Thread.getAllStackTraces().keySet()) {
			if (thread.getClass().getClassLoader() == pluginclassloader) {
				try {
					thread.interrupt();
					thread.join(2000);
					if (thread.isAlive()) {
						thread.stop();
					}
				} catch (Throwable t) {
					error.addSuppressed(t);
				}
			}
		}

		//finish uncompleted intents
		ModifiedPluginEventBus.completeIntents(plugin);

		//remove commands that were registered by plugin not through normal means
		try {
			Map<String, Command> commandMap = ReflectionUtils.getFieldValue(pluginmanager, "commandMap");
			Iterator<Entry<String, Command>> iterator = commandMap.entrySet().iterator();
			while (iterator.hasNext()) {
				Entry<String, Command> entry = iterator.next();
				if (entry.getValue().getClass().getClassLoader() == pluginclassloader) {
					iterator.remove();
				}
			}
		} catch (Throwable t) {
			error.addSuppressed(t);
		}

		//remove plugin ref from internal plugins map
		try {
			ReflectionUtils.<Map<String, Plugin>>getFieldValue(pluginmanager, "plugins").values().remove(plugin);
		} catch (Throwable t) {
			error.addSuppressed(t);
		}

		//close classloader
		if (pluginclassloader instanceof URLClassLoader) {
			try {
				((URLClassLoader) pluginclassloader).close();
			} catch (Throwable t) {
				error.addSuppressed(t);
			}
		}

		//remove classloader
		try {
			ReflectionUtils.<Set<ClassLoader>>getStaticFieldValue(pluginclassloader.getClass(), "allLoaders").remove(pluginclassloader);
		} catch (Throwable t) {
			error.addSuppressed(t);
		}

		if (error.getSuppressed().length > 0) {
			throw error;
		}
	}

	public static void loadPlugin(File pluginFile) {
		ProxyServer proxy = ProxyServer.getInstance();
		PluginManager pluginmanager = proxy.getPluginManager();

		try (JarFile jar = new JarFile(pluginFile)) {
			JarEntry pdf = jar.getJarEntry("bungee.yml");
			if (pdf == null) {
				pdf = jar.getJarEntry("plugin.yml");
			}
			try (InputStream in = jar.getInputStream(pdf)) {
				//load description
				PluginDescription pluginDescription = new Yaml().loadAs(in, PluginDescription.class);
				pluginDescription.setFile(pluginFile);
				//check depends
				HashSet<String> plugins = new HashSet<>();
				for (Plugin plugin : pluginmanager.getPlugins()) {
					plugins.add(plugin.getDescription().getName());
				}
				for (String dependency : pluginDescription.getDepends()) {
					if (!plugins.contains(dependency)) {
						throw new IllegalArgumentException(MessageFormat.format("Missing plugin dependency {0}", dependency));
					}
				}
				//load plugin
				Plugin plugin = createPluginInstance(proxy, pluginFile, pluginDescription);
				ReflectionUtils.invokeMethod(plugin, "init", proxy, pluginDescription);
				ReflectionUtils.<Map<String, Plugin>>getFieldValue(pluginmanager, "plugins").put(pluginDescription.getName(), plugin);
				plugin.onLoad();
				plugin.onEnable();
			}
		} catch (Throwable t) {
			throw new IllegalStateException("Error while loading plugin " + pluginFile.getName(), t);
		}
	}

	private static Plugin createPluginInstance(ProxyServer proxy, File pluginFile, PluginDescription pluginDescription) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, MalformedURLException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		Class<?> pluginClassLoaderClass = BungeePluginManager.class.getClassLoader().getClass();
		ClassLoader pluginClassLoader = null;
		for (Constructor<?> constructor : pluginClassLoaderClass.getDeclaredConstructors()) {
			ReflectionUtils.setAccessible(constructor);
			Parameter[] parameters = constructor.getParameters();
			if (
				(parameters.length == 3) &&
					parameters[0].getType().isAssignableFrom(ProxyServer.class) &&
					parameters[1].getType().isAssignableFrom(PluginDescription.class) &&
					parameters[2].getType().isAssignableFrom(URL[].class)
			) {
				pluginClassLoader = (ClassLoader) constructor.newInstance(proxy, pluginDescription, new URL[]{pluginFile.toURI().toURL()});
				break;
			} else if (
				(parameters.length == 4) &&
					parameters[0].getType().isAssignableFrom(ProxyServer.class) &&
					parameters[1].getType().isAssignableFrom(PluginDescription.class) &&
					parameters[2].getType().isAssignableFrom(File.class) &&
					parameters[3].getType().isAssignableFrom(ClassLoader.class)
			) {
				pluginClassLoader = (ClassLoader) constructor.newInstance(proxy, pluginDescription,pluginFile , null);
				break;
			} else if (
				(parameters.length == 1) &&
					parameters[0].getType().isAssignableFrom(URL[].class)
			) {
				pluginClassLoader = (ClassLoader) constructor.newInstance(new Object[]{new URL[]{pluginFile.toURI().toURL()}});
				break;
			}
		}
		if (pluginClassLoader == null) {
			throw new IllegalStateException(MessageFormat.format(
				"Unable to create PluginClassLoader instance, no suitable constructors found in class {0} constructors {1}",
				pluginClassLoaderClass, Arrays.toString(pluginClassLoaderClass.getDeclaredConstructors())
			));
		}
		return (Plugin)
			pluginClassLoader
				.loadClass(pluginDescription.getMain())
				.getDeclaredConstructor()
				.newInstance();
	}

}
