package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/**
 * Succeeds when the ship is likely under threat: a close target with high positive closure.
 */
public class IsBeingThreatenedCondition extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    public float threatRange = 250f;

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;
        boolean threatened = ai.blackboard.rangeToTarget < threatRange
            && ai.blackboard.closureRate > 30f;
        return threatened ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        ((IsBeingThreatenedCondition) task).threatRange = threatRange;
        return task;
    }
}
