package com.galacticodyssey.combat.ai;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CoverPoint;

/** Shared per-NPC blackboard passed as the BehaviorTree object. */
public class CombatBlackboard {
    public Entity self;
    public Entity target;
    public final Vector3 targetPosition = new Vector3();
    public final Vector3 moveTarget = new Vector3();
    public CoverPoint selectedCover;
    public final Vector3 retreatPoint = new Vector3();
    public boolean hasLineOfSight;
    public float distanceToTarget;
    public float selfHealthPercent;
}
