package com.galacticodyssey.ship.power;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.power.events.PowerWarningEvent;
import com.galacticodyssey.ship.structure.StructuralIntegrityComponent;
import com.galacticodyssey.ship.structure.StructuralZone;
import com.galacticodyssey.ship.structure.ZoneId;
import com.galacticodyssey.ship.thermal.ThermalStateComponent;

/**
 * Computes ship power budget each frame: reactor output, subsystem demand,
 * priority-weighted allocation, and battery/capacitor charge management.
 *
 * <p>Priority 8 — runs before ThermalSystem (10) so reactor waste heat
 * can be added to the thermal simulation in the same frame.</p>
 */
public class PowerSystem extends EntitySystem {

    private static final int PRIORITY = 8;
    private static final float LIFE_SUPPORT_MIN_RATIO = 0.5f;
    private static final float WARNING_THRESHOLD = 0.7f;

    private static final ComponentMapper<PowerStateComponent> powerMapper =
            ComponentMapper.getFor(PowerStateComponent.class);
    private static final ComponentMapper<ShipFlightComponent> flightMapper =
            ComponentMapper.getFor(ShipFlightComponent.class);
    private static final ComponentMapper<ThermalStateComponent> thermalMapper =
            ComponentMapper.getFor(ThermalStateComponent.class);
    private static final ComponentMapper<StructuralIntegrityComponent> structureMapper =
            ComponentMapper.getFor(StructuralIntegrityComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;
    private float warningCooldown = 0f;

    public PowerSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
                Family.all(PowerStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (warningCooldown > 0f) warningCooldown -= deltaTime;

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            PowerStateComponent power = powerMapper.get(entity);

            updateReactorEfficiency(power, entity);
            float reactorOutput = computeReactorOutput(power);
            computeSubsystemDemands(power, entity);
            allocatePower(power, entity, reactorOutput, deltaTime);
            updateEnergyStorage(power, reactorOutput, deltaTime);
            injectReactorHeat(power, entity, deltaTime);
            publishWarnings(power, entity);
        }
    }

    private void updateReactorEfficiency(PowerStateComponent power, Entity entity) {
        StructuralIntegrityComponent structure = structureMapper.get(entity);
        if (structure == null) {
            power.reactorEfficiency = 1.0f;
            return;
        }
        StructuralZone engineRoom = structure.getZone(ZoneId.ENGINE_ROOM);
        if (engineRoom == null) {
            power.reactorEfficiency = 1.0f;
            return;
        }
        power.reactorEfficiency = MathUtils.clamp(engineRoom.integrity, 0.1f, 1.0f);
    }

    private float computeReactorOutput(PowerStateComponent power) {
        power.reactorCurrentOutput = power.reactorBaseOutput * power.reactorEfficiency;
        return power.reactorCurrentOutput;
    }

    private void computeSubsystemDemands(PowerStateComponent power, Entity entity) {
        ShipFlightComponent flight = flightMapper.get(entity);
        float throttle = (flight != null) ? Math.abs(flight.currentThrottle) : 0f;
        float engineDemand = power.engineMaxDraw * Math.max(throttle, 0.1f);

        power.totalDemand = engineDemand
                + power.weaponMaxDraw
                + power.shieldMaxDraw
                + power.lifeSupportDraw
                + power.sensorMaxDraw;
    }

    private void allocatePower(PowerStateComponent power, Entity entity, float reactorOutput, float deltaTime) {
        float availablePower = reactorOutput;

        float batteryContribution = 0f;
        if (power.totalDemand > availablePower && power.batteryCharge > 0f) {
            float deficit = power.totalDemand - availablePower;
            float maxDraw = Math.min(power.batteryDischargeRate, power.batteryCharge / Math.max(deltaTime, 0.001f));
            batteryContribution = Math.min(deficit, maxDraw);
            availablePower += batteryContribution;
        }

        power.totalSupply = availablePower;

        if (availablePower >= power.totalDemand) {
            power.engineAllocation = 1f;
            power.weaponAllocation = 1f;
            power.shieldAllocation = 1f;
            power.lifeSupportAllocation = 1f;
            power.sensorAllocation = 1f;
            if (batteryContribution > 0f) {
                power.batteryCharge = Math.max(0f, power.batteryCharge - batteryContribution * deltaTime);
            }
            return;
        }

        if (batteryContribution > 0f) {
            power.batteryCharge = Math.max(0f, power.batteryCharge - batteryContribution * deltaTime);
        }

        // Life support gets guaranteed minimum
        float lifeSupportMin = power.lifeSupportDraw * LIFE_SUPPORT_MIN_RATIO;
        float lifeSupportReserved = Math.min(lifeSupportMin, availablePower);
        float remaining = availablePower - lifeSupportReserved;

        float lifeSupportExtra = power.lifeSupportDraw - lifeSupportReserved;

        ShipFlightComponent flight = flightMapper.get(entity);
        float throttle = (flight != null) ? Math.abs(flight.currentThrottle) : 0f;
        float engineDemand = power.engineMaxDraw * Math.max(throttle, 0.1f);

        float[] demands = { engineDemand, power.weaponMaxDraw, power.shieldMaxDraw,
                lifeSupportExtra, power.sensorMaxDraw };
        float[] priorities = { power.enginePriority, power.weaponPriority, power.shieldPriority,
                power.lifeSupportPriority, power.sensorPriority };

        float totalWeightedDemand = 0f;
        for (int j = 0; j < demands.length; j++) {
            totalWeightedDemand += demands[j] * priorities[j];
        }

        float[] allocations = new float[5];
        if (totalWeightedDemand > 0f) {
            for (int j = 0; j < demands.length; j++) {
                float share = (demands[j] * priorities[j] / totalWeightedDemand) * remaining;
                allocations[j] = (demands[j] > 0f) ? Math.min(share / demands[j], 1f) : 1f;
            }
        }

        power.engineAllocation = MathUtils.clamp(allocations[0], 0f, 1f);
        power.weaponAllocation = MathUtils.clamp(allocations[1], 0f, 1f);
        power.shieldAllocation = MathUtils.clamp(allocations[2], 0f, 1f);
        float lsTotal = lifeSupportReserved + (lifeSupportExtra > 0f ? allocations[3] * lifeSupportExtra : 0f);
        power.lifeSupportAllocation = (power.lifeSupportDraw > 0f)
                ? MathUtils.clamp(lsTotal / power.lifeSupportDraw, 0f, 1f) : 1f;
        power.sensorAllocation = MathUtils.clamp(allocations[4], 0f, 1f);
    }

    private void updateEnergyStorage(PowerStateComponent power, float reactorOutput, float deltaTime) {
        float surplus = reactorOutput - power.totalDemand;
        if (surplus <= 0f) return;

        // Capacitor charges first (higher priority for weapon bursts)
        if (power.capacitorCharge < power.capacitorCapacity) {
            float capRoom = power.capacitorCapacity - power.capacitorCharge;
            float capCharge = Math.min(surplus, power.capacitorChargeRate) * deltaTime;
            capCharge = Math.min(capCharge, capRoom);
            power.capacitorCharge += capCharge;
            surplus -= capCharge / Math.max(deltaTime, 0.001f);
        }

        // Then battery charges from remaining surplus
        if (surplus > 0f && power.batteryCharge < power.batteryCapacity) {
            float batRoom = power.batteryCapacity - power.batteryCharge;
            float batCharge = Math.min(surplus, power.batteryChargeRate) * deltaTime;
            batCharge = Math.min(batCharge, batRoom);
            power.batteryCharge += batCharge;
        }
    }

    private void injectReactorHeat(PowerStateComponent power, Entity entity, float deltaTime) {
        ThermalStateComponent thermal = thermalMapper.get(entity);
        if (thermal == null) return;

        float wasteHeat = power.reactorCurrentOutput * power.reactorWasteHeatFactor * 1000f;
        float tempRise = wasteHeat * deltaTime / 50_000f;
        thermal.reactorTemp += tempRise;
    }

    private void publishWarnings(PowerStateComponent power, Entity entity) {
        if (warningCooldown > 0f) return;

        if (power.engineAllocation < WARNING_THRESHOLD) {
            eventBus.publish(new PowerWarningEvent(entity, "engines", power.engineAllocation));
            warningCooldown = 2f;
        } else if (power.weaponAllocation < WARNING_THRESHOLD) {
            eventBus.publish(new PowerWarningEvent(entity, "weapons", power.weaponAllocation));
            warningCooldown = 2f;
        } else if (power.shieldAllocation < WARNING_THRESHOLD) {
            eventBus.publish(new PowerWarningEvent(entity, "shields", power.shieldAllocation));
            warningCooldown = 2f;
        }
    }

    public boolean consumeCapacitorEnergy(Entity entity, float energyKJ) {
        PowerStateComponent power = powerMapper.get(entity);
        if (power == null) return true;
        if (power.capacitorCharge >= energyKJ) {
            power.capacitorCharge -= energyKJ;
            return true;
        }
        float deficit = energyKJ - power.capacitorCharge;
        power.capacitorCharge = 0f;
        if (power.batteryCharge >= deficit) {
            power.batteryCharge -= deficit;
            return true;
        }
        return false;
    }
}
