package com.galacticodyssey.npc;

import com.galacticodyssey.galaxy.SeedDeriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NPCGeneratorTest {

    private static final long FIXED_SEED = 12345L;
    private NPCGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new NPCGenerator();
    }

    @Test
    void deterministic() {
        NPCData a = new NPCGenerator().generate(NPCRole.PILOT, "faction-0", FIXED_SEED);
        NPCData b = new NPCGenerator().generate(NPCRole.PILOT, "faction-0", FIXED_SEED);

        assertEquals(a.name, b.name);
        assertEquals(a.species, b.species);
        assertEquals(a.piloting, b.piloting, 1e-6f);
        assertEquals(a.combat, b.combat, 1e-6f);
        assertEquals(a.traits, b.traits);
        assertEquals(a.hooks.size(), b.hooks.size());
        for (int i = 0; i < a.hooks.size(); i++) {
            assertEquals(a.hooks.get(i).type, b.hooks.get(i).type);
        }
    }

    @Test
    void roleAffectsPrimaryStat() {
        // Generate many PILOTs and verify piloting is consistently high
        float totalPiloting = 0;
        int count = 100;
        for (int i = 0; i < count; i++) {
            NPCData npc = generator.generate(NPCRole.PILOT, null, SeedDeriver.forId(FIXED_SEED, i));
            totalPiloting += npc.piloting;
        }
        float avgPiloting = totalPiloting / count;
        assertTrue(avgPiloting > 0.45f,
                "PILOT average piloting should be high, got " + avgPiloting);

        // Generate many MEDICs and verify medicine is consistently high
        float totalMedicine = 0;
        for (int i = 0; i < count; i++) {
            NPCData npc = generator.generate(NPCRole.MEDIC, null, SeedDeriver.forId(FIXED_SEED, i));
            totalMedicine += npc.medicine;
        }
        float avgMedicine = totalMedicine / count;
        assertTrue(avgMedicine > 0.45f,
                "MEDIC average medicine should be high, got " + avgMedicine);
    }

    @Test
    void traitsAreUnique() {
        for (int i = 0; i < 200; i++) {
            NPCData npc = generator.generate(NPCRole.ENGINEER, null, SeedDeriver.forId(FIXED_SEED, i));
            Set<PersonalityTrait> unique = new HashSet<>(npc.traits);
            assertEquals(unique.size(), npc.traits.size(),
                    "NPC " + i + " has duplicate traits: " + npc.traits);
        }
    }

    @Test
    void noContradictoryTraits() {
        for (int i = 0; i < 500; i++) {
            NPCData npc = generator.generate(NPCRole.MERCHANT, null, SeedDeriver.forId(FIXED_SEED, i));
            Set<String> names = new HashSet<>();
            for (PersonalityTrait t : npc.traits) {
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
    void pirateAlwaysWanted() {
        for (int i = 0; i < 100; i++) {
            NPCData npc = generator.generate(NPCRole.PIRATE_CAPTAIN, null, SeedDeriver.forId(FIXED_SEED, i));
            boolean hasWanted = false;
            for (BackstoryHook hook : npc.hooks) {
                if (hook.type == HookType.WANTED_CRIMINAL) {
                    hasWanted = true;
                    break;
                }
            }
            assertTrue(hasWanted,
                    "PIRATE_CAPTAIN " + i + " missing WANTED_CRIMINAL hook");
        }
    }

    @Test
    void statsInValidRange() {
        for (int i = 0; i < 200; i++) {
            NPCData npc = generator.generate(NPCRole.GUNNER, null, SeedDeriver.forId(FIXED_SEED, i));
            assertStatInRange("piloting", npc.piloting);
            assertStatInRange("engineering", npc.engineering);
            assertStatInRange("combat", npc.combat);
            assertStatInRange("medicine", npc.medicine);
            assertStatInRange("science", npc.science);
            assertStatInRange("persuasion", npc.persuasion);
            assertStatInRange("stealth", npc.stealth);
        }
    }

    @Test
    void speciesDistribution() {
        int humanCount = 0;
        int total = 200;
        for (int i = 0; i < total; i++) {
            // Use SeedDeriver to get well-distributed seeds (sequential seeds
            // produce correlated first values in java.util.Random)
            long seed = SeedDeriver.forId(FIXED_SEED, i);
            NPCData npc = generator.generate(NPCRole.COLONIST, null, seed);
            if ("human".equals(npc.species)) humanCount++;
        }
        float humanPct = (float) humanCount / total;
        // 70% target, allow +/-15% tolerance
        assertTrue(humanPct > 0.55f && humanPct < 0.85f,
                "Human percentage should be ~70%, got " + (humanPct * 100) + "%");
    }

    private void assertStatInRange(String statName, float value) {
        assertTrue(value >= 0f && value <= 1f,
                statName + " = " + value + " is outside [0, 1]");
    }
}
