package bungeepluginmanager;

import java.util.logging.Level;

import net.md_5.bungee.api.plugin.Plugin;

public class BungeePluginManager extends Plugin {

	@Override
	public void onLoad() {
		try {
			ReflectionUtils.setFieldValue(getProxy().getPluginManager(), "eventBus", new ModifiedPluginEventBus(getProxy().getLogger()));
		} catch (Throwable t) {
			getLogger().log(Level.SEVERE, "Unable to inject modified command bus, completing plugin async intents won't work", t);
		}
	}

	@Override
	public void onEnable() {
		getProxy().getPluginManager().registerCommand(this, new Commands(getLogger()));
	}

}
