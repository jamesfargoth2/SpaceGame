package com.galacticodyssey.core;

import com.galacticodyssey.core.events.OriginRebasedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
    }

    @Test
    void publishDeliversToSubscriber() {
        List<OriginRebasedEvent> received = new ArrayList<>();
        eventBus.subscribe(OriginRebasedEvent.class, received::add);

        var event = new OriginRebasedEvent(1f, 2f, 3f);
        eventBus.publish(event);

        assertEquals(1, received.size());
        assertSame(event, received.get(0));
    }

    @Test
    void publishDeliversToMultipleSubscribers() {
        List<String> order = new ArrayList<>();
        eventBus.subscribe(OriginRebasedEvent.class, e -> order.add("first"));
        eventBus.subscribe(OriginRebasedEvent.class, e -> order.add("second"));

        eventBus.publish(new OriginRebasedEvent(0, 0, 0));

        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void unsubscribeStopsDelivery() {
        List<OriginRebasedEvent> received = new ArrayList<>();
        EventBus.EventListener<OriginRebasedEvent> listener = received::add;
        eventBus.subscribe(OriginRebasedEvent.class, listener);
        eventBus.unsubscribe(OriginRebasedEvent.class, listener);

        eventBus.publish(new OriginRebasedEvent(1, 2, 3));

        assertTrue(received.isEmpty());
    }

    @Test
    void publishWithNoSubscribersDoesNotThrow() {
        assertDoesNotThrow(() -> eventBus.publish(new OriginRebasedEvent(0, 0, 0)));
    }
}
