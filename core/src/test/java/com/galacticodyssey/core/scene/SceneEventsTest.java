package com.galacticodyssey.core.scene;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SceneEventsTest {

    @Test
    void eventsCarryDataAndDispatchThroughBus() {
        EventBus bus = new EventBus();

        AtomicReference<SceneActivatedEvent> got = new AtomicReference<>();
        bus.subscribe(SceneActivatedEvent.class, got::set);
        bus.publish(new SceneActivatedEvent(3, SceneType.ORBITAL));
        assertEquals(3, got.get().sceneId);
        assertEquals(SceneType.ORBITAL, got.get().type);

        SceneTransitionBeganEvent began = new SceneTransitionBeganEvent(SceneType.DEEP_SPACE, SceneType.ORBITAL);
        assertEquals(SceneType.DEEP_SPACE, began.from);
        assertEquals(SceneType.ORBITAL, began.to);

        assertEquals(0.5f, new SceneLoadProgressEvent(1, 0.5f).progress, 1e-6);
        assertEquals(2, new SceneTransitionReadyEvent(2).sceneId);
        assertEquals(SceneType.PLANET_SURFACE, new SceneTransitionCompletedEvent(SceneType.PLANET_SURFACE).type);
        assertEquals("busy", new SceneTransitionRejectedEvent("busy").reason);
        SceneLoadFailedEvent failed = new SceneLoadFailedEvent(SceneType.SHIP_INTERIOR, "boom");
        assertEquals(SceneType.SHIP_INTERIOR, failed.type);
        assertEquals("boom", failed.reason);
    }
}
