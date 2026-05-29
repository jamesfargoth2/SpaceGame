package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/**
 * When close with high closure (overshoot), keep aiming at the target but run full throttle to
 * extend and swing back around. Fallback reposition behaviour.
 */
public class ExtendAndReengageTask extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;

        float extend = ai.archetype != null ? ai.archetype.overshootExtendDist : 130f;
        ai.blackboard.desiredThrottle = ai.blackboard.rangeToTarget < extend ? 1f : 0.9f;
        ai.blackboard.desiredRoll = 0f;
        ai.blackboard.fireGuns = false;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
