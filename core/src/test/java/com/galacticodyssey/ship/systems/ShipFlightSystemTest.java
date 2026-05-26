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
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipFlightSystemTest {

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    @Test
    void forwardThrustIncreasesVelocity() {
        // Build a minimal dynamics world so stepSimulation can integrate forces into velocity
        btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
        btDbvtBroadphase broadphase = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        btDiscreteDynamicsWorld world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, 0, 0)); // No gravity in space

        Engine engine = new Engine();
        ShipFlightSystem system = new ShipFlightSystem();
        engine.addSystem(system);

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

        ShipFlightComponent flight = new ShipFlightComponent();
        flight.linearThrust = 50000;
        flight.strafeThrustFraction = 0.6f;
        flight.verticalThrustFraction = 0.6f;
        flight.pitchYawTorque = 20000;
        flight.rollTorque = 15000;
        flight.linearDrag = 0.3f;
        flight.angularDrag = 2.0f;
        ship.add(flight);

        PlayerInputComponent input = new PlayerInputComponent();
        input.moveForward = 1f;
        ship.add(input);

        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = ship;
        ship.add(state);

        engine.addEntity(ship);

        // Add body to dynamics world so forces can be integrated into velocity
        world.addRigidBody(physics.body);

        // Apply thrust forces via the system
        float dt = 1f / 60f;
        system.update(dt);

        // Step the physics world to integrate forces into velocity
        world.stepSimulation(dt, 1, dt);

        Vector3 vel = physics.body.getLinearVelocity();
        float speed = vel.len();
        assertTrue(speed > 0, "Ship should have gained velocity from thrust, speed=" + speed);

        world.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
        world.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        config.dispose();
    }
}
