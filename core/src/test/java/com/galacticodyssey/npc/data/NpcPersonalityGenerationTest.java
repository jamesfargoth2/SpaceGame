package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.npc.PersonalityTrait;
import com.galacticodyssey.npc.components.NpcPersonalityComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NpcPersonalityGenerationTest {

    private NpcDataRegistry registry;
    private NpcGenerator generator;
    private Engine engine;

    private static final ComponentMapper<NpcPersonalityComponent> PERSONALITY_M =
        ComponentMapper.getFor(NpcPersonalityComponent.class);

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

        BackgroundDefinition military = new BackgroundDefinition();
        military.id = "military";
        military.name = "Military";
        registry.registerBackground(military);

        registry.registerNames("human",
            List.of("James", "Elena"),
            List.of("Voss", "Chen"));

        generator = new NpcGenerator(registry);
    }

    @Test
    void generatedNpcHasPersonalityComponent() {
        Entity npc = generator.generate(engine, FIXED_SEED);
        NpcPersonalityComponent personality = PERSONALITY_M.get(npc);
        assertNotNull(personality);
    }

    @Test
    void personalityHasTwoToFourTraits() {
        for (int i = 0; i < 200; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcPersonalityComponent p = PERSONALITY_M.get(npc);
            assertTrue(p.traits.size() >= 2 && p.traits.size() <= 4,
                "NPC " + i + " has " + p.traits.size() + " traits, expected 2-4");
        }
    }

    @Test
    void traitsAreUnique() {
        for (int i = 0; i < 200; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcPersonalityComponent p = PERSONALITY_M.get(npc);
            Set<PersonalityTrait> unique = new HashSet<>(p.traits);
            assertEquals(unique.size(), p.traits.size(),
                "NPC " + i + " has duplicate traits: " + p.traits);
        }
    }

    @Test
    void noContradictoryTraits() {
        for (int i = 0; i < 500; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcPersonalityComponent p = PERSONALITY_M.get(npc);
            Set<String> names = new HashSet<>();
            for (PersonalityTrait t : p.traits) {
                names.add(t.name());
            }
            assertFalse(names.contains("BRAVE") && names.contains("COWARDLY"),
                "NPC " + i + " has BRAVE + COWARDLY");
            assertFalse(names.contains("LOYAL") && names.contains("VINDICTIVE"),
                "NPC " + i + " has LOYAL + VINDICTIVE");
            assertFalse(names.contains("GENEROUS") && names.contains("GREEDY"),
                "NPC " + i + " has GENEROUS + GREEDY");
            assertFalse(names.contains("DISCIPLINED") && names.contains("RECKLESS"),
                "NPC " + i + " has DISCIPLINED + RECKLESS");
            assertFalse(names.contains("CURIOUS") && names.contains("PRAGMATIC"),
                "NPC " + i + " has CURIOUS + PRAGMATIC");
        }
    }

    @Test
    void personalityIsDeterministic() {
        Entity npc1 = generator.generate(engine, FIXED_SEED);
        Entity npc2 = generator.generate(engine, FIXED_SEED);
        NpcPersonalityComponent p1 = PERSONALITY_M.get(npc1);
        NpcPersonalityComponent p2 = PERSONALITY_M.get(npc2);
        assertEquals(p1.traits, p2.traits);
        assertEquals(p1.loyalty, p2.loyalty, 1e-6f);
        assertEquals(p1.greed, p2.greed, 1e-6f);
        assertEquals(p1.bravery, p2.bravery, 1e-6f);
    }

    @Test
    void personalityScoresAreInRange() {
        for (int i = 0; i < 200; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcPersonalityComponent p = PERSONALITY_M.get(npc);
            assertTrue(p.loyalty >= 0f && p.loyalty <= 1f,
                "loyalty " + p.loyalty + " out of range");
            assertTrue(p.greed >= 0f && p.greed <= 1f,
                "greed " + p.greed + " out of range");
            assertTrue(p.bravery >= 0f && p.bravery <= 1f,
                "bravery " + p.bravery + " out of range");
        }
    }
}
