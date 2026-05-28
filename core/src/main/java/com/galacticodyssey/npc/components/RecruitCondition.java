package com.galacticodyssey.npc.components;

public final class RecruitCondition {
    public final RecruitConditionType type;
    public final String targetId;
    public final String description;
    public boolean met;

    public RecruitCondition(RecruitConditionType type, String targetId, String description) {
        this.type = type;
        this.targetId = targetId;
        this.description = description;
        this.met = false;
    }
}
