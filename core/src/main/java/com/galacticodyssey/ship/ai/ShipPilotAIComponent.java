package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ShipPilotAISnapshot;

/**
 * Per-ship dogfight AI state. Mirrors the ground-combat {@code CombatAIComponent}: the behaviour
 * tree and live target are runtime-only (rebuilt from {@code archetypeId} / re-acquired), while
 * tuning is persisted.
 */
public class ShipPilotAIComponent implements Component, Snapshotable<ShipPilotAISnapshot> {

    /** Built by DogfightTreeFactory; stepped each FULL-tier tick. May be null (skipped). */
    public BehaviorTree<Entity> behaviorTree;
    public Entity currentTarget;
    public PilotArchetype archetype;
    public String archetypeId;
    public final DogfightBlackboard blackboard = new DogfightBlackboard();

    /** Seconds between behaviour-tree decisions (set from archetype.reactionTimeSec). */
    public float decisionInterval = 0.25f;
    /** Counts down to the next decision. */
    public float decisionTimer;

    @Override
    public ShipPilotAISnapshot takeSnapshot() {
        ShipPilotAISnapshot s = new ShipPilotAISnapshot();
        s.archetypeId = archetypeId;
        s.decisionInterval = decisionInterval;
        return s;
    }

    @Override
    public void restoreFromSnapshot(ShipPilotAISnapshot s) {
        archetypeId = s.archetypeId;
        decisionInterval = s.decisionInterval;
    }
}
