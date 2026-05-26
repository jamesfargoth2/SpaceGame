package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Low-health retreat behavior. Clears the current target and sets a retreat
 * destination directly away from the last known threat position.
 */
public class HealOrFleeTask extends LeafTask<Entity> {

    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private static final float FLEE_DISTANCE = 20f;
    private static final float MOVE_SPEED = 5f;

    private final Vector3 tmp = new Vector3();

    @Override
    public Status execute() {
        Entity self = getObject();
        CombatAIComponent ai = AI_M.get(self);
        TransformComponent selfTransform = TRANSFORM_M.get(self);
        if (ai == null || selfTransform == null) return Status.FAILED;

        // Move away from last known threat position
        if (ai.hasLastKnownPosition) {
            tmp.set(selfTransform.position).sub(ai.lastKnownTargetPosition).nor();
            selfTransform.position.mulAdd(tmp, MOVE_SPEED * 0.016f);
        }

        // Drop aggression temporarily
        ai.currentTarget = null;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
