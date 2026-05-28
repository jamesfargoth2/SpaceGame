package com.galacticodyssey.core.events;

public class ResourceCollectedEvent {
    public final String resourceType;
    public final int amount;

    public ResourceCollectedEvent(String resourceType, int amount) {
        this.resourceType = resourceType;
        this.amount = amount;
    }
}
