package com.galacticodyssey.core.events;

import com.galacticodyssey.galaxy.faction.ReputationTier;

public final class ReputationTierChangedEvent {
    public final String factionId;
    public final ReputationTier oldTier;
    public final ReputationTier newTier;
    public final float newStanding;

    public ReputationTierChangedEvent(String factionId, ReputationTier oldTier,
                                      ReputationTier newTier, float newStanding) {
        this.factionId = factionId;
        this.oldTier = oldTier;
        this.newTier = newTier;
        this.newStanding = newStanding;
    }
}
