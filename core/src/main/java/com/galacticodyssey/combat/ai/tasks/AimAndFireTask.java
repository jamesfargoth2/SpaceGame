package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Computes direction from self to target, sets aimDirection on CombatInputComponent,
 * and triggers fireRequested/fireHeld. SUCCEEDED when a valid target is in range.
 */
public class AimAndFireTask extends LeafTask<Entity> {

    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<CombatInputComponent> INPUT_M =
        ComponentMapper.getFor(CombatInputComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private final Vector3 tmp = new Vector3();

    @Override
    public Status execute() {
        Entity self = getObject();
        CombatAIComponent ai = AI_M.get(self);
        CombatInputComponent input = INPUT_M.get(self);
        TransformComponent selfTransform = TRANSFORM_M.get(self);
        if (ai == null || input == null || selfTransform == null || ai.currentTarget == null) {
            return Status.FAILED;
        }

        TransformComponent targetTransform = TRANSFORM_M.get(ai.currentTarget);
        if (targetTransform == null) return Status.FAILED;

        tmp.set(targetTransform.position).sub(selfTransform.position).nor();
        input.aimDirection.set(tmp);
        input.fireRequested = true;
        input.fireHeld = true;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
