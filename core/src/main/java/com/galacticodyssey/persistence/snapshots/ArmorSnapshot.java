package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import java.util.EnumMap;
import java.util.Map;

public class ArmorSnapshot {
    public Map<HitRegion, Float> armorRating = new EnumMap<>(HitRegion.class);
    public Map<HitRegion, Map<DamageType, Float>> resistances = new EnumMap<>(HitRegion.class);
    public Map<HitRegion, Float> durability = new EnumMap<>(HitRegion.class);
    public Map<HitRegion, Float> maxDurability = new EnumMap<>(HitRegion.class);
    public ArmorSnapshot() {}
}
