package com.galacticodyssey.npc;

import com.galacticodyssey.npc.crew.CrewRole;

public enum NPCRole {
    // Crew roles (map 1:1 to CrewRole)
    PILOT,
    ENGINEER,
    GUNNER,
    MEDIC,
    NAVIGATOR,
    SCIENCE_OFFICER,
    MARINE,
    // Station roles
    MERCHANT,
    BARTENDER,
    INFORMATION_BROKER,
    MECHANIC,
    // Adversarial roles
    PIRATE_CAPTAIN,
    BOUNTY_HUNTER,
    MERCENARY,
    SMUGGLER,
    // Civilian roles
    COLONIST,
    SCIENTIST;

    public CrewRole toCrewRole() {
        switch (this) {
            case PILOT:           return CrewRole.PILOT;
            case ENGINEER:        return CrewRole.ENGINEER;
            case GUNNER:          return CrewRole.GUNNER;
            case MEDIC:           return CrewRole.MEDIC;
            case NAVIGATOR:       return CrewRole.NAVIGATOR;
            case SCIENCE_OFFICER: return CrewRole.SCIENTIST;
            case MARINE:          return CrewRole.MARINE;
            default:              return null;
        }
    }

    public boolean isCrewRole() {
        return toCrewRole() != null;
    }
}
