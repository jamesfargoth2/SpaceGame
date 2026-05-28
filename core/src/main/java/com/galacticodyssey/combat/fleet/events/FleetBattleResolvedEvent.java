package com.galacticodyssey.combat.fleet.events;

import java.util.List;

public final class FleetBattleResolvedEvent {
    public final String winnerFleetId;
    public final String loserFleetId;
    public final int winnerCasualties;
    public final int loserCasualties;
    public final List<String> capturedShipIds;

    public FleetBattleResolvedEvent(String winnerFleetId, String loserFleetId,
                                    int winnerCasualties, int loserCasualties,
                                    List<String> capturedShipIds) {
        this.winnerFleetId = winnerFleetId;
        this.loserFleetId = loserFleetId;
        this.winnerCasualties = winnerCasualties;
        this.loserCasualties = loserCasualties;
        this.capturedShipIds = capturedShipIds;
    }
}
