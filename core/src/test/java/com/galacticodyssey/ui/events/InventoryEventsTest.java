package com.galacticodyssey.ui.events;

import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class InventoryEventsTest {

    @Test
    void inventoryOpenedEventPublishesAndReceives() {
        EventBus bus = new EventBus();
        AtomicBoolean received = new AtomicBoolean(false);
        bus.subscribe(InventoryOpenedEvent.class, e -> received.set(true));
        bus.publish(new InventoryOpenedEvent());
        assertTrue(received.get());
    }

    @Test
    void inventoryClosedEventPublishesAndReceives() {
        EventBus bus = new EventBus();
        AtomicBoolean received = new AtomicBoolean(false);
        bus.subscribe(InventoryClosedEvent.class, e -> received.set(true));
        bus.publish(new InventoryClosedEvent());
        assertTrue(received.get());
    }
}
