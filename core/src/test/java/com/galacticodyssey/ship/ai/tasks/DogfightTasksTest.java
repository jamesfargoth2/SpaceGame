package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.ship.ai.PilotArchetype;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DogfightTasksTest {

    private Entity shipWith(ShipPilotAIComponent ai, HealthComponent hp) {
        Entity e = new Entity();
        e.add(ai);
        if (hp != null) e.add(hp);
        return e;
    }

    /** Wire a leaf task into a BehaviorTree so getObject() works, then call execute(). */
    private static <E> Task.Status run(LeafTask<E> task, E object) {
        BehaviorTree<E> tree = new BehaviorTree<>(task, object);
        task.setControl(tree);
        return task.execute();
    }

    @Test
    void hasTargetConditionReflectsBlackboard() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        Entity e = shipWith(ai, null);
        HasDogfightTargetCondition c = new HasDogfightTargetCondition();
        ai.blackboard.hasTarget = false;
        assertEquals(Task.Status.FAILED, run(c, e));
        ai.blackboard.hasTarget = true;
        assertEquals(Task.Status.SUCCEEDED, run(c, e));
    }

    @Test
    void attackRunFiresOnlyWhenInConeAndRange() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        ai.archetype.preferredEngageRange = 350f;
        Entity e = shipWith(ai, null);

        ai.blackboard.hasTarget = true;
        ai.blackboard.angleOffBore = 3f;
        ai.blackboard.rangeToTarget = 300f;

        AttackRunTask atk = new AttackRunTask();
        atk.weaponRange = 500f;
        atk.firingConeDeg = 6f;
        assertEquals(Task.Status.SUCCEEDED, run(atk, e));
        assertTrue(ai.blackboard.fireGuns, "should request guns when aligned + in range");

        ai.blackboard.fireGuns = false;
        ai.blackboard.angleOffBore = 30f;
        run(atk, e);
        assertFalse(ai.blackboard.fireGuns, "no fire when nose off target");
    }

    @Test
    void evadeTriggersAtLowHealth() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        ai.archetype.evadeHealthThreshold = 0.35f;
        ai.blackboard.hasTarget = true;
        Entity e = shipWith(ai, null);

        LowHealthCondition cond = new LowHealthCondition();
        ai.blackboard.selfHealthPercent = 0.5f;
        assertEquals(Task.Status.FAILED, run(cond, e));
        ai.blackboard.selfHealthPercent = 0.2f;
        assertEquals(Task.Status.SUCCEEDED, run(cond, e));
    }

    @Test
    void pursueSetsThrottleTowardTarget() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        ai.archetype.preferredEngageRange = 350f;
        ai.archetype.throttleDiscipline = 0.8f;
        ai.blackboard.hasTarget = true;
        ai.blackboard.rangeToTarget = 1000f;
        Entity e = shipWith(ai, null);

        PursueTargetTask pursue = new PursueTargetTask();
        assertEquals(Task.Status.SUCCEEDED, run(pursue, e));
        assertTrue(ai.blackboard.desiredThrottle > 0.5f, "throttle up when far from target");
    }
}
