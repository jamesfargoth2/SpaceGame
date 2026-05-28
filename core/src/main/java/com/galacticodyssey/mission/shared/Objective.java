package com.galacticodyssey.mission.shared;

public class Objective {
    public String id;
    public ObjectiveType type;
    public String targetId;
    public int requiredCount;
    public int currentCount;
    public boolean optional;
    public boolean completed;

    public float optionalBonusMultiplier() {
        return optional ? 1.25f : 1.0f;
    }

    public boolean isSatisfied() {
        return currentCount >= requiredCount;
    }
}
