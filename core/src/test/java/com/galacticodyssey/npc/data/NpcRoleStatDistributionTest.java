package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.npc.NPCRole;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcRoleStatDistributionTest {

    private NpcDataRegistry registry;
    private NpcGenerator generator;
    private Engine engine;

    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);

    private static final long FIXED_SEED = 12345L;

    @BeforeEach
    void setUp() {
        registry = new NpcDataRegistry();
        engine = new Engine();

        SpeciesDefinition human = new SpeciesDefinition();
        human.id = "human";
        human.name = "Human";
        human.portraitIds.addAll(List.of("portrait_human_01"));
        registry.registerSpecies(human);

        BackgroundDefinition bg = new BackgroundDefinition();
        bg.id = "generic";
        bg.name = "Generic";
        registry.registerBackground(bg);

        registry.registerNames("human",
            List.of("James", "Elena"),
            List.of("Voss", "Chen"));

        generator = new NpcGenerator(registry);
    }

    @Test
    void pilotRoleHasHigherPilotingStat() {
        float totalPiloting = 0;
        float totalCombat = 0;
        int count = 100;
        for (int i = 0; i < count; i++) {
            Entity npc = generator.generate(engine,
                SeedDeriver.forId(FIXED_SEED, i), "human", "generic", NPCRole.PILOT);
            NpcStatsComponent stats = STATS_M.get(npc);
            totalPiloting += stats.piloting;
            totalCombat += stats.combat;
        }
        float avgPiloting = totalPiloting / count;
        float avgCombat = totalCombat / count;
        assertTrue(avgPiloting > avgCombat,
            "PILOT avg piloting (" + avgPiloting + ") should exceed avg combat (" + avgCombat + ")");
    }

    @Test
    void medicRoleHasHigherMedicalStat() {
        float totalMedical = 0;
        float totalCombat = 0;
        int count = 100;
        for (int i = 0; i < count; i++) {
            Entity npc = generator.generate(engine,
                SeedDeriver.forId(FIXED_SEED, i), "human", "generic", NPCRole.MEDIC);
            NpcStatsComponent stats = STATS_M.get(npc);
            totalMedical += stats.medical;
            totalCombat += stats.combat;
        }
        float avgMedical = totalMedical / count;
        float avgCombat = totalCombat / count;
        assertTrue(avgMedical > avgCombat,
            "MEDIC avg medical (" + avgMedical + ") should exceed avg combat (" + avgCombat + ")");
    }

    @Test
    void merchantRoleHasHigherPersuasionStat() {
        float totalPersuasion = 0;
        float totalCombat = 0;
        int count = 100;
        for (int i = 0; i < count; i++) {
            Entity npc = generator.generate(engine,
                SeedDeriver.forId(FIXED_SEED, i), "human", "generic", NPCRole.MERCHANT);
            NpcStatsComponent stats = STATS_M.get(npc);
            totalPersuasion += stats.persuasion;
            totalCombat += stats.combat;
        }
        float avgPersuasion = totalPersuasion / count;
        float avgCombat = totalCombat / count;
        assertTrue(avgPersuasion > avgCombat,
            "MERCHANT avg persuasion (" + avgPersuasion + ") should exceed avg combat (" + avgCombat + ")");
    }

    @Test
    void nullRoleProducesUnweightedStats() {
        Entity npc = generator.generate(engine, FIXED_SEED, "human", "generic", null);
        NpcStatsComponent stats = STATS_M.get(npc);
        assertStatInRange(stats.accuracy);
        assertStatInRange(stats.piloting);
        assertStatInRange(stats.persuasion);
    }

    private void assertStatInRange(float stat) {
        assertTrue(stat >= 0f && stat <= 100f,
            "Stat " + stat + " should be in range [0, 100]");
    }
}
