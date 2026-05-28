package com.galacticodyssey.core.events;

public class LocationEnteredEvent {
    public final String locationId;

    public LocationEnteredEvent(String locationId) {
        this.locationId = locationId;
    }
}
