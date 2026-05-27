package com.galacticodyssey.core.solar;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.solar.events.RadiationDoseEvent;

/**
 * Applies radiation dose to entities traversing radiation belts around
 * planets or magnetised bodies.
 * <p>
 * Iterates over all {@link SolarSourceComponent} entities (which hold
 * radiation belt definitions) and checks whether affected entities fall
 * within any belt's radial range. Dose is scaled by the entity's magnetic
 * shield factor and accumulated over time.
 */
public class RadiationBeltSystem extends EntitySystem {

    public static final int PRIORITY = 6;

    private static final ComponentMapper<SolarSourceComponent> SOURCE_M =
        ComponentMapper.getFor(SolarSourceComponent.class);
    private static final ComponentMapper<SolarAffectedComponent> AFFECTED_M =
        ComponentMapper.getFor(SolarAffectedComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private static final Family SOURCE_FAMILY = Family.all(
        SolarSourceComponent.class, TransformComponent.class
    ).get();

    private static final Family AFFECTED_FAMILY = Family.all(
        SolarAffectedComponent.class, PhysicsBodyComponent.class, TransformComponent.class
    ).get();

    private final EventBus eventBus;
    private ImmutableArray<Entity> sources;
    private ImmutableArray<Entity> affectedEntities;

    private final Pool<Vector3> vectorPool = new Pool<Vector3>() {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };
    private final Matrix4 tempMat = new Matrix4();

    public RadiationBeltSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        sources = engine.getEntitiesFor(SOURCE_FAMILY);
        affectedEntities = engine.getEntitiesFor(AFFECTED_FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        sources = null;
        affectedEntities = null;
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0, an = affectedEntities.size(); i < an; i++) {
            Entity affected = affectedEntities.get(i);
            SolarAffectedComponent solar = AFFECTED_M.get(affected);
            PhysicsBodyComponent physics = PHYSICS_M.get(affected);
            if (physics.body == null) continue;

            Vector3 entityPos = vectorPool.obtain();
            physics.body.getWorldTransform(tempMat);
            tempMat.getTranslation(entityPos);

            try {
                for (int j = 0, sn = sources.size(); j < sn; j++) {
                    Entity sourceEntity = sources.get(j);
                    SolarSourceComponent source = SOURCE_M.get(sourceEntity);
                    if (source.radiationBelts.size == 0) continue;

                    TransformComponent sourceTransform = TRANSFORM_M.get(sourceEntity);
                    applyBeltRadiation(affected, entityPos, solar,
                                       source, sourceTransform, deltaTime);
                }
            } finally {
                vectorPool.free(entityPos);
            }
        }
    }

    private void applyBeltRadiation(Entity entity, Vector3 entityPos,
                                    SolarAffectedComponent solar,
                                    SolarSourceComponent source,
                                    TransformComponent sourceTransform,
                                    float deltaTime) {
        Vector3 offset = vectorPool.obtain();
        try {
            offset.set(entityPos).sub(sourceTransform.position);
            float radius = offset.len();

            for (int k = 0, bn = source.radiationBelts.size; k < bn; k++) {
                RadiationBelt belt = source.radiationBelts.get(k);
                float doseRate = belt.doseRateAt(radius);
                if (doseRate <= 0f) continue;

                float dose = doseRate * (1f - solar.magneticShieldFactor) * deltaTime;
                if (dose > 0f) {
                    solar.accumulatedRadiationDose += dose;
                    eventBus.publish(new RadiationDoseEvent(entity, dose));
                }
            }
        } finally {
            vectorPool.free(offset);
        }
    }
}
