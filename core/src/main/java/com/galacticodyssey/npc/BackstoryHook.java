package com.galacticodyssey.npc;

/** A backstory element that can trigger quests or dialogue for an NPC. */
public final class BackstoryHook {

    public final HookType type;
    public final boolean isRevealed;
    public final long questSeed;
    public final String summary;

    public BackstoryHook(HookType type, boolean isRevealed, long questSeed, String summary) {
        this.type = type;
        this.isRevealed = isRevealed;
        this.questSeed = questSeed;
        this.summary = summary;
    }
}
