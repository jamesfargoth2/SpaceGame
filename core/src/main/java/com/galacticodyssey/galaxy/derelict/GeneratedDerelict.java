package com.galacticodyssey.galaxy.derelict;

import java.util.List;
import java.util.Map;

/** Output of derelict wreck generation. */
public final class GeneratedDerelict {
    public final DamageProfile damageProfile;
    public final Map<String, SectionState> sectionStates;
    public final Map<String, List<SalvageItem>> salvageContents;
    public final DerelictNarrative narrative;
    public final List<String> enemySpawnSections;

    public GeneratedDerelict(DamageProfile damageProfile,
                             Map<String, SectionState> sectionStates,
                             Map<String, List<SalvageItem>> salvageContents,
                             DerelictNarrative narrative,
                             List<String> enemySpawnSections) {
        this.damageProfile = damageProfile;
        this.sectionStates = sectionStates;
        this.salvageContents = salvageContents;
        this.narrative = narrative;
        this.enemySpawnSections = enemySpawnSections;
    }
}
