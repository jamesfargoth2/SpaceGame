package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.combat.events.RecoilEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.RecoilComponent;

import java.util.ArrayList;
import java.util.List;

public class RecoilSystem extends EntitySystem {
    private static final int PRIORITY = 10;
    private final List<RecoilEvent> pendingRecoils = new ArrayList<>();
    private ImmutableArray<Entity> entities;

    public RecoilSystem(EventBus eventBus) {
        super(PRIORITY);
        eventBus.subscribe(RecoilEvent.class, pendingRecoils::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(RecoilComponent.class, FPSCameraComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (RecoilEvent event : pendingRecoils) {
            for (int i = 0; i < entities.size(); i++) {
                Entity entity = entities.get(i);
                if (entity == event.entity) {
                    applyRecoil(entity);
                }
            }
        }
        pendingRecoils.clear();

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            RecoilComponent rc = entity.getComponent(RecoilComponent.class);
            FPSCameraComponent cam = entity.getComponent(FPSCameraComponent.class);

            rc.timeSinceLastShot += deltaTime;
            if (rc.timeSinceLastShot >= rc.patternResetDelay) {
                rc.patternIndex = 0;
            }

            float decay = rc.recoverySpeed * deltaTime;
            rc.currentPunch.x = approach(rc.currentPunch.x, 0f, decay);
            rc.currentPunch.y = approach(rc.currentPunch.y, 0f, decay);

            cam.pitchAngle += rc.currentPunch.x * deltaTime;
            cam.yawAngle += rc.currentPunch.y * deltaTime;
        }
    }

    private void applyRecoil(Entity entity) {
        RecoilComponent rc = entity.getComponent(RecoilComponent.class);
        rc.timeSinceLastShot = 0f;

        Vector2 punch;
        if (rc.pattern != null && rc.pattern.length > 0) {
            punch = rc.pattern[rc.patternIndex % rc.pattern.length];
            rc.patternIndex++;
        } else {
            punch = new Vector2(1f, 0f);
        }

        rc.currentPunch.add(punch);
        rc.currentPunch.x = MathUtils.clamp(rc.currentPunch.x, -rc.maxPunch.x, rc.maxPunch.x);
        rc.currentPunch.y = MathUtils.clamp(rc.currentPunch.y, -rc.maxPunch.y, rc.maxPunch.y);
    }

    private float approach(float current, float target, float maxDelta) {
        if (current < target) return Math.min(current + maxDelta, target);
        if (current > target) return Math.max(current - maxDelta, target);
        return target;
    }
}
