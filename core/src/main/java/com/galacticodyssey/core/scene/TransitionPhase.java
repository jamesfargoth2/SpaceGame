package com.galacticodyssey.core.scene;

/** Phases of the single in-flight scene transition. */
public enum TransitionPhase {
    IDLE,
    REQUESTED,
    PRELOADING,
    READY_OVERLAP,
    ACTIVATING,
    UNLOADING_OLD
}
