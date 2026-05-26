package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.combat.components.CoverComponent;

/** Succeeds if the entity's CoverComponent.inCover flag is true. */
public class InCoverCondition extends LeafTask<Entity> {

    private static final ComponentMapper<CoverComponent> COVER_M =
        ComponentMapper.getFor(CoverComponent.class);

    @Override
    public Status execute() {
        CoverComponent cover = COVER_M.get(getObject());
        if (cover == null) return Status.FAILED;
        return cover.inCover ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
