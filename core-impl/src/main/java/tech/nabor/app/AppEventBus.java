package tech.nabor.app;

import tech.nabor.api.EventBus;
import tech.nabor.api.I18n;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class AppEventBus implements EventBus {

    private final I18n i18n;

    public AppEventBus(I18n i18n) {
        this.i18n = i18n;
    }

    // ConcurrentHashMap — thread-safe for simultaneous access
    // CopyOnWriteArrayList — allow to iterate without ConcurrentModificationException
    //                        even if a listener unsubscribes while a publish is occuring
    private final Map<String, CopyOnWriteArrayList<Consumer<Object>>> listeners =
            new ConcurrentHashMap<>();

    @Override
    public void subscribe(String event, Consumer<Object> listener) {
        listeners
                .computeIfAbsent(event, k -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    @Override
    public void unsubscribe(String event, Consumer<Object> listener) {
        List<Consumer<Object>> eventListeners = listeners.get(event);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }

    @Override
    public void publish(String event, Object payload) {
        List<Consumer<Object>> eventListeners = listeners.get(event);
        if (eventListeners == null || eventListeners.isEmpty()) return;

        for (Consumer<Object> listener : eventListeners) {
            try {
                listener.accept(payload);
            } catch (Exception e) {
                // a crashing listener must not block the others
                i18n.t("event.error.listener", event, e.getMessage());
            }
        }
    }
}