package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.player.components.ScreenShakeComponent;

public class ScreenShakeSystem extends EntitySystem {
    private static final int PRIORITY = 14;
    private float elapsed;
    private ImmutableArray<Entity> entities;

    public ScreenShakeSystem() {
        super(PRIORITY);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(Family.all(ScreenShakeComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        elapsed += deltaTime;

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ScreenShakeComponent shake = entity.getComponent(ScreenShakeComponent.class);

            shake.trauma = Math.max(0f, shake.trauma - shake.decayRate * deltaTime);

            float intensity = shake.getIntensity();
            if (intensity > 0.001f) {
                float freq = shake.frequency;
                shake.currentOffset.set(
                    shake.maxOffset.x * intensity * noise(elapsed * freq, 0),
                    shake.maxOffset.y * intensity * noise(elapsed * freq, 100),
                    shake.maxOffset.z * intensity * noise(elapsed * freq, 200)
                );
                shake.currentAngle.set(
                    shake.maxAngle.x * intensity * noise(elapsed * freq, 300),
                    shake.maxAngle.y * intensity * noise(elapsed * freq, 400)
                );
            } else {
                shake.currentOffset.setZero();
                shake.currentAngle.setZero();
            }
        }
    }

    private float noise(float x, float seed) {
        return MathUtils.sin(x + seed) * MathUtils.cos(x * 0.7f + seed * 1.3f);
    }
}
