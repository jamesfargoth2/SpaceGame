package com.galacticodyssey.galaxy;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;

/**
 * Ashley EntitySystem (priority 2) that advances every {@link OrbitalSlot} in the active
 * {@link StarSystem} each frame.  Runs after GravitySystem (priority 1).
 *
 * No GL resources are touched here — only float arithmetic and event publishing.
 */
public final class KeplerianOrbitSystem extends EntitySystem {

    // ------------------------------------------------------------------ inner events

    public static final class OrbitTickEvent {
        public final StarSystem system;

        public OrbitTickEvent(StarSystem system) {
            this.system = system;
        }
    }

    // ------------------------------------------------------------------ fields

    private final EventBus eventBus;
    private StarSystem activeSystem;

    // ------------------------------------------------------------------ construction

    public KeplerianOrbitSystem(EventBus eventBus) {
        super(2);
        this.eventBus = eventBus;
    }

    // ------------------------------------------------------------------ API

    public void setActiveSystem(StarSystem system) {
        this.activeSystem = system;
        if (system != null) {
            for (OrbitalSlot slot : system.orbits) {
                slot.starMass = system.mass;
            }
        }
    }

    public StarSystem getActiveSystem() {
        return activeSystem;
    }

    // ------------------------------------------------------------------ update

    @Override
    public void update(float dt) {
        if (activeSystem == null || activeSystem.orbits.isEmpty()) return;

        final float starMass = activeSystem.mass;
        final float GM       = OrbitalMechanics.G * starMass;

        for (OrbitalSlot slot : activeSystem.orbits) {
            if (slot.orbitalPeriod <= 0f) continue;

            // Mean motion n = 2π / T
            final float n = com.badlogic.gdx.math.MathUtils.PI2 / slot.orbitalPeriod;

            // Advance mean anomaly
            slot.currentMeanAnomaly += n * dt;

            // Convert M → ν
            slot.currentTrueAnomaly = OrbitalMechanics.trueAnomalyFromMean(
                slot.currentMeanAnomaly, slot.eccentricity);
        }

        eventBus.publish(new OrbitTickEvent(activeSystem));
    }
}
