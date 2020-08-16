package bungeepluginmanager;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.yaml.snakeyaml.Yaml;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.api.plugin.TabExecutor;

//TODO: split to subcommands
public class Commands extends Command implements TabExecutor {

	private final Logger logger;

	public Commands(Logger logger) {
		super("bungeepluginmanager", "bungeepluginmanager.cmds", "bpm");
		this.logger = logger;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if (args.length < 1) {
			sender.sendMessage(textWithColor("Not enough args", ChatColor.RED));
			return;
		}
		switch (toLowerCase(args[0])) {
			case "list": {
				sender.sendMessage(textWithColor(getPluginNamesStream().collect(Collectors.joining(", ")), ChatColor.GREEN));
				return;
			}
			case "unload": {
				if (args.length < 2) {
					sender.sendMessage(textWithColor("Not enough args", ChatColor.RED));
					return;
				}

				Plugin plugin = findPlugin(args[1]);
				if (plugin == null) {
					sender.sendMessage(textWithColor("Plugin not found", ChatColor.RED));
					return;
				}

				try {
					PluginUtils.unloadPlugin(plugin);
					sender.sendMessage(textWithColor("Plugin unloaded", ChatColor.YELLOW));
				} catch (Throwable t) {
					sender.sendMessage(textWithColor("Error occured while unloading plugin, see console for more details", ChatColor.RED));
					logger.log(Level.WARNING, "Failed to unload plugin " + plugin.getDescription().getName(), t);
				}
				return;
			}
			case "load": {
				if (args.length < 2) {
					sender.sendMessage(textWithColor("Not enough args", ChatColor.RED));
					return;
				}

				if (findPlugin(args[1]) != null) {
					sender.sendMessage(textWithColor("Plugin is already loaded", ChatColor.RED));
					return;
				}

				File pluginFile = findFile(args[1]);
				if (!pluginFile.exists()) {
					sender.sendMessage(textWithColor("Plugin not found", ChatColor.RED));
					return;
				}

				try {
					PluginUtils.loadPlugin(pluginFile);
					sender.sendMessage(textWithColor("Plugin loaded", ChatColor.YELLOW));
				} catch (Throwable t) {
					sender.sendMessage(textWithColor("Error occured while loading plugin, see console for more details", ChatColor.RED));
					logger.log(Level.WARNING, "Failed to load plugin " + pluginFile.getName(), t);
				}
				return;
			}
			case "reload": {
				if (args.length < 2) {
					sender.sendMessage(textWithColor("Not enough args", ChatColor.RED));
					return;
				}

				Plugin plugin = findPlugin(args[1]);
				if (plugin == null) {
					sender.sendMessage(textWithColor("Plugin not found", ChatColor.RED));
					return;
				}
				File pluginFile = plugin.getFile();

				try {
					PluginUtils.unloadPlugin(plugin);
					PluginUtils.loadPlugin(pluginFile);
					sender.sendMessage(textWithColor("Plugin reloaded", ChatColor.YELLOW));
				} catch (Throwable t) {
					sender.sendMessage(textWithColor("Error occured while reloading plugin, see console for more details", ChatColor.RED));
					logger.log(Level.WARNING, "Failed to reload plugin " + "(" + plugin.getDescription().getName() + "," + pluginFile.getName() + ")", t);
				}
			}
		}
	}

	private static Plugin findPlugin(String pluginname) {
		for (Plugin plugin : ProxyServer.getInstance().getPluginManager().getPlugins()) {
			if (plugin.getDescription().getName().equalsIgnoreCase(pluginname)) {
				return plugin;
			}
		}
		return null;
	}

	private static File findFile(String pluginname) {
		File folder = ProxyServer.getInstance().getPluginsFolder();
		if (folder.exists()) {
			for (File file : folder.listFiles()) {
				if (file.isFile() && file.getName().endsWith(".jar")) {
					try (JarFile jar = new JarFile(file)) {
						JarEntry pdf = jar.getJarEntry("bungee.yml");
						if (pdf == null) {
							pdf = jar.getJarEntry("plugin.yml");
						}
						try (InputStream in = jar.getInputStream(pdf)) {
							final PluginDescription desc = new Yaml().loadAs(in, PluginDescription.class);
							if (desc.getName().equalsIgnoreCase(pluginname)) {
								return file;
							}
						}
					} catch (Throwable ex) {
					}
				}
			}
		}
		return new File(folder, pluginname + ".jar");
	}

	private Stream<String> getPluginNamesStream() {
		return ProxyServer.getInstance().getPluginManager().getPlugins().stream().map(plugin -> plugin.getDescription().getName());
	}


	private static TextComponent textWithColor(String message, ChatColor color) {
		TextComponent text = new TextComponent(message);
		text.setColor(color);
		return text;
	}

	private final List<String> subCommands = Arrays.asList("list", "load", "unload", "reload");

	@Override
	public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
		String arg0low = toLowerCase(args[0]);
		if (args.length == 1) {
			return subCommands.stream().filter(cmd -> toLowerCase(cmd).startsWith(arg0low)).collect(Collectors.toList());
		} else {
			if ((args.length == 2) && subCommands.contains(arg0low) && (arg0low.equals("unload") || (arg0low.equals("reload")))) {
				return getPluginNamesStream().filter(cmd -> toLowerCase(cmd).startsWith(toLowerCase(args[1]))).collect(Collectors.toList());
			}
			return Collections.emptyList();
		}
	}

	private static String toLowerCase(String s) {
		return s.toLowerCase(Locale.ENGLISH);
	}

}
