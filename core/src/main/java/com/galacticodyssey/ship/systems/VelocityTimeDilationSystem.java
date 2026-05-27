package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.RelativisticConstants;
import com.galacticodyssey.core.RelativisticMath;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.events.RelativisticEvents;
import com.galacticodyssey.ship.components.ShipFlightComponent;

public class VelocityTimeDilationSystem extends IteratingSystem {

    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ShipFlightComponent> flightMapper =
        ComponentMapper.getFor(ShipFlightComponent.class);

    private final EventBus eventBus;
    private final Vector3 tmp = new Vector3();

    public VelocityTimeDilationSystem(EventBus eventBus) {
        super(Family.all(PhysicsBodyComponent.class, ShipFlightComponent.class).get(), 4);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final PhysicsBodyComponent physics = physicsMapper.get(entity);
        final ShipFlightComponent flight = flightMapper.get(entity);
        if (physics.body == null) return;

        tmp.set(physics.body.getLinearVelocity());
        final float speed = tmp.len();

        final float velFactor = RelativisticMath.shipTimeFactor(speed);
        flight.timeDilationFactor = velFactor * flight.gravitationalTimeDilation;

        if (speed > RelativisticConstants.MAX_GAME_SPEED) {
            tmp.scl(RelativisticConstants.MAX_GAME_SPEED / speed);
            physics.body.setLinearVelocity(tmp);
            eventBus.publish(new RelativisticEvents.SpeedCapEnforcedEvent(entity));
        }
    }
}
