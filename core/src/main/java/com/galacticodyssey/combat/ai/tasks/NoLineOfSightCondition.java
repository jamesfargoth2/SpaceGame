package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;

/**
 * Inverse of HasLineOfSightCondition — always FAILED while LOS is unimplemented.
 * Replace with Bullet Physics raycast when physics world is available.
 */
public class NoLineOfSightCondition extends LeafTask<Entity> {

    @Override
    public Status execute() {
        // Placeholder: always has LOS for now, so no-LOS condition fails
        return Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
