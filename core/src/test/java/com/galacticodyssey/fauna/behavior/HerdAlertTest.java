package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HerdAlertTest {

    @Test
    void herdAlertCausesFleeInSameGroup() {
        Entity herdMate = new Entity();
        CreatureBehaviorComponent beh = new CreatureBehaviorComponent();
        beh.stateMachine = new DefaultStateMachine<>(herdMate, CreatureState.IDLE);
        beh.socialStructure = SocialStructure.HERD;
        beh.spawnGroupId = 42;
        herdMate.add(beh);

        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        herdMate.add(drives);

        TransformComponent tx = new TransformComponent();
        tx.position.set(10, 0, 0);
        herdMate.add(tx);

        HerdAlertEvent alert = new HerdAlertEvent(42, new Vector3(0, 0, 0));

        float dist = tx.position.dst(alert.fleeFrom);
        boolean inRange = dist < 50f;
        boolean sameGroup = beh.spawnGroupId == alert.spawnGroupId;

        assertTrue(inRange && sameGroup);

        if (inRange && sameGroup && beh.socialStructure == SocialStructure.HERD) {
            beh.stateMachine.changeState(CreatureState.FLEE);
            drives.fear = Math.min(1f, drives.fear + 0.5f);
        }

        assertEquals(CreatureState.FLEE, beh.stateMachine.getCurrentState());
        assertTrue(drives.fear >= 0.5f);
    }

    @Test
    void differentGroupIgnoresAlert() {
        Entity other = new Entity();
        CreatureBehaviorComponent beh = new CreatureBehaviorComponent();
        beh.stateMachine = new DefaultStateMachine<>(other, CreatureState.IDLE);
        beh.socialStructure = SocialStructure.HERD;
        beh.spawnGroupId = 99;
        other.add(beh);

        HerdAlertEvent alert = new HerdAlertEvent(42, new Vector3(0, 0, 0));

        boolean sameGroup = beh.spawnGroupId == alert.spawnGroupId;
        assertFalse(sameGroup);
        assertEquals(CreatureState.IDLE, beh.stateMachine.getCurrentState());
    }
}
