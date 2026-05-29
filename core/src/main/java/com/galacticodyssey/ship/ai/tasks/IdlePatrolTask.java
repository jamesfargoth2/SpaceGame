package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/** No target: cruise gently straight ahead. Always succeeds (leaf fallback). */
public class IdlePatrolTask extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null) return Status.FAILED;
        ai.blackboard.desiredThrottle = 0.2f;
        ai.blackboard.desiredRoll = 0f;
        ai.blackboard.fireGuns = false;
        ai.blackboard.fireMissiles = false;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
