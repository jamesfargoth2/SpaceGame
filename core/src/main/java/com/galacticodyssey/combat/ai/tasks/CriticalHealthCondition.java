package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.combat.components.HealthComponent;

/** Succeeds if entity HP is below 20% of max (critical health threshold). */
public class CriticalHealthCondition extends LeafTask<Entity> {

    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);

    private static final float CRITICAL_THRESHOLD = 0.2f;

    @Override
    public Status execute() {
        HealthComponent health = HEALTH_M.get(getObject());
        if (health == null || health.maxHP <= 0f) return Status.FAILED;
        float percent = health.currentHP / health.maxHP;
        return percent < CRITICAL_THRESHOLD ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
