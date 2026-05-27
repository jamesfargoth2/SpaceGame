package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.GravityAffectedComponent;
import com.galacticodyssey.core.components.GravitySourceComponent;
import com.galacticodyssey.core.components.GravityZoneComponent;
import com.galacticodyssey.core.components.TransformComponent;

public class GravitySystem extends EntitySystem {

    public static final float G = 6.674e-11f;
    private static final float ZERO_G_THRESHOLD = 0.05f;

    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<GravitySourceComponent> sourceMapper =
        ComponentMapper.getFor(GravitySourceComponent.class);
    private static final ComponentMapper<GravityAffectedComponent> affectedMapper =
        ComponentMapper.getFor(GravityAffectedComponent.class);
    private static final ComponentMapper<GravityZoneComponent> zoneMapper =
        ComponentMapper.getFor(GravityZoneComponent.class);

    private ImmutableArray<Entity> sources;
    private ImmutableArray<Entity> affectedBodies;
    private ImmutableArray<Entity> zones;

    public GravitySystem() {
        super(1);
    }

    @Override
    public void addedToEngine(Engine engine) {
        sources = engine.getEntitiesFor(
            Family.all(TransformComponent.class, GravitySourceComponent.class).get());
        affectedBodies = engine.getEntitiesFor(
            Family.all(TransformComponent.class, GravityAffectedComponent.class).get());
        zones = engine.getEntitiesFor(
            Family.all(TransformComponent.class, GravityZoneComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        sources = null;
        affectedBodies = null;
        zones = null;
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < affectedBodies.size(); i++) {
            Entity body = affectedBodies.get(i);
            TransformComponent bodyTransform = transformMapper.get(body);
            GravityAffectedComponent affected = affectedMapper.get(body);

            computeNetAccelerationInternal(bodyTransform.position, affected.mass, affected.lastAcceleration);
        }
    }

    public Vector3 computeNetAcceleration(Vector3 position, float bodyMass) {
        Vector3 result = new Vector3();
        computeNetAccelerationInternal(position, bodyMass, result);
        return result;
    }

    public boolean isInZeroG(Vector3 position) {
        // Check explicit zero-g zones
        Vector3 tmp = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < zones.size(); i++) {
                Entity zoneEntity = zones.get(i);
                GravityZoneComponent zone = zoneMapper.get(zoneEntity);
                TransformComponent zoneTransform = transformMapper.get(zoneEntity);

                if (zone.mode == GravityZoneComponent.Mode.ZERO_G) {
                    float dist = tmp.set(position).sub(zoneTransform.position).len();
                    if (dist <= zone.radius) {
                        return true;
                    }
                }
            }

            // Check if net acceleration is below threshold
            computeNetAccelerationInternal(position, 1f, tmp);
            return tmp.len() < ZERO_G_THRESHOLD;
        } finally {
            Pools.free(tmp);
        }
    }

    private void computeNetAccelerationInternal(Vector3 position, float bodyMass, Vector3 out) {
        out.setZero();

        Vector3 direction = Pools.obtain(Vector3.class);
        try {
            // Accumulate gravitational forces from all active sources
            for (int i = 0; i < sources.size(); i++) {
                Entity srcEntity = sources.get(i);
                GravitySourceComponent src = sourceMapper.get(srcEntity);
                if (!src.active) continue;

                TransformComponent srcTransform = transformMapper.get(srcEntity);
                direction.set(srcTransform.position).sub(position);
                float dist = direction.len();

                if (dist > src.influenceRadius) continue;

                float clampedDist = Math.max(dist, src.minRadius);
                // F = G * m1 * m2 / r^e, acceleration = F / bodyMass = G * srcMass / r^e
                float accelMag = G * src.mass / (float) Math.pow(clampedDist, src.falloffExponent);

                if (dist > 0f) {
                    direction.scl(1f / dist); // normalize using original distance for direction
                }
                out.mulAdd(direction, accelMag);
            }

            // Apply zone overrides
            applyZoneOverrides(position, out);
        } finally {
            Pools.free(direction);
        }
    }

    private void applyZoneOverrides(Vector3 position, Vector3 acceleration) {
        Vector3 tmp = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < zones.size(); i++) {
                Entity zoneEntity = zones.get(i);
                GravityZoneComponent zone = zoneMapper.get(zoneEntity);
                TransformComponent zoneTransform = transformMapper.get(zoneEntity);

                float dist = tmp.set(position).sub(zoneTransform.position).len();
                if (dist > zone.radius) continue;

                switch (zone.mode) {
                    case ZERO_G:
                        acceleration.setZero();
                        return;
                    case CONSTANT:
                        acceleration.set(zone.constantVector);
                        return;
                    case SCALE:
                        acceleration.scl(zone.scaleFactor);
                        return;
                    case REDIRECT:
                        float mag = acceleration.len();
                        if (mag > 0f && zone.constantVector.len2() > 0f) {
                            acceleration.set(zone.constantVector).nor().scl(mag);
                        }
                        return;
                }
            }
        } finally {
            Pools.free(tmp);
        }
    }
}
