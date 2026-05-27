package com.galacticodyssey.ship.fluid;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.fluid.events.CryoVentEvent;

/**
 * Simulates boil-off and pressure management for cryogenic propellant tanks.
 * Each tick, heat leaking into the tank vapourises a small amount of liquid,
 * raising ullage pressure. If pressure exceeds the relief valve setpoint
 * the valve opens and vapour is vented, publishing a {@link CryoVentEvent}.
 */
public class CryoTankSystem extends EntitySystem {

    private static final int PRIORITY = 5;

    /** Universal gas constant (J/(mol*K)). */
    private static final float R = 8.314f;

    /** Maximum boil-off fraction per second (0.1%). */
    private static final float MAX_BOILOFF_FRACTION = 0.001f;

    /**
     * Assumed heat input rate (W) when no external source is provided.
     * A real implementation would pull this from a thermal model; this
     * default gives a gentle background boil-off.
     */
    private static final float DEFAULT_HEAT_INPUT = 50f;

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    private final ComponentMapper<SloshTankComponent> tankMapper =
        ComponentMapper.getFor(SloshTankComponent.class);
    private final ComponentMapper<CryoTankStateComponent> cryoMapper =
        ComponentMapper.getFor(CryoTankStateComponent.class);

    public CryoTankSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(SloshTankComponent.class, CryoTankStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (deltaTime <= 0f) return;

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            SloshTankComponent tank = tankMapper.get(entity);
            CryoTankStateComponent cryo = cryoMapper.get(entity);

            updateCryoTank(entity, tank, cryo, DEFAULT_HEAT_INPUT, deltaTime);
        }
    }

    /**
     * Core cryo-tank update. Exposed as package-private so that callers with
     * a real thermal model can supply their own heat input value.
     */
    void updateCryoTank(Entity entity, SloshTankComponent tank,
                        CryoTankStateComponent cryo, float heatInput, float dt) {
        if (tank.fluidMass <= 0f) {
            updatePressure(cryo);
            return;
        }

        // --- Boil-off ---
        float boilOffMass = 0f;
        if (cryo.vapourisationEnthalpy > 0f) {
            boilOffMass = (heatInput / cryo.vapourisationEnthalpy) * dt;
        }
        // Cap at MAX_BOILOFF_FRACTION of remaining liquid per second
        boilOffMass = Math.min(boilOffMass, tank.fluidMass * MAX_BOILOFF_FRACTION * dt);
        boilOffMass = Math.min(boilOffMass, tank.fluidMass);

        tank.fluidMass -= boilOffMass;
        cryo.vaporMass += boilOffMass;

        // --- Pressure (ideal gas law) ---
        updatePressure(cryo);

        // --- Relief valve ---
        if (cryo.pressure > cryo.maxPressure && cryo.vaporVolume > 0f) {
            float excessPressure = cryo.pressure - cryo.maxPressure;
            float ventMass = excessPressure * cryo.vaporVolume * cryo.molarMass
                / (R * cryo.temperature);
            ventMass = Math.min(ventMass, cryo.vaporMass);
            ventMass = Math.max(0f, ventMass);

            cryo.vaporMass -= ventMass;
            updatePressure(cryo);

            if (ventMass > 0f) {
                eventBus.publish(new CryoVentEvent(entity, ventMass));
            }
        }
    }

    /** Recalculates ullage pressure from ideal gas law: P = (m/M) * R * T / V. */
    private void updatePressure(CryoTankStateComponent cryo) {
        if (cryo.molarMass <= 0f || cryo.vaporVolume <= 0f) {
            cryo.pressure = 0f;
            return;
        }
        float moles = cryo.vaporMass / cryo.molarMass;
        cryo.pressure = moles * R * cryo.temperature / cryo.vaporVolume;
    }
}
