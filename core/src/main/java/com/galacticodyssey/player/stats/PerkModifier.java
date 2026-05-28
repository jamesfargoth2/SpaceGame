package com.galacticodyssey.player.stats;

/** One typed effect contributed by a perk. Fields are public for libGDX Json binding. */
public class PerkModifier {
    public PerkTarget target;
    public ModifierOp op;
    public float value;

    public PerkModifier() {}

    public PerkModifier(PerkTarget target, ModifierOp op, float value) {
        this.target = target;
        this.op = op;
        this.value = value;
    }
}
