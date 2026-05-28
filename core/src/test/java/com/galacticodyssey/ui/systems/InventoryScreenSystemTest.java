package com.galacticodyssey.ui.systems;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ui.events.InventoryClosedEvent;
import com.galacticodyssey.ui.events.InventoryOpenedEvent;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class InventoryScreenSystemTest {

    @Test
    void togglePublishesOpenAndCloseEvents() {
        EventBus bus = new EventBus();
        AtomicInteger openCount = new AtomicInteger(0);
        AtomicInteger closeCount = new AtomicInteger(0);
        bus.subscribe(InventoryOpenedEvent.class, e -> openCount.incrementAndGet());
        bus.subscribe(InventoryClosedEvent.class, e -> closeCount.incrementAndGet());

        InventoryScreenSystem system = new InventoryScreenSystem(bus, null);

        assertFalse(system.isOpen());

        system.toggle();
        assertTrue(system.isOpen());
        assertEquals(1, openCount.get());
        assertEquals(0, closeCount.get());

        system.toggle();
        assertFalse(system.isOpen());
        assertEquals(1, openCount.get());
        assertEquals(1, closeCount.get());
    }

    @Test
    void doubleOpenDoesNotPublishTwice() {
        EventBus bus = new EventBus();
        AtomicInteger openCount = new AtomicInteger(0);
        bus.subscribe(InventoryOpenedEvent.class, e -> openCount.incrementAndGet());

        InventoryScreenSystem system = new InventoryScreenSystem(bus, null);
        system.open();
        system.open();
        assertEquals(1, openCount.get());
    }

    @Test
    void closeWhenAlreadyClosedDoesNotPublish() {
        EventBus bus = new EventBus();
        AtomicInteger closeCount = new AtomicInteger(0);
        bus.subscribe(InventoryClosedEvent.class, e -> closeCount.incrementAndGet());

        InventoryScreenSystem system = new InventoryScreenSystem(bus, null);
        system.close();
        assertEquals(0, closeCount.get());
    }
}
