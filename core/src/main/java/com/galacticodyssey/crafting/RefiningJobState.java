package com.galacticodyssey.crafting;

/**
 * Lifecycle states of a refining job within a refinery queue.
 */
public enum RefiningJobState {
    QUEUED,
    ACTIVE,
    PAUSED,
    COMPLETE,
    CANCELLED
}
