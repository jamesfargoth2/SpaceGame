package com.galacticodyssey.crafting;

/**
 * Reasons a refining request may be rejected.
 */
public enum RefiningFailureReason {
    NO_REFINERY,
    TIER_TOO_LOW,
    QUEUE_FULL,
    INSUFFICIENT_MATERIALS,
    RECIPE_NOT_FOUND
}
