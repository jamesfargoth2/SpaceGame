package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.terrain.SurfaceEvents.AnchorDeployedEvent;

public class SurfaceAnchorSystem extends IteratingSystem {

    private final ComponentMapper<GroundVehicleComponent> vehicleMapper =
        ComponentMapper.getFor(GroundVehicleComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private final EventBus eventBus;

    private final Vector3 tmpAnchorDelta = new Vector3();
    private final Vector3 tmpForce = new Vector3();

    public SurfaceAnchorSystem(EventBus eventBus) {
        super(Family.all(GroundVehicleComponent.class, PhysicsBodyComponent.class).get(), 8);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final GroundVehicleComponent vehicle = vehicleMapper.get(entity);
        final PhysicsBodyComponent physics = physicsMapper.get(entity);
        final TransformComponent transform = transformMapper.get(entity);

        if (vehicle.anchor == null || !vehicle.anchor.isDeployed) return;
        if (physics.body == null) return;

        // Compute vector from current position to anchor point
        tmpAnchorDelta.set(vehicle.anchor.anchorPoint).sub(transform.position);
        final float dist = tmpAnchorDelta.len();
        if (dist < 0.001f) return;

        // Tension only pulls — no push when vehicle is on top of anchor
        final float tensionMag = Math.min(
            dist * physics.mass / Math.max(deltaTime * deltaTime, 0.0001f),
            vehicle.anchor.maxTensionForce);

        tmpForce.set(tmpAnchorDelta).nor().scl(tensionMag);
        physics.body.applyCentralForce(tmpForce);
        physics.body.activate();

        // Break anchor if the tension requested would exceed break force
        if (tensionMag >= vehicle.anchorBreakForce) {
            vehicle.anchor.isDeployed = false;
        }
    }

    public boolean canMaintainContact(GroundVehicleComponent v, float gravMag, float centripetal) {
        return v.mass * gravMag > centripetal + v.dynamicLift;
    }

    public void deployAnchor(GroundVehicleComponent v, Vector3 currentPos) {
        AnchorConstraint a = new AnchorConstraint();
        a.anchorPoint = currentPos.cpy();
        a.maxTensionForce = v.anchorBreakForce;
        a.isDeployed = true;
        v.anchor = a;
        eventBus.publish(new AnchorDeployedEvent(currentPos.cpy()));
    }
}
