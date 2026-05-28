package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;

/**
 * Scans engine entities for PlayerTagComponent entities within aggroRange.
 * Sets the closest player as the AI's currentTarget. SUCCEEDED if a target was found.
 */
public class ScanForThreatsTask extends LeafTask<Entity> {

    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private Engine engine;

    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    @Override
    public Status execute() {
        if (engine == null) return Status.FAILED;
        Entity self = getObject();
        CombatAIComponent ai = AI_M.get(self);
        TransformComponent selfTransform = TRANSFORM_M.get(self);
        if (ai == null || selfTransform == null) return Status.FAILED;

        ImmutableArray<Entity> players = engine.getEntitiesFor(
            Family.all(PlayerTagComponent.class, TransformComponent.class).get());

        Entity closest = null;
        float closestDist = Float.MAX_VALUE;

        for (Entity player : players) {
            TransformComponent playerTransform = TRANSFORM_M.get(player);
            if (playerTransform == null) continue;
            float dist = Vector3.dst(
                selfTransform.position.x, selfTransform.position.y, selfTransform.position.z,
                playerTransform.position.x, playerTransform.position.y, playerTransform.position.z);
            if (dist <= ai.aggroRange && dist < closestDist) {
                closestDist = dist;
                closest = player;
            }
        }

        if (closest != null) {
            ai.currentTarget = closest;
            TransformComponent pt = TRANSFORM_M.get(closest);
            if (pt != null) {
                ai.lastKnownTargetPosition.set(pt.position);
                ai.hasLastKnownPosition = true;
            }
            return Status.SUCCEEDED;
        }
        return Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        ((ScanForThreatsTask) task).engine = this.engine;
        return task;
    }
}
