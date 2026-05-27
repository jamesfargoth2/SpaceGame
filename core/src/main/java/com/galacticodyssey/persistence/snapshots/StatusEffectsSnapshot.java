package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StatusEffectsSnapshot {
    public static class ActiveEffectData {
        public StatusEffectType type;
        public float remainingDuration;
        public float tickRate;
        public float magnitude;
        public float tickAccumulator;
        public UUID sourceEntityId;
        public int stacks;
        public ActiveEffectData() {}
    }
    public List<ActiveEffectData> activeEffects = new ArrayList<>();
    public StatusEffectsSnapshot() {}
}
