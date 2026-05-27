package com.galacticodyssey.core.solar;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.solar.events.RadiationDoseEvent;

/**
 * Applies solar wind dynamic pressure (charged particle stream) to entities
 * with {@link SolarAffectedComponent}.
 * <p>
 * Dynamic pressure = 0.5 * density * windSpeed^2, where density =
 * massFlux / (windSpeed * dist^2) from mass-conservation falloff.
 * Force is reduced by the entity's magnetic shield factor and amplified
 * during CME events.
 */
public class SolarWindSystem extends EntitySystem {

    public static final int PRIORITY = 4;

    /** Half-angle of the CME cone (radians). */
    private static final float CME_HALF_ANGLE = MathUtils.degreesToRadians * 30f;

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

    public SolarWindSystem(EventBus eventBus) {
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
            if (physics.body == null || physics.mass <= 0f) continue;

            Vector3 entityPos = vectorPool.obtain();
            physics.body.getWorldTransform(tempMat);
            tempMat.getTranslation(entityPos);

            try {
                for (int j = 0, sn = sources.size(); j < sn; j++) {
                    Entity starEntity = sources.get(j);
                    SolarSourceComponent source = SOURCE_M.get(starEntity);
                    TransformComponent starTransform = TRANSFORM_M.get(starEntity);

                    applySolarWind(affected, entityPos, solar, physics,
                                   source, starTransform, deltaTime);
                }
            } finally {
                vectorPool.free(entityPos);
            }
        }
    }

    private void applySolarWind(Entity entity, Vector3 entityPos,
                                SolarAffectedComponent solar,
                                PhysicsBodyComponent physics,
                                SolarSourceComponent source,
                                TransformComponent starTransform,
                                float deltaTime) {
        Vector3 awayFromStar = vectorPool.obtain();
        try {
            awayFromStar.set(entityPos).sub(starTransform.position);
            float dist = awayFromStar.len();
            if (dist <= 0f) return;

            // Wind density falls off as 1/r^2 from mass conservation
            float density = source.solarWindMassFlux
                / (source.solarWindSpeed * dist * dist);

            // Dynamic pressure: P = 0.5 * rho * v^2
            float pressure = 0.5f * density
                * source.solarWindSpeed * source.solarWindSpeed;

            // CME amplification
            float cmeMultiplier = source.cmeActive
                ? source.cmeIntensityMultiplier : 1f;

            // Force magnitude, reduced by magnetic shielding
            float forceMag = pressure * solar.projectedArea
                * (1f - solar.magneticShieldFactor) * cmeMultiplier;

            // Direction: away from star
            awayFromStar.nor().scl(forceMag);
            physics.body.applyCentralForce(awayFromStar);
            physics.body.activate();

            // Radiation dose during CME for entities in the cone
            if (source.cmeActive && isInCMECone(source, starTransform, entityPos)) {
                float dose = pressure * cmeMultiplier
                    * (1f - solar.magneticShieldFactor) * deltaTime;
                if (dose > 0f) {
                    solar.accumulatedRadiationDose += dose;
                    eventBus.publish(new RadiationDoseEvent(entity, dose));
                }
            }
        } finally {
            vectorPool.free(awayFromStar);
        }
    }

    private boolean isInCMECone(SolarSourceComponent source,
                                TransformComponent starTransform,
                                Vector3 position) {
        Vector3 toPos = vectorPool.obtain();
        Vector3 cmeDir = vectorPool.obtain();
        try {
            toPos.set(position).sub(starTransform.position).nor();
            cmeDir.set(
                MathUtils.cos(source.cmeDirection), 0f,
                MathUtils.sin(source.cmeDirection)
            );

            float dot = MathUtils.clamp(toPos.dot(cmeDir), -1f, 1f);
            float angle = (float) Math.acos(dot);
            return angle < CME_HALF_ANGLE;
        } finally {
            vectorPool.free(toPos);
            vectorPool.free(cmeDir);
        }
    }
}
