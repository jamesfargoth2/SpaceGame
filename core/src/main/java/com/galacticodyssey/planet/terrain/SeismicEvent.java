package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;

public class SeismicEvent {
    public final Vector3 epicentre;
    public final float amplitude;
    public final float frequency;
    public final float duration;
    public float elapsedTime;

    public SeismicEvent(Vector3 epicentre, float amplitude, float frequency, float duration) {
        this.epicentre = epicentre;
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.duration = duration;
    }
}
