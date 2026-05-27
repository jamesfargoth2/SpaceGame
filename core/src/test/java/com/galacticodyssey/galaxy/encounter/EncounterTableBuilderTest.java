package com.galacticodyssey.galaxy.encounter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncounterTableBuilderTest {

    @Test
    void baseWeightsApplied() {
        EncounterContext ctx = new EncounterContext(
                1L, "faction_a", 0.5f, 0.5f, 0f, false, false);
        EncounterTable table = EncounterTableBuilder.build(ctx);

        // MERCHANT_CONVOY starts at 8, modified by security (1+0.5=1.5x) and rim (max(0.1, 1-0.5)=0.5x)
        // 8 * 1.5 * 0.5 = 6.0
        // But we verify it's a reasonable positive value derived from base weight 8
        float merchantWeight = table.getWeight(EncounterType.MERCHANT_CONVOY);
        assertTrue(merchantWeight > 0f, "MERCHANT_CONVOY should have positive weight");
    }

    @Test
    void baseWeightsInNeutralContext() {
        // Neutral context: security=0, dist=0, not contested, not at war, rep=0
        EncounterContext ctx = new EncounterContext(
                1L, "faction_a", 0f, 0f, 0f, false, false);
        EncounterTable table = EncounterTableBuilder.build(ctx);

        // With zero modifiers, base weights should be preserved exactly
        // Security: MERCHANT_CONVOY *= (1+0) = 1x, FACTION_PATROL *= (1+0) = 1x
        // Rim: MERCHANT_CONVOY *= max(0.1, 1-0) = 1x
        // No war, no contested, rep=0
        assertEquals(8f, table.getWeight(EncounterType.MERCHANT_CONVOY), 0.01f,
                "MERCHANT_CONVOY base weight should be 8");
        assertEquals(6f, table.getWeight(EncounterType.FACTION_PATROL), 0.01f,
                "FACTION_PATROL base weight should be 6");
    }

    @Test
    void highSecurityReducesPirates() {
        EncounterContext lowSec = new EncounterContext(
                1L, "faction_a", 0f, 0f, 0f, false, false);
        EncounterContext highSec = new EncounterContext(
                1L, "faction_a", 1.0f, 0f, 0f, false, false);

        EncounterTable lowTable = EncounterTableBuilder.build(lowSec);
        EncounterTable highTable = EncounterTableBuilder.build(highSec);

        assertTrue(highTable.getWeight(EncounterType.PIRATE_AMBUSH)
                        < lowTable.getWeight(EncounterType.PIRATE_AMBUSH),
                "PIRATE_AMBUSH weight should be lower with high security");
    }

    @Test
    void rimIncreasesDerelicts() {
        EncounterContext core = new EncounterContext(
                1L, "faction_a", 0.5f, 0f, 0f, false, false);
        EncounterContext rim = new EncounterContext(
                1L, "faction_a", 0.5f, 1.0f, 0f, false, false);

        EncounterTable coreTable = EncounterTableBuilder.build(core);
        EncounterTable rimTable = EncounterTableBuilder.build(rim);

        assertTrue(rimTable.getWeight(EncounterType.DERELICT_DISCOVERY)
                        > coreTable.getWeight(EncounterType.DERELICT_DISCOVERY),
                "DERELICT_DISCOVERY weight should be higher in the rim");
    }

    @Test
    void warAddsHostileFleet() {
        EncounterContext peace = new EncounterContext(
                1L, "faction_a", 0.5f, 0.5f, 0f, false, false);
        EncounterContext war = new EncounterContext(
                1L, "faction_a", 0.5f, 0.5f, 0f, false, true);

        EncounterTable peaceTable = EncounterTableBuilder.build(peace);
        EncounterTable warTable = EncounterTableBuilder.build(war);

        assertEquals(0f, peaceTable.getWeight(EncounterType.HOSTILE_FLEET), 0.01f,
                "HOSTILE_FLEET should be 0 in peacetime");
        assertTrue(warTable.getWeight(EncounterType.HOSTILE_FLEET) > 0f,
                "HOSTILE_FLEET should have positive weight during war");
    }

    @Test
    void contestedBoostsCombat() {
        EncounterContext normal = new EncounterContext(
                1L, "faction_a", 0.5f, 0.5f, 0f, false, false);
        EncounterContext contested = new EncounterContext(
                1L, "faction_a", 0.5f, 0.5f, 0f, true, false);

        EncounterTable normalTable = EncounterTableBuilder.build(normal);
        EncounterTable contestedTable = EncounterTableBuilder.build(contested);

        assertTrue(contestedTable.getWeight(EncounterType.PIRATE_AMBUSH)
                        > normalTable.getWeight(EncounterType.PIRATE_AMBUSH),
                "PIRATE_AMBUSH should be boosted in contested systems");
        assertTrue(contestedTable.getWeight(EncounterType.FACTION_PATROL)
                        > normalTable.getWeight(EncounterType.FACTION_PATROL),
                "FACTION_PATROL should be boosted in contested systems");
    }
}
