package com.galacticodyssey.fauna.archetype;

/** Template for one body plan: the attachment tree, size/mass band, stat constants, gait metadata. */
public final class BodyPlanArchetypeDef {
    public String id;
    public BodyPlan bodyPlan;
    public AttachmentNode root;            // root.partType is the torso/root part type

    public float minSize = 1f;             // overall size multiplier band (mouse .. megafauna)
    public float maxSize = 1f;
    public float density = 1000f;          // kg/m^3 for mass calc

    public String gaitClass = "walk";      // metadata for Cycle B

    // Allometric stat constants (tunable). HP = kHp*m^(2/3), speed = kSpeed*m^(-1/4), dmg = kDmg*m^(3/4)
    public float kHp = 12f;
    public float kSpeed = 9f;
    public float kDamage = 4f;
}
