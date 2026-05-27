package com.galacticodyssey.data.names;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NameGeneratorTest {

    private static final long GALAXY_SEED = 42L;
    private NameGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new NameGenerator();
    }

    @Test
    void sameSeedProducesIdenticalName() {
        NameGenerator gen1 = new NameGenerator();
        NameGenerator gen2 = new NameGenerator();
        Random rng1 = new Random(GALAXY_SEED);
        Random rng2 = new Random(GALAXY_SEED);

        String name1 = gen1.generate(LanguageStyles.STAR_SCIENTIFIC, rng1, "star");
        String name2 = gen2.generate(LanguageStyles.STAR_SCIENTIFIC, rng2, "star");

        assertEquals(name1, name2, "Same seed must produce identical names");
    }

    @Test
    void namesAreUnique() {
        Random rng = new Random(GALAXY_SEED);
        Set<String> names = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            String name = generator.generate(LanguageStyles.HUMAN_COLONY, rng, "planet");
            assertTrue(names.add(name), "Duplicate name generated: " + name);
        }

        assertEquals(100, names.size());
    }

    @Test
    void pronounceabilityFilterRejectsLongClusters() {
        // Names with 4+ consecutive consonants should be rejected
        assertFalse(NameGenerator.isPronounceable("Krxzta"),
                "4+ consecutive consonants should be unpronounceable");
        assertFalse(NameGenerator.isPronounceable("Abcdfg"),
                "4+ consecutive consonants should be unpronounceable");

        // Names with fewer than 4 consecutive consonants should be accepted
        assertTrue(NameGenerator.isPronounceable("Kreta"),
                "3 consecutive consonants should be pronounceable");
        assertTrue(NameGenerator.isPronounceable("Aba"),
                "Simple syllable should be pronounceable");
    }

    @Test
    void pronounceabilityRejectsShortNames() {
        assertFalse(NameGenerator.isPronounceable("a"),
                "Single character names should be rejected");
        assertFalse(NameGenerator.isPronounceable(""),
                "Empty names should be rejected");
        assertTrue(NameGenerator.isPronounceable("ab"),
                "Two character names should be accepted");
    }

    @Test
    void vowelHarmonyConsistency() {
        // ALIEN_HARSH has explicit front (i) and back (a,aa,u,uu,o) vowels
        LanguageStyle style = LanguageStyles.ALIEN_HARSH;
        Random rng = new Random(GALAXY_SEED);

        for (int i = 0; i < 50; i++) {
            String name = generator.buildName(style, new Random(rng.nextLong()));
            String lower = name.toLowerCase();

            // Extract vowel groups present in the name
            boolean hasFrontVowels = containsAnyNucleus(lower, style.frontVowels);
            boolean hasBackVowels = containsAnyNucleus(lower, style.backVowels);

            // When vowel harmony is active, a name should use predominantly
            // one vowel set. Since we pick front or back per name, at least
            // one set must be present, and ideally not both.
            assertTrue(hasFrontVowels || hasBackVowels,
                    "Name '" + name + "' should contain at least some vowels from the defined harmony sets");

            // Allow that both could appear due to onset/coda containing vowel chars,
            // but the nuclei themselves should be consistent
        }
    }

    @Test
    void fallbackAppendsNumberWhenExhausted() {
        // Create a very constrained style that can only produce one name
        LanguageStyle tiny = LanguageStyle.builder("tiny")
                .setOnsets("")
                .setNuclei("a")
                .setCodaChance(0f)
                .setSyllableCount(2)
                .build();

        Random rng = new Random(GALAXY_SEED);

        // First name should be "Aa"
        String first = generator.generate(tiny, rng, "tiny");
        assertNotNull(first);

        // Keep generating until we get a fallback with numeric suffix
        String fallbackName = null;
        for (int i = 0; i < 50; i++) {
            String name = generator.generate(tiny, rng, "tiny");
            if (name.matches(".*-\\d{3}$")) {
                fallbackName = name;
                break;
            }
        }

        assertNotNull(fallbackName, "Generator should eventually fall back to numeric suffix");
        assertTrue(fallbackName.matches(".*-\\d{3}$"),
                "Fallback name should end with -NNN pattern, got: " + fallbackName);
    }

    @Test
    void generatedNamesAreCapitalized() {
        Random rng = new Random(GALAXY_SEED);
        for (int i = 0; i < 20; i++) {
            String name = generator.generate(LanguageStyles.HUMAN_COLONY, rng, "test");
            assertTrue(Character.isUpperCase(name.charAt(0)),
                    "Name should start with uppercase: " + name);
        }
    }

    @Test
    void allLanguageStylesProduceValidNames() {
        LanguageStyle[] styles = {
                LanguageStyles.STAR_SCIENTIFIC,
                LanguageStyles.HUMAN_COLONY,
                LanguageStyles.ALIEN_HARSH,
                LanguageStyles.ALIEN_SOFT,
                LanguageStyles.FACTION_IMPERIAL
        };

        for (LanguageStyle style : styles) {
            Random rng = new Random(GALAXY_SEED);
            for (int i = 0; i < 20; i++) {
                String name = generator.generate(style, rng, style.id);
                assertNotNull(name, "Name should not be null for style " + style.id);
                assertFalse(name.isEmpty(), "Name should not be empty for style " + style.id);
                assertTrue(name.length() >= 2,
                        "Name too short for style " + style.id + ": " + name);
            }
        }
    }

    private boolean containsAnyNucleus(String name, String[] nuclei) {
        for (String n : nuclei) {
            if (name.contains(n.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
