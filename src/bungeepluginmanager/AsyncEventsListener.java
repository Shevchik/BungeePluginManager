package bungeepluginmanager;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.event.AsyncEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public class AsyncEventsListener implements Listener {

	public static Set<AsyncEvent<?>> uncompletedEvents = Collections.newSetFromMap(new ConcurrentHashMap<AsyncEvent<?>, Boolean>());

	public static void completeIntents(Plugin plugin) {
		for (AsyncEvent<?> event : uncompletedEvents) {
			try {
				event.completeIntent(plugin);
			} catch (Throwable t) {
			}
		}
	}

	@EventHandler
	public void onLogin(LoginEvent event) {
		replaceCallback(event);
		rememberEvent(event);
	}

	@EventHandler
	public void onPreLogin(PreLoginEvent event) {
		replaceCallback(event);
		rememberEvent(event);
	}

	@EventHandler
	public void onPing(ProxyPingEvent event) {
		replaceCallback(event);
		rememberEvent(event);
	}

	static void replaceCallback(AsyncEvent<?> event) {
		Callback<AsyncEvent<?>> realCallback = ReflectionUtils.getFieldValue(event, "done");
		ReflectionUtils.setFieldValue(event, "done", new WrappedCallback(realCallback));
	}

	static void rememberEvent(AsyncEvent<?> event) {
		uncompletedEvents.add(event);
	}

	private static class WrappedCallback implements Callback<AsyncEvent<?>> {

		private Callback<AsyncEvent<?>> realCallback;

		public WrappedCallback(Callback<AsyncEvent<?>> realCallback) {
			this.realCallback = realCallback;
		}

		@Override
		public void done(AsyncEvent<?> event, Throwable throwable) {
			uncompletedEvents.remove(event);
			realCallback.done(event, throwable);
		}

	}

}
