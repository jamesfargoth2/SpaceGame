package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ArmorSnapshot;
import java.util.EnumMap;
import java.util.Map;

public class ArmorComponent implements Component, Snapshotable<ArmorSnapshot> {
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

    @Override
    public ArmorSnapshot takeSnapshot() {
        ArmorSnapshot s = new ArmorSnapshot();
        for (HitRegion region : HitRegion.values()) {
            s.armorRating.put(region, armorRating.get(region));
            s.durability.put(region, durability.get(region));
            s.maxDurability.put(region, maxDurability.get(region));
            Map<DamageType, Float> innerCopy = new EnumMap<>(DamageType.class);
            innerCopy.putAll(resistances.get(region));
            s.resistances.put(region, innerCopy);
        }
        return s;
    }

    @Override
    public void restoreFromSnapshot(ArmorSnapshot s) {
        for (HitRegion region : HitRegion.values()) {
            if (s.armorRating.containsKey(region)) armorRating.put(region, s.armorRating.get(region));
            if (s.durability.containsKey(region)) durability.put(region, s.durability.get(region));
            if (s.maxDurability.containsKey(region)) maxDurability.put(region, s.maxDurability.get(region));
            if (s.resistances.containsKey(region)) {
                Map<DamageType, Float> inner = resistances.get(region);
                inner.clear();
                inner.putAll(s.resistances.get(region));
            }
        }
    }
}
