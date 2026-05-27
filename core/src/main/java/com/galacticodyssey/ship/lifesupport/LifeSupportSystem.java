package com.galacticodyssey.ship.lifesupport;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.lifesupport.events.O2ReserveDepletedEvent;
import com.galacticodyssey.ship.lifesupport.events.ScrubberSaturatedEvent;

/**
 * Runs CO2 scrubbing and O2 generation/supply for compartments with life support equipment.
 *
 * <p>CO2 scrubbing: absorbs CO2 from the atmosphere up to scrubRate * dt, tracking absorbed mass
 * against capacity. Publishes {@link ScrubberSaturatedEvent} when the scrubber reaches capacity.
 *
 * <p>O2 generation: when O2 partial pressure drops below 21 kPa, supplies O2 from the generator
 * (if online) or from the reserve tank. Publishes {@link O2ReserveDepletedEvent} when reserves
 * are exhausted.
 */
public class LifeSupportSystem extends EntitySystem {

    public static final int PRIORITY = 11;

    private static final float TARGET_O2_PRESSURE = 21.0f; // kPa
    private static final float O2_MOLAR_MASS = 0.032f;
    private static final float CO2_MOLAR_MASS = 0.044f;

    private static final Family FAMILY = Family.all(
        CompartmentAtmosphereComponent.class,
        LifeSupportEquipmentComponent.class
    ).get();

    private static final ComponentMapper<CompartmentAtmosphereComponent> ATMO_M =
        ComponentMapper.getFor(CompartmentAtmosphereComponent.class);
    private static final ComponentMapper<LifeSupportEquipmentComponent> EQUIP_M =
        ComponentMapper.getFor(LifeSupportEquipmentComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    public LifeSupportSystem(EventBus eventBus) {
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
            LifeSupportEquipmentComponent equip = EQUIP_M.get(entity);

            processCO2Scrubbing(entity, atmo, equip, deltaTime);
            processO2Supply(entity, atmo, equip, deltaTime);
        }
    }

    private void processCO2Scrubbing(Entity entity, CompartmentAtmosphereComponent atmo,
                                     LifeSupportEquipmentComponent equip, float dt) {
        if (!equip.scrubberOperational) return;
        if (equip.scrubberAbsorbed >= equip.scrubberCapacity) return;

        float co2MassInAtmo = atmo.gasMassKg(atmo.co2Pressure, CO2_MOLAR_MASS);
        float remainingCapacity = equip.scrubberCapacity - equip.scrubberAbsorbed;
        float toAbsorb = Math.min(equip.scrubRate * dt, Math.min(co2MassInAtmo, remainingCapacity));

        if (toAbsorb <= 0f) return;

        float deltaPressure = CrewMetabolicSystem.massToPartialPressure(toAbsorb, CO2_MOLAR_MASS, atmo);
        atmo.co2Pressure = Math.max(0f, atmo.co2Pressure - deltaPressure);
        atmo.totalPressure = atmo.o2Pressure + atmo.co2Pressure + atmo.n2Pressure;
        equip.scrubberAbsorbed += toAbsorb;

        if (equip.scrubberAbsorbed >= equip.scrubberCapacity) {
            eventBus.publish(new ScrubberSaturatedEvent(entity));
        }
    }

    private void processO2Supply(Entity entity, CompartmentAtmosphereComponent atmo,
                                 LifeSupportEquipmentComponent equip, float dt) {
        if (!equip.generatorOnline && equip.reserveTankKg <= 0f) return;

        float deficit = TARGET_O2_PRESSURE - atmo.o2Pressure;
        if (deficit <= 0f) return;

        // Convert pressure deficit to mass needed
        float massNeeded = deficit * 1000f * atmo.volume * O2_MOLAR_MASS
                           / (8.314f * atmo.temperature);

        float supply;
        if (equip.generatorOnline) {
            supply = Math.min(massNeeded, equip.generationRate * dt);
        } else {
            supply = Math.min(massNeeded, equip.reserveTankKg);
            equip.reserveTankKg -= supply;
            if (equip.reserveTankKg <= 0f) {
                equip.reserveTankKg = 0f;
                eventBus.publish(new O2ReserveDepletedEvent(entity));
            }
        }

        float deltaPressure = CrewMetabolicSystem.massToPartialPressure(supply, O2_MOLAR_MASS, atmo);
        atmo.o2Pressure += deltaPressure;
        atmo.totalPressure = atmo.o2Pressure + atmo.co2Pressure + atmo.n2Pressure;
    }
}
