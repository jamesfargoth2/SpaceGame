package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.OriginRebasedEvent;

import java.util.HashMap;
import java.util.Map;

public class BulletPhysicsSystem extends EntitySystem implements Disposable {

    private static final float FIXED_TIMESTEP = 1f / 60f;
    private static final int MAX_SUBSTEPS = 3;

    private final EventBus eventBus;
    private final Array<btRigidBody> managedBodies = new Array<>();
    private final Map<btRigidBody, Entity> bodyEntityMap = new HashMap<>();
    private final EventBus.EventListener<OriginRebasedEvent> rebaseListener = this::onOriginRebased;

    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;
    private btDiscreteDynamicsWorld dynamicsWorld;

    private final Vector3 tempVec = new Vector3();
    private final Matrix4 tempMat = new Matrix4();

    public BulletPhysicsSystem(EventBus eventBus) {
        super(2);
        this.eventBus = eventBus;
    }

    public void initialize() {
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, -9.81f, 0));

        eventBus.subscribe(OriginRebasedEvent.class, rebaseListener);
    }

    @Override
    public void update(float deltaTime) {
        stepWorld(deltaTime);
    }

    public void stepWorld(float deltaTime) {
        dynamicsWorld.stepSimulation(deltaTime, MAX_SUBSTEPS, FIXED_TIMESTEP);
    }

    private void onOriginRebased(OriginRebasedEvent event) {
        for (int i = 0; i < managedBodies.size; i++) {
            btRigidBody body = managedBodies.get(i);
            body.getWorldTransform(tempMat);
            tempMat.getTranslation(tempVec);
            tempVec.sub(event.deltaX, event.deltaY, event.deltaZ);
            tempMat.setTranslation(tempVec);
            body.setWorldTransform(tempMat);
        }
    }

    /** Tracks a body for origin rebasing; no entity association (terrain, world geometry). */
    public void addManagedBody(btRigidBody body) {
        addManagedBody(body, null);
    }

    /** Tracks a body for origin rebasing and maps it back to its owning entity for raycast hits. */
    public void addManagedBody(btRigidBody body, Entity owner) {
        managedBodies.add(body);
        if (owner != null) bodyEntityMap.put(body, owner);
    }

    public void removeManagedBody(btRigidBody body) {
        managedBodies.removeValue(body, true);
        bodyEntityMap.remove(body);
    }

    /** Returns the entity that owns {@code body}, or null for world geometry. */
    public Entity getEntityForBody(btRigidBody body) {
        return bodyEntityMap.get(body);
    }

    public btDiscreteDynamicsWorld getDynamicsWorld() {
        return dynamicsWorld;
    }

    @Override
    public void dispose() {
        eventBus.unsubscribe(OriginRebasedEvent.class, rebaseListener);
        if (dynamicsWorld != null) dynamicsWorld.dispose();
        if (solver != null) solver.dispose();
        if (broadphase != null) broadphase.dispose();
        if (dispatcher != null) dispatcher.dispose();
        if (collisionConfig != null) collisionConfig.dispose();
    }
}
