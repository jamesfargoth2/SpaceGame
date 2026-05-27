package com.galacticodyssey.ship.lifesupport;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;

/**
 * Applies crew metabolic load to compartment atmospheres.
 * Each crew member consumes O2 and produces CO2 at rates scaled by activity level.
 *
 * <p>Activity level multipliers:
 * <ul>
 *   <li>RESTING = 1.0</li>
 *   <li>LIGHT_WORK = 1.5</li>
 *   <li>HEAVY_WORK = 3.0</li>
 *   <li>COMBAT = 4.0</li>
 * </ul>
 */
public class CrewMetabolicSystem extends EntitySystem {

    public static final int PRIORITY = 10;

    // Per-person metabolic rates at rest (kg/s)
    public static final float O2_CONSUMPTION_KG_PER_S = 8.33e-5f;
    public static final float CO2_PRODUCTION_KG_PER_S = 1.02e-4f;

    // Activity level constants
    public static final float RESTING = 1.0f;
    public static final float LIGHT_WORK = 1.5f;
    public static final float HEAVY_WORK = 3.0f;
    public static final float COMBAT = 4.0f;

    // Molar masses (kg/mol)
    private static final float O2_MOLAR_MASS = 0.032f;
    private static final float CO2_MOLAR_MASS = 0.044f;

    private static final Family FAMILY = Family.all(
        CompartmentAtmosphereComponent.class
    ).get();

    private static final ComponentMapper<CompartmentAtmosphereComponent> ATMO_M =
        ComponentMapper.getFor(CompartmentAtmosphereComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    public CrewMetabolicSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (entities == null) return;

        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity entity = entities.get(i);
            CompartmentAtmosphereComponent atmo = ATMO_M.get(entity);
            if (atmo.crewCount <= 0) continue;

            float o2MassConsumed = O2_CONSUMPTION_KG_PER_S * atmo.crewCount * atmo.activityLevel * deltaTime;
            float co2MassProduced = CO2_PRODUCTION_KG_PER_S * atmo.crewCount * atmo.activityLevel * deltaTime;

            float o2DeltaP = massToPartialPressure(o2MassConsumed, O2_MOLAR_MASS, atmo);
            float co2DeltaP = massToPartialPressure(co2MassProduced, CO2_MOLAR_MASS, atmo);

            atmo.o2Pressure = Math.max(0f, atmo.o2Pressure - o2DeltaP);
            atmo.co2Pressure += co2DeltaP;
            atmo.totalPressure = atmo.o2Pressure + atmo.co2Pressure + atmo.n2Pressure;
        }
    }

    /**
     * Converts a mass of gas (kg) to its partial pressure contribution (kPa)
     * using the ideal gas law: deltaP = (m / M) * R * T / V / 1000.
     */
    static float massToPartialPressure(float massKg, float molarMass,
                                       CompartmentAtmosphereComponent atmo) {
        return (massKg / molarMass) * 8.314f * atmo.temperature / atmo.volume / 1000f;
    }
}
