package tech.nabor.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AppEventBusTest {

    private AppEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new AppEventBus(new TestI18n());
    }

    // ── subscribe / publish ───────────────────────────────────────────────────

    @Test
    void publish_delivers_payload_to_subscriber() {
        List<Object> received = new ArrayList<>();
        bus.subscribe("test.event", received::add);

        bus.publish("test.event", "hello");

        assertEquals(1,       received.size());
        assertEquals("hello", received.getFirst());
    }

    @Test
    void publish_delivers_to_all_subscribers() {
        List<Object> received1 = new ArrayList<>();
        List<Object> received2 = new ArrayList<>();

        bus.subscribe("test.event", received1::add);
        bus.subscribe("test.event", received2::add);

        bus.publish("test.event", "payload");

        assertEquals(1, received1.size());
        assertEquals(1, received2.size());
    }

    @Test
    void publish_does_not_deliver_to_other_events() {
        List<Object> received = new ArrayList<>();
        bus.subscribe("event.a", received::add);

        bus.publish("event.b", "payload");

        assertTrue(received.isEmpty());
    }

    @Test
    void publish_with_no_subscribers_does_not_throw() {
        assertDoesNotThrow(() -> bus.publish("event.inexistant", "payload"));
    }

    @Test
    void publish_null_payload_is_delivered() {
        List<Object> received = new ArrayList<>();
        bus.subscribe("test.event", received::add);

        bus.publish("test.event", null);

        assertEquals(1,    received.size());
        assertNull(received.getFirst());
    }

    @Test
    void publish_delivers_multiple_events_in_order() {
        List<Object> received = new ArrayList<>();
        bus.subscribe("test.event", received::add);

        bus.publish("test.event", "first");
        bus.publish("test.event", "second");
        bus.publish("test.event", "third");

        assertEquals(List.of("first", "second", "third"), received);
    }

    // ── unsubscribe ───────────────────────────────────────────────────────────

    @Test
    void unsubscribe_stops_delivery() {
        List<Object> received = new ArrayList<>();
        Consumer<Object> listener = received::add;

        bus.subscribe("test.event", listener);
        bus.publish("test.event", "before");

        bus.unsubscribe("test.event", listener);
        bus.publish("test.event", "after");

        assertEquals(1,        received.size());
        assertEquals("before", received.getFirst());
    }

    @Test
    void unsubscribe_only_removes_target_listener() {
        List<Object> received1 = new ArrayList<>();
        List<Object> received2 = new ArrayList<>();

        Consumer<Object> listener1 = received1::add;
        Consumer<Object> listener2 = received2::add;

        bus.subscribe("test.event", listener1);
        bus.subscribe("test.event", listener2);

        bus.unsubscribe("test.event", listener1);
        bus.publish("test.event", "payload");

        assertTrue(received1.isEmpty());
        assertEquals(1, received2.size());
    }

    @Test
    void unsubscribe_nonexistent_listener_does_not_throw() {
        Consumer<Object> listener = obj -> {};
        assertDoesNotThrow(() -> bus.unsubscribe("test.event", listener));
    }

    @Test
    void unsubscribe_nonexistent_event_does_not_throw() {
        Consumer<Object> listener = obj -> {};
        assertDoesNotThrow(() -> bus.unsubscribe("inexistant", listener));
    }

    // ── robustesse ────────────────────────────────────────────────────────────

    @Test
    void failing_listener_does_not_block_other_listeners() {
        List<Object> received = new ArrayList<>();

        // listener qui plante
        bus.subscribe("test.event", payload -> {
            throw new RuntimeException("Listener défaillant");
        });

        // listener qui doit quand même recevoir
        bus.subscribe("test.event", received::add);

        assertDoesNotThrow(() -> bus.publish("test.event", "payload"));
        assertEquals(1, received.size());
    }

    @Test
    void listener_can_unsubscribe_during_publish() {
        List<Object> received = new ArrayList<>();
        var listenerHolder = new Object() {
            Consumer<Object> listener;
        };

        listenerHolder.listener = payload -> {
            received.add(payload);
            bus.unsubscribe("test.event", listenerHolder.listener); // se unsubscribe meanwhile the payload is treated
        };

        bus.subscribe("test.event", listenerHolder.listener);

        assertDoesNotThrow(() -> {
            bus.publish("test.event", "first");
            bus.publish("test.event", "second"); // should not be received
        });

        assertEquals(1, received.size()); // received only once
    }

    // ── thread safety ─────────────────────────────────────────────────────────

    @Test
    void concurrent_subscribe_and_publish_does_not_throw() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    bus.subscribe("concurrent.event", obj -> {});
                    bus.publish("concurrent.event", "payload-" + idx);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
    }

    @Test
    void concurrent_publish_to_same_event_does_not_throw() throws InterruptedException {
        List<Object> received = new ArrayList<>();
        bus.subscribe("concurrent.event", obj -> {
            synchronized (received) {
                received.add(obj);
            }
        });

        int threadCount = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    bus.publish("concurrent.event", "payload-" + idx);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(threadCount, received.size());
    }
}