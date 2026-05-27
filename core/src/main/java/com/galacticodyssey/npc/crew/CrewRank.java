package com.galacticodyssey.npc.crew;

public enum CrewRank {
    RECRUIT(0f, 10f),
    CREWMAN(100f, 20f),
    SPECIALIST(300f, 35f),
    VETERAN(600f, 55f),
    OFFICER(1000f, 80f),
    COMMANDER(1500f, 120f);

    public final float xpThreshold;
    public final float baseWage;

    CrewRank(float xpThreshold, float baseWage) {
        this.xpThreshold = xpThreshold;
        this.baseWage = baseWage;
    }

    public CrewRank nextRank() {
        int next = ordinal() + 1;
        CrewRank[] values = values();
        return next < values.length ? values[next] : null;
    }
}
