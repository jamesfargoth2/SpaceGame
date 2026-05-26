package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.combat.events.ProjectileHitEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.ScreenShakeComponent;

import java.util.ArrayList;
import java.util.List;

public class ScreenShakeSystem extends EntitySystem {
    private static final int PRIORITY = 14;
    private static final float TRAUMA_WEAPON_FIRED = 0.08f;
    private static final float TRAUMA_PROJECTILE_HIT = 0.25f;

    private float elapsed;
    private ImmutableArray<Entity> entities;
    private final List<Float> pendingTrauma = new ArrayList<>();

    /** No-arg constructor for tests — no event subscriptions. */
    public ScreenShakeSystem() {
        super(PRIORITY);
    }

    /** Production constructor — subscribes to combat events for automatic trauma. */
    public ScreenShakeSystem(EventBus eventBus) {
        super(PRIORITY);
        eventBus.subscribe(WeaponFiredEvent.class, e -> pendingTrauma.add(TRAUMA_WEAPON_FIRED));
        eventBus.subscribe(ProjectileHitEvent.class, e -> pendingTrauma.add(TRAUMA_PROJECTILE_HIT));
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(Family.all(ScreenShakeComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        elapsed += deltaTime;

        // Apply any pending trauma from events to ALL entities with ScreenShakeComponent
        // (typically only the player camera entity has this component)
        if (!pendingTrauma.isEmpty()) {
            for (int i = 0; i < entities.size(); i++) {
                ScreenShakeComponent shake = entities.get(i).getComponent(ScreenShakeComponent.class);
                for (float t : pendingTrauma) {
                    shake.addTrauma(t);
                }
            }
            pendingTrauma.clear();
        }

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
