package com.galacticodyssey.galaxy.faction;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FactionGeneratorTest {

    private static final long GALAXY_SEED = 42L;

    @Test
    void deterministic() {
        FactionGenerator gen1 = new FactionGenerator();
        FactionGenerator gen2 = new FactionGenerator();

        List<FactionData> a = gen1.generateFactions(5, GALAXY_SEED, new Random(GALAXY_SEED));
        List<FactionData> b = gen2.generateFactions(5, GALAXY_SEED, new Random(GALAXY_SEED));

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).name, b.get(i).name);
            assertEquals(a.get(i).ethos, b.get(i).ethos);
            assertEquals(a.get(i).militaryStrength, b.get(i).militaryStrength, 1e-6f);
            assertEquals(a.get(i).capitalX, b.get(i).capitalX, 1e-10);
        }
    }

    @Test
    void uniqueNames() {
        FactionGenerator gen = new FactionGenerator();
        List<FactionData> factions = gen.generateFactions(20, GALAXY_SEED, new Random(GALAXY_SEED));

        Set<String> names = new HashSet<>();
        for (FactionData f : factions) {
            assertTrue(names.add(f.name),
                    "Duplicate faction name: " + f.name);
        }
    }

    @Test
    void ethosDistribution() {
        FactionGenerator gen = new FactionGenerator();
        List<FactionData> factions = gen.generateFactions(50, GALAXY_SEED, new Random(GALAXY_SEED));

        Set<FactionEthos> seen = new HashSet<>();
        for (FactionData f : factions) {
            seen.add(f.ethos);
        }

        for (FactionEthos ethos : FactionEthos.values()) {
            assertTrue(seen.contains(ethos),
                    "Ethos " + ethos + " never appeared among 50 factions");
        }
    }

    @Test
    void strengthsInValidRange() {
        FactionGenerator gen = new FactionGenerator();
        List<FactionData> factions = gen.generateFactions(20, GALAXY_SEED, new Random(GALAXY_SEED));

        for (FactionData f : factions) {
            assertTrue(f.militaryStrength >= 0.3f && f.militaryStrength <= 1.0f,
                    f.name + " military " + f.militaryStrength + " out of range");
            assertTrue(f.economicStrength >= 0.3f && f.economicStrength <= 1.0f,
                    f.name + " economic " + f.economicStrength + " out of range");
            assertTrue(f.influenceRadiusLY >= 200f && f.influenceRadiusLY <= 800f,
                    f.name + " influence " + f.influenceRadiusLY + " out of range");
        }
    }
}
