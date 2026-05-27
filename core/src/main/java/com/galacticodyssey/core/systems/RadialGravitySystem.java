package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;

public class RadialGravitySystem extends IteratingSystem {

    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerStateComponent> playerStateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);

    private final Vector3 planetCenter = new Vector3();
    private final float gravity;
    private final Vector3 tempVec = new Vector3();
    private final Matrix4 tempMat = new Matrix4();

    public RadialGravitySystem(btDiscreteDynamicsWorld dynamicsWorld,
                                Vector3 planetCenter, float gravity) {
        super(Family.all(PhysicsBodyComponent.class, TransformComponent.class).get());
        this.planetCenter.set(planetCenter);
        this.gravity = gravity;
        dynamicsWorld.setGravity(new Vector3(0, 0, 0));
    }

    public float getGravityMagnitude() { return gravity; }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerStateComponent playerState = playerStateMapper.get(entity);
        if (playerState != null
            && playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            return;
        }

        PhysicsBodyComponent physics = physicsMapper.get(entity);
        if (physics.body == null || physics.mass <= 0f) return;

        physics.body.getWorldTransform(tempMat);
        tempMat.getTranslation(tempVec);

        tempVec.sub(planetCenter).nor().scl(-gravity * physics.mass);
        physics.body.applyCentralForce(tempVec);
        physics.body.activate();
    }
}
