package com.galacticodyssey.stealth;

public enum AwarenessState {
    UNAWARE,    // normal patrol
    CURIOUS,    // investigating; suspicionTimer accumulates; transitions to ALERTED on threshold or timeout
    ALERTED,    // active pursuit; lastKnownPosition updated each frame
    SEARCHING   // lost contact; searching last known position; reverts to UNAWARE on timeout
}
