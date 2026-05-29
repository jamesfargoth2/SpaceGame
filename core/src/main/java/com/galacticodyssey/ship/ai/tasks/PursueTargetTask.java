package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/**
 * Lead-pursuit: aim at the lead point (already in blackboard.desiredAimDir) and throttle to
 * close to the preferred engage range.
 */
public class PursueTargetTask extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;

        float preferred = ai.archetype != null ? ai.archetype.preferredEngageRange : 350f;
        float discipline = ai.archetype != null ? ai.archetype.throttleDiscipline : 0.6f;
        float range = ai.blackboard.rangeToTarget;

        float t = MathUtils.clamp((range - preferred) / preferred, -1f, 1f);
        ai.blackboard.desiredThrottle = MathUtils.clamp(
            MathUtils.lerp(1f, t, discipline), -0.3f, 1f);
        ai.blackboard.desiredRoll = 0f;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
