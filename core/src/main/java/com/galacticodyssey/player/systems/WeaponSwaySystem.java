package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.player.components.ADSComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;

public class WeaponSwaySystem extends EntitySystem {
    private static final int PRIORITY = 11;
    private ImmutableArray<Entity> entities;

    public WeaponSwaySystem() {
        super(PRIORITY);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(FPSCameraComponent.class, MovementStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            FPSCameraComponent cam = entity.getComponent(FPSCameraComponent.class);
            MovementStateComponent move = entity.getComponent(MovementStateComponent.class);
            ADSComponent ads = entity.getComponent(ADSComponent.class);

            float adsSuppress = ads != null ? (1f - ads.adsProgress) : 1f;

            if (move.isGrounded && move.currentSpeed > 0.1f) {
                float speedFactor = Math.min(move.currentSpeed / 6f, 1f);
                cam.headBobPhase += deltaTime * cam.headBobFrequency * speedFactor * adsSuppress;
            }
        }
    }
}
