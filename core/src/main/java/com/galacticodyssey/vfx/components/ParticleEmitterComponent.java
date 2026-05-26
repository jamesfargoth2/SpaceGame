package com.galacticodyssey.vfx.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.vfx.ActiveEmitter;
import java.util.ArrayList;
import java.util.List;

public class ParticleEmitterComponent implements Component {
    public final List<ActiveEmitter> activeEmitters = new ArrayList<>();

    public ActiveEmitter addEmitter(String definitionId, float duration) {
        ActiveEmitter emitter = new ActiveEmitter(definitionId, duration);
        activeEmitters.add(emitter);
        return emitter;
    }

    public void removeExpired() {
        activeEmitters.removeIf(ActiveEmitter::isExpired);
    }
}
