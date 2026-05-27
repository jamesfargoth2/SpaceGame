package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class RadialGravitySystemTest {
    private Engine engine;
    private btDiscreteDynamicsWorld dynamicsWorld;
    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;
    private RadialGravitySystem gravitySystem;

    @BeforeAll
    static void initBullet() {
        Bullet.init();
    }

    @BeforeEach
    void setUp() {
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, 0, 0));

        engine = new Engine();
        gravitySystem = new RadialGravitySystem(dynamicsWorld, new Vector3(0, 0, 0), 9.81f);
        engine.addSystem(gravitySystem);
    }

    @AfterEach
    void tearDown() {
        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
    }

    @Test
    void appliesForceTowardPlanetCenter() {
        Entity entity = createPhysicsEntity(0f, 6371f, 0f, 80f);
        engine.addEntity(entity);

        PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
        physics.body.clearForces();

        engine.update(1f / 60f);

        Vector3 totalForce = physics.body.getTotalForce();
        assertTrue(totalForce.y < 0, "Gravity should pull downward (toward center) but got y=" + totalForce.y);
        assertEquals(0f, totalForce.x, 0.01f, "No x-force expected for entity directly above center");
        assertEquals(0f, totalForce.z, 0.01f, "No z-force expected for entity directly above center");
    }

    @Test
    void gravityDirectionMatchesEntityPosition() {
        Entity entity = createPhysicsEntity(6371f, 0f, 0f, 80f);
        engine.addEntity(entity);

        PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
        physics.body.clearForces();

        engine.update(1f / 60f);

        Vector3 totalForce = physics.body.getTotalForce();
        assertTrue(totalForce.x < 0, "Gravity should pull in -x direction but got x=" + totalForce.x);
        assertEquals(0f, totalForce.y, 0.01f);
        assertEquals(0f, totalForce.z, 0.01f);
    }

    @Test
    void skipsPilotingEntities() {
        Entity entity = createPhysicsEntity(0f, 6371f, 0f, 80f);
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerStateComponent.PlayerMode.PILOTING;
        entity.add(state);
        engine.addEntity(entity);

        PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
        physics.body.clearForces();

        engine.update(1f / 60f);

        Vector3 totalForce = physics.body.getTotalForce();
        assertEquals(0f, totalForce.len(), 0.01f, "No gravity should apply to piloting entities");
    }

    @Test
    void forceMagnitudeIsGravityTimesMass() {
        float mass = 80f;
        Entity entity = createPhysicsEntity(0f, 6371f, 0f, mass);
        engine.addEntity(entity);

        PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
        physics.body.clearForces();

        engine.update(1f / 60f);

        Vector3 totalForce = physics.body.getTotalForce();
        float expectedMag = 9.81f * mass;
        assertEquals(expectedMag, totalForce.len(), 0.1f,
            "Force magnitude should be g * mass");
    }

    private Entity createPhysicsEntity(float x, float y, float z, float mass) {
        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.mass = mass;
        physics.shape = new btBoxShape(new Vector3(0.5f, 0.5f, 0.5f));

        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(x, y, z));
        info.dispose();

        dynamicsWorld.addRigidBody(physics.body);
        entity.add(physics);

        return entity;
    }
}
