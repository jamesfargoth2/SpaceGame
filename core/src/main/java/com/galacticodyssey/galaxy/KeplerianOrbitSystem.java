package com.galacticodyssey.galaxy;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.planet.Moon;
import com.galacticodyssey.planet.Planet;

public final class KeplerianOrbitSystem extends EntitySystem {

    public static final class OrbitTickEvent {
        public final StarSystem system;

        public OrbitTickEvent(StarSystem system) {
            this.system = system;
        }
    }

    private final EventBus eventBus;
    private StarSystem activeSystem;
    private float timeScale = 1.0f;

    public KeplerianOrbitSystem(EventBus eventBus) {
        super(2);
        this.eventBus = eventBus;
    }

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

    public void setTimeScale(float timeScale) {
        this.timeScale = timeScale;
    }

    public float getTimeScale() {
        return timeScale;
    }

    @Override
    public void update(float dt) {
        if (activeSystem == null || activeSystem.orbits.isEmpty()) return;

        final float scaledDt = dt * timeScale;

        for (OrbitalSlot slot : activeSystem.orbits) {
            if (slot.orbitalPeriod <= 0f) continue;

            float n = MathUtils.PI2 / slot.orbitalPeriod;
            slot.currentMeanAnomaly += n * scaledDt;
            slot.currentTrueAnomaly = OrbitalMechanics.trueAnomalyFromMean(
                slot.currentMeanAnomaly, slot.eccentricity);

            Planet planet = slot.planet;
            if (planet != null) {
                for (Moon moon : planet.moons) {
                    if (moon.orbitalPeriod <= 0f || moon.orbitalPeriod >= Float.MAX_VALUE) continue;
                    float moonN = MathUtils.PI2 / moon.orbitalPeriod;
                    moon.currentMeanAnomaly += moonN * scaledDt;
                    moon.currentTrueAnomaly = OrbitalMechanics.trueAnomalyFromMean(
                        moon.currentMeanAnomaly, moon.orbitalEccentricity);
                }
            }
        }

        eventBus.publish(new OrbitTickEvent(activeSystem));
    }
}
