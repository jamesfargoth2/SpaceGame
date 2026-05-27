package com.galacticodyssey.galaxy.derelict;

/** Ship hull size classification with maximum crew capacity. */
public enum HullClass {
    SHUTTLE(4),
    CORVETTE(15),
    FRIGATE(40),
    CRUISER(120),
    CAPITAL(500);

    public final int maxCrew;

    HullClass(int maxCrew) {
        this.maxCrew = maxCrew;
    }
}
