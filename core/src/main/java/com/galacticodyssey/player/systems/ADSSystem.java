package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.player.components.ADSComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;

public class ADSSystem extends EntitySystem {
    private static final int PRIORITY = 11;
    private ImmutableArray<Entity> entities;

    public ADSSystem() {
        super(PRIORITY);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(ADSComponent.class, CombatInputComponent.class, FPSCameraComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ADSComponent ads = entity.getComponent(ADSComponent.class);
            CombatInputComponent input = entity.getComponent(CombatInputComponent.class);

            float target = input.aimHeld ? 1f : 0f;
            ads.adsProgress = MathUtils.clamp(
                ads.adsProgress + (target - ads.adsProgress) * ads.adsSpeed * deltaTime,
                0f, 1f
            );
        }
    }
}
