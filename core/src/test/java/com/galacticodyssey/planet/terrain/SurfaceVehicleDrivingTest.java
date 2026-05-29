package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceVehicleDrivingTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;
    private btDiscreteDynamicsWorld world;
    private btRigidBody vehicleBody;
    private btCollisionShape vehicleShape;

    @AfterEach
    void tearDownWorld() {
        if (vehicleBody != null) {
            world.removeRigidBody(vehicleBody);
            vehicleBody.dispose();
        }
        if (vehicleShape != null) vehicleShape.dispose();
        if (world != null) world.dispose();
        if (solver != null) solver.dispose();
        if (broadphase != null) broadphase.dispose();
        if (dispatcher != null) dispatcher.dispose();
        if (collisionConfig != null) collisionConfig.dispose();
    }

    private Entity makeVehicle(Engine engine, float mass) {
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        world.setGravity(new Vector3(0, 0, 0));

        Entity e = new Entity();
        TransformComponent t = new TransformComponent();
        e.add(t);
        GroundVehicleComponent gv = new GroundVehicleComponent();
        gv.mass = mass; gv.maxDriveForce = 8000f; gv.maxSteerAngle = 35f;
        gv.wheelbase = 3f; gv.trackWidth = 2f;
        e.add(gv);
        PhysicsBodyComponent p = new PhysicsBodyComponent();
        p.shape = new btBoxShape(new Vector3(1, 0.5f, 1.5f));
        p.mass = mass;
        Vector3 inertia = new Vector3();
        p.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, p.shape, inertia);
        p.body = new btRigidBody(info);
        p.body.setWorldTransform(new Matrix4().idt());
        info.dispose();
        e.add(p);
        engine.addEntity(e);
        world.addRigidBody(p.body);
        vehicleBody = p.body;
        vehicleShape = p.shape;
        return e;
    }

    @Test
    void fullThrottleAccelerates() {
        Engine engine = new Engine();
        SurfaceVehicleSystem sys = new SurfaceVehicleSystem(null, new EventBus());
        engine.addSystem(sys);
        Entity v = makeVehicle(engine, 1000f);
        v.getComponent(GroundVehicleComponent.class).throttleInput = 1f;
        float dt = 1f / 60f;
        for (int i = 0; i < 30; i++) { sys.update(dt); world.stepSimulation(dt, 1, dt); }
        float speed = v.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().len();
        assertTrue(speed > 0.5f, "expected motion under throttle, speed=" + speed);
    }

    @Test
    void zeroThrottleStaysStill() {
        Engine engine = new Engine();
        SurfaceVehicleSystem sys = new SurfaceVehicleSystem(null, new EventBus());
        engine.addSystem(sys);
        Entity v = makeVehicle(engine, 1000f);
        v.getComponent(GroundVehicleComponent.class).throttleInput = 0f;
        float dt = 1f / 60f;
        for (int i = 0; i < 30; i++) { sys.update(dt); world.stepSimulation(dt, 1, dt); }
        float speed = v.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().len();
        assertTrue(speed < 0.01f, "expected no motion at zero throttle, speed=" + speed);
    }

    @Test
    void steerInputProducesYaw() {
        Engine engine = new Engine();
        SurfaceVehicleSystem sys = new SurfaceVehicleSystem(null, new EventBus());
        engine.addSystem(sys);
        Entity v = makeVehicle(engine, 1000f);
        v.getComponent(GroundVehicleComponent.class).steerInput = 1f;
        float dt = 1f / 60f;
        for (int i = 0; i < 30; i++) { sys.update(dt); world.stepSimulation(dt, 1, dt); }
        float yawRate = Math.abs(v.getComponent(PhysicsBodyComponent.class).body.getAngularVelocity().y);
        assertTrue(yawRate > 0.001f, "expected yaw from steering, yawRate=" + yawRate);
    }
}
