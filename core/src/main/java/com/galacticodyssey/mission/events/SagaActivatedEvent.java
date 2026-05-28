package com.galacticodyssey.mission.events;

public final class SagaActivatedEvent {
    public final String sagaId;

    public SagaActivatedEvent(String sagaId) {
        this.sagaId = sagaId;
    }
}
