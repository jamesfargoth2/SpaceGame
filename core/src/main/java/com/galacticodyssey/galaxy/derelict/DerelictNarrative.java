package com.galacticodyssey.galaxy.derelict;

/** Environmental storytelling data for a derelict wreck. */
public final class DerelictNarrative {
    public final int remainsCount;
    public final int logCount;
    public final boolean logsRevealCause;
    public final boolean logsRevealDestination;
    public final boolean hasMissionHook;
    public final int lastEntryDaysAgo;
    public final boolean hasBlackBox;
    public final boolean blackBoxIntact;
    public final boolean hasSurvivor;

    public DerelictNarrative(int remainsCount, int logCount,
                             boolean logsRevealCause, boolean logsRevealDestination,
                             boolean hasMissionHook, int lastEntryDaysAgo,
                             boolean hasBlackBox, boolean blackBoxIntact,
                             boolean hasSurvivor) {
        this.remainsCount = remainsCount;
        this.logCount = logCount;
        this.logsRevealCause = logsRevealCause;
        this.logsRevealDestination = logsRevealDestination;
        this.hasMissionHook = hasMissionHook;
        this.lastEntryDaysAgo = lastEntryDaysAgo;
        this.hasBlackBox = hasBlackBox;
        this.blackBoxIntact = blackBoxIntact;
        this.hasSurvivor = hasSurvivor;
    }
}
