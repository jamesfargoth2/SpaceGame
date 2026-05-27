package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.SaveCompleteEvent;
import com.galacticodyssey.core.events.LoadCompleteEvent;
import com.galacticodyssey.persistence.snapshots.HealthSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SaveCoordinatorTest {

    @TempDir
    File tempDir;

    @Test
    void saveAndLoadRoundTrip() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        LocalFileSaveBackend backend = new LocalFileSaveBackend(tempDir);

        // Create a player entity
        Entity player = new Entity();
        PersistenceIdComponent pid = new PersistenceIdComponent();
        player.add(pid);

        TransformComponent tc = new TransformComponent();
        tc.position.set(5f, 10f, 15f);
        player.add(tc);

        HealthComponent hc = new HealthComponent();
        hc.currentHP = 55f;
        hc.maxHP = 100f;
        player.add(hc);

        engine.addEntity(player);

        SaveCoordinator coordinator = new SaveCoordinator(
            eventBus, engine, backend, 42L, pid.uuid, null);

        // Track events
        AtomicBoolean saveComplete = new AtomicBoolean(false);
        eventBus.subscribe(SaveCompleteEvent.class, e -> saveComplete.set(true));

        // Save
        coordinator.save("test-save");
        assertTrue(saveComplete.get());

        // Modify state
        hc.currentHP = 10f;
        tc.position.set(0f, 0f, 0f);

        // Load
        AtomicBoolean loadComplete = new AtomicBoolean(false);
        eventBus.subscribe(LoadCompleteEvent.class, e -> loadComplete.set(true));

        coordinator.load("test-save");
        assertTrue(loadComplete.get());

        // Verify restored state — find the player entity by PersistenceId
        Entity restored = null;
        for (Entity e : engine.getEntitiesFor(
                Family.all(PersistenceIdComponent.class).get())) {
            if (e.getComponent(PersistenceIdComponent.class).uuid.equals(pid.uuid)) {
                restored = e;
                break;
            }
        }
        assertNotNull(restored);
        assertEquals(55f, restored.getComponent(HealthComponent.class).currentHP);
    }
}
