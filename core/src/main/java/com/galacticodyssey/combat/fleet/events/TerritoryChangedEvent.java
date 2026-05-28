package com.galacticodyssey.combat.fleet.events;

public final class TerritoryChangedEvent {
    public final String systemId;
    public final String oldFactionId;
    public final String newFactionId;

    public TerritoryChangedEvent(String systemId, String oldFactionId, String newFactionId) {
        this.systemId = systemId;
        this.oldFactionId = oldFactionId;
        this.newFactionId = newFactionId;
    }
}
