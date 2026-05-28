package com.galacticodyssey.player.stats;

import java.util.ArrayList;
import java.util.List;

/** A single perk node. Public fields + no-arg ctor for libGDX Json binding. */
public class PerkNodeDef {
    public String id;
    public String name;
    public String description;
    public String treeSkill;            // RealTimeSkill enum name, e.g. "FIREARMS"
    public int    tier;                 // depth, 0 = root
    public int    requiredSkillLevel;   // gate on the anchoring skill's level
    public List<String> prerequisitePerkIds = new ArrayList<>();
    public List<PerkModifier> modifiers      = new ArrayList<>();
    public String specialEffectId;      // nullable; named-handler id when no modifier fits

    public PerkNodeDef() {}
}
