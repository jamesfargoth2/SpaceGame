package com.galacticodyssey.ship.structure;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.ship.structure.events.HullBreachEvent;

/**
 * Each tick, computes mechanical stress on every {@link StructuralZone} of every
 * entity with a {@link StructuralIntegrityComponent}. Stress sources:
 * <ul>
 *     <li>Acceleration stress: sigma = m*a / A</li>
 *     <li>Pressure-differential hoop stress on boundary zones: sigma = dP*r / t</li>
 * </ul>
 *
 * When currentStress exceeds maxStress the zone takes damage proportional to
 * the excess. If integrity reaches zero a {@link HullBreachEvent} is published.
 *
 * <p>Priority 8.</p>
 */
public class StructuralIntegritySystem extends EntitySystem {

    public static final int PRIORITY = 8;

    /** Coefficient that scales excess-stress into integrity damage per second. */
    private static final float STRESS_DAMAGE_COEFF = 0.5f;

    /** Exterior pressure (Pa). Space vacuum = 0. */
    private static final float EXTERIOR_PRESSURE = 0f;

    private static final Family FAMILY = Family.all(
        StructuralIntegrityComponent.class, PhysicsBodyComponent.class
    ).get();

    private static final ComponentMapper<StructuralIntegrityComponent> STRUCT_M =
        ComponentMapper.getFor(StructuralIntegrityComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final EventBus eventBus;
    private final Pool<Vector3> vectorPool = new Pool<Vector3>() {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };

    private ImmutableArray<Entity> entities;

    public StructuralIntegritySystem(EventBus eventBus) {
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
            processEntity(entities.get(i), deltaTime);
        }
    }

    private void processEntity(Entity entity, float dt) {
        StructuralIntegrityComponent structure = STRUCT_M.get(entity);
        PhysicsBodyComponent physics = PHYSICS_M.get(entity);
        if (structure == null || physics == null || physics.body == null) return;

        // Compute acceleration magnitude from total force and mass
        Vector3 totalForce = vectorPool.obtain();
        try {
            totalForce.set(physics.body.getTotalForce());
            float acceleration = (physics.mass > 0f) ? totalForce.len() / physics.mass : 0f;

            for (int z = 0, zn = structure.zones.size; z < zn; z++) {
                StructuralZone zone = structure.zones.get(z);
                zone.currentStress = 0f;

                // Acceleration stress: sigma = m * a / A
                if (zone.area > 0f) {
                    zone.currentStress += (zone.massContribution * acceleration) / zone.area;
                }

                // Pressure differential hoop stress for boundary zones: sigma = dP * r / t
                if (zone.isBoundaryZone && zone.thickness > 0f) {
                    float deltaP = Math.abs(zone.pressure - EXTERIOR_PRESSURE);
                    zone.currentStress += (deltaP * zone.hullRadius) / zone.thickness;
                }

                // Apply damage if stress exceeds yield strength
                if (zone.currentStress > zone.maxStress) {
                    float excess = zone.currentStress - zone.maxStress;
                    float damage = (excess / zone.maxStress) * STRESS_DAMAGE_COEFF * dt;
                    zone.integrity = Math.max(0f, zone.integrity - damage);

                    if (zone.integrity <= 0f && !zone.isBreached) {
                        triggerBreach(entity, zone);
                    }
                }
            }
        } finally {
            vectorPool.free(totalForce);
        }
    }

    private void triggerBreach(Entity entity, StructuralZone zone) {
        zone.isBreached = true;
        // Default breach area if not already set
        if (zone.breachArea <= 0f) {
            zone.breachArea = 0.1f; // 0.1 m^2 initial breach
        }
        eventBus.publish(new HullBreachEvent(entity, zone.id));
    }
}
