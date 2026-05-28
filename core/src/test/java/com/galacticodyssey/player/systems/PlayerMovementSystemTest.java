// core/src/test/java/com/galacticodyssey/player/systems/PlayerMovementSystemTest.java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerMovementSystemTest {

    private Engine engine;
    private PlayerMovementSystem movementSystem;
    private btDiscreteDynamicsWorld dynamicsWorld;
    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;

    private btCollisionShape groundShape;
    private btRigidBody groundBody;

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
        dynamicsWorld.setGravity(new Vector3(0, -9.81f, 0));

        groundShape = new btBoxShape(new Vector3(500, 0.5f, 500));
        btRigidBody.btRigidBodyConstructionInfo groundInfo =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, groundShape);
        groundBody = new btRigidBody(groundInfo);
        groundBody.setWorldTransform(new Matrix4().setToTranslation(0, -0.5f, 0));
        dynamicsWorld.addRigidBody(groundBody);
        groundInfo.dispose();

        engine = new Engine();
        movementSystem = new PlayerMovementSystem(dynamicsWorld);
        // Place planet centre far below so localUp stays (0,1,0) on the flat test ground.
        movementSystem.setPlanetCenter(new Vector3(0f, -1_000_000f, 0f));
        engine.addSystem(movementSystem);
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        engine.removeAllSystems();
        dynamicsWorld.removeRigidBody(groundBody);
        groundBody.dispose();
        groundShape.dispose();
        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
    }

    private Entity createPlayerEntity(float startY) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(0, startY, 0);

        PlayerInputComponent input = new PlayerInputComponent();
        MovementStateComponent movement = new MovementStateComponent();
        FPSCameraComponent camera = new FPSCameraComponent();

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btCapsuleShape(0.3f, 1.2f);
        physics.mass = 80f;
        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(physics.mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(physics.mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(0, startY, 0));
        physics.body.setAngularFactor(new Vector3(0, 0, 0));
        physics.body.setFriction(1.0f);
        physics.body.setRestitution(0f);
        dynamicsWorld.addRigidBody(physics.body);
        info.dispose();

        entity.add(transform);
        entity.add(input);
        entity.add(movement);
        entity.add(camera);
        entity.add(physics);

        engine.addEntity(entity);
        return entity;
    }

    @Test
    void playerOnGroundIsGrounded() {
        Entity player = createPlayerEntity(1.0f);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);

        for (int i = 0; i < 120; i++) {
            engine.update(1f / 60f);
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        engine.update(1f / 60f);
        assertTrue(state.isGrounded, "Player resting on ground should be grounded");

        PhysicsBodyComponent physics = player.getComponent(PhysicsBodyComponent.class);
        dynamicsWorld.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
    }

    @Test
    void forwardInputAppliesForce() {
        Entity player = createPlayerEntity(1.0f);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        PhysicsBodyComponent physics = player.getComponent(PhysicsBodyComponent.class);

        for (int i = 0; i < 120; i++) {
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
            engine.update(1f / 60f);
        }

        input.moveForward = 1f;

        for (int i = 0; i < 30; i++) {
            engine.update(1f / 60f);
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        Vector3 velocity = physics.body.getLinearVelocity();
        float horizontalSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        assertTrue(horizontalSpeed > 0.1f,
            "Player should be moving horizontally, speed=" + horizontalSpeed);

        dynamicsWorld.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
    }

    @Test
    void staminaDrainsWhileSprinting() {
        Entity player = createPlayerEntity(1.0f);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PhysicsBodyComponent physics = player.getComponent(PhysicsBodyComponent.class);

        for (int i = 0; i < 120; i++) {
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
            engine.update(1f / 60f);
        }

        float staminaBefore = state.currentStamina;
        input.moveForward = 1f;
        input.sprint = true;

        for (int i = 0; i < 60; i++) {
            engine.update(1f / 60f);
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        assertTrue(state.currentStamina < staminaBefore,
            "Stamina should drain while sprinting");

        dynamicsWorld.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
    }

    @Test
    void jumpAppliesUpwardImpulse() {
        Entity player = createPlayerEntity(1.0f);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        PhysicsBodyComponent physics = player.getComponent(PhysicsBodyComponent.class);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);

        for (int i = 0; i < 120; i++) {
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
            engine.update(1f / 60f);
        }

        input.jumpRequested = true;
        engine.update(1f / 60f);
        dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);

        Vector3 velocity = physics.body.getLinearVelocity();
        assertTrue(velocity.y > 0f, "Player should have upward velocity after jump, vy=" + velocity.y);

        assertFalse(input.jumpRequested, "Jump flag should be consumed");

        dynamicsWorld.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
    }
}
