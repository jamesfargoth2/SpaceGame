package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.components.CombatInputComponent;

/**
 * Requests a melee attack. Uses OVERHEAD direction as default; pair with
 * SelectMeleeDirectionTask to pick a counter-direction first.
 */
public class MeleeAttackTask extends LeafTask<Entity> {

    private static final ComponentMapper<CombatInputComponent> INPUT_M =
        ComponentMapper.getFor(CombatInputComponent.class);

    @Override
    public Status execute() {
        CombatInputComponent input = INPUT_M.get(getObject());
        if (input == null) return Status.FAILED;
        input.meleeAttackRequested = true;
        // Default to OVERHEAD; SelectMeleeDirectionTask may have pre-set this
        if (input.meleeAttackDirection == null) {
            input.meleeAttackDirection = AttackDirection.OVERHEAD;
        }
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        return task;
    }
}
