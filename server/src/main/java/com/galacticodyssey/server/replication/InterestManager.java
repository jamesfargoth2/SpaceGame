package com.galacticodyssey.server.replication;

/**
 * Determines the replication interest tier for an entity based on its distance
 * from the player. Used by ServerReplicationSystem to throttle update frequency:
 * <ul>
 *   <li>NEAR (0–500 m): every tick (~20 Hz)</li>
 *   <li>MID (500–2000 m): every 4th tick (~5 Hz)</li>
 *   <li>FAR (2000–10000 m): every 10th tick (~2 Hz)</li>
 *   <li>NONE (&gt;10 km): not replicated</li>
 * </ul>
 * All coordinates are local-space doubles (meters). Squared-distance comparisons
 * avoid the cost of {@code Math.sqrt}.
 */
public class InterestManager {

    private static final double NEAR_RADIUS = 500.0;
    private static final double MID_RADIUS = 2000.0;
    private static final double FAR_RADIUS = 10000.0;

    private static final double NEAR_SQ = NEAR_RADIUS * NEAR_RADIUS;
    private static final double MID_SQ = MID_RADIUS * MID_RADIUS;
    private static final double FAR_SQ = FAR_RADIUS * FAR_RADIUS;

    /**
     * Returns the interest tier for an entity at {@code (entityX, entityY, entityZ)}
     * relative to a player at {@code (playerX, playerY, playerZ)}.
     */
    public InterestTier computeTier(double playerX, double playerY, double playerZ,
                                    double entityX, double entityY, double entityZ) {
        final double dx = entityX - playerX;
        final double dy = entityY - playerY;
        final double dz = entityZ - playerZ;
        final double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq <= NEAR_SQ) return InterestTier.NEAR;
        if (distSq <= MID_SQ)  return InterestTier.MID;
        if (distSq <= FAR_SQ)  return InterestTier.FAR;
        return InterestTier.NONE;
    }

    /**
     * Returns {@code true} if an entity at the given tier should have its state
     * sent on this tick number.
     *
     * @param tier        the entity's current interest tier
     * @param currentTick monotonically increasing tick counter (0-based)
     */
    public boolean shouldSendThisTick(InterestTier tier, int currentTick) {
        return switch (tier) {
            case NEAR -> true;
            case MID  -> currentTick % 4 == 0;
            case FAR  -> currentTick % 10 == 0;
            case NONE -> false;
        };
    }
}
