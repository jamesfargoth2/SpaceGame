package com.galacticodyssey.npc.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcDataRegistryTest {

    private NpcDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NpcDataRegistry();

        SpeciesDefinition human = new SpeciesDefinition();
        human.id = "human";
        human.name = "Human";
        human.repairMod = 5f;
        human.portraitIds.add("portrait_human_01");
        registry.registerSpecies(human);

        SpeciesDefinition veloxi = new SpeciesDefinition();
        veloxi.id = "veloxi";
        veloxi.name = "Veloxi";
        veloxi.accuracyMod = 10f;
        registry.registerSpecies(veloxi);

        BackgroundDefinition military = new BackgroundDefinition();
        military.id = "military";
        military.name = "Military";
        military.combatMod = 10f;
        military.accuracyMod = 10f;
        registry.registerBackground(military);

        PerkDefinition quickHands = new PerkDefinition();
        quickHands.id = "quick_hands";
        quickHands.name = "Quick Hands";
        quickHands.minRank = "SPECIALIST";
        quickHands.applicableRole = "ENGINEER";
        registry.registerPerk(quickHands);

        registry.registerNames("human",
            List.of("James", "Elena"),
            List.of("Voss", "Chen"));
    }

    @Test
    void getSpeciesById() {
        SpeciesDefinition human = registry.getSpecies("human");
        assertNotNull(human);
        assertEquals("Human", human.name);
        assertEquals(5f, human.repairMod);
    }

    @Test
    void getSpeciesReturnsNullForUnknownId() {
        assertNull(registry.getSpecies("unknown"));
    }

    @Test
    void getAllSpecies() {
        List<SpeciesDefinition> all = registry.getAllSpecies();
        assertEquals(2, all.size());
    }

    @Test
    void getBackgroundById() {
        BackgroundDefinition mil = registry.getBackground("military");
        assertNotNull(mil);
        assertEquals(10f, mil.combatMod);
    }

    @Test
    void getPerkById() {
        PerkDefinition perk = registry.getPerk("quick_hands");
        assertNotNull(perk);
        assertEquals("SPECIALIST", perk.minRank);
    }

    @Test
    void getAllPerks() {
        assertEquals(1, registry.getAllPerks().size());
    }

    @Test
    void getNamePool() {
        NpcDataRegistry.NamePool pool = registry.getNamePool("human");
        assertNotNull(pool);
        assertEquals(2, pool.firstNames.size());
        assertEquals(2, pool.lastNames.size());
        assertTrue(pool.firstNames.contains("James"));
    }

    @Test
    void getNamePoolReturnsNullForUnknownSpecies() {
        assertNull(registry.getNamePool("martian"));
    }

    @Test
    void getSpeciesIds() {
        List<String> ids = registry.getSpeciesIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains("human"));
        assertTrue(ids.contains("veloxi"));
    }
}
