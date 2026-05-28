package com.galacticodyssey.npc.data;

import java.util.HashMap;
import java.util.Map;

public class DialogTree {
    public String id;
    public String startNodeId;
    public Map<String, DialogNode> nodes = new HashMap<>();

    public DialogNode getStartNode() {
        return nodes.get(startNodeId);
    }

    public DialogNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }
}
