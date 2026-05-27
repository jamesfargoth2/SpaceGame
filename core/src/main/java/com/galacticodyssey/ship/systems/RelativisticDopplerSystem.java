package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.RelativisticConstants;
import com.galacticodyssey.core.RelativisticMath;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.events.RelativisticEvents;
import com.galacticodyssey.ship.components.ShipFlightComponent;

public class RelativisticDopplerSystem extends IteratingSystem {

    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ShipFlightComponent> flightMapper =
        ComponentMapper.getFor(ShipFlightComponent.class);

    private final EventBus eventBus;
    private final Vector3 tmp = new Vector3();

    public RelativisticDopplerSystem(EventBus eventBus) {
        super(Family.all(PhysicsBodyComponent.class, ShipFlightComponent.class).get(), 5);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final PhysicsBodyComponent physics = physicsMapper.get(entity);
        if (physics.body == null) return;

        tmp.set(physics.body.getLinearVelocity());
        final float speed = tmp.len();
        if (speed <= RelativisticConstants.THRESHOLD) return;

        final float factor = RelativisticMath.dopplerFactor(speed);
        final Color tint;
        if (factor >= 1f) {
            final float intensity = MathUtils.clamp((factor - 1f) * 2f, 0f, 1f);
            tint = new Color(1f - intensity * 0.3f, 1f - intensity * 0.1f, 1f, 1f);
        } else {
            final float intensity = MathUtils.clamp((1f - factor) * 2f, 0f, 1f);
            tint = new Color(1f, 1f - intensity * 0.4f, 1f - intensity * 0.8f, 1f);
        }

        eventBus.publish(new RelativisticEvents.DopplerTintEvent(entity, tint));
    }
}
