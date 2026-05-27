package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.PropulsionUtil;
import com.galacticodyssey.ship.components.EngineSpecComponent;
import com.galacticodyssey.ship.components.FuelTankComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.events.FuelDepletedEvent;

public class PropulsionSystem extends IteratingSystem {

    private final ComponentMapper<EngineSpecComponent> engineMapper =
        ComponentMapper.getFor(EngineSpecComponent.class);
    private final ComponentMapper<FuelTankComponent> fuelMapper =
        ComponentMapper.getFor(FuelTankComponent.class);
    private final ComponentMapper<ShipFlightComponent> flightMapper =
        ComponentMapper.getFor(ShipFlightComponent.class);

    private final EventBus eventBus;

    public PropulsionSystem(EventBus eventBus) {
        super(Family.all(EngineSpecComponent.class, FuelTankComponent.class,
            ShipFlightComponent.class).get(), 3);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        EngineSpecComponent engine = engineMapper.get(entity);
        FuelTankComponent fuel = fuelMapper.get(entity);
        ShipFlightComponent flight = flightMapper.get(entity);

        float requestedThrottle = flight.currentThrottle;
        float targetThrottle = MathUtils.clamp(requestedThrottle,
            engine.canThrottleOff ? 0f : engine.minThrottle, 1f);

        float lerpAlpha = Math.min(engine.throttleResponseRate * deltaTime, 1f);
        engine.currentThrottle = MathUtils.lerp(engine.currentThrottle, targetThrottle, lerpAlpha);

        if (fuel.currentMass <= 0f) {
            engine.actualThrust = 0f;
            return;
        }

        float thrust = engine.maxThrust * engine.currentThrottle;
        float massFlowRate = PropulsionUtil.massFlowRate(thrust, engine.isp);
        float propUsed = massFlowRate * deltaTime;

        if (propUsed > fuel.currentMass) {
            float scale = fuel.currentMass / propUsed;
            thrust *= scale;
            propUsed = fuel.currentMass;
        }

        fuel.currentMass -= propUsed;
        engine.actualThrust = thrust;

        if (fuel.currentMass <= 0f) {
            fuel.currentMass = 0f;
            eventBus.publish(new FuelDepletedEvent(entity));
        }
    }
}
