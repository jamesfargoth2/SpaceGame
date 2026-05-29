package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/** Succeeds when the target is roughly ahead and within engage range. */
public class TargetInWeaponArcCondition extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    public float arcDeg = 25f;

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;
        float range = ai.blackboard.rangeToTarget;
        float maxRange = ai.archetype != null ? ai.archetype.preferredEngageRange * 1.5f : 600f;
        boolean inArc = ai.blackboard.angleOffBore <= arcDeg && range <= maxRange;
        return inArc ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        ((TargetInWeaponArcCondition) task).arcDeg = arcDeg;
        return task;
    }
}
