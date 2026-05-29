package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

public class CreatureDriveSystem extends IteratingSystem {

    private final ComponentMapper<CreatureDrivesComponent> drivesMapper =
        ComponentMapper.getFor(CreatureDrivesComponent.class);

    public CreatureDriveSystem(int priority) {
        super(Family.all(CreatureDrivesComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CreatureDrivesComponent d = drivesMapper.get(entity);
        tickDrives(d, deltaTime, false);
    }

    public static void tickDrives(CreatureDrivesComponent d, float dt, boolean lowActivity) {
        float hungerMul = lowActivity ? 0.3f : 1f;
        d.hunger += d.hungerRate * dt * hungerMul;

        if (d.sprinting) {
            d.energy -= CreatureDrivesComponent.ENERGY_SPRINT_DRAIN * dt;
        } else if (d.moving) {
            d.energy -= CreatureDrivesComponent.ENERGY_MOVE_DRAIN * dt;
        } else {
            d.energy += CreatureDrivesComponent.ENERGY_IDLE_REGEN * dt;
        }

        d.fear -= CreatureDrivesComponent.FEAR_DECAY * dt;

        d.hunger = clamp01(d.hunger);
        d.energy = clamp01(d.energy);
        d.fear = clamp01(d.fear);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
