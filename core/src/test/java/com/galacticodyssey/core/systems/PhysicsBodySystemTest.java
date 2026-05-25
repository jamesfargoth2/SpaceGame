package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsBodySystemTest {

    private Engine engine;
    private PhysicsBodySystem physicsBodySystem;
    private btBoxShape shape;
    private btRigidBody.btRigidBodyConstructionInfo constructionInfo;

    @BeforeAll
    static void initBullet() {
        Bullet.init();
    }

    @BeforeEach
    void setUp() {
        engine = new Engine();
        physicsBodySystem = new PhysicsBodySystem();
        engine.addSystem(physicsBodySystem);

        shape = new btBoxShape(new Vector3(1, 1, 1));
        constructionInfo = new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape);
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        engine.removeAllSystems();
        constructionInfo.dispose();
        shape.dispose();
    }

    @Test
    void syncsCopyRigidBodyPositionToTransform() {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        PhysicsBodyComponent physicsBody = new PhysicsBodyComponent();
        physicsBody.body = new btRigidBody(constructionInfo);
        physicsBody.body.setWorldTransform(new Matrix4().setToTranslation(5f, 10f, 15f));
        physicsBody.shape = shape;

        entity.add(transform);
        entity.add(physicsBody);
        engine.addEntity(entity);

        engine.update(1f / 60f);

        assertEquals(5f, transform.position.x, 0.01f);
        assertEquals(10f, transform.position.y, 0.01f);
        assertEquals(15f, transform.position.z, 0.01f);

        physicsBody.body.dispose();
    }

    @Test
    void syncsCopyRigidBodyRotationToTransform() {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        PhysicsBodyComponent physicsBody = new PhysicsBodyComponent();
        physicsBody.body = new btRigidBody(constructionInfo);
        Matrix4 mat = new Matrix4();
        mat.setToRotation(Vector3.Y, 90f);
        mat.setTranslation(0, 0, 0);
        physicsBody.body.setWorldTransform(mat);
        physicsBody.shape = shape;

        entity.add(transform);
        entity.add(physicsBody);
        engine.addEntity(entity);

        engine.update(1f / 60f);

        float yaw = transform.rotation.getYaw();
        assertEquals(90f, Math.abs(yaw), 1f);

        physicsBody.body.dispose();
    }
}
