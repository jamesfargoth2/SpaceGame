package com.galacticodyssey.ship.thermal;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.thermal.events.HeatVentEvent;

/**
 * Static utility methods for heat sink absorption and emergency venting.
 */
public final class HeatSinkUtil {

    private HeatSinkUtil() {
    }

    /**
     * Attempts to absorb incoming heat into the heat sinks.
     *
     * @param state     the thermal state to modify
     * @param heatRateJ heat input rate in Watts (J/s)
     * @param dt        frame delta time in seconds
     * @return the amount of energy actually absorbed (Joules)
     */
    public static float absorb(ThermalStateComponent state, float heatRateJ, float dt) {
        if (state.heatSinkCharge >= 1f) {
            return 0f;
        }
        float maxAbsorb = state.heatSinkCapacity * (1f - state.heatSinkCharge);
        float requested = heatRateJ * dt;
        float absorbed = Math.min(requested, maxAbsorb);
        state.heatSinkCharge += absorbed / state.heatSinkCapacity;
        return absorbed;
    }

    /**
     * Emergency vent: instantly dumps a fraction of the heat sink charge.
     * 30% of the vented energy is transferred as a hull temperature spike
     * (unavoidable thermal blowback).
     *
     * @param state        the thermal state to modify
     * @param ventFraction fraction of current heat sink charge to vent (0-1)
     * @param entity       the entity performing the vent (for the event)
     * @param eventBus     event bus to publish {@link HeatVentEvent}
     */
    public static void vent(ThermalStateComponent state, float ventFraction,
                            Entity entity, EventBus eventBus) {
        float clampedFraction = Math.max(0f, Math.min(ventFraction, 1f));
        float ventedCharge = state.heatSinkCharge * clampedFraction;
        state.heatSinkCharge -= ventedCharge;

        // 30% of vented energy transfers to hull as thermal blowback
        float ventedEnergy = ventedCharge * state.heatSinkCapacity;
        float hullSpike = ventedEnergy * 0.3f / state.hullThermalMass;
        state.hullTemp += hullSpike;

        eventBus.publish(new HeatVentEvent(entity, clampedFraction));
    }
}
