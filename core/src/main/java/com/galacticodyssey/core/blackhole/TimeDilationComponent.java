package com.galacticodyssey.core.blackhole;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Attached to entities that are within a black hole's time dilation field.
 *
 * <p>The {@link TimeDilationSystem} writes to this component every frame.
 * Other systems (cooldowns, fuel burn, animation) should multiply their
 * local {@code deltaTime} by {@link #timeDilationFactor} to implement
 * per-entity time slowing.
 */
public class TimeDilationComponent implements Component, Pool.Poolable {

    /**
     * Current time dilation factor. 1.0 = normal time, values approaching
     * 0 = increasingly dilated (slower). Never reaches exactly 0 thanks to
     * the {@link BlackHoleComponent#maxTimeDilation} floor.
     */
    public float timeDilationFactor = 1f;

    /**
     * True when the entity is inside the innermost stable circular orbit
     * (r < 3 * rs). No stable orbit exists below ISCO; the entity will
     * spiral inward unless it applies thrust to escape.
     */
    public boolean isInsideISCO;

    /**
     * True when the entity is inside the photon sphere (r <= 1.5 * rs).
     * Used as a flag for the rendering layer to apply gravitational lensing
     * visual effects.
     */
    public boolean isInPhotonSphere;

    @Override
    public void reset() {
        timeDilationFactor = 1f;
        isInsideISCO = false;
        isInPhotonSphere = false;
    }
}
