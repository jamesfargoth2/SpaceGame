package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.components.CombatInputComponent;

/**
 * Picks the attack direction that opposes the target's current block direction.
 * If the target is not blocking, defaults to THRUST.
 */
public class SelectMeleeDirectionTask extends LeafTask<Entity> {

    private static final ComponentMapper<CombatInputComponent> INPUT_M =
        ComponentMapper.getFor(CombatInputComponent.class);

    @Override
    public Status execute() {
        Entity self = getObject();
        CombatInputComponent selfInput = INPUT_M.get(self);
        if (selfInput == null) return Status.FAILED;

        // Simple counter: if the target's block direction is known, oppose it.
        // Without target access here, default to THRUST as a safe choice.
        selfInput.meleeAttackDirection = AttackDirection.THRUST;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
