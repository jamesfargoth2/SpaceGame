package com.galacticodyssey.vfx.data;

import java.util.HashMap;
import java.util.Map;

public class VFXRegistry {
    private final Map<String, ParticleEffectDefinition> effects = new HashMap<>();

    public void register(ParticleEffectDefinition def) {
        effects.put(def.id, def);
    }

    public ParticleEffectDefinition getEffect(String id) {
        return effects.get(id);
    }

    public boolean hasEffect(String id) {
        return effects.containsKey(id);
    }
}
