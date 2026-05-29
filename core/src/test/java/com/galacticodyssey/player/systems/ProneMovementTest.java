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
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class ProneMovementTest {

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
    static void initBullet() { Bullet.init(); }

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
        physics.proneShape = new btCapsuleShape(0.3f, 0.0f);
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

    private void settle(Entity entity) {
        for (int i = 0; i < 120; i++) {
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
            engine.update(1f / 60f);
        }
    }

    private void cleanupEntity(Entity entity) {
        PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
        dynamicsWorld.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
        physics.proneShape.dispose();
    }

    @Test
    void standingToProne() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        input.proneToggleRequested = true;
        engine.update(1f / 60f);

        assertTrue(state.isProne, "Player should be prone after toggle");
        assertFalse(state.isCrouching, "Crouching should be false while prone");
        cleanupEntity(player);
    }

    @Test
    void crouchingToProne() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        input.crouch = true;
        engine.update(1f / 60f);
        assertTrue(state.isCrouching, "Should be crouching before prone toggle");

        input.proneToggleRequested = true;
        engine.update(1f / 60f);

        assertTrue(state.isProne, "Player should be prone after toggle from crouch");
        assertFalse(state.isCrouching, "Crouching should be cleared when prone");
        cleanupEntity(player);
    }

    @Test
    void proneToStandingWhenClear() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        input.proneToggleRequested = true;
        engine.update(1f / 60f);
        assertTrue(state.isProne);

        input.proneToggleRequested = true;
        engine.update(1f / 60f);

        assertFalse(state.isProne, "Player should be standing — no ceiling blocking");
        cleanupEntity(player);
    }

    @Test
    void proneToStandingBlockedByCeiling() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        // Go prone — body centre stays at settled standing height (~0.9 m)
        input.proneToggleRequested = true;
        engine.update(1f / 60f);
        assertTrue(state.isProne);

        // No physics step between toggles is deliberate: body centre stays at ~0.9 m (settled standing
        // position). Ceiling at 1.6 m sits between prone top (0.9+0.3=1.2 m) and standing top (0.9+0.9=1.8 m).
        btBoxShape ceilingShape = new btBoxShape(new Vector3(500, 0.1f, 500));
        btRigidBody.btRigidBodyConstructionInfo ceilingInfo =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, ceilingShape);
        btRigidBody ceilingBody = new btRigidBody(ceilingInfo);
        ceilingBody.setWorldTransform(new Matrix4().setToTranslation(0, 1.6f, 0));
        dynamicsWorld.addRigidBody(ceilingBody);
        ceilingInfo.dispose();

        input.proneToggleRequested = true;
        engine.update(1f / 60f);

        assertTrue(state.isProne, "Player should remain prone — ceiling blocks standing");

        dynamicsWorld.removeRigidBody(ceilingBody);
        ceilingBody.dispose();
        ceilingShape.dispose();
        cleanupEntity(player);
    }

    @Test
    void proneSpeedCapIsLowerThanCrouch() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        input.proneToggleRequested = true;
        engine.update(1f / 60f);

        input.moveForward = 1f;
        for (int i = 0; i < 120; i++) {
            engine.update(1f / 60f);
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        PhysicsBodyComponent physics = player.getComponent(PhysicsBodyComponent.class);
        Vector3 vel = physics.body.getLinearVelocity();
        float hSpeed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        assertTrue(hSpeed <= 1.1f,
            "Prone speed should be at most 1.1 m/s (near PRONE_SPEED=0.8), got " + hSpeed);
        assertTrue(hSpeed > 0.1f, "Player should be moving while prone");
        cleanupEntity(player);
    }

    @Test
    void staminaDoesNotDrainWhileProne() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        input.proneToggleRequested = true;
        engine.update(1f / 60f);
        assertTrue(state.isProne);

        float staminaBefore = state.currentStamina;
        input.moveForward = 1f;
        for (int i = 0; i < 60; i++) {
            engine.update(1f / 60f);
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        assertTrue(state.currentStamina >= staminaBefore,
            "Stamina must not drain while prone. Before=" + staminaBefore + " After=" + state.currentStamina);
        cleanupEntity(player);
    }
}
