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
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipFlightSystemNpcTest {

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    private btDefaultCollisionConfiguration config;
    private btCollisionDispatcher dispatcher;
    private btDbvtBroadphase broadphase;
    private btSequentialImpulseConstraintSolver solver;
    private btDiscreteDynamicsWorld world;
    private btRigidBody body;
    private btBoxShape shape;

    @AfterEach
    void tearDown() {
        if (body != null && world != null) world.removeRigidBody(body);
        if (body != null) { body.dispose(); body = null; }
        if (shape != null) { shape.dispose(); shape = null; }
        if (world != null) { world.dispose(); world = null; }
        if (solver != null) { solver.dispose(); solver = null; }
        if (broadphase != null) { broadphase.dispose(); broadphase = null; }
        if (dispatcher != null) { dispatcher.dispose(); dispatcher = null; }
        if (config != null) { config.dispose(); config = null; }
    }

    @Test
    void npcShipWithOwnInputAccelerates() {
        config = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(config);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, 0, 0));

        Engine engine = new Engine();
        ShipFlightSystem system = new ShipFlightSystem();
        engine.addSystem(system);

        Entity ship = new Entity();
        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        shape = new btBoxShape(new Vector3(1, 1, 1));
        physics.shape = shape;
        float mass = 10000f;
        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        body = new btRigidBody(info);
        physics.body = body;
        physics.body.setWorldTransform(new Matrix4().idt());
        physics.mass = mass;
        info.dispose();
        ship.add(physics);

        ShipFlightComponent flight = new ShipFlightComponent();
        flight.linearThrust = 50000;
        flight.strafeThrustFraction = 0.6f;
        flight.verticalThrustFraction = 0.6f;
        flight.pitchYawTorque = 20000;
        flight.rollTorque = 15000;
        flight.linearDrag = 0.1f;
        flight.angularDrag = 2.0f;
        ship.add(flight);

        ShipFlightInputComponent input = new ShipFlightInputComponent();
        input.throttle = 1f;
        ship.add(input);

        engine.addEntity(ship);
        world.addRigidBody(physics.body);

        float dt = 1f / 60f;
        for (int i = 0; i < 30; i++) {
            system.update(dt);
            world.stepSimulation(dt, 1, dt);
        }

        float speed = physics.body.getLinearVelocity().len();
        assertTrue(speed > 1f, "NPC ship should have accelerated, speed=" + speed);
    }
}
