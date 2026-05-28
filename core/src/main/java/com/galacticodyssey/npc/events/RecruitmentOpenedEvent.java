package com.galacticodyssey.npc.events;

public final class RecruitmentOpenedEvent {
    public final String stationId;

    public RecruitmentOpenedEvent(String stationId) {
        this.stationId = stationId;
    }
}
