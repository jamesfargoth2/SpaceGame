package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;

public class PhysicsBodySystem extends IteratingSystem {

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final Matrix4 tempMat = new Matrix4();
    private final Vector3 tempVec = new Vector3();

    public PhysicsBodySystem() {
        super(Family.all(TransformComponent.class, PhysicsBodyComponent.class).get(), 3);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = transformMapper.get(entity);
        PhysicsBodyComponent physics = physicsMapper.get(entity);

        if (physics.body == null) return;

        physics.body.getWorldTransform(tempMat);
        tempMat.getTranslation(transform.position);
        tempMat.getRotation(transform.rotation);
    }
}
