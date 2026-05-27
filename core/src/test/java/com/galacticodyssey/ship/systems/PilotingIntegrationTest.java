package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.events.PlayerStartPilotingEvent;
import com.galacticodyssey.core.events.PlayerStopPilotingEvent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.player.systems.PilotTransitionSystem;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ui.events.CockpitHUDHideEvent;
import com.galacticodyssey.ui.events.CockpitHUDShowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test covering the full pilot enter/fly/exit lifecycle across
 * {@link PilotTransitionSystem} and {@link ShipFlightSystem}.
 */
class PilotingIntegrationTest {

    @BeforeAll
    static void initBullet() {
        Bullet.init();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private btDiscreteDynamicsWorld createWorld() {
        btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
        btDbvtBroadphase broadphase = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        btDiscreteDynamicsWorld world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, 0, 0));
        return world;
    }

    private Entity createShipEntity(btDiscreteDynamicsWorld world) {
        Entity ship = new Entity();

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(1, 1, 1));
        float mass = 10000f;
        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().idt());
        physics.mass = mass;
        info.dispose();
        ship.add(physics);
        world.addRigidBody(physics.body);

        ShipFlightComponent flight = new ShipFlightComponent();
        flight.linearThrust = 50000f;
        flight.strafeThrustFraction = 0.3f;
        flight.verticalThrustFraction = 0.4f;
        flight.pitchYawTorque = 20000f;
        flight.rollTorque = 15000f;
        flight.linearDrag = 0.1f;
        flight.angularDrag = 2.0f;
        ship.add(flight);

        return ship;
    }

    private Entity createPlayerEntity() {
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new PlayerInputComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        player.add(state);
        return player;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * Verifies the full lifecycle: enter pilot seat → apply thrust → verify motion → exit seat.
     */
    @Test
    void fullPilotingLoop() {
        // Setup
        btDiscreteDynamicsWorld world = createWorld();
        Engine engine = new Engine();
        EventBus eventBus = new EventBus();

        PilotTransitionSystem pilotTransition = new PilotTransitionSystem(eventBus);
        ShipFlightSystem flightSystem = new ShipFlightSystem();
        engine.addSystem(pilotTransition);
        engine.addSystem(flightSystem);

        Entity ship = createShipEntity(world);
        Entity player = createPlayerEntity();
        PlayerStateComponent state = player.getComponent(PlayerStateComponent.class);
        state.currentShip = ship;
        engine.addEntity(ship);
        engine.addEntity(player);

        // Step 1: no flight input component before entering
        assertNull(player.getComponent(ShipFlightInputComponent.class),
            "ShipFlightInputComponent should not exist before entering pilot seat");

        // Step 2: publish start-piloting — PilotTransitionSystem handles it synchronously
        eventBus.publish(new PlayerStartPilotingEvent(player, ship));

        ShipFlightInputComponent flightInput = player.getComponent(ShipFlightInputComponent.class);
        assertNotNull(flightInput, "ShipFlightInputComponent should be added when entering pilot seat");

        // Step 3: apply throttle and tick one frame
        flightInput.throttle = 1f;
        engine.update(1f / 60f);
        world.stepSimulation(1f / 60f, 1, 1f / 60f);

        // Step 4: ship must have gained velocity
        PhysicsBodyComponent physics = ship.getComponent(PhysicsBodyComponent.class);
        float speed = physics.body.getLinearVelocity().len();
        assertTrue(speed > 0, "Ship should have gained velocity from throttle input. Speed=" + speed);

        // Step 5: publish stop-piloting
        eventBus.publish(new PlayerStopPilotingEvent(player, ship));

        // Step 6: flight input component must be gone
        assertNull(player.getComponent(ShipFlightInputComponent.class),
            "ShipFlightInputComponent should be removed when exiting pilot seat");

        // Cleanup
        physics.body.dispose();
        physics.shape.dispose();
        world.dispose();
    }

    /**
     * Verifies that HUD events are published on enter and exit transitions.
     */
    @Test
    void pilotingEventsFireCorrectly() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        PilotTransitionSystem system = new PilotTransitionSystem(eventBus);
        engine.addSystem(system);

        AtomicBoolean hudShown = new AtomicBoolean(false);
        AtomicBoolean hudHidden = new AtomicBoolean(false);
        eventBus.subscribe(CockpitHUDShowEvent.class, e -> hudShown.set(true));
        eventBus.subscribe(CockpitHUDHideEvent.class, e -> hudHidden.set(true));

        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new PlayerInputComponent());
        player.add(new PlayerStateComponent());
        Entity ship = new Entity();
        engine.addEntity(player);

        eventBus.publish(new PlayerStartPilotingEvent(player, ship));
        assertTrue(hudShown.get(), "CockpitHUDShowEvent should fire on enter");

        eventBus.publish(new PlayerStopPilotingEvent(player, ship));
        assertTrue(hudHidden.get(), "CockpitHUDHideEvent should fire on exit");
    }
}
