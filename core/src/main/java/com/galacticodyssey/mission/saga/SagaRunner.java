package com.galacticodyssey.mission.saga;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.DialogueChoiceMadeEvent;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.mission.events.ObjectiveCompletedEvent;
import com.galacticodyssey.mission.events.SagaNodeEnteredEvent;
import com.galacticodyssey.mission.shared.QuestJournal;

import java.util.List;

public class SagaRunner extends EntitySystem {

    private final EventBus eventBus;
    private final QuestJournal journal;
    private final SagaRegistry registry;

    public SagaRunner(EventBus eventBus, QuestJournal journal, SagaRegistry registry) {
        this.eventBus = eventBus;
        this.journal = journal;
        this.registry = registry;

        eventBus.subscribe(ObjectiveCompletedEvent.class, this::onObjectiveCompleted);
        eventBus.subscribe(DialogueChoiceMadeEvent.class, this::onDialogueChoice);
    }

    private void onObjectiveCompleted(ObjectiveCompletedEvent e) {
        for (SagaInstance instance : journal.getAllActiveSagas()) {
            if (!instance.sagaDataId.equals(e.missionId)) continue;
            if (instance.state != SagaState.ACTIVE) continue;
            SagaData saga = registry.get(instance.sagaDataId);
            if (saga == null) continue;
            SagaNodeData node = saga.getNode(instance.currentNodeId);
            if (node == null || node.type != SagaNodeType.OBJECTIVE) continue;
            if (instance.allRequiredObjectivesComplete()) {
                advance(instance, saga, null);
            }
        }
    }

    private void onDialogueChoice(DialogueChoiceMadeEvent e) {
        for (SagaInstance instance : journal.getAllActiveSagas()) {
            if (instance.state != SagaState.ACTIVE) continue;
            SagaData saga = registry.get(instance.sagaDataId);
            if (saga == null) continue;
            SagaNodeData node = saga.getNode(instance.currentNodeId);
            if (node == null || node.type != SagaNodeType.DIALOGUE_CHOICE) continue;
            if (!e.npcId.equals(node.npcId)) continue;
            instance.choicesMade.put(instance.currentNodeId, e.choiceKey);
            advance(instance, saga, e.choiceKey);
        }
    }

    private void advance(SagaInstance instance, SagaData saga, String choiceKey) {
        List<SagaEdgeData> edges = saga.edgesFrom(instance.currentNodeId);
        for (SagaEdgeData edge : edges) {
            if (edge.requiresChoice == null || edge.requiresChoice.equals(choiceKey)) {
                SagaNodeData next = saga.getNode(edge.to);
                if (next != null) {
                    enterNode(instance, next, saga);
                    return;
                }
            }
        }
    }

    public void enterNode(SagaInstance instance, SagaNodeData node, SagaData saga) {
        instance.currentNodeId = node.id;
        instance.activeObjectives.clear();
        eventBus.publish(new SagaNodeEnteredEvent(instance.sagaDataId, node.id, node.type.name()));

        switch (node.type) {
            case OBJECTIVE -> instance.activeObjectives.addAll(node.objectives);
            case CONSEQUENCE -> {
                for (SagaNodeData.ConsequenceEvent ce : node.consequences) {
                    if ("REPUTATION_CHANGE".equals(ce.type)) {
                        eventBus.publish(new ReputationChangeEvent(ce.faction, ce.delta, instance.sagaDataId));
                    }
                }
                advance(instance, saga, null);
            }
            case TERMINUS -> instance.state = "COMPLETE".equals(node.outcome) ? SagaState.COMPLETE : SagaState.FAILED;
        }
    }
}
