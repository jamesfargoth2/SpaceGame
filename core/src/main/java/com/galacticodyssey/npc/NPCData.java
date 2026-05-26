package com.galacticodyssey.npc;

import java.util.List;

/** Immutable data bag holding all procedurally generated NPC attributes. */
public final class NPCData {

    public final String id;
    public final String name;
    public final NPCRole role;

    /** Faction this NPC belongs to, or null if independent. */
    public final String factionId;

    public final float age;
    /** One of "human", "alien_harsh", "alien_soft". */
    public final String species;

    // --- stats (all 0-1) ---
    public final float piloting;
    public final float engineering;
    public final float combat;
    public final float medicine;
    public final float science;
    public final float persuasion;
    public final float stealth;

    // --- personality ---
    public final List<PersonalityTrait> traits;
    public final float loyalty;
    public final float greed;
    public final float bravery;

    // --- narrative ---
    public final List<BackstoryHook> hooks;
    public final long appearanceSeed;

    public NPCData(String id, String name, NPCRole role, String factionId,
                   float age, String species,
                   float piloting, float engineering, float combat,
                   float medicine, float science, float persuasion, float stealth,
                   List<PersonalityTrait> traits, float loyalty, float greed, float bravery,
                   List<BackstoryHook> hooks, long appearanceSeed) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.factionId = factionId;
        this.age = age;
        this.species = species;
        this.piloting = piloting;
        this.engineering = engineering;
        this.combat = combat;
        this.medicine = medicine;
        this.science = science;
        this.persuasion = persuasion;
        this.stealth = stealth;
        this.traits = List.copyOf(traits);
        this.loyalty = loyalty;
        this.greed = greed;
        this.bravery = bravery;
        this.hooks = List.copyOf(hooks);
        this.appearanceSeed = appearanceSeed;
    }
}
