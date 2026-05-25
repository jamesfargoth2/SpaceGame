package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;

public class PhysicsBodyComponent implements Component {
    public btRigidBody body;
    public btCollisionShape shape;
    public float mass;
    public float friction = 0.5f;
    public float restitution = 0f;
    public short collisionGroup = 1;
    public short collisionMask = -1;
    public boolean rebaseOnOriginShift = true;
}
