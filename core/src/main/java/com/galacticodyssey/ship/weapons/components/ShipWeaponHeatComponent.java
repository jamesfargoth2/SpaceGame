package com.galacticodyssey.ship.weapons.components;

import com.badlogic.ashley.core.Component;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ShipWeaponHeatComponent implements Component {
    public final Map<String, Float> heatPerHardpoint = new HashMap<>();
    public float maxHeat = 1.0f;
    public float dissipationRate = 0.15f;
    public float overheatThreshold = 0.5f;
    public final Set<String> overheatedHardpoints = new HashSet<>();

    public float getHeat(String hardpointId) {
        return heatPerHardpoint.getOrDefault(hardpointId, 0f);
    }

    public void addHeat(String hardpointId, float amount) {
        float current = getHeat(hardpointId);
        heatPerHardpoint.put(hardpointId, Math.min(current + amount, maxHeat));
    }

    public boolean isOverheated(String hardpointId) {
        return overheatedHardpoints.contains(hardpointId);
    }
}
