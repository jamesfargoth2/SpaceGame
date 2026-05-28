package com.galacticodyssey.mission.saga;

import com.galacticodyssey.mission.shared.Objective;
import java.util.ArrayList;
import java.util.List;

public class SagaNodeData {
    public String id;
    public SagaNodeType type;
    public String npcId;                    // DIALOGUE_CHOICE nodes
    public String outcome;                  // TERMINUS nodes: "COMPLETE" or "FAILED"
    public List<Objective> objectives = new ArrayList<>();
    public List<ConsequenceEvent> consequences = new ArrayList<>();

    public static class ConsequenceEvent {
        public String type;                 // "REPUTATION_CHANGE"
        public String faction;
        public float delta;
        public String worldEventType;       // optional
    }
}
