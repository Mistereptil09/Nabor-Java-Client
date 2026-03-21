package tech.nabor.api;

public interface EventBus {
    void publish(String event, Object payload);
    void subscribe(String event, java.util.function.Consumer<Object> listener);
    void unsubscribe(String event, java.util.function.Consumer<Object> listener);
}