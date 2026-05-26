package com.galacticodyssey.galaxy.derelict;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DerelictGeneratorTest {

    private static final long TEST_SEED = 42L;
    private DerelictGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DerelictGenerator();
    }

    @Test
    void deterministic() {
        DerelictConfig cfg = new DerelictConfig(TEST_SEED,
                WreckType.BATTLE_CASUALTY, HullClass.FRIGATE,
                10f, 0.5f, true, false);

        GeneratedDerelict a = generator.generate(cfg);
        GeneratedDerelict b = new DerelictGenerator().generate(cfg);

        assertEquals(a.damageProfile.impactSites, b.damageProfile.impactSites);
        assertEquals(a.damageProfile.explosionSites, b.damageProfile.explosionSites);
        assertEquals(a.damageProfile.systemicDamage, b.damageProfile.systemicDamage, 1e-6f);
        assertEquals(a.sectionStates.size(), b.sectionStates.size());
        for (Map.Entry<String, SectionState> entry : a.sectionStates.entrySet()) {
            assertEquals(entry.getValue(), b.sectionStates.get(entry.getKey()),
                    "Section " + entry.getKey() + " state mismatch");
        }
        assertEquals(a.salvageContents.size(), b.salvageContents.size());
        assertEquals(a.narrative.logCount, b.narrative.logCount);
        assertEquals(a.narrative.remainsCount, b.narrative.remainsCount);
        assertEquals(a.enemySpawnSections.size(), b.enemySpawnSections.size());
    }

    @Test
    void entryPointAlwaysReachable() {
        // Test across multiple wreck types and hull classes.
        for (WreckType type : WreckType.values()) {
            for (HullClass hull : HullClass.values()) {
                DerelictConfig cfg = new DerelictConfig(TEST_SEED + type.ordinal() * 100L + hull.ordinal(),
                        type, hull, 5f, 0.5f, false, false);
                GeneratedDerelict result = generator.generate(cfg);

                // First section in the ordered map should be BREACHED.
                Map.Entry<String, SectionState> first = result.sectionStates.entrySet().iterator().next();
                assertEquals(SectionState.BREACHED, first.getValue(),
                        "First section should be BREACHED for " + type + "/" + hull);
            }
        }
    }

    @Test
    void abandonedMostlyIntact() {
        // Run many seeds to verify statistical property.
        int totalSections = 0;
        int intactSections = 0;
        for (long seed = 0; seed < 100; seed++) {
            DerelictConfig cfg = new DerelictConfig(seed,
                    WreckType.ABANDONED, HullClass.CORVETTE,
                    2f, 0.8f, false, false);
            GeneratedDerelict result = generator.generate(cfg);
            for (SectionState state : result.sectionStates.values()) {
                totalSections++;
                if (state == SectionState.INTACT) intactSections++;
            }
        }
        float intactRatio = (float) intactSections / totalSections;
        assertTrue(intactRatio > 0.50f,
                "ABANDONED wrecks should have >50% INTACT sections, got " + intactRatio);
    }

    @Test
    void battleCasualtyHasDamage() {
        for (long seed = 0; seed < 50; seed++) {
            DerelictConfig cfg = new DerelictConfig(seed,
                    WreckType.BATTLE_CASUALTY, HullClass.CRUISER,
                    15f, 0.3f, false, false);
            GeneratedDerelict result = generator.generate(cfg);
            assertTrue(result.damageProfile.impactSites > 0,
                    "BATTLE_CASUALTY should have impact sites, seed=" + seed);
        }
    }

    @Test
    void salvageInCargoHold() {
        // Over many seeds, cargo_hold should have salvage frequently.
        int cargoWithSalvage = 0;
        int total = 100;
        for (long seed = 0; seed < total; seed++) {
            DerelictConfig cfg = new DerelictConfig(seed,
                    WreckType.ABANDONED, HullClass.FRIGATE,
                    5f, 0.8f, false, false);
            GeneratedDerelict result = generator.generate(cfg);
            if (result.salvageContents.containsKey("cargo_hold")) {
                cargoWithSalvage++;
            }
        }
        assertTrue(cargoWithSalvage > total / 2,
                "cargo_hold should have salvage in most wrecks, got " + cargoWithSalvage + "/" + total);
    }

    @Test
    void narrativeBlackBoxForLargeShips() {
        // Corvette and above should have black box.
        for (HullClass hull : new HullClass[]{HullClass.CORVETTE, HullClass.FRIGATE,
                HullClass.CRUISER, HullClass.CAPITAL}) {
            DerelictConfig cfg = new DerelictConfig(TEST_SEED,
                    WreckType.COLLISION, hull,
                    8f, 0.5f, false, false);
            GeneratedDerelict result = generator.generate(cfg);
            assertTrue(result.narrative.hasBlackBox,
                    hull + " should have a black box");
        }

        // Shuttle should not.
        DerelictConfig shuttleCfg = new DerelictConfig(TEST_SEED,
                WreckType.COLLISION, HullClass.SHUTTLE,
                8f, 0.5f, false, false);
        GeneratedDerelict shuttleResult = generator.generate(shuttleCfg);
        assertFalse(shuttleResult.narrative.hasBlackBox,
                "SHUTTLE should not have a black box");
    }

    @Test
    void noCrewRemainsForAbandoned() {
        for (long seed = 0; seed < 50; seed++) {
            DerelictConfig cfg = new DerelictConfig(seed,
                    WreckType.ABANDONED, HullClass.CRUISER,
                    20f, 0.9f, false, false);
            GeneratedDerelict result = generator.generate(cfg);
            assertEquals(0, result.narrative.remainsCount,
                    "ABANDONED wreck should have 0 crew remains, seed=" + seed);
        }
    }
}
