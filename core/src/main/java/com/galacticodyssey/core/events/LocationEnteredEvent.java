package com.galacticodyssey.core.events;

public final class LocationEnteredEvent {
    public final String locationId;

    public LocationEnteredEvent(String locationId) {
        this.locationId = locationId;
    }
}
