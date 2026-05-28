package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.GravitySourceComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.SOITrackerComponent;
import com.galacticodyssey.core.components.TrajectoryComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.galaxy.KeplerOrbit;
import com.galacticodyssey.galaxy.OrbitalMechanics;

public class TrajectoryPredictionSystem extends EntitySystem {

    private static final float SMA_CHANGE_THRESHOLD = 0.01f;
    private static final float ECC_CHANGE_THRESHOLD = 0.01f;

    private static final ComponentMapper<TrajectoryComponent> trajectoryMapper =
        ComponentMapper.getFor(TrajectoryComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<SOITrackerComponent> soiMapper =
        ComponentMapper.getFor(SOITrackerComponent.class);
    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<GravitySourceComponent> gravitySourceMapper =
        ComponentMapper.getFor(GravitySourceComponent.class);

    private ImmutableArray<Entity> trackedEntities;

    public TrajectoryPredictionSystem() {
        super(6);
    }

    @Override
    public void addedToEngine(Engine engine) {
        trackedEntities = engine.getEntitiesFor(
            Family.all(TrajectoryComponent.class, TransformComponent.class,
                       SOITrackerComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        trackedEntities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (trackedEntities == null) return;

        Vector3 relPos = Pools.obtain(Vector3.class);
        Vector3 relVel = Pools.obtain(Vector3.class);
        Vector3 dominantPos = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < trackedEntities.size(); i++) {
                Entity entity = trackedEntities.get(i);
                SOITrackerComponent soi = soiMapper.get(entity);
                if (soi.dominantBody == null) continue;
                if (!gravitySourceMapper.has(soi.dominantBody)) continue;

                TransformComponent entityTransform = transformMapper.get(entity);
                TransformComponent dominantTransform = transformMapper.get(soi.dominantBody);
                GravitySourceComponent dominantGravity = gravitySourceMapper.get(soi.dominantBody);

                dominantPos.set(dominantTransform.position);
                relPos.set(entityTransform.position).sub(dominantPos);

                PhysicsBodyComponent physics = physicsMapper.has(entity) ? physicsMapper.get(entity) : null;
                if (physics != null && physics.body != null) {
                    relVel.set(physics.body.getLinearVelocity());
                } else {
                    relVel.setZero();
                }

                TrajectoryComponent traj = trajectoryMapper.get(entity);
                computeTrajectory(traj, relPos, relVel, dominantGravity.mass, dominantPos);
            }
        } finally {
            Pools.free(relPos);
            Pools.free(relVel);
            Pools.free(dominantPos);
        }
    }

    public void computeTrajectory(TrajectoryComponent traj, Vector3 relPos, Vector3 relVel,
                                   float dominantMass, Vector3 dominantWorldPos) {
        float r = relPos.len();
        if (r < 1f) return;

        KeplerOrbit orbit = OrbitalMechanics.fromStateVectors(relPos, relVel, dominantMass);
        float GM = OrbitalMechanics.G * dominantMass;

        boolean needsResample = traj.dirty;
        if (traj.currentOrbit != null && !traj.dirty) {
            float smaChange = Math.abs(orbit.semiMajorAxis - traj.currentOrbit.semiMajorAxis)
                / Math.max(1f, Math.abs(traj.currentOrbit.semiMajorAxis));
            float eccChange = Math.abs(orbit.eccentricity - traj.currentOrbit.eccentricity);
            if (smaChange > SMA_CHANGE_THRESHOLD || eccChange > ECC_CHANGE_THRESHOLD) {
                needsResample = true;
            }
        }

        traj.currentOrbit = orbit;
        traj.isStable = OrbitalMechanics.isStableOrbit(relPos, relVel, GM, 0f);
        traj.periapsis = orbit.periapsis;
        traj.apoapsis = orbit.apoapsis;

        if (needsResample) {
            traj.predictedPath.clear();
            if (orbit.eccentricity < 1f) {
                OrbitalMechanics.sampleOrbit(orbit, traj.sampleSegments, traj.predictedPath);
            } else {
                float startNu = -MathUtils.PI * 0.4f;
                float endNu = MathUtils.PI * 0.4f;
                float step = (endNu - startNu) / traj.sampleSegments;
                for (int s = 0; s < traj.sampleSegments; s++) {
                    float nu = startNu + s * step;
                    traj.predictedPath.add(OrbitalMechanics.orbitPosition(orbit, nu, new Vector3()));
                }
            }

            for (int j = 0; j < traj.predictedPath.size; j++) {
                traj.predictedPath.get(j).add(dominantWorldPos);
            }

            traj.dirty = false;
        }
    }
}
