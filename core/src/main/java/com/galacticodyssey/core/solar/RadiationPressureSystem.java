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
 * Applies electromagnetic radiation pressure (photon momentum transfer)
 * to entities with {@link SolarAffectedComponent} from all
 * {@link SolarSourceComponent} stars.
 * <p>
 * Force = (luminosity / (4 * pi * dist^2 * c)) * (1 + reflectivity) * projectedArea,
 * directed away from the star.
 */
public class RadiationPressureSystem extends EntitySystem {

    public static final int PRIORITY = 4;

    /** Speed of light (m/s). */
    private static final float C = 3e8f;

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

    public RadiationPressureSystem(EventBus eventBus) {
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

            // Read position from the rigid body world transform
            Vector3 entityPos = vectorPool.obtain();
            physics.body.getWorldTransform(tempMat);
            tempMat.getTranslation(entityPos);

            try {
                for (int j = 0, sn = sources.size(); j < sn; j++) {
                    Entity starEntity = sources.get(j);
                    SolarSourceComponent source = SOURCE_M.get(starEntity);
                    TransformComponent starTransform = TRANSFORM_M.get(starEntity);

                    applyRadiationPressure(entityPos, solar, physics, source, starTransform);
                }
            } finally {
                vectorPool.free(entityPos);
            }
        }
    }

    private void applyRadiationPressure(Vector3 entityPos,
                                        SolarAffectedComponent solar,
                                        PhysicsBodyComponent physics,
                                        SolarSourceComponent source,
                                        TransformComponent starTransform) {
        Vector3 awayFromStar = vectorPool.obtain();
        try {
            awayFromStar.set(entityPos).sub(starTransform.position);
            float dist = awayFromStar.len();
            if (dist <= 0f) return;

            // Flux at distance (W/m^2)
            float flux = source.luminosity / (4f * MathUtils.PI * dist * dist);

            // Radiation pressure: P = flux/c * (1 + reflectivity)
            float pressure = flux / C * (1f + solar.reflectivity);

            // Force magnitude (Newtons)
            float forceMag = pressure * solar.projectedArea;

            // Direction: away from star
            awayFromStar.nor().scl(forceMag);
            physics.body.applyCentralForce(awayFromStar);
            physics.body.activate();
        } finally {
            vectorPool.free(awayFromStar);
        }
    }
}
