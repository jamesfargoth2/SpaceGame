package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.DialogueChoiceMadeEvent;
import com.galacticodyssey.core.events.NpcDialogueEvent;
import com.galacticodyssey.npc.data.DialogChoice;
import com.galacticodyssey.npc.data.DialogDataRegistry;
import com.galacticodyssey.npc.data.DialogNode;
import com.galacticodyssey.npc.data.DialogTree;
import com.galacticodyssey.npc.events.DialogClosedEvent;
import com.galacticodyssey.npc.events.DialogNodeChangedEvent;
import com.galacticodyssey.npc.events.DialogOpenedEvent;

public class DialogSystem extends EntitySystem {

    private final EventBus eventBus;
    private final DialogDataRegistry registry;

    private boolean active;
    private String activeNpcId;
    private String activeNpcName;
    private DialogTree activeTree;
    private DialogNode currentNode;

    public DialogSystem(EventBus eventBus, DialogDataRegistry registry) {
        super(0);
        this.eventBus = eventBus;
        this.registry = registry;

        eventBus.subscribe(NpcDialogueEvent.class, this::onDialogueRequested);
    }

    private void onDialogueRequested(NpcDialogueEvent event) {
        if (active) return;

        DialogTree tree = registry.getTree(event.topic);
        if (tree == null) return;

        activeTree = tree;
        activeNpcId = event.npcId;
        activeNpcName = event.npcId;
        currentNode = tree.getStartNode();
        active = true;

        eventBus.publish(new DialogOpenedEvent(activeNpcId, activeNpcName, currentNode));
    }

    public void openDialog(String npcId, String npcName, String dialogTreeId) {
        if (active) return;

        DialogTree tree = registry.getTree(dialogTreeId);
        if (tree == null) return;

        activeTree = tree;
        activeNpcId = npcId;
        activeNpcName = npcName;
        currentNode = tree.getStartNode();
        active = true;

        eventBus.publish(new DialogOpenedEvent(activeNpcId, activeNpcName, currentNode));
    }

    public void selectChoice(int choiceIndex) {
        if (!active || currentNode == null) return;

        if (currentNode.isEndNode()) {
            closeDialog();
            return;
        }

        if (choiceIndex < 0 || choiceIndex >= currentNode.choices.size()) return;

        DialogChoice choice = currentNode.choices.get(choiceIndex);
        eventBus.publish(new DialogueChoiceMadeEvent(activeNpcId, choice.key));

        if (choice.nextNodeId == null) {
            closeDialog();
            return;
        }

        DialogNode nextNode = activeTree.getNode(choice.nextNodeId);
        if (nextNode == null) {
            closeDialog();
            return;
        }

        currentNode = nextNode;
        eventBus.publish(new DialogNodeChangedEvent(activeNpcId, activeNpcName, currentNode));
    }

    public void advanceOrClose() {
        if (!active || currentNode == null) return;

        if (currentNode.isEndNode()) {
            closeDialog();
        }
    }

    public void closeDialog() {
        if (!active) return;

        active = false;
        String npcId = activeNpcId;
        activeNpcId = null;
        activeNpcName = null;
        activeTree = null;
        currentNode = null;

        eventBus.publish(new DialogClosedEvent(npcId));
    }

    public boolean isActive() {
        return active;
    }

    public DialogNode getCurrentNode() {
        return currentNode;
    }
}
