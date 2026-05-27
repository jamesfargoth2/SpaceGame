package com.galacticodyssey.ship.lifesupport;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.lifesupport.events.FireStatusEvent;

/**
 * Simulates fire physics in compartments. Fire consumes O2 and produces CO2,
 * while increasing compartment temperature.
 *
 * <p>Fire behaviour based on O2 levels:
 * <ul>
 *   <li>O2 &gt;= 16 kPa: fire burns normally</li>
 *   <li>O2 &lt; 16 kPa: fire intensity decays (starving)</li>
 *   <li>O2 &lt; 12 kPa: fire extinguishes</li>
 * </ul>
 *
 * <p>Publishes {@link FireStatusEvent} when a fire is extinguished.
 */
public class FirePhysicsSystem extends EntitySystem {

    public static final int PRIORITY = 14;

    private static final float O2_STARVE_THRESHOLD = 16.0f;  // kPa
    private static final float O2_EXTINGUISH_THRESHOLD = 12.0f;  // kPa
    private static final float STARVE_DECAY_FACTOR = 0.9f;
    private static final float CO2_STOICHIOMETRY = 1.375f;  // kg CO2 produced per kg O2 consumed

    private static final float O2_MOLAR_MASS = 0.032f;
    private static final float CO2_MOLAR_MASS = 0.044f;

    /** Specific heat capacity of air at constant volume (kJ/(kg*K)), simplified. */
    private static final float AIR_DENSITY = 1.2f;  // kg/m3 (approximate at ~1 atm)

    private static final Family FAMILY = Family.all(
        CompartmentAtmosphereComponent.class,
        FireComponent.class
    ).get();

    private static final ComponentMapper<CompartmentAtmosphereComponent> ATMO_M =
        ComponentMapper.getFor(CompartmentAtmosphereComponent.class);
    private static final ComponentMapper<FireComponent> FIRE_M =
        ComponentMapper.getFor(FireComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    public FirePhysicsSystem(EventBus eventBus) {
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
            FireComponent fire = FIRE_M.get(entity);

            processFire(entity, atmo, fire, deltaTime);
        }
    }

    private void processFire(Entity entity, CompartmentAtmosphereComponent atmo,
                             FireComponent fire, float dt) {
        // O2 starvation
        if (atmo.o2Pressure < O2_STARVE_THRESHOLD) {
            fire.intensity *= STARVE_DECAY_FACTOR;
        }
        if (atmo.o2Pressure < O2_EXTINGUISH_THRESHOLD) {
            fire.intensity = 0f;
        }

        if (fire.intensity <= 0f) {
            fire.intensity = 0f;
            eventBus.publish(new FireStatusEvent(entity, true));
            return;
        }

        // O2 consumed and CO2 produced by fire
        float o2Consumed = fire.burnRate * fire.intensity * dt;
        float co2Produced = o2Consumed * CO2_STOICHIOMETRY;

        float deltaO2 = CrewMetabolicSystem.massToPartialPressure(o2Consumed, O2_MOLAR_MASS, atmo);
        float deltaCO2 = CrewMetabolicSystem.massToPartialPressure(co2Produced, CO2_MOLAR_MASS, atmo);

        atmo.o2Pressure = Math.max(0f, atmo.o2Pressure - deltaO2);
        atmo.co2Pressure += deltaCO2;
        atmo.totalPressure = atmo.o2Pressure + atmo.co2Pressure + atmo.n2Pressure;

        // Temperature increase from fire heat output
        atmo.temperature += fire.heatOutput * fire.intensity * dt / (atmo.volume * AIR_DENSITY);
    }
}
