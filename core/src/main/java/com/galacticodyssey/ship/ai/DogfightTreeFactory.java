package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.branch.Selector;
import com.badlogic.gdx.ai.btree.branch.Sequence;
import com.galacticodyssey.ship.ai.tasks.AttackRunTask;
import com.galacticodyssey.ship.ai.tasks.EvadeTask;
import com.galacticodyssey.ship.ai.tasks.ExtendAndReengageTask;
import com.galacticodyssey.ship.ai.tasks.HasDogfightTargetCondition;
import com.galacticodyssey.ship.ai.tasks.IdlePatrolTask;
import com.galacticodyssey.ship.ai.tasks.IsBeingThreatenedCondition;
import com.galacticodyssey.ship.ai.tasks.LowHealthCondition;
import com.galacticodyssey.ship.ai.tasks.PursueTargetTask;
import com.galacticodyssey.ship.ai.tasks.TargetInWeaponArcCondition;

/** Builds the dogfight behaviour tree in code (no .tree files exist in this project). */
public final class DogfightTreeFactory {

    private DogfightTreeFactory() {}

    /**
     * @param blackboardEntity the entity the tree operates on (carries ShipPilotAIComponent)
     * @param gunRange         the ship's actual gun range, used to gate AttackRun fire
     */
    public static BehaviorTree<Entity> build(Entity blackboardEntity, float gunRange) {
        AttackRunTask attack = new AttackRunTask();
        attack.weaponRange = gunRange;

        Selector<Entity> root = new Selector<>(
            new Sequence<>(new LowHealthCondition(), new EvadeTask()),
            new Sequence<>(new IsBeingThreatenedCondition(), new EvadeTask()),
            new Sequence<>(new TargetInWeaponArcCondition(), attack),
            new Sequence<>(new HasDogfightTargetCondition(), new PursueTargetTask()),
            new ExtendAndReengageTask(),
            new IdlePatrolTask()
        );

        return new BehaviorTree<>(root, blackboardEntity);
    }
}
