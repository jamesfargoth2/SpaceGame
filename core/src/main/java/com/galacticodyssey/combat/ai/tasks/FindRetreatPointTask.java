package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Picks a retreat position directly away from the threat (currentTarget),
 * at a distance of 15 units. Writes the result into ai.lastKnownTargetPosition
 * as the movement goal (callers read blackboard retreatPoint via moveTarget).
 */
public class FindRetreatPointTask extends LeafTask<Entity> {

    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private static final float RETREAT_DISTANCE = 15f;

    private final Vector3 tmp = new Vector3();

    @Override
    public Status execute() {
        Entity self = getObject();
        CombatAIComponent ai = AI_M.get(self);
        TransformComponent selfTransform = TRANSFORM_M.get(self);
        if (ai == null || selfTransform == null) return Status.FAILED;

        if (ai.currentTarget != null) {
            TransformComponent targetTransform = TRANSFORM_M.get(ai.currentTarget);
            if (targetTransform != null) {
                // Direction away from threat
                tmp.set(selfTransform.position).sub(targetTransform.position).nor();
                ai.lastKnownTargetPosition.set(selfTransform.position).mulAdd(tmp, RETREAT_DISTANCE);
                ai.hasLastKnownPosition = true;
                return Status.SUCCEEDED;
            }
        }

        // Fall back: retreat along -Z if no target
        tmp.set(0, 0, -1);
        ai.lastKnownTargetPosition.set(selfTransform.position).mulAdd(tmp, RETREAT_DISTANCE);
        ai.hasLastKnownPosition = true;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
