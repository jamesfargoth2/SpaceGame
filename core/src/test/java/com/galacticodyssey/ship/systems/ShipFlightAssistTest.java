package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipFlightAssistTest {

    @BeforeAll static void initBullet() { Bullet.init(); }

    private btCollisionConfiguration cc;
    private btCollisionDispatcher disp;
    private btBroadphaseInterface bp;
    private btConstraintSolver solver;
    private btDiscreteDynamicsWorld world;
    private Entity ship;

    @AfterEach
    void tearDown() {
        if (ship != null) {
            PhysicsBodyComponent p = ship.getComponent(PhysicsBodyComponent.class);
            if (p != null && p.body != null) {
                world.removeRigidBody(p.body);
                p.body.dispose();
                p.shape.dispose();
            }
        }
        if (world != null) world.dispose();
        if (solver != null) solver.dispose();
        if (bp != null) bp.dispose();
        if (disp != null) disp.dispose();
        if (cc != null) cc.dispose();
    }

    /** Builds a flyable ship entity registered with engine+world, returns the system to step. */
    private ShipFlightSystem buildShip(Engine engine, float maxSpeed, boolean faOn,
                                       Vector3 initialVelocity) {
        cc = new btDefaultCollisionConfiguration();
        disp = new btCollisionDispatcher(cc);
        bp = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(disp, bp, solver, cc);
        world.setGravity(new Vector3(0, 0, 0));

        ShipFlightSystem system = new ShipFlightSystem();
        engine.addSystem(system);

        ship = new Entity();
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
        if (initialVelocity != null) physics.body.setLinearVelocity(initialVelocity);
        ship.add(physics);

        ShipFlightComponent flight = new ShipFlightComponent();
        // Thrust-to-mass = 150000/10000 = 15 m/s^2, enough for the FA controller to
        // converge to maxSpeed (and bleed lateral drift) inside the test's step window.
        flight.linearThrust = 150000f;
        flight.strafeThrustFraction = 0.6f;
        flight.verticalThrustFraction = 0.6f;
        flight.pitchYawTorque = 20000f;
        flight.rollTorque = 15000f;
        flight.flightAssistEnabled = faOn;
        ship.add(flight);

        ShipDataComponent data = new ShipDataComponent();
        data.maxSpeed = maxSpeed;
        data.maxTurnRate = 90f;
        ship.add(data);

        ship.add(new ShipFlightInputComponent());

        engine.addEntity(ship);
        world.addRigidBody(physics.body);
        return system;
    }

    private void step(ShipFlightSystem system, float seconds) {
        float dt = 1f / 60f;
        int steps = Math.round(seconds / dt);
        for (int i = 0; i < steps; i++) {
            system.update(dt);
            world.stepSimulation(dt, 1, dt);
        }
    }

    @Test
    void fullThrottleConvergesToMaxSpeedAndHolds() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        ShipFlightInputComponent in = ship.getComponent(ShipFlightInputComponent.class);
        in.throttle = 1f;

        step(system, 10f);

        float speed = ship.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().len();
        assertEquals(100f, speed, 5f, "should hold near maxSpeed, got " + speed);
    }

    @Test
    void halfThrottleConvergesToHalfMaxSpeed() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        ship.getComponent(ShipFlightInputComponent.class).throttle = 0.5f;

        step(system, 10f);

        float speed = ship.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().len();
        assertEquals(50f, speed, 5f, "got " + speed);
    }

    @Test
    void faBleedsLateralVelocity() {
        Engine engine = new Engine();
        // Nose points -Z; give it sideways (+X) drift, zero throttle.
        ShipFlightSystem system = buildShip(engine, 100f, true, new Vector3(40f, 0f, 0f));
        ship.getComponent(ShipFlightInputComponent.class).throttle = 0f;

        step(system, 8f);

        float lateral = Math.abs(ship.getComponent(PhysicsBodyComponent.class)
            .body.getLinearVelocity().x);
        assertTrue(lateral < 5f, "FA should bleed lateral drift, |vx|=" + lateral);
    }

    @Test
    void flightAssistOffPreservesLateralMomentum() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, false, new Vector3(40f, 0f, 0f));
        ship.getComponent(ShipFlightInputComponent.class).throttle = 0f;

        step(system, 8f);

        float lateral = ship.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().x;
        assertEquals(40f, lateral, 2f, "FA-off must preserve drift, vx=" + lateral);
    }

    @Test
    void reverseThrottleProducesBackwardSpeed() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        ship.getComponent(ShipFlightInputComponent.class).throttle = -0.4f;

        step(system, 10f);

        // Nose is -Z; reverse means +Z world velocity.
        float vz = ship.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().z;
        assertTrue(vz > 20f, "reverse should drive +Z, vz=" + vz);
    }
}
