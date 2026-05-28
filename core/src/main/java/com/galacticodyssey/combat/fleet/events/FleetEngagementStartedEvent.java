package com.galacticodyssey.combat.fleet.events;

public final class FleetEngagementStartedEvent {
    public final String attackerFleetId;
    public final String defenderFleetId;
    public final double x, y, z;

    public FleetEngagementStartedEvent(String attackerFleetId, String defenderFleetId,
                                       double x, double y, double z) {
        this.attackerFleetId = attackerFleetId;
        this.defenderFleetId = defenderFleetId;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
