package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/**
 * Hold the nose on the lead point and request gun fire when aligned within the firing cone and
 * inside weapon range. Requests missiles when the archetype uses them and the target is ahead.
 */
public class AttackRunTask extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    public float weaponRange = 500f;
    public float firingConeDeg = 6f;
    public float missileRange = 2000f;
    public float missileConeDeg = 20f;

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;

        ai.blackboard.desiredThrottle = 0.85f;

        boolean gunsAligned = ai.blackboard.angleOffBore <= firingConeDeg
            && ai.blackboard.rangeToTarget <= weaponRange;
        ai.blackboard.fireGuns = gunsAligned;

        boolean useMissiles = ai.archetype != null && ai.archetype.usesMissiles
            && ai.blackboard.angleOffBore <= missileConeDeg
            && ai.blackboard.rangeToTarget <= missileRange;
        ai.blackboard.fireMissiles = useMissiles;

        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        AttackRunTask t = (AttackRunTask) task;
        t.weaponRange = weaponRange;
        t.firingConeDeg = firingConeDeg;
        t.missileRange = missileRange;
        t.missileConeDeg = missileConeDeg;
        return task;
    }
}
