package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.weapons.components.ShipWeaponHeatComponent;
import com.galacticodyssey.ship.weapons.events.ShipOverheatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShipHeatSystem extends EntitySystem {
    private static final int PRIORITY = 9;
    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;
    private final List<String> toRemoveOverheat = new ArrayList<>();

    public ShipHeatSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(Family.all(ShipWeaponHeatComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ShipWeaponHeatComponent heat = entity.getComponent(ShipWeaponHeatComponent.class);

            toRemoveOverheat.clear();

            for (Map.Entry<String, Float> entry : heat.heatPerHardpoint.entrySet()) {
                String hpId = entry.getKey();
                float currentHeat = entry.getValue();
                float newHeat = Math.max(0f, currentHeat - heat.dissipationRate * deltaTime);
                entry.setValue(newHeat);

                if (currentHeat >= heat.maxHeat && !heat.overheatedHardpoints.contains(hpId)) {
                    heat.overheatedHardpoints.add(hpId);
                    eventBus.publish(new ShipOverheatEvent(entity, hpId));
                }

                if (heat.overheatedHardpoints.contains(hpId) && newHeat <= heat.overheatThreshold) {
                    toRemoveOverheat.add(hpId);
                }
            }

            for (String hpId : toRemoveOverheat) {
                heat.overheatedHardpoints.remove(hpId);
            }
        }
    }
}
