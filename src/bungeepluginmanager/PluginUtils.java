package bungeepluginmanager;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.yaml.snakeyaml.Yaml;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.api.plugin.PluginManager;

public class PluginUtils {

	@SuppressWarnings("deprecation")
	public static Exception unloadPlugin(Plugin plugin) {
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
			commandMap.entrySet().removeIf(entry -> entry.getValue().getClass().getClassLoader() == pluginclassloader);
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

		return error.getSuppressed().length > 0 ? error : null;
	}

	public static void loadPlugin(File pluginfile) {
		ProxyServer proxyserver = ProxyServer.getInstance();
		PluginManager pluginmanager = proxyserver.getPluginManager();

		try (JarFile jar = new JarFile(pluginfile)) {
			JarEntry pdf = jar.getJarEntry("bungee.yml");
			if (pdf == null) {
				pdf = jar.getJarEntry("plugin.yml");
			}
			try (InputStream in = jar.getInputStream(pdf)) {
				//load description
				PluginDescription desc = new Yaml().loadAs(in, PluginDescription.class);
				desc.setFile(pluginfile);
				//check depends
				HashSet<String> plugins = new HashSet<>();
				for (Plugin plugin : pluginmanager.getPlugins()) {
					plugins.add(plugin.getDescription().getName());
				}
				for (String dependency : desc.getDepends()) {
					if (!plugins.contains(dependency)) {
						throw new IllegalArgumentException(MessageFormat.format("Missing plugin dependency {0}", dependency));
					}
				}
				//load plugin
				Plugin plugin = (Plugin)
					ReflectionUtils.setAccessible(
						BungeePluginManager.class.getClassLoader().getClass()
						.getDeclaredConstructor(ProxyServer.class, PluginDescription.class, URL[].class)
					)
					.newInstance(proxyserver, desc, new URL[] {pluginfile.toURI().toURL()})
					.loadClass(desc.getMain()).getDeclaredConstructor()
					.newInstance();
				ReflectionUtils.invokeMethod(plugin, "init", proxyserver, desc);
				ReflectionUtils.<Map<String, Plugin>>getFieldValue(pluginmanager, "plugins").put(desc.getName(), plugin);
				plugin.onLoad();
				plugin.onEnable();
			}
		} catch (Throwable t) {
			throw new IllegalStateException("Error while loading plugin " + pluginfile.getName(), t);
		}
	}

	static void severe(String message, Throwable t) {
		ProxyServer.getInstance().getLogger().log(Level.SEVERE,  message);
	}

}
