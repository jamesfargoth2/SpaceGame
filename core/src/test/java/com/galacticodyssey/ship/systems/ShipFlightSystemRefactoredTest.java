package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.EngineSpecComponent;
import com.galacticodyssey.ship.components.FuelTankComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipFlightSystemRefactoredTest {

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;
    private btDiscreteDynamicsWorld world;

    @AfterEach
    void tearDownWorld() {
        if (world != null) world.dispose();
        if (solver != null) solver.dispose();
        if (broadphase != null) broadphase.dispose();
        if (dispatcher != null) dispatcher.dispose();
        if (collisionConfig != null) collisionConfig.dispose();
    }

    private btDiscreteDynamicsWorld createWorld() {
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        world.setGravity(new Vector3(0, 0, 0));
        return world;
    }

    @Test
    void readsFromShipFlightInputComponent() {
        createWorld();
        Engine engine = new Engine();
        ShipFlightSystem system = new ShipFlightSystem();
        engine.addSystem(system);

        Entity ship = createShipEntity(world);
        Entity player = createPilotingPlayer(ship);
        ShipFlightInputComponent flightInput = new ShipFlightInputComponent();
        flightInput.throttle = 1f;
        player.add(flightInput);

        engine.addEntity(ship);
        engine.addEntity(player);

        system.update(1f / 60f);
        world.stepSimulation(1f / 60f, 1, 1f / 60f);

        PhysicsBodyComponent physics = ship.getComponent(PhysicsBodyComponent.class);
        float speed = physics.body.getLinearVelocity().len();
        assertTrue(speed > 0, "Ship should gain velocity from ShipFlightInputComponent.throttle");

        physics.body.dispose();
        physics.shape.dispose();
    }

    @Test
    void consumesFuelProportionalToThrust() {
        createWorld();
        Engine engine = new Engine();
        ShipFlightSystem system = new ShipFlightSystem();
        engine.addSystem(system);

        Entity ship = createShipEntity(world);
        FuelTankComponent fuel = new FuelTankComponent();
        fuel.maxMass = 1000f;
        fuel.currentMass = 1000f;
        ship.add(fuel);
        EngineSpecComponent engineSpec = new EngineSpecComponent();
        engineSpec.maxThrust = 50000f;
        engineSpec.isp = 3200f;
        engineSpec.throttleResponseRate = 100f;
        ship.add(engineSpec);

        Entity player = createPilotingPlayer(ship);
        ShipFlightInputComponent flightInput = new ShipFlightInputComponent();
        flightInput.throttle = 1f;
        player.add(flightInput);

        engine.addEntity(ship);
        engine.addEntity(player);

        system.update(1f);

        assertTrue(fuel.currentMass < 1000f, "Fuel should be consumed when thrusting");

        ship.getComponent(PhysicsBodyComponent.class).body.dispose();
        ship.getComponent(PhysicsBodyComponent.class).shape.dispose();
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
        flight.linearThrust = 50000;
        flight.strafeThrustFraction = 0.3f;
        flight.verticalThrustFraction = 0.4f;
        flight.pitchYawTorque = 20000;
        flight.rollTorque = 15000;
        flight.linearDrag = 0.1f;
        flight.angularDrag = 2.0f;
        ship.add(flight);
        return ship;
    }

    private Entity createPilotingPlayer(Entity ship) {
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new PlayerInputComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = ship;
        player.add(state);
        return player;
    }
}
