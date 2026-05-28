package com.galacticodyssey.stealth;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.dynamics.btDynamicsWorld;

/**
 * Bullet-physics implementation of {@link LineOfSightQuery}.
 *
 * <p>Fires a closest-hit ray test between two world-space positions. Returns {@code true}
 * (has LoS) when no collision object intersects the ray. Returns {@code false} when the
 * ray hits something (i.e., the target is occluded).
 *
 * <p>The internal {@link ClosestRayResultCallback} is reused across calls to avoid per-frame
 * allocation. Call {@link #dispose()} when this query is no longer needed.
 */
public final class BulletLineOfSightQuery implements LineOfSightQuery {

    private final btDynamicsWorld world;
    private final ClosestRayResultCallback rayCallback;

    public BulletLineOfSightQuery(btDynamicsWorld world) {
        this.world = world;
        this.rayCallback = new ClosestRayResultCallback(Vector3.Zero, Vector3.Zero);
    }

    @Override
    public boolean hasLoS(Vector3 from, Vector3 to) {
        rayCallback.setCollisionObject(null);
        rayCallback.setClosestHitFraction(1f);
        rayCallback.setRayFromWorld(from);
        rayCallback.setRayToWorld(to);
        world.rayTest(from, to, rayCallback);
        return !rayCallback.hasHit();
    }

    /** Releases the native Bullet callback. Must be called when this object is no longer needed. */
    public void dispose() {
        rayCallback.dispose();
    }
}
