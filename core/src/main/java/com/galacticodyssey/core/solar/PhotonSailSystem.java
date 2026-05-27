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

/**
 * Computes thrust for deployed photon sails from stellar radiation pressure.
 * <p>
 * Thrust = (flux / c) * (1 + reflectivity) * sailArea * cos(angle),
 * where angle is between the sail normal and the star direction.
 * Supports tacking by rotating the sail normal around a perpendicular axis
 * to redirect the force vector for course changes.
 */
public class PhotonSailSystem extends EntitySystem {

    public static final int PRIORITY = 5;

    /** Speed of light (m/s). */
    private static final float C = 3e8f;

    private static final ComponentMapper<PhotonSailComponent> SAIL_M =
        ComponentMapper.getFor(PhotonSailComponent.class);
    private static final ComponentMapper<SolarAffectedComponent> AFFECTED_M =
        ComponentMapper.getFor(SolarAffectedComponent.class);
    private static final ComponentMapper<SolarSourceComponent> SOURCE_M =
        ComponentMapper.getFor(SolarSourceComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private static final Family SAIL_FAMILY = Family.all(
        PhotonSailComponent.class, SolarAffectedComponent.class,
        PhysicsBodyComponent.class, TransformComponent.class
    ).get();

    private static final Family SOURCE_FAMILY = Family.all(
        SolarSourceComponent.class, TransformComponent.class
    ).get();

    private final EventBus eventBus;
    private ImmutableArray<Entity> sailEntities;
    private ImmutableArray<Entity> sources;

    private final Pool<Vector3> vectorPool = new Pool<Vector3>() {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };
    private final Matrix4 tempMat = new Matrix4();

    public PhotonSailSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        sailEntities = engine.getEntitiesFor(SAIL_FAMILY);
        sources = engine.getEntitiesFor(SOURCE_FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        sailEntities = null;
        sources = null;
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0, sn = sailEntities.size(); i < sn; i++) {
            Entity sailEntity = sailEntities.get(i);
            PhotonSailComponent sail = SAIL_M.get(sailEntity);
            if (!sail.isSailDeployed) continue;

            PhysicsBodyComponent physics = PHYSICS_M.get(sailEntity);
            if (physics.body == null || physics.mass <= 0f) continue;

            Vector3 entityPos = vectorPool.obtain();
            physics.body.getWorldTransform(tempMat);
            tempMat.getTranslation(entityPos);

            try {
                for (int j = 0, srcN = sources.size(); j < srcN; j++) {
                    Entity starEntity = sources.get(j);
                    SolarSourceComponent source = SOURCE_M.get(starEntity);
                    TransformComponent starTransform = TRANSFORM_M.get(starEntity);

                    applySailThrust(entityPos, sail, physics, source, starTransform);
                }
            } finally {
                vectorPool.free(entityPos);
            }
        }
    }

    private void applySailThrust(Vector3 entityPos, PhotonSailComponent sail,
                                 PhysicsBodyComponent physics,
                                 SolarSourceComponent source,
                                 TransformComponent starTransform) {
        Vector3 toStar = vectorPool.obtain();
        Vector3 effectiveNormal = vectorPool.obtain();
        Vector3 forceVec = vectorPool.obtain();
        try {
            toStar.set(starTransform.position).sub(entityPos);
            float dist = toStar.len();
            if (dist <= 0f) return;
            toStar.nor();

            // Compute effective sail normal with tacking
            effectiveNormal.set(sail.sailNormal).nor();
            if (sail.tackAngle != 0f) {
                // Tack axis is perpendicular to both sail normal and star direction
                Vector3 tackAxis = vectorPool.obtain();
                try {
                    tackAxis.set(effectiveNormal).crs(toStar);
                    float tackAxisLen = tackAxis.len();
                    if (tackAxisLen > 1e-6f) {
                        tackAxis.scl(1f / tackAxisLen);
                        effectiveNormal.rotate(tackAxis,
                            MathUtils.radiansToDegrees * sail.tackAngle);
                    }
                } finally {
                    vectorPool.free(tackAxis);
                }
            }

            // Cos of angle between sail normal and incoming light direction
            // Incoming light comes FROM star, so direction is -toStar
            float cosAngle = Math.abs(effectiveNormal.dot(toStar));
            if (cosAngle <= 0f) return;

            // Flux at distance (W/m^2)
            float flux = source.luminosity / (4f * MathUtils.PI * dist * dist);

            // Thrust magnitude
            float thrustMag = (flux / C)
                * (1f + sail.sailReflectivity) * sail.sailArea * cosAngle;

            // Force direction: reflected off sail surface
            // For a perfect reflector, force is along 2 * (n . L) * n - L
            // Simplified: force is along the effective sail normal, away from star
            forceVec.set(effectiveNormal);
            // Ensure we push away from star (dot with -toStar should be positive)
            if (effectiveNormal.dot(toStar) > 0f) {
                forceVec.scl(-1f);
            }
            forceVec.scl(thrustMag);

            physics.body.applyCentralForce(forceVec);
            physics.body.activate();
        } finally {
            vectorPool.free(toStar);
            vectorPool.free(effectiveNormal);
            vectorPool.free(forceVec);
        }
    }
}
