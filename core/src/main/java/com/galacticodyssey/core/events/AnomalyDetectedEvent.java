package com.galacticodyssey.core.events;

public final class AnomalyDetectedEvent {
    public final String anomalyId;
    public final String locationId;

    public AnomalyDetectedEvent(String anomalyId, String locationId) {
        this.anomalyId = anomalyId;
        this.locationId = locationId;
    }
}
