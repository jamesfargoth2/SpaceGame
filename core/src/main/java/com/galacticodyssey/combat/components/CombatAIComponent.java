package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.math.Vector3;

public class CombatAIComponent implements Component {
    public BehaviorTree<Entity> behaviorTree;
    public Entity currentTarget;
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
}
