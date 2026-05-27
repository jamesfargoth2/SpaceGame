package com.galacticodyssey.npc.crew;

import com.galacticodyssey.npc.components.NpcStatsComponent;
import com.galacticodyssey.ship.RoomType;

public enum CrewRole {
    PILOT,
    GUNNER,
    ENGINEER,
    MEDIC,
    MARINE,
    SCIENTIST,
    NAVIGATOR;

    public float getRelevantStat(NpcStatsComponent stats) {
        switch (this) {
            case PILOT:     return stats.piloting;
            case GUNNER:    return stats.accuracy;
            case ENGINEER:  return stats.repair;
            case MEDIC:     return stats.medical;
            case MARINE:    return stats.combat;
            case SCIENTIST: return stats.science;
            case NAVIGATOR: return stats.piloting;
            default:        return 0f;
        }
    }

    public static CrewRole forRoomType(RoomType roomType) {
        switch (roomType) {
            case COCKPIT:     return PILOT;
            case ENGINE_ROOM: return ENGINEER;
            case MEDBAY:      return MEDIC;
            case ARMORY:      return MARINE;
            default:          return null;
        }
    }
}
