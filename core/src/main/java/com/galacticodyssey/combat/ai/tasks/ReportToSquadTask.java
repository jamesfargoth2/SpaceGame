package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.SquadComponent;
import com.galacticodyssey.combat.events.ThreatDetectedEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Broadcasts a ThreatDetectedEvent so squad-mates can share target information.
 * Requires the EventBus to be injected before the tree runs.
 */
public class ReportToSquadTask extends LeafTask<Entity> {

    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<SquadComponent> SQUAD_M =
        ComponentMapper.getFor(SquadComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private EventBus eventBus;

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public Status execute() {
        if (eventBus == null) return Status.FAILED;
        Entity self = getObject();
        CombatAIComponent ai = AI_M.get(self);
        if (ai == null || ai.currentTarget == null) return Status.FAILED;

        SquadComponent squad = SQUAD_M.get(self);
        int squadId = squad != null ? squad.squadId : -1;

        TransformComponent targetTransform = TRANSFORM_M.get(ai.currentTarget);
        if (targetTransform == null) return Status.FAILED;

        eventBus.publish(new ThreatDetectedEvent(self, ai.currentTarget, targetTransform.position, squadId));
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        ((ReportToSquadTask) task).eventBus = this.eventBus;
        return task;
    }
}
