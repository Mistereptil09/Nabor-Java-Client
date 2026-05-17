package tech.nabor.app;

import tech.nabor.api.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AppEventBus implements EventBus {
    private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();

    @Override
    public void publish(String event, Object payload) {
        System.out.println("[EventBus] Publishing event: " + event + " with payload: " + payload);
        List<Consumer<Object>> eventListeners = listeners.get(event);
        if (eventListeners != null) {
            for (Consumer<Object> listener : eventListeners) {
                listener.accept(payload);
            }
        }
    }

    @Override
    public void subscribe(String event, Consumer<Object> listener) {
        System.out.println("[EventBus] Subscribing to event: " + event);
        listeners.computeIfAbsent(event, k -> new ArrayList<>()).add(listener);
    }

    @Override
    public void unsubscribe(String event, Consumer<Object> listener) {
        System.out.println("[EventBus] Unsubscribing from event: " + event);
        List<Consumer<Object>> eventListeners = listeners.get(event);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }
}
