package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import java.util.ArrayList;
import java.util.List;

public class StatusEffectsComponent implements Component {
    public final List<ActiveStatusEffect> activeEffects = new ArrayList<>();

    public ActiveStatusEffect getEffect(StatusEffectType type) {
        for (ActiveStatusEffect e : activeEffects) {
            if (e.type == type) return e;
        }
        return null;
    }

    public boolean hasEffect(StatusEffectType type) {
        return getEffect(type) != null;
    }
}
