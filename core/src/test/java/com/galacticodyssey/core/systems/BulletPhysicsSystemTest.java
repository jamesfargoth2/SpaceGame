package com.galacticodyssey.core.systems;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btSphereShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.OriginRebasedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BulletPhysicsSystemTest {

    private EventBus eventBus;
    private BulletPhysicsSystem physicsSystem;

    @BeforeAll
    static void initBullet() {
        Bullet.init();
    }

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        physicsSystem = new BulletPhysicsSystem(eventBus);
        physicsSystem.initialize();
    }

    @AfterEach
    void tearDown() {
        physicsSystem.dispose();
    }

    @Test
    void worldHasGravity() {
        Vector3 gravity = physicsSystem.getDynamicsWorld().getGravity();
        assertEquals(-9.81f, gravity.y, 0.01f);
        assertEquals(0f, gravity.x, 0.01f);
        assertEquals(0f, gravity.z, 0.01f);
    }

    @Test
    void dynamicBodyFallsUnderGravity() {
        btCollisionShape shape = new btSphereShape(0.5f);
        Vector3 inertia = new Vector3();
        shape.calculateLocalInertia(1f, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(1f, null, shape, inertia);
        btRigidBody body = new btRigidBody(info);
        body.setWorldTransform(new Matrix4().setToTranslation(0, 10, 0));

        physicsSystem.getDynamicsWorld().addRigidBody(body);

        for (int i = 0; i < 60; i++) {
            physicsSystem.stepWorld(1f / 60f);
        }

        Matrix4 transform = new Matrix4();
        body.getWorldTransform(transform);
        Vector3 pos = new Vector3();
        transform.getTranslation(pos);

        assertTrue(pos.y < 10f, "Body should have fallen from y=10, actual y=" + pos.y);

        physicsSystem.getDynamicsWorld().removeRigidBody(body);
        body.dispose();
        info.dispose();
        shape.dispose();
    }

    @Test
    void originRebaseShiftsBodyTransforms() {
        btCollisionShape shape = new btBoxShape(new Vector3(1, 1, 1));
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape);
        btRigidBody body = new btRigidBody(info);
        body.setWorldTransform(new Matrix4().setToTranslation(100, 0, 0));

        physicsSystem.getDynamicsWorld().addRigidBody(body);
        physicsSystem.addManagedBody(body);

        eventBus.publish(new OriginRebasedEvent(100f, 0f, 0f));

        Matrix4 transform = new Matrix4();
        body.getWorldTransform(transform);
        Vector3 pos = new Vector3();
        transform.getTranslation(pos);

        assertEquals(0f, pos.x, 0.01f);

        physicsSystem.getDynamicsWorld().removeRigidBody(body);
        physicsSystem.removeManagedBody(body);
        body.dispose();
        info.dispose();
        shape.dispose();
    }
}
