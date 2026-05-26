package com.galacticodyssey.vfx;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

public class ActiveEmitter {
    public String definitionId;
    public float elapsed;
    public float duration;
    public final Vector3 localOffset = new Vector3();
    public final Quaternion localRotation = new Quaternion();
    public VFXEnums.EmitterState state = VFXEnums.EmitterState.PLAYING;
    public float emitAccumulator;

    public ActiveEmitter(String definitionId, float duration) {
        this.definitionId = definitionId;
        this.duration = duration;
    }

    public boolean isLooping() {
        return duration < 0;
    }

    public boolean isExpired() {
        return !isLooping() && elapsed >= duration && state != VFXEnums.EmitterState.STOPPING;
    }
}
