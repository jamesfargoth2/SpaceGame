package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CoverComponent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Moves the entity toward its selectedCover position.
 * SUCCEEDED once within arrival threshold; RUNNING while en route.
 */
public class MoveToCoverTask extends LeafTask<Entity> {

    private static final ComponentMapper<CoverComponent> COVER_M =
        ComponentMapper.getFor(CoverComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private static final float ARRIVAL_THRESHOLD = 0.5f;

    @Override
    public Status execute() {
        Entity self = getObject();
        CoverComponent cover = COVER_M.get(self);
        TransformComponent selfTransform = TRANSFORM_M.get(self);
        if (cover == null || cover.currentCoverPoint == null || selfTransform == null) {
            return Status.FAILED;
        }

        Vector3 destination = cover.currentCoverPoint.position;
        float dist = Vector3.dst(
            selfTransform.position.x, selfTransform.position.y, selfTransform.position.z,
            destination.x, destination.y, destination.z);

        if (dist <= ARRIVAL_THRESHOLD) {
            cover.inCover = true;
            return Status.SUCCEEDED;
        }

        // Movement is handled by the movement system reading peek/cover state;
        // the cover component signals intent to move toward cover.
        cover.peekDirection.set(destination).sub(selfTransform.position).nor();
        return Status.RUNNING;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
