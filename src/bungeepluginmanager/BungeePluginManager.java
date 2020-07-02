package bungeepluginmanager;

import java.util.logging.Level;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

public class BungeePluginManager extends Plugin {

	@Override
	public void onLoad() {
		try {
			ReflectionUtils.setFieldValue(ProxyServer.getInstance().getPluginManager(), "eventBus", new ModifiedPluginEventBus());
		} catch (IllegalAccessException | NoSuchFieldException e) {
			getLogger().log(Level.SEVERE, "Unable to inject modified command bus, completing plugin async intents won't work", e);
		}
	}

	@Override
	public void onEnable() {
		getProxy().getPluginManager().registerCommand(this, new Commands());
	}

}
