package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.core.components.TransformComponent;

/** Succeeds if the target is within the AI's engageRange. */
public class InWeaponRangeCondition extends LeafTask<Entity> {

    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    @Override
    public Status execute() {
        Entity self = getObject();
        CombatAIComponent ai = AI_M.get(self);
        TransformComponent selfTransform = TRANSFORM_M.get(self);
        if (ai == null || selfTransform == null || ai.currentTarget == null) return Status.FAILED;

        TransformComponent targetTransform = TRANSFORM_M.get(ai.currentTarget);
        if (targetTransform == null) return Status.FAILED;

        float dist = Vector3.dst(
            selfTransform.position.x, selfTransform.position.y, selfTransform.position.z,
            targetTransform.position.x, targetTransform.position.y, targetTransform.position.z);
        return dist <= ai.engageRange ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
