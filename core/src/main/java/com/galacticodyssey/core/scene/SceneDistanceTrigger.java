package com.galacticodyssey.core.scene;

/**
 * Pure distance threshold with hysteresis to prevent transition thrash at a scene boundary.
 * Enter only when distance &lt; enterRadius; once inside, stay until distance &gt; exitRadius.
 */
public final class SceneDistanceTrigger {

    private final float enterRadius;
    private final float exitRadius;

    public SceneDistanceTrigger(float enterRadius, float exitRadius) {
        if (enterRadius >= exitRadius) {
            throw new IllegalArgumentException(
                "enterRadius (" + enterRadius + ") must be < exitRadius (" + exitRadius + ") for hysteresis");
        }
        this.enterRadius = enterRadius;
        this.exitRadius = exitRadius;
    }

    /**
     * @param currentlyInside whether the player is currently treated as inside the boundary
     * @param distance        current distance to the boundary body
     * @return whether the player should now be treated as inside
     */
    public boolean shouldBeInside(boolean currentlyInside, float distance) {
        if (currentlyInside) {
            return distance <= exitRadius;
        }
        return distance < enterRadius;
    }

    public float getEnterRadius() { return enterRadius; }
    public float getExitRadius() { return exitRadius; }
}
