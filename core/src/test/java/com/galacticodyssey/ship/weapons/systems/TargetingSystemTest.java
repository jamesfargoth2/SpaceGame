package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.player.components.PlayerTargetComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.events.TargetLockedEvent;
import com.galacticodyssey.ship.weapons.events.TargetLostEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TargetingSystem}.
 * Uses the Vector3 constructor to avoid native libGDX (Matrix4 JNI) during headless tests.
 */
class TargetingSystemTest {

    /** Camera sitting at origin, looking along -Z. */
    private static final Vector3 CAM_POS = new Vector3(0, 0, 0);
    private static final Vector3 CAM_DIR = new Vector3(0, 0, -1);

    @Test
    void softTargetDetectedWithinCone() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        TargetingSystem system = new TargetingSystem(eventBus, CAM_POS, CAM_DIR);
        engine.addSystem(system);

        Entity player = createPilotingPlayer();
        PlayerTargetComponent target = new PlayerTargetComponent();
        player.add(target);
        engine.addEntity(player);

        Entity enemy = createTargetableEntity(0, 0, -50); // directly ahead along -Z
        engine.addEntity(enemy);

        system.update(1f / 60f);

        assertEquals(enemy, target.softTarget, "Enemy directly ahead should be soft target");
    }

    @Test
    void noSoftTargetOutsideCone() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        TargetingSystem system = new TargetingSystem(eventBus, CAM_POS, CAM_DIR);
        engine.addSystem(system);

        Entity player = createPilotingPlayer();
        PlayerTargetComponent target = new PlayerTargetComponent();
        player.add(target);
        engine.addEntity(player);

        Entity enemy = createTargetableEntity(100, 0, -10); // far off to the side — angle >> 5°
        engine.addEntity(enemy);

        system.update(1f / 60f);

        assertNull(target.softTarget, "Enemy outside cone should not be soft target");
    }

    @Test
    void hardLockOnTargetPress() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        TargetingSystem system = new TargetingSystem(eventBus, CAM_POS, CAM_DIR);
        engine.addSystem(system);

        Entity player = createPilotingPlayer();
        PlayerTargetComponent target = new PlayerTargetComponent();
        player.add(target);
        ShipFlightInputComponent flightInput = player.getComponent(ShipFlightInputComponent.class);
        engine.addEntity(player);

        Entity enemy = createTargetableEntity(0, 0, -50);
        engine.addEntity(enemy);

        AtomicBoolean locked = new AtomicBoolean(false);
        eventBus.subscribe(TargetLockedEvent.class, e -> locked.set(true));

        // First update: detect soft target
        system.update(1f / 60f);
        assertEquals(enemy, target.softTarget);

        // Press T to lock
        flightInput.targetLockPressed = true;
        system.update(1f / 60f);

        assertEquals(enemy, target.lockedTarget, "Should lock onto soft target");
        assertTrue(locked.get(), "Should publish TargetLockedEvent");
    }

    @Test
    void unlockWhenPressingTOnLockedTarget() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        TargetingSystem system = new TargetingSystem(eventBus, CAM_POS, CAM_DIR);
        engine.addSystem(system);

        Entity player = createPilotingPlayer();
        PlayerTargetComponent target = new PlayerTargetComponent();
        player.add(target);
        ShipFlightInputComponent flightInput = player.getComponent(ShipFlightInputComponent.class);
        engine.addEntity(player);

        Entity enemy = createTargetableEntity(0, 0, -50);
        engine.addEntity(enemy);

        AtomicBoolean lost = new AtomicBoolean(false);
        eventBus.subscribe(TargetLostEvent.class, e -> lost.set(true));

        // Lock
        system.update(1f / 60f);
        flightInput.targetLockPressed = true;
        system.update(1f / 60f);
        assertEquals(enemy, target.lockedTarget);

        // Unlock (press T again while locked on same target)
        flightInput.targetLockPressed = true;
        system.update(1f / 60f);

        assertNull(target.lockedTarget, "Should unlock");
        assertTrue(lost.get(), "Should publish TargetLostEvent");
    }

    @Test
    void leadIndicatorCalculated() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        TargetingSystem system = new TargetingSystem(eventBus, CAM_POS, CAM_DIR);
        engine.addSystem(system);

        Entity player = createPilotingPlayer();
        PlayerTargetComponent target = new PlayerTargetComponent();
        player.add(target);
        engine.addEntity(player);

        Entity enemy = createTargetableEntity(0, 0, -100);
        engine.addEntity(enemy);

        // Soft target, then lock
        system.update(1f / 60f);
        ShipFlightInputComponent flightInput = player.getComponent(ShipFlightInputComponent.class);
        flightInput.targetLockPressed = true;
        system.update(1f / 60f);

        // Lead indicator should be at target position (velocity = 0)
        assertNotNull(target.lockedTarget);
        assertEquals(-100f, target.leadIndicatorPos.z, 1f);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Entity createPilotingPlayer() {
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = new Entity(); // dummy ship
        player.add(state);
        player.add(new ShipFlightInputComponent());
        TransformComponent t = new TransformComponent();
        t.position.set(0, 0, 0);
        player.add(t);
        return player;
    }

    private Entity createTargetableEntity(float x, float y, float z) {
        Entity entity = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(x, y, z);
        entity.add(t);
        entity.add(new ShipHardpointComponent());
        return entity;
    }
}
