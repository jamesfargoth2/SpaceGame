package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.NpcDisposition;
import com.galacticodyssey.npc.NPCRole;
import com.galacticodyssey.npc.components.NpcBackstoryComponent;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.npc.components.NpcPersonalityComponent;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcGeneratorTest {

    private NpcDataRegistry registry;
    private NpcGenerator generator;
    private Engine engine;

    private static final ComponentMapper<NpcIdentityComponent> ID_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);

    @BeforeEach
    void setUp() {
        registry = new NpcDataRegistry();
        engine = new Engine();

        SpeciesDefinition human = new SpeciesDefinition();
        human.id = "human";
        human.name = "Human";
        human.repairMod = 5f;
        human.medicalMod = 5f;
        human.portraitIds.addAll(List.of("portrait_human_01", "portrait_human_02"));
        registry.registerSpecies(human);

        BackgroundDefinition military = new BackgroundDefinition();
        military.id = "military";
        military.name = "Military";
        military.accuracyMod = 10f;
        military.combatMod = 10f;
        registry.registerBackground(military);

        BackgroundDefinition civilian = new BackgroundDefinition();
        civilian.id = "civilian";
        civilian.name = "Civilian";
        civilian.repairMod = 5f;
        registry.registerBackground(civilian);

        registry.registerNames("human",
            List.of("James", "Elena", "Marcus"),
            List.of("Voss", "Chen", "Okafor"));

        generator = new NpcGenerator(registry);
    }

    @Test
    void generatedNpcHasIdentityAndStats() {
        Entity npc = generator.generate(engine, 12345L);
        assertNotNull(ID_M.get(npc));
        assertNotNull(STATS_M.get(npc));
    }

    @Test
    void generatedNpcHasNonEmptyNameAndSpecies() {
        Entity npc = generator.generate(engine, 12345L);
        NpcIdentityComponent id = ID_M.get(npc);
        assertNotNull(id.name);
        assertFalse(id.name.isEmpty());
        assertEquals("human", id.species);
    }

    @Test
    void generatedNpcHasPortraitFromSpeciesPool() {
        Entity npc = generator.generate(engine, 12345L);
        NpcIdentityComponent id = ID_M.get(npc);
        assertTrue(id.portraitId.startsWith("portrait_human_"));
    }

    @Test
    void generatedNpcStatsAreInValidRange() {
        Entity npc = generator.generate(engine, 12345L);
        NpcStatsComponent stats = STATS_M.get(npc);
        assertStatInRange(stats.accuracy);
        assertStatInRange(stats.repair);
        assertStatInRange(stats.medical);
        assertStatInRange(stats.piloting);
        assertStatInRange(stats.science);
        assertStatInRange(stats.combat);
        assertStatInRange(stats.persuasion);
        assertStatInRange(stats.stealth);
    }

    @Test
    void generatedNpcHasPersuasionAndStealthStats() {
        Entity npc = generator.generate(engine, 12345L);
        NpcStatsComponent stats = STATS_M.get(npc);
        assertStatInRange(stats.persuasion);
        assertStatInRange(stats.stealth);
    }

    @Test
    void sameSeedProducesSameNpc() {
        Entity npc1 = generator.generate(engine, 99999L);
        Entity npc2 = generator.generate(engine, 99999L);
        NpcIdentityComponent id1 = ID_M.get(npc1);
        NpcIdentityComponent id2 = ID_M.get(npc2);
        assertEquals(id1.name, id2.name);
        assertEquals(id1.species, id2.species);

        NpcStatsComponent stats1 = STATS_M.get(npc1);
        NpcStatsComponent stats2 = STATS_M.get(npc2);
        assertEquals(stats1.accuracy, stats2.accuracy);
        assertEquals(stats1.combat, stats2.combat);
    }

    @Test
    void differentSeedsProduceDifferentNpcs() {
        Entity npc1 = generator.generate(engine, 11111L);
        Entity npc2 = generator.generate(engine, 22222L);
        NpcStatsComponent stats1 = STATS_M.get(npc1);
        NpcStatsComponent stats2 = STATS_M.get(npc2);
        boolean allEqual = stats1.accuracy == stats2.accuracy
            && stats1.repair == stats2.repair
            && stats1.combat == stats2.combat;
        assertFalse(allEqual, "Different seeds should produce different stats");
    }

    @Test
    void generateWithExplicitSpeciesAndBackground() {
        Entity npc = generator.generate(engine, 12345L, "human", "military");
        NpcIdentityComponent id = ID_M.get(npc);
        assertEquals("human", id.species);
        assertEquals("military", id.background);
    }

    @Test
    void defaultDispositionIsNeutral() {
        Entity npc = generator.generate(engine, 12345L);
        assertEquals(NpcDisposition.NEUTRAL, ID_M.get(npc).disposition);
    }

    @Test
    void npcIdIsDeterministicFromSeed() {
        Entity npc1 = generator.generate(engine, 42L);
        Entity npc2 = generator.generate(engine, 42L);
        assertEquals(ID_M.get(npc1).npcId, ID_M.get(npc2).npcId);
        assertFalse(ID_M.get(npc1).npcId.isEmpty());
    }

    @Test
    void generatedNpcHasRoleAndAge() {
        Entity npc = generator.generate(engine, 12345L, "human", "military", NPCRole.MARINE);
        NpcIdentityComponent id = ID_M.get(npc);
        assertEquals(NPCRole.MARINE, id.role);
        assertTrue(id.age >= 18f && id.age <= 70f);
    }

    @Test
    void fullyLoadedNpcHasAllComponents() {
        Entity npc = generator.generate(engine, 12345L, "human", "military", NPCRole.ENGINEER);

        // Identity
        NpcIdentityComponent id = ID_M.get(npc);
        assertNotNull(id);
        assertEquals(NPCRole.ENGINEER, id.role);
        assertTrue(id.age >= 18f && id.age <= 70f);
        assertTrue(id.recruitable);

        // Stats
        NpcStatsComponent stats = STATS_M.get(npc);
        assertNotNull(stats);
        assertTrue(stats.accuracy >= 0f && stats.accuracy <= 100f);
        assertTrue(stats.repair >= 0f && stats.repair <= 100f);
        assertTrue(stats.medical >= 0f && stats.medical <= 100f);
        assertTrue(stats.piloting >= 0f && stats.piloting <= 100f);
        assertTrue(stats.science >= 0f && stats.science <= 100f);
        assertTrue(stats.combat >= 0f && stats.combat <= 100f);
        assertTrue(stats.persuasion >= 0f && stats.persuasion <= 100f);
        assertTrue(stats.stealth >= 0f && stats.stealth <= 100f);

        // Personality
        ComponentMapper<NpcPersonalityComponent> PM =
            ComponentMapper.getFor(NpcPersonalityComponent.class);
        NpcPersonalityComponent personality = PM.get(npc);
        assertNotNull(personality);
        assertTrue(personality.traits.size() >= 2 && personality.traits.size() <= 4);

        // Backstory
        ComponentMapper<NpcBackstoryComponent> BM =
            ComponentMapper.getFor(NpcBackstoryComponent.class);
        NpcBackstoryComponent backstory = BM.get(npc);
        assertNotNull(backstory);
        assertTrue(backstory.hooks.size() >= 1 && backstory.hooks.size() <= 2);
    }

    private void assertStatInRange(float stat) {
        assertTrue(stat >= 0f && stat <= 100f,
            "Stat " + stat + " should be in range [0, 100]");
    }
}
