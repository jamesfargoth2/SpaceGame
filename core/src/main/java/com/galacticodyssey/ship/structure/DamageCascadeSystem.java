package com.galacticodyssey.ship.structure;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.structure.events.ComponentDetachEvent;
import com.galacticodyssey.ship.structure.events.HullBreachEvent;

/**
 * Propagates structural damage from badly damaged zones (integrity &lt; 0.3) to
 * their adjacent zones at a rate-limited pace. When an adjacent zone's integrity
 * reaches zero it also breaches.
 *
 * <p>Priority 10 -- runs after {@link AtmosphereVentSystem} so that all
 * breach state is settled before cascade evaluation.</p>
 */
public class DamageCascadeSystem extends EntitySystem {

    public static final int PRIORITY = 10;

    /** Fraction of damage that propagates per second. */
    private static final float CASCADE_FACTOR = 0.15f;

    /** Integrity threshold below which cascade propagation begins. */
    private static final float CASCADE_THRESHOLD = 0.3f;

    /** Integrity threshold below which the zone may detach entirely. */
    private static final float DETACH_THRESHOLD = 0f;

    private static final Family FAMILY = Family.all(
        StructuralIntegrityComponent.class
    ).get();

    private static final ComponentMapper<StructuralIntegrityComponent> STRUCT_M =
        ComponentMapper.getFor(StructuralIntegrityComponent.class);

    private final EventBus eventBus;

    private ImmutableArray<Entity> entities;

    public DamageCascadeSystem(EventBus eventBus) {
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
        if (structure == null) return;

        for (int z = 0, zn = structure.zones.size; z < zn; z++) {
            StructuralZone zone = structure.zones.get(z);
            if (zone.integrity >= CASCADE_THRESHOLD) continue;

            float propagateDamage = (CASCADE_THRESHOLD - zone.integrity) * CASCADE_FACTOR * dt;

            for (int a = 0, an = zone.adjacentZones.size; a < an; a++) {
                ZoneId adjId = zone.adjacentZones.get(a);
                StructuralZone adj = structure.getZone(adjId);
                if (adj == null || adj.isBreached) continue;

                adj.integrity = Math.max(0f, adj.integrity - propagateDamage);

                if (adj.integrity <= DETACH_THRESHOLD) {
                    adj.isBreached = true;
                    if (adj.breachArea <= 0f) {
                        adj.breachArea = 0.1f;
                    }
                    eventBus.publish(new HullBreachEvent(entity, adj.id));
                    eventBus.publish(new ComponentDetachEvent(entity, adj.id));
                }
            }
        }
    }
}
