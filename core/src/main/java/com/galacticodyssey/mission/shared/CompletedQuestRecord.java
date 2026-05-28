package com.galacticodyssey.mission.shared;

public class CompletedQuestRecord {
    public final String questId;
    public final String questName;
    public final String questType;
    public final QuestOutcome outcome;
    public final int creditsEarned;
    public final String reputationFaction;
    public final float reputationDelta;
    public final long timestampMs;

    public CompletedQuestRecord(String questId, String questName, String questType,
                                QuestOutcome outcome, int creditsEarned,
                                String reputationFaction, float reputationDelta,
                                long timestampMs) {
        this.questId = questId;
        this.questName = questName;
        this.questType = questType;
        this.outcome = outcome;
        this.creditsEarned = creditsEarned;
        this.reputationFaction = reputationFaction;
        this.reputationDelta = reputationDelta;
        this.timestampMs = timestampMs;
    }
}
