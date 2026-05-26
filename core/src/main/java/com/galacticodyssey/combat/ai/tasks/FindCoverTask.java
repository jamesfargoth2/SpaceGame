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
import com.galacticodyssey.combat.components.CoverComponent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Finds the nearest unoccupied CoverPoint from engine entities that have
 * a CoverComponent, and writes the result to ai.currentTarget's cover slot
 * via the CombatAIComponent. SUCCEEDED if a valid cover point was found.
 */
public class FindCoverTask extends LeafTask<Entity> {

    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<CoverComponent> COVER_M =
        ComponentMapper.getFor(CoverComponent.class);
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

        ImmutableArray<Entity> coverEntities = engine.getEntitiesFor(
            Family.all(CoverComponent.class, TransformComponent.class).get());

        float closestDist = Float.MAX_VALUE;
        com.galacticodyssey.combat.components.CoverPoint bestPoint = null;

        for (Entity entity : coverEntities) {
            CoverComponent cover = COVER_M.get(entity);
            if (cover == null || cover.currentCoverPoint == null) continue;
            if (cover.currentCoverPoint.occupied) continue;

            TransformComponent coverTransform = TRANSFORM_M.get(entity);
            if (coverTransform == null) continue;

            float dist = Vector3.dst(
                selfTransform.position.x, selfTransform.position.y, selfTransform.position.z,
                coverTransform.position.x, coverTransform.position.y, coverTransform.position.z);
            if (dist < closestDist) {
                closestDist = dist;
                bestPoint = cover.currentCoverPoint;
            }
        }

        if (bestPoint != null) {
            CoverComponent selfCover = COVER_M.get(self);
            if (selfCover != null) {
                selfCover.currentCoverPoint = bestPoint;
                bestPoint.occupied = true;
            }
            return Status.SUCCEEDED;
        }
        return Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        ((FindCoverTask) task).engine = this.engine;
        return task;
    }
}
