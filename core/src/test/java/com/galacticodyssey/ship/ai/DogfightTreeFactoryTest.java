package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DogfightTreeFactoryTest {

    @Test
    void buildsAndStepsWithoutTargetSelectingPatrol() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        Entity e = new Entity();
        e.add(ai);

        BehaviorTree<Entity> tree = DogfightTreeFactory.build(e, 500f);
        ai.behaviorTree = tree;
        ai.blackboard.hasTarget = false;

        tree.step();
        assertEquals(0.2f, ai.blackboard.desiredThrottle, 1e-3);
    }

    @Test
    void inArcTargetTriggersAttackRunFire() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        ai.archetype.preferredEngageRange = 350f;
        Entity e = new Entity();
        e.add(ai);

        BehaviorTree<Entity> tree = DogfightTreeFactory.build(e, 500f);
        ai.behaviorTree = tree;
        ai.blackboard.hasTarget = true;
        ai.blackboard.selfHealthPercent = 1f;
        ai.blackboard.angleOffBore = 2f;
        ai.blackboard.rangeToTarget = 300f;
        ai.blackboard.closureRate = 0f;

        tree.step();
        assertTrue(ai.blackboard.fireGuns, "aligned in-range target should produce gun fire intent");
    }
}
