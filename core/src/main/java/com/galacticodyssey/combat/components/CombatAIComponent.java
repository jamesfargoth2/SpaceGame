package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.CombatAISnapshot;
import java.util.UUID;

public class CombatAIComponent implements Component, Snapshotable<CombatAISnapshot> {
    public BehaviorTree<Entity> behaviorTree;
    public Entity currentTarget;
    /** Persisted UUID for {@link #currentTarget}; resolved by ReferenceResolver on load. */
    public UUID currentTargetId;
    public float aggroRange = 25f;
    public float engageRange = 20f;
    public float preferredRangeMin = 10f;
    public float preferredRangeMax = 20f;
    public float aggression = 0.5f;
    public float threatLevel;
    public final Vector3 lastKnownTargetPosition = new Vector3();
    public boolean hasLastKnownPosition;
    public float searchTimer;
    public float searchDuration = 10f;
    public String archetypeId;

    @Override
    public CombatAISnapshot takeSnapshot() {
        CombatAISnapshot s = new CombatAISnapshot();
        s.currentTargetId = currentTargetId;
        s.aggroRange = aggroRange;
        s.engageRange = engageRange;
        s.preferredRangeMin = preferredRangeMin;
        s.preferredRangeMax = preferredRangeMax;
        s.aggression = aggression;
        s.threatLevel = threatLevel;
        s.lastKnownX = lastKnownTargetPosition.x;
        s.lastKnownY = lastKnownTargetPosition.y;
        s.lastKnownZ = lastKnownTargetPosition.z;
        s.hasLastKnownPosition = hasLastKnownPosition;
        s.searchTimer = searchTimer;
        s.searchDuration = searchDuration;
        s.archetypeId = archetypeId;
        return s;
    }

    @Override
    public void restoreFromSnapshot(CombatAISnapshot s) {
        currentTargetId = s.currentTargetId;
        // currentTarget (Entity) and behaviorTree are resolved/rebuilt from archetypeId by ReferenceResolver
        aggroRange = s.aggroRange;
        engageRange = s.engageRange;
        preferredRangeMin = s.preferredRangeMin;
        preferredRangeMax = s.preferredRangeMax;
        aggression = s.aggression;
        threatLevel = s.threatLevel;
        lastKnownTargetPosition.set(s.lastKnownX, s.lastKnownY, s.lastKnownZ);
        hasLastKnownPosition = s.hasLastKnownPosition;
        searchTimer = s.searchTimer;
        searchDuration = s.searchDuration;
        archetypeId = s.archetypeId;
    }
}
