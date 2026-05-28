package com.galacticodyssey.mission.saga;

import java.util.ArrayList;
import java.util.List;

public class SagaData {
    public String id;
    public String title;
    public SagaCategory category;
    public List<SagaNodeData> nodes = new ArrayList<>();
    public List<SagaEdgeData> edges = new ArrayList<>();
    public List<TriggerData> triggers = new ArrayList<>();

    public SagaNodeData getNode(String nodeId) {
        return nodes.stream().filter(n -> nodeId.equals(n.id)).findFirst().orElse(null);
    }

    public List<SagaEdgeData> edgesFrom(String nodeId) {
        return edges.stream().filter(e -> nodeId.equals(e.from)).toList();
    }
}
