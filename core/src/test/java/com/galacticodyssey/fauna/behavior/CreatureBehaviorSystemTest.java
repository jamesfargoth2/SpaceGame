package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureBehaviorSystemTest {

    private Entity creature;
    private CreatureBehaviorComponent behavior;
    private CreatureDrivesComponent drives;
    private TransformComponent transform;

    @BeforeEach
    void setUp() {
        creature = new Entity();
        behavior = new CreatureBehaviorComponent();
        behavior.stateMachine = new DefaultStateMachine<>(creature, CreatureState.IDLE);
        behavior.temperament = Temperament.TIMID;
        behavior.detectionRadius = 20f;
        behavior.fleeRadius = 10f;
        behavior.safeDistance = 30f;
        behavior.idleDuration = 2f;
        creature.add(behavior);

        drives = new CreatureDrivesComponent();
        creature.add(drives);

        transform = new TransformComponent();
        transform.position.set(0, 0, 0);
        creature.add(transform);
    }

    @Test
    void startsInIdleState() {
        assertEquals(CreatureState.IDLE, behavior.stateMachine.getCurrentState());
    }

    @Test
    void idleTransitionsToWanderAfterTimer() {
        behavior.stateTimer = 3f;
        CreatureBehaviorSystem.updateEntity(creature, behavior, drives, transform, 0.1f, null, -1f);
        assertEquals(CreatureState.WANDER, behavior.stateMachine.getCurrentState());
    }

    @Test
    void alertWhenThreatInDetectionRadius() {
        Vector3 threatPos = new Vector3(15, 0, 0);
        CreatureBehaviorSystem.updateEntity(creature, behavior, drives, transform, 0.1f, threatPos, -1f);
        assertEquals(CreatureState.ALERT, behavior.stateMachine.getCurrentState());
    }

    @Test
    void fleeWhenThreatInFleeRadius() {
        Vector3 threatPos = new Vector3(5, 0, 0);
        CreatureBehaviorSystem.updateEntity(creature, behavior, drives, transform, 0.1f, threatPos, -1f);
        assertEquals(CreatureState.FLEE, behavior.stateMachine.getCurrentState());
    }

    @Test
    void fleeReturnsToAlertWhenThreatFarEnough() {
        behavior.stateMachine.changeState(CreatureState.FLEE);
        Vector3 threatPos = new Vector3(35, 0, 0);
        CreatureBehaviorSystem.updateEntity(creature, behavior, drives, transform, 0.1f, threatPos, -1f);
        assertEquals(CreatureState.ALERT, behavior.stateMachine.getCurrentState());
    }
}
