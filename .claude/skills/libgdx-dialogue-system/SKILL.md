---
name: libgdx-dialogue-system
description: >
  Enforces correct dialogue tree architecture, branching conversation flow,
  conditional node evaluation, skill checks in dialogue, consequence application,
  and dialogue state persistence for a libGDX 3D space game. Use this skill
  whenever writing or modifying: dialogue data format (JSON/Ink), conversation
  tree nodes, conditional branches based on faction reputation or player skills,
  dialogue choice presentation UI, skill check pass/fail outcomes, NPC personality
  variations, companion loyalty dialogue, or dialogue state tracking. Also
  triggers when integrating quest-giving NPCs or connecting dialogue outcomes
  to reputation and mission systems.
---

# libGDX Dialogue System

## Architecture

Data-driven. Conversation trees in JSON, evaluated by dialogue engine:

```
Dialogue Data (JSON) -> DialogueRegistry -> DialogueEngine
    -> DialogueUI (Scene2D) -> ConsequenceProcessor
```

## Data Format

Nodes with choices, conditions, and consequences. Skill checks use [Skill Level] prefix and have pass/fail routing via next/failNext.

## Dialogue Engine

```java
public class DialogueEngine {
    public void startDialogue(String dialogueId, Entity player) {
        currentDialogue = dialogueRegistry.get(dialogueId);
        currentNode = currentDialogue.getNode("start");
        presentNode();
    }

    private void presentNode() {
        Array<DialogueChoice> visible = new Array<>();
        for (DialogueChoice choice : currentNode.choices) {
            if (evaluateConditions(choice.conditions)) visible.add(choice);
            else if (choice.showWhenLocked) { choice.locked = true; visible.add(choice); }
        }
        ui.showDialogue(currentNode.speaker, currentNode.text, currentNode.speakerPortrait, visible);
    }
}
```

## Conditions

REPUTATION (faction standing), SKILL_CHECK (point or real-time skill), QUEST_COMPLETE, HAS_ITEM, CREW_ABOARD.

## Consequences

REPUTATION_CHANGE (via event bus), GIVE_ITEM, START_QUEST, MODIFY_CREW_LOYALTY, UNLOCK_SHOP_TIER, PRICE_MODIFIER.

## Skill Checks

Deterministic threshold pass/fail, no RNG. Locked choices shown grayed out to motivate skill investment.

## Dialogue State

Track seen dialogues and branch choices for NPC memory. Persists in save data.

## Companion Dialogue

Loyalty-gated tiers: 0-25 (professional), 25-50 (personal), 50-75 (quest-triggering), 75-100 (loyalty mission unlock).

## Common Mistakes

| Mistake | Fix |
|---|---|
| Hardcoding dialogue in Java | All dialogue in JSON data files |
| Conditions only at dialogue start | Re-evaluate at each node |
| Skill checks with RNG | Deterministic threshold only |
| Consequences before confirmation | Apply after player commits |
| Missing state persistence | Seen dialogues must save/load |
| Direct reputation modification | Post ReputationChangeEvent |
