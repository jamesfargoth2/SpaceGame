package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/** Succeeds when self health is at/below the archetype's evade threshold. */
public class LowHealthCondition extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null) return Status.FAILED;
        float threshold = ai.archetype != null ? ai.archetype.evadeHealthThreshold : 0.35f;
        return ai.blackboard.selfHealthPercent <= threshold ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
