package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import java.util.EnumMap;
import java.util.Map;

public class HitboxComponent implements Component {
    public final Map<HitRegion, Float> regionMultipliers = new EnumMap<>(HitRegion.class);
    public float bodyHeight = 1.8f;
    public float headHeightRatio = 0.85f;
    public float torsoHeightRatio = 0.5f;
    public float legsHeightRatio = 0.25f;

    public HitboxComponent() {
        regionMultipliers.put(HitRegion.HEAD, 2.0f);
        regionMultipliers.put(HitRegion.TORSO, 1.0f);
        regionMultipliers.put(HitRegion.ARMS, 0.75f);
        regionMultipliers.put(HitRegion.LEGS, 0.75f);
    }

    public HitRegion getRegionForHeight(float hitHeightRatio) {
        if (hitHeightRatio >= headHeightRatio) return HitRegion.HEAD;
        if (hitHeightRatio >= torsoHeightRatio) return HitRegion.TORSO;
        if (hitHeightRatio >= legsHeightRatio) return HitRegion.ARMS;
        return HitRegion.LEGS;
    }
}
