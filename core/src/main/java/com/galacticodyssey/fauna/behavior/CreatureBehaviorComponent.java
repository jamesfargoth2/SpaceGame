package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.math.Vector3;

public class CreatureBehaviorComponent implements Component {
    public StateMachine<Entity, CreatureState> stateMachine;
    public String speciesId;
    public int spawnGroupId = -1;

    public final Vector3 homePosition = new Vector3();
    public final Vector3 wanderTarget = new Vector3();
    public float stateTimer = 0f;
    public float idleDuration = 5f;

    public Diet diet = Diet.HERBIVORE;
    public Temperament temperament = Temperament.NEUTRAL;
    public SocialStructure socialStructure = SocialStructure.SOLITARY;
    public float detectionRadius = 25f;
    public float fleeRadius = 15f;
    public float fleeSpeedMultiplier = 1.5f;
    public float safeDistance = 40f;
}
