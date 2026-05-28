package com.galacticodyssey.mission.events;

public class SagaNodeEnteredEvent {
    public final String sagaId;
    public final String nodeId;
    public final String nodeType;

    public SagaNodeEnteredEvent(String sagaId, String nodeId, String nodeType) {
        this.sagaId = sagaId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
    }
}
