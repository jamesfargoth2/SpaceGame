package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.persistence.PersistenceIdComponent;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.StatusEffectsSnapshot;
import com.galacticodyssey.persistence.snapshots.StatusEffectsSnapshot.ActiveEffectData;
import java.util.ArrayList;
import java.util.List;

public class StatusEffectsComponent implements Component, Snapshotable<StatusEffectsSnapshot> {
    private static final ComponentMapper<PersistenceIdComponent> PID_MAPPER =
        ComponentMapper.getFor(PersistenceIdComponent.class);

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

    @Override
    public StatusEffectsSnapshot takeSnapshot() {
        StatusEffectsSnapshot s = new StatusEffectsSnapshot();
        for (ActiveStatusEffect effect : activeEffects) {
            ActiveEffectData data = new ActiveEffectData();
            data.type = effect.type;
            data.remainingDuration = effect.remainingDuration;
            data.tickRate = effect.tickRate;
            data.magnitude = effect.magnitude;
            data.tickAccumulator = effect.tickAccumulator;
            data.stacks = effect.stacks;
            if (effect.source != null) {
                PersistenceIdComponent pid = PID_MAPPER.get(effect.source);
                data.sourceEntityId = (pid != null) ? pid.uuid : null;
            }
            s.activeEffects.add(data);
        }
        return s;
    }

    @Override
    public void restoreFromSnapshot(StatusEffectsSnapshot s) {
        activeEffects.clear();
        for (ActiveEffectData data : s.activeEffects) {
            // Reconstruct with null source — ReferenceResolver will patch it later using sourceEntityId.
            ActiveStatusEffect effect = new ActiveStatusEffect(
                data.type, data.remainingDuration, data.tickRate, data.magnitude, null);
            effect.tickAccumulator = data.tickAccumulator;
            effect.stacks = data.stacks;
            activeEffects.add(effect);
        }
    }
}
