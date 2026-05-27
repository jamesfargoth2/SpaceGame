package com.galacticodyssey.galaxy.station;

/**
 * Classification of space station by size and purpose.
 * Module count ranges define the structural complexity of each type.
 */
public enum StationType {
    OUTPOST(2, 5),
    TRADING_POST(5, 12),
    WAYSTATION(12, 30),
    STARPORT(30, 80),
    SHIPYARD(20, 50),
    RESEARCH_STATION(8, 20),
    BATTLE_STATION(25, 60);

    public final int minModules;
    public final int maxModules;

    StationType(int minModules, int maxModules) {
        this.minModules = minModules;
        this.maxModules = maxModules;
    }
}
