package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import java.util.EnumMap;
import java.util.Map;

public class ArmorComponent implements Component {
    public final Map<HitRegion, Float> armorRating = new EnumMap<>(HitRegion.class);
    public final Map<HitRegion, Map<DamageType, Float>> resistances = new EnumMap<>(HitRegion.class);
    public final Map<HitRegion, Float> durability = new EnumMap<>(HitRegion.class);
    public final Map<HitRegion, Float> maxDurability = new EnumMap<>(HitRegion.class);

    public ArmorComponent() {
        for (HitRegion region : HitRegion.values()) {
            armorRating.put(region, 0f);
            resistances.put(region, new EnumMap<>(DamageType.class));
            durability.put(region, 100f);
            maxDurability.put(region, 100f);
        }
    }

    public float getResistance(DamageType type, HitRegion region) {
        float durabilityRatio = maxDurability.get(region) > 0
            ? durability.get(region) / maxDurability.get(region) : 0f;
        float baseResist = resistances.get(region).getOrDefault(type, 0f);
        return baseResist * durabilityRatio;
    }

    public void degradeSlot(HitRegion region, float amount) {
        float current = durability.get(region);
        durability.put(region, Math.max(0f, current - amount * 0.1f));
    }
}
