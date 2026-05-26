package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Moves the entity toward the blackboard moveTarget (stored in lastKnownTargetPosition
 * for convenience since we don't have a separate blackboard reference here).
 * SUCCEEDED once within arrival threshold.
 */
public class MoveToTask extends LeafTask<Entity> {

    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private static final float ARRIVAL_THRESHOLD = 1.0f;
    /** Units-per-second move speed applied directly to position (physics-free placeholder). */
    private static final float MOVE_SPEED = 3.5f;

    @Override
    public Status execute() {
        Entity self = getObject();
        CombatAIComponent ai = AI_M.get(self);
        TransformComponent selfTransform = TRANSFORM_M.get(self);
        if (ai == null || selfTransform == null || !ai.hasLastKnownPosition) return Status.FAILED;

        Vector3 destination = ai.lastKnownTargetPosition;
        float dist = Vector3.dst(
            selfTransform.position.x, selfTransform.position.y, selfTransform.position.z,
            destination.x, destination.y, destination.z);

        if (dist <= ARRIVAL_THRESHOLD) return Status.SUCCEEDED;

        // Advance toward destination (deltaTime not available in leaf task; nudge by fixed step)
        Vector3 dir = new Vector3(destination).sub(selfTransform.position).nor();
        selfTransform.position.mulAdd(dir, MOVE_SPEED * 0.016f); // ~60fps frame step
        return Status.RUNNING;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
