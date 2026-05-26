package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;

/**
 * Placeholder patrol behavior. Will follow waypoint data when waypoint
 * components are implemented. Currently always SUCCEEDED so the branch
 * completes without blocking.
 */
public class PatrolTask extends LeafTask<Entity> {

    @Override
    public Status execute() {
        // TODO: follow PatrolComponent waypoints when available
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
