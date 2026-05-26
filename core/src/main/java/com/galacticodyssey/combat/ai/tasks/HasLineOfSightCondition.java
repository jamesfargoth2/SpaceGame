package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;

/**
 * Simplified LOS check — always SUCCEEDED.
 * Replace with Bullet Physics raycast when physics world is available.
 */
public class HasLineOfSightCondition extends LeafTask<Entity> {

    @Override
    public Status execute() {
        // Placeholder: full Bullet raycast in a future task
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
