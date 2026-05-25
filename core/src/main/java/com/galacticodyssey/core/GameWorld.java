// core/src/main/java/com/galacticodyssey/core/GameWorld.java
package com.galacticodyssey.core;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.BulletPhysicsSystem;
import com.galacticodyssey.core.systems.PhysicsBodySystem;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.systems.CameraSystem;
import com.galacticodyssey.player.systems.PlayerInputSystem;
import com.galacticodyssey.player.systems.PlayerMovementSystem;
import com.galacticodyssey.ui.systems.DebugHudSystem;

public class GameWorld implements Disposable {

    private final Engine engine;
    private final EventBus eventBus;
    private final CoordinateManager coordinateManager;
    private final BulletPhysicsSystem bulletPhysicsSystem;
    private final PhysicsBodySystem physicsBodySystem;
    private final PlayerInputSystem playerInputSystem;
    private final PlayerMovementSystem playerMovementSystem;
    private final CameraSystem cameraSystem;
    private final DebugHudSystem debugHudSystem;

    private final Array<Disposable> disposables = new Array<>();

    public GameWorld(EventBus eventBus, CoordinateManager coordinateManager) {
        this.engine = new Engine();
        this.eventBus = eventBus;
        this.coordinateManager = coordinateManager;

        bulletPhysicsSystem = new BulletPhysicsSystem(eventBus);
        bulletPhysicsSystem.initialize();

        physicsBodySystem = new PhysicsBodySystem();
        playerInputSystem = new PlayerInputSystem();
        playerMovementSystem = new PlayerMovementSystem(bulletPhysicsSystem.getDynamicsWorld());
        cameraSystem = new CameraSystem();
        debugHudSystem = new DebugHudSystem(coordinateManager);

        engine.addSystem(playerInputSystem);
        engine.addSystem(playerMovementSystem);
        engine.addSystem(bulletPhysicsSystem);
        engine.addSystem(physicsBodySystem);
        engine.addSystem(cameraSystem);
        engine.addSystem(debugHudSystem);
    }

    public void initializeSystems(PerspectiveCamera camera) {
        playerInputSystem.initialize();
        cameraSystem.setCamera(camera);
        debugHudSystem.initialize();
    }

    public Entity createPlayerEntity(float spawnX, float spawnY, float spawnZ) {
        Entity player = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(spawnX, spawnY, spawnZ);
        player.add(transform);

        player.add(new PlayerTagComponent());
        player.add(new PlayerInputComponent());
        player.add(new MovementStateComponent());
        player.add(new FPSCameraComponent());

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btCapsuleShape(0.3f, 1.2f);
        physics.mass = 80f;
        physics.friction = 1.0f;
        physics.restitution = 0f;

        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(physics.mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(physics.mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(spawnX, spawnY, spawnZ));
        physics.body.setAngularFactor(new Vector3(0, 0, 0));
        physics.body.setFriction(physics.friction);
        physics.body.setRestitution(physics.restitution);
        info.dispose();

        player.add(physics);

        bulletPhysicsSystem.getDynamicsWorld().addRigidBody(physics.body);
        bulletPhysicsSystem.addManagedBody(physics.body);

        engine.addEntity(player);
        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(physics.body);
            physics.body.dispose();
            physics.shape.dispose();
        });

        return player;
    }

    public Entity createStaticBox(float x, float y, float z, float halfExtent) {
        return createBox(x, y, z, halfExtent, 0f);
    }

    public Entity createDynamicBox(float x, float y, float z, float halfExtent, float mass) {
        return createBox(x, y, z, halfExtent, mass);
    }

    private Entity createBox(float x, float y, float z, float halfExtent, float mass) {
        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(halfExtent, halfExtent, halfExtent));
        physics.mass = mass;

        Vector3 inertia = new Vector3();
        if (mass > 0) {
            physics.shape.calculateLocalInertia(mass, inertia);
        }
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(x, y, z));
        physics.body.setFriction(0.8f);
        info.dispose();

        entity.add(physics);

        bulletPhysicsSystem.getDynamicsWorld().addRigidBody(physics.body);
        bulletPhysicsSystem.addManagedBody(physics.body);

        engine.addEntity(entity);
        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(physics.body);
            physics.body.dispose();
            physics.shape.dispose();
        });

        return entity;
    }

    public void addTerrainBody(btRigidBody terrainBody) {
        bulletPhysicsSystem.getDynamicsWorld().addRigidBody(terrainBody);
        bulletPhysicsSystem.addManagedBody(terrainBody);
        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(terrainBody);
            terrainBody.dispose();
        });
    }

    public void update(float delta) {
        engine.update(delta);

        Entity player = engine.getEntitiesFor(
            com.badlogic.ashley.core.Family.all(PlayerTagComponent.class, TransformComponent.class).get()).first();
        TransformComponent t = player.getComponent(TransformComponent.class);
        coordinateManager.checkRebase(t.position);
    }

    public void resize(int width, int height) {
        debugHudSystem.resize(width, height);
    }

    public BulletPhysicsSystem getBulletPhysicsSystem() {
        return bulletPhysicsSystem;
    }

    @Override
    public void dispose() {
        for (int i = disposables.size - 1; i >= 0; i--) {
            disposables.get(i).dispose();
        }
        disposables.clear();
        debugHudSystem.dispose();
        bulletPhysicsSystem.dispose();
    }
}
