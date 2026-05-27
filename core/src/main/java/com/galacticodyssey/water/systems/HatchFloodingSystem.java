package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;

public class HatchFloodingSystem extends IteratingSystem {

    private static final float GRAVITY = 9.81f;
    private static final float HATCH_CD = 0.6f;

    private final ComponentMapper<FloodingComponent> floodMapper =
        ComponentMapper.getFor(FloodingComponent.class);
    private final ComponentMapper<HatchComponent> hatchMapper =
        ComponentMapper.getFor(HatchComponent.class);

    private final EventBus eventBus;

    public HatchFloodingSystem(int priority, EventBus eventBus) {
        super(Family.all(
            FloodingComponent.class,
            HatchComponent.class
        ).get(), priority);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        FloodingComponent flooding = floodMapper.get(entity);
        HatchComponent hatchComp = hatchMapper.get(entity);

        for (int i = 0; i < hatchComp.hatches.size; i++) {
            Hatch hatch = hatchComp.hatches.get(i);
            if (!hatch.isOpen) continue;

            Compartment compA = findCompartment(flooding, hatch.compartmentA);
            Compartment compB = findCompartment(flooding, hatch.compartmentB);
            if (compA == null || compB == null) continue;

            float headA = compA.fillFraction();
            float headB = compB.fillFraction();
            float headDiff = headA - headB;

            if (Math.abs(headDiff) < 0.001f) continue;

            float flow = HATCH_CD * hatch.area
                * (float) Math.sqrt(2f * GRAVITY * Math.abs(headDiff)) * dt;

            if (headDiff > 0) {
                float transfer = Math.min(flow, compA.waterVolume);
                transfer = Math.min(transfer, compB.volume - compB.waterVolume);
                compA.waterVolume -= transfer;
                compB.waterVolume += transfer;
            } else {
                float transfer = Math.min(flow, compB.waterVolume);
                transfer = Math.min(transfer, compA.volume - compA.waterVolume);
                compB.waterVolume -= transfer;
                compA.waterVolume += transfer;
            }

            compA.waterVolume = MathUtils.clamp(compA.waterVolume, 0f, compA.volume);
            compB.waterVolume = MathUtils.clamp(compB.waterVolume, 0f, compB.volume);
        }
    }

    private Compartment findCompartment(FloodingComponent flooding, String id) {
        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment c = flooding.compartments.get(i);
            if (id.equals(c.id)) return c;
        }
        return null;
    }
}
