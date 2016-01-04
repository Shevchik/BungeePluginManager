package bungeepluginmanager;

import java.io.File;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.yaml.snakeyaml.Yaml;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;

public class Commands extends Command {

	public Commands() {
		super("bungeepluginmanager", "bungeepluginmanager.cmds", new String[] { "bpm" });
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(textWithColor("Not enough args", ChatColor.RED));
			return;
		}
		switch (args[0].toLowerCase()) {
			case "unload": {
				Plugin plugin = findPlugin(args[1]);
				if (plugin == null) {
					sender.sendMessage(textWithColor("Plugin not found", ChatColor.RED));
					return;
				}
				PluginUtils.unloadPlugin(plugin);
				sender.sendMessage(textWithColor("Plugin unloaded", ChatColor.YELLOW));
				return;
			}
			case "load": {
				Plugin plugin = findPlugin(args[1]);
				if (plugin != null) {
					sender.sendMessage(textWithColor("Plugin is already loaded", ChatColor.RED));
					return;
				}				
				File file = findFile(args[1]);
				if (!file.exists()) {
					sender.sendMessage(textWithColor("Plugin not found", ChatColor.RED));
					return;
				}
				boolean success = PluginUtils.loadPlugin(file);
				if (success) {
					sender.sendMessage(textWithColor("Plugin loaded", ChatColor.YELLOW));
				} else {
					sender.sendMessage(textWithColor("Failed to load plugin, see console for more info", ChatColor.RED));
				}
				return;
			}
			case "reload": {
				Plugin plugin = findPlugin(args[1]);
				if (plugin == null) {
					sender.sendMessage(textWithColor("Plugin not found", ChatColor.RED));
					return;
				}
				File pluginfile = plugin.getFile();
				PluginUtils.unloadPlugin(plugin);
				boolean success = PluginUtils.loadPlugin(pluginfile);
				if (success) {
					sender.sendMessage(textWithColor("Plugin reloaded", ChatColor.YELLOW));
				} else {
					sender.sendMessage(textWithColor("Failed to reload plugin, see console for more info", ChatColor.RED));
				}
				return;
			}
		}
	}

	static Plugin findPlugin(String pluginname) {
		for (Plugin plugin : ProxyServer.getInstance().getPluginManager().getPlugins()) {
			if (plugin.getDescription().getName().equalsIgnoreCase(pluginname)) {
				return plugin;
			}
		}
		return null;
	}

	static File findFile(String pluginname) {
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
		return new File(folder, pluginname+".jar");
	}

	static TextComponent textWithColor(String message, ChatColor color) {
		TextComponent text = new TextComponent(message);
		text.setColor(color);
		return text;
	}

}
