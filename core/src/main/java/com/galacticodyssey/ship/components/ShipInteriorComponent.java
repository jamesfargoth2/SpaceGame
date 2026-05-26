package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.ship.InteriorLayout;

public class ShipInteriorComponent implements Component, Disposable {
    public InteriorLayout layout;
    public Mesh floorMesh;
    public Mesh wallMesh;

    public btCollisionConfiguration collisionConfig;
    public btCollisionDispatcher dispatcher;
    public btBroadphaseInterface broadphase;
    public btConstraintSolver solver;
    public btDiscreteDynamicsWorld interiorWorld;
    public btRigidBody interiorStaticBody;
    public btCollisionShape interiorShape;

    public boolean active;

    @Override
    public void dispose() {
        if (interiorStaticBody != null && interiorWorld != null) {
            interiorWorld.removeRigidBody(interiorStaticBody);
        }
        if (interiorStaticBody != null) { interiorStaticBody.dispose(); interiorStaticBody = null; }
        if (interiorShape != null) { interiorShape.dispose(); interiorShape = null; }
        if (interiorWorld != null) { interiorWorld.dispose(); interiorWorld = null; }
        if (solver != null) { solver.dispose(); solver = null; }
        if (broadphase != null) { broadphase.dispose(); broadphase = null; }
        if (dispatcher != null) { dispatcher.dispose(); dispatcher = null; }
        if (collisionConfig != null) { collisionConfig.dispose(); collisionConfig = null; }
        if (floorMesh != null) { floorMesh.dispose(); floorMesh = null; }
        if (wallMesh != null) { wallMesh.dispose(); wallMesh = null; }
    }
}
