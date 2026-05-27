package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Quaternion;
import com.galacticodyssey.core.components.DebrisComponent;
import com.galacticodyssey.core.components.DebrisComponent.DebrisClass;
import com.galacticodyssey.core.components.DebrisComponent.SimLevel;
import com.galacticodyssey.core.components.TransformComponent;

public class TumbleSystem extends IteratingSystem {

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<DebrisComponent> debrisMapper =
        ComponentMapper.getFor(DebrisComponent.class);

    private final Quaternion tempSpin = new Quaternion();

    public TumbleSystem() {
        super(Family.all(TransformComponent.class, DebrisComponent.class).get(), 10);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        DebrisComponent debris = debrisMapper.get(entity);

        if (debris.simulationLevel != SimLevel.FULL) return;
        if (debris.debrisClass == DebrisClass.DUST || debris.debrisClass == DebrisClass.PEBBLE) return;

        TransformComponent transform = transformMapper.get(entity);

        float i1 = debris.inertiaTensor.x;
        float i2 = debris.inertiaTensor.y;
        float i3 = debris.inertiaTensor.z;
        float w1 = debris.angularVelocity.x;
        float w2 = debris.angularVelocity.y;
        float w3 = debris.angularVelocity.z;

        float dw1 = (i2 - i3) * w2 * w3 / i1;
        float dw2 = (i3 - i1) * w3 * w1 / i2;
        float dw3 = (i1 - i2) * w1 * w2 / i3;

        debris.angularVelocity.x += dw1 * deltaTime;
        debris.angularVelocity.y += dw2 * deltaTime;
        debris.angularVelocity.z += dw3 * deltaTime;

        tempSpin.set(
            debris.angularVelocity.x * 0.5f * deltaTime,
            debris.angularVelocity.y * 0.5f * deltaTime,
            debris.angularVelocity.z * 0.5f * deltaTime,
            1f
        ).nor();
        transform.rotation.mulLeft(tempSpin).nor();

        transform.position.mulAdd(debris.velocity, deltaTime);
    }
}
