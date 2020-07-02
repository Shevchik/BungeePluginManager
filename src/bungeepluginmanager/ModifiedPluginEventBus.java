package bungeepluginmanager;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.md_5.bungee.api.event.AsyncEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventBus;

public class ModifiedPluginEventBus extends EventBus {

	private static final Set<AsyncEvent<?>> uncompletedEvents = Collections.newSetFromMap(new WeakHashMap<AsyncEvent<?>, Boolean>());

	public static void completeIntents(Plugin plugin) {
		synchronized (uncompletedEvents) {
			for (AsyncEvent<?> event : uncompletedEvents) {
				try {
					event.completeIntent(plugin);
				} catch (Throwable t) {
				}
			}
		}
	}

	@Override
	public void post(Object event) {
		if (event instanceof AsyncEvent) {
			synchronized (uncompletedEvents) {
				uncompletedEvents.add((AsyncEvent<?>) event);
			}
		}
		super.post(event);
	}

}
