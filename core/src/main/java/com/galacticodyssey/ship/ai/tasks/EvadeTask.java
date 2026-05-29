package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/**
 * Break away from the target: aim perpendicular to the line of sight, full throttle, hard roll.
 * Never requests fire.
 */
public class EvadeTask extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    private final Vector3 perp = new Vector3();

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;

        Vector3 aim = ai.blackboard.desiredAimDir;
        perp.set(aim).crs(Vector3.Y);
        if (perp.len2() < 1e-4f) perp.set(1, 0, 0);
        perp.nor();
        ai.blackboard.desiredAimDir.set(perp);
        ai.blackboard.desiredThrottle = 1f;
        ai.blackboard.desiredRoll = 1f;
        ai.blackboard.fireGuns = false;
        ai.blackboard.fireMissiles = false;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
