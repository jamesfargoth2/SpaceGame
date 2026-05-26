package com.galacticodyssey.vfx.data;

import java.util.HashMap;
import java.util.Map;

public class VFXRegistry {
    private final Map<String, ParticleEffectDefinition> effects = new HashMap<>();

    public void register(ParticleEffectDefinition def) {
        effects.put(def.id, def);
    }

    /** Alias for {@link #register(ParticleEffectDefinition)} — used by JSON/asset-pipeline loaders. */
    public void loadEffect(ParticleEffectDefinition def) {
        register(def);
    }

    public ParticleEffectDefinition getEffect(String id) {
        return effects.get(id);
    }

    public boolean hasEffect(String id) {
        return effects.containsKey(id);
    }
}
