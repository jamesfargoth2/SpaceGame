package com.galacticodyssey.npc.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatTypeTest {

    @Test
    void getValueReadsCorrectField() {
        NpcStatsComponent stats = new NpcStatsComponent();
        stats.accuracy = 78f;
        stats.repair = 85f;
        stats.medical = 60f;
        stats.piloting = 55f;
        stats.science = 68f;
        stats.combat = 72f;
        stats.persuasion = 45f;
        stats.stealth = 30f;

        assertEquals(78f, StatType.ACCURACY.getValue(stats));
        assertEquals(85f, StatType.REPAIR.getValue(stats));
        assertEquals(60f, StatType.MEDICAL.getValue(stats));
        assertEquals(55f, StatType.PILOTING.getValue(stats));
        assertEquals(68f, StatType.SCIENCE.getValue(stats));
        assertEquals(72f, StatType.COMBAT.getValue(stats));
        assertEquals(45f, StatType.PERSUASION.getValue(stats));
        assertEquals(30f, StatType.STEALTH.getValue(stats));
    }

    @Test
    void allValuesHaveThreeLetterAbbreviation() {
        for (StatType type : StatType.values()) {
            assertEquals(3, type.abbreviation.length(),
                type.name() + " abbreviation should be 3 chars");
        }
    }

    @Test
    void getTopNReturnsHighestStats() {
        NpcStatsComponent stats = new NpcStatsComponent();
        stats.accuracy = 10f;
        stats.repair = 85f;
        stats.medical = 20f;
        stats.piloting = 30f;
        stats.science = 68f;
        stats.combat = 5f;
        stats.persuasion = 40f;
        stats.stealth = 15f;

        var top2 = StatType.getTopN(stats, 2);
        assertEquals(2, top2.size());
        assertEquals(StatType.REPAIR, top2.get(0));
        assertEquals(StatType.SCIENCE, top2.get(1));
    }
}
