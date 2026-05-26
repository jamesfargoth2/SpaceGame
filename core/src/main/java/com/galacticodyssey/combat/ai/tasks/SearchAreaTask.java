package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Wanders near the last known target position while the search timer is running.
 * Ticks ai.searchTimer down; SUCCEEDED when timer expires (giving up the search).
 */
public class SearchAreaTask extends LeafTask<Entity> {

    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private static final float WANDER_RADIUS = 5f;
    private static final float FIXED_DELTA = 0.016f;

    @Override
    public Status execute() {
        Entity self = getObject();
        CombatAIComponent ai = AI_M.get(self);
        TransformComponent selfTransform = TRANSFORM_M.get(self);
        if (ai == null || selfTransform == null) return Status.FAILED;

        ai.searchTimer -= FIXED_DELTA;
        if (ai.searchTimer <= 0f) {
            ai.hasLastKnownPosition = false;
            ai.searchTimer = ai.searchDuration;
            return Status.SUCCEEDED; // gave up search
        }

        // Wander toward a random offset from lastKnownPosition
        if (ai.hasLastKnownPosition) {
            float offsetX = MathUtils.random(-WANDER_RADIUS, WANDER_RADIUS);
            float offsetZ = MathUtils.random(-WANDER_RADIUS, WANDER_RADIUS);
            Vector3 wanderTarget = new Vector3(
                ai.lastKnownTargetPosition.x + offsetX,
                ai.lastKnownTargetPosition.y,
                ai.lastKnownTargetPosition.z + offsetZ);
            Vector3 dir = wanderTarget.sub(selfTransform.position).nor();
            selfTransform.position.mulAdd(dir, 2.0f * FIXED_DELTA);
        }

        return Status.RUNNING;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
