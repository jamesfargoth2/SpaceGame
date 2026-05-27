package com.galacticodyssey.persistence.snapshots;

import java.util.UUID;

public class CombatAISnapshot {
    public UUID currentTargetId;
    public float aggroRange;
    public float engageRange;
    public float preferredRangeMin;
    public float preferredRangeMax;
    public float aggression;
    public float threatLevel;
    public float lastKnownX;
    public float lastKnownY;
    public float lastKnownZ;
    public boolean hasLastKnownPosition;
    public float searchTimer;
    public float searchDuration;
    public String archetypeId;
    public CombatAISnapshot() {}
}
