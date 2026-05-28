package com.galacticodyssey.mission.saga;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.DialogueChoiceMadeEvent;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.mission.events.*;
import com.galacticodyssey.mission.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SagaRunnerTest {

    private EventBus eventBus;
    private QuestJournal journal;
    private SagaRegistry registry;
    private SagaRunner runner;
    private final List<SagaNodeEnteredEvent> nodeEntered = new ArrayList<>();
    private final List<ReputationChangeEvent> repEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        journal = new QuestJournal();
        registry = new SagaRegistry();
        runner = new SagaRunner(eventBus, journal, registry);
        eventBus.subscribe(SagaNodeEnteredEvent.class, nodeEntered::add);
        eventBus.subscribe(ReputationChangeEvent.class, repEvents::add);
    }

    @Test
    void objectiveNodeComplete_advancesToNextNode() {
        SagaData saga = buildTwoNodeSaga("scan_stage", "end_stage");
        registry.register(saga);
        SagaInstance instance = activateSaga(saga, "scan_stage");
        journal.setMainStory(instance);

        // Complete the objective
        Objective obj = instance.activeObjectives.get(0);
        obj.currentCount = obj.requiredCount;
        obj.completed = true;
        eventBus.publish(new ObjectiveCompletedEvent("test_saga", obj.id));

        assertEquals("end_stage", instance.currentNodeId);
        assertTrue(nodeEntered.stream().anyMatch(e -> "end_stage".equals(e.nodeId)));
    }

    @Test
    void terminusNode_setsStateComplete() {
        SagaData saga = buildTerminusSaga("end_node", "COMPLETE");
        registry.register(saga);
        SagaInstance instance = activateSaga(saga, "end_node");
        journal.setMainStory(instance);

        runner.enterNode(instance, saga.getNode("end_node"), saga);

        assertEquals(SagaState.COMPLETE, instance.state);
    }

    @Test
    void dialogueChoice_takesMatchingEdge() {
        SagaData saga = buildChoiceSaga();
        registry.register(saga);
        SagaInstance instance = activateSaga(saga, "choice_node");
        journal.setMainStory(instance);

        eventBus.publish(new DialogueChoiceMadeEvent("npc_varek", "sided_with_guild"));

        assertEquals("guild_outcome", instance.currentNodeId);
    }

    @Test
    void consequenceNode_publishesReputationAndAutoAdvances() {
        SagaData saga = buildConsequenceSaga();
        registry.register(saga);
        SagaInstance instance = activateSaga(saga, "consequence_node");
        journal.setMainStory(instance);

        runner.enterNode(instance, saga.getNode("consequence_node"), saga);

        assertFalse(repEvents.isEmpty());
        assertEquals("explorers_guild", repEvents.get(0).factionId);
        assertEquals("end_node", instance.currentNodeId);
    }

    // --- helpers ---

    private SagaData buildTwoNodeSaga(String firstId, String secondId) {
        SagaData saga = new SagaData();
        saga.id = "test_saga"; saga.title = "Test"; saga.category = SagaCategory.MAIN_STORY;

        SagaNodeData n1 = new SagaNodeData();
        n1.id = firstId; n1.type = SagaNodeType.OBJECTIVE;
        Objective obj = new Objective(); obj.id = "obj1"; obj.type = com.galacticodyssey.mission.shared.ObjectiveType.SCAN_OBJECT;
        obj.targetId = "anomaly1"; obj.requiredCount = 1;
        n1.objectives.add(obj);
        saga.nodes.add(n1);

        SagaNodeData n2 = new SagaNodeData();
        n2.id = secondId; n2.type = SagaNodeType.TERMINUS; n2.outcome = "COMPLETE";
        saga.nodes.add(n2);

        SagaEdgeData edge = new SagaEdgeData(); edge.from = firstId; edge.to = secondId;
        saga.edges.add(edge);
        return saga;
    }

    private SagaData buildTerminusSaga(String nodeId, String outcome) {
        SagaData saga = new SagaData();
        saga.id = "terminus_saga"; saga.category = SagaCategory.MAIN_STORY;
        SagaNodeData n = new SagaNodeData(); n.id = nodeId; n.type = SagaNodeType.TERMINUS; n.outcome = outcome;
        saga.nodes.add(n);
        return saga;
    }

    private SagaData buildChoiceSaga() {
        SagaData saga = new SagaData();
        saga.id = "test_saga"; saga.category = SagaCategory.MAIN_STORY;

        SagaNodeData choice = new SagaNodeData();
        choice.id = "choice_node"; choice.type = SagaNodeType.DIALOGUE_CHOICE; choice.npcId = "npc_varek";
        saga.nodes.add(choice);

        SagaNodeData outcome = new SagaNodeData();
        outcome.id = "guild_outcome"; outcome.type = SagaNodeType.TERMINUS; outcome.outcome = "COMPLETE";
        saga.nodes.add(outcome);

        SagaEdgeData edge = new SagaEdgeData();
        edge.from = "choice_node"; edge.to = "guild_outcome"; edge.requiresChoice = "sided_with_guild";
        saga.edges.add(edge);
        return saga;
    }

    private SagaData buildConsequenceSaga() {
        SagaData saga = new SagaData();
        saga.id = "test_saga"; saga.category = SagaCategory.MAIN_STORY;

        SagaNodeData con = new SagaNodeData(); con.id = "consequence_node"; con.type = SagaNodeType.CONSEQUENCE;
        SagaNodeData.ConsequenceEvent ce = new SagaNodeData.ConsequenceEvent();
        ce.type = "REPUTATION_CHANGE"; ce.faction = "explorers_guild"; ce.delta = 15f;
        con.consequences.add(ce);
        saga.nodes.add(con);

        SagaNodeData end = new SagaNodeData(); end.id = "end_node"; end.type = SagaNodeType.TERMINUS; end.outcome = "COMPLETE";
        saga.nodes.add(end);

        SagaEdgeData edge = new SagaEdgeData(); edge.from = "consequence_node"; edge.to = "end_node";
        saga.edges.add(edge);
        return saga;
    }

    private SagaInstance activateSaga(SagaData saga, String startNodeId) {
        SagaInstance instance = new SagaInstance();
        instance.sagaDataId = saga.id;
        instance.currentNodeId = startNodeId;
        instance.state = SagaState.ACTIVE;
        SagaNodeData startNode = saga.getNode(startNodeId);
        if (startNode != null && startNode.type == SagaNodeType.OBJECTIVE) {
            instance.activeObjectives.addAll(startNode.objectives);
        }
        return instance;
    }
}
