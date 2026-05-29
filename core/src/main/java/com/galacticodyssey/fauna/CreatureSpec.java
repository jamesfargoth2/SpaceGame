package com.galacticodyssey.fauna;

import com.badlogic.gdx.math.collision.BoundingBox;
import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.fauna.assembly.AssembledNode;

import java.util.ArrayList;
import java.util.List;

/** Deterministic, GL-free blueprint of a fully assembled creature. */
public final class CreatureSpec {
    public long seed;
    public String archetypeId;
    public BodyPlan bodyPlan;
    public String gaitClass = "walk";
    public AssembledNode root;
    public final List<AssembledNode> allNodes = new ArrayList<>(); // flattened, root first

    public float sizeMultiplier = 1f;
    public float mass;        // kg
    public float maxHP;
    public float moveSpeed;   // m/s
    public float meleeDamage;
    public long colorSeed;    // drives flat biome tint now; full patterns in Cycle C
    public com.galacticodyssey.fauna.skin.CreatureSkinSpec skinSpec;

    public final BoundingBox bounds = new BoundingBox();

    public int partCount() { return allNodes.size(); }

    public int countOfType(com.galacticodyssey.fauna.part.PartType type) {
        int n = 0;
        for (AssembledNode node : allNodes) if (node.part.partType == type) n++;
        return n;
    }
}
