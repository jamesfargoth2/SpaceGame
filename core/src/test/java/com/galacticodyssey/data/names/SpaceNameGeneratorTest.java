package com.galacticodyssey.data.names;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SpaceNameGeneratorTest {

    private static final long GALAXY_SEED = 42L;
    private SpaceNameGenerator spaceGen;

    @BeforeEach
    void setUp() {
        spaceGen = new SpaceNameGenerator();
    }

    @Test
    void starNameUsesScientificStyle() {
        Random rng = new Random(GALAXY_SEED);
        String name = spaceGen.starName(rng);
        assertNotNull(name);
        assertFalse(name.isEmpty());
        assertTrue(Character.isUpperCase(name.charAt(0)),
                "Star name should be capitalised: " + name);
    }

    @Test
    void starNameIsDeterministic() {
        SpaceNameGenerator gen1 = new SpaceNameGenerator();
        SpaceNameGenerator gen2 = new SpaceNameGenerator();

        String name1 = gen1.starName(new Random(GALAXY_SEED));
        String name2 = gen2.starName(new Random(GALAXY_SEED));

        assertEquals(name1, name2, "Same seed must produce identical star names");
    }

    @Test
    void shipNameHasPrefixAndNoun() {
        Random rng = new Random(GALAXY_SEED);
        for (int i = 0; i < 20; i++) {
            String name = spaceGen.shipName(rng);
            assertTrue(name.contains(" "), "Ship name should have space between prefix and noun: " + name);

            String[] parts = name.split(" ", 2);
            assertEquals(2, parts.length, "Ship name should have exactly prefix and noun");

            // Prefix should be one of the known prefixes
            String prefix = parts[0];
            boolean validPrefix = prefix.equals("ISV") || prefix.equals("UNSS") ||
                    prefix.equals("FCS") || prefix.equals("RSV") ||
                    prefix.equals("TMS") || prefix.equals("DSV") ||
                    prefix.equals("MCS") || prefix.equals("ACS");
            assertTrue(validPrefix, "Unknown ship prefix: " + prefix);

            // Noun should be one of the known nouns
            String noun = parts[1];
            boolean validNoun = noun.equals("Resolve") || noun.equals("Vigilance") ||
                    noun.equals("Endurance") || noun.equals("Prometheus") ||
                    noun.equals("Harbinger") || noun.equals("Wanderer") ||
                    noun.equals("Nemesis") || noun.equals("Ascendant") ||
                    noun.equals("Relentless") || noun.equals("Pioneer");
            assertTrue(validNoun, "Unknown ship noun: " + noun);
        }
    }

    @Test
    void stationNameHasFactionPrefixAndNumber() {
        Random rng = new Random(GALAXY_SEED);
        String name = spaceGen.stationName("Terran", rng);

        assertTrue(name.startsWith("Terran "), "Station name should start with faction prefix: " + name);

        // Should contain a station type
        boolean hasType = name.contains("Station") || name.contains("Waypoint") ||
                name.contains("Depot") || name.contains("Outpost");
        assertTrue(hasType, "Station name should contain a station type: " + name);

        // Should end with a 3-digit number
        String[] parts = name.split(" ");
        String lastPart = parts[parts.length - 1];
        assertTrue(lastPart.matches("\\d{3}"),
                "Station name should end with a 3-digit number: " + name);
    }

    @Test
    void planetNameProducesValidNames() {
        Random rng = new Random(GALAXY_SEED);
        for (int i = 0; i < 20; i++) {
            String name = spaceGen.planetName(rng);
            assertNotNull(name);
            assertFalse(name.isEmpty());
            assertTrue(name.length() >= 2, "Planet name too short: " + name);
        }
    }

    @Test
    void factionNameProducesValidNames() {
        Random rng = new Random(GALAXY_SEED);
        for (int i = 0; i < 20; i++) {
            String name = spaceGen.factionName(rng);
            assertNotNull(name);
            assertFalse(name.isEmpty());
        }
    }

    @Test
    void characterNameProducesValidNames() {
        Random rng = new Random(GALAXY_SEED);
        for (int i = 0; i < 20; i++) {
            String name = spaceGen.characterName(rng);
            assertNotNull(name);
            assertFalse(name.isEmpty());
        }
    }
}
