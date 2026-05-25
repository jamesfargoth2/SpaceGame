package com.galacticodyssey.core;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.events.OriginRebasedEvent;

public final class CoordinateManager {

    private static final float REBASE_THRESHOLD = 1000f;

    private final EventBus eventBus;

    private double originOffsetX;
    private double originOffsetY;
    private double originOffsetZ;

    /** Full-precision galaxy coordinates from the most recent {@link #toLocalSpace} call. */
    private double lastGalaxyX;
    private double lastGalaxyY;
    private double lastGalaxyZ;
    /** Float result of the most recent {@link #toLocalSpace} call, used to match in {@link #checkRebase}. */
    private final Vector3 lastLocal = new Vector3();

    public CoordinateManager(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Converts galaxy-space (double) coordinates to local float space relative to the current origin.
     * Stores the full-precision input so that a subsequent {@link #checkRebase} call can recover it.
     */
    public Vector3 toLocalSpace(double galaxyX, double galaxyY, double galaxyZ) {
        lastGalaxyX = galaxyX;
        lastGalaxyY = galaxyY;
        lastGalaxyZ = galaxyZ;
        float lx = (float) (galaxyX - originOffsetX);
        float ly = (float) (galaxyY - originOffsetY);
        float lz = (float) (galaxyZ - originOffsetZ);
        lastLocal.set(lx, ly, lz);
        return new Vector3(lx, ly, lz);
    }

    /** Converts a local float position back to galaxy-space doubles. */
    public double[] toGalaxySpace(Vector3 local) {
        return new double[]{
            local.x + originOffsetX,
            local.y + originOffsetY,
            local.z + originOffsetZ
        };
    }

    /**
     * Checks whether {@code playerLocalPos} exceeds the rebase threshold and, if so, shifts the
     * floating origin.  If {@code playerLocalPos} matches the result of the most recent
     * {@link #toLocalSpace} call (within float precision), the stored double-precision galaxy
     * coordinates are used so that no precision is lost when updating the origin offsets.
     */
    public void checkRebase(Vector3 playerLocalPos) {
        if (playerLocalPos.len() > REBASE_THRESHOLD) {
            double newOriginX, newOriginY, newOriginZ;
            // Recover full double precision when the caller passes the vector returned by toLocalSpace.
            if (lastLocal.epsilonEquals(playerLocalPos, 0.5f)) {
                newOriginX = lastGalaxyX;
                newOriginY = lastGalaxyY;
                newOriginZ = lastGalaxyZ;
            } else {
                newOriginX = originOffsetX + playerLocalPos.x;
                newOriginY = originOffsetY + playerLocalPos.y;
                newOriginZ = originOffsetZ + playerLocalPos.z;
            }

            float dx = (float) (newOriginX - originOffsetX);
            float dy = (float) (newOriginY - originOffsetY);
            float dz = (float) (newOriginZ - originOffsetZ);

            originOffsetX = newOriginX;
            originOffsetY = newOriginY;
            originOffsetZ = newOriginZ;

            eventBus.publish(new OriginRebasedEvent(dx, dy, dz));
        }
    }

    /** Returns the current X component of the floating origin in galaxy space. */
    public double getOriginOffsetX() { return originOffsetX; }

    /** Returns the current Y component of the floating origin in galaxy space. */
    public double getOriginOffsetY() { return originOffsetY; }

    /** Returns the current Z component of the floating origin in galaxy space. */
    public double getOriginOffsetZ() { return originOffsetZ; }
}
