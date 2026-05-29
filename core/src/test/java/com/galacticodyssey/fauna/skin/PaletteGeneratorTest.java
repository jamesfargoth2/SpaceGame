package com.galacticodyssey.fauna.skin;

import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaletteGeneratorTest {

    @Test
    void deterministic_sameSeedAndBiomeProduceIdenticalSpec() {
        CreatureSkinSpec a = PaletteGenerator.generate(12345L, BiomeType.GRASSLAND, BodyPlan.QUADRUPED);
        CreatureSkinSpec b = PaletteGenerator.generate(12345L, BiomeType.GRASSLAND, BodyPlan.QUADRUPED);
        assertEquals(a.patternType, b.patternType);
        assertEquals(a.primaryR, b.primaryR, 1e-6f);
        assertEquals(a.primaryG, b.primaryG, 1e-6f);
        assertEquals(a.primaryB, b.primaryB, 1e-6f);
        assertEquals(a.secondaryR, b.secondaryR, 1e-6f);
        assertEquals(a.patternScale, b.patternScale, 1e-6f);
    }

    @Test
    void differentSeedsProduceDifferentPalettes() {
        CreatureSkinSpec a = PaletteGenerator.generate(100L, BiomeType.GRASSLAND, BodyPlan.QUADRUPED);
        CreatureSkinSpec b = PaletteGenerator.generate(999L, BiomeType.GRASSLAND, BodyPlan.QUADRUPED);
        boolean differs = a.primaryR != b.primaryR || a.primaryG != b.primaryG
                       || a.primaryB != b.primaryB || a.patternType != b.patternType;
        assertTrue(differs, "different seeds should generally produce different palettes");
    }

    @Test
    void colorsAreInValidRange() {
        CreatureSkinSpec s = PaletteGenerator.generate(42L, BiomeType.DESERT, BodyPlan.BIPEDAL);
        assertColorRange(s.primaryR, s.primaryG, s.primaryB);
        assertColorRange(s.secondaryR, s.secondaryG, s.secondaryB);
        assertColorRange(s.accentR, s.accentG, s.accentB);
        assertColorRange(s.ventralR, s.ventralG, s.ventralB);
    }

    @Test
    void ventralIsLighterThanPrimary() {
        CreatureSkinSpec s = PaletteGenerator.generate(42L, BiomeType.TEMPERATE_FOREST, BodyPlan.QUADRUPED);
        float primaryLum = 0.299f * s.primaryR + 0.587f * s.primaryG + 0.114f * s.primaryB;
        float ventralLum = 0.299f * s.ventralR + 0.587f * s.ventralG + 0.114f * s.ventralB;
        assertTrue(ventralLum >= primaryLum - 0.01f, "ventral should be at least as bright as primary");
    }

    @Test
    void hexapodHasLowRoughness() {
        CreatureSkinSpec s = PaletteGenerator.generate(42L, BiomeType.GRASSLAND, BodyPlan.HEXAPOD);
        assertTrue(s.roughness < 0.5f, "chitin should be shiny");
        assertTrue(s.metallic > 0f, "chitin should have slight metallic");
    }

    @Test
    void swampBiomeFavorsBioluminescent() {
        int bioCount = 0;
        for (int i = 0; i < 100; i++) {
            CreatureSkinSpec s = PaletteGenerator.generate(i * 7919L, BiomeType.SWAMP, BodyPlan.HEXAPOD);
            if (s.patternType == PatternType.BIOLUMINESCENT) bioCount++;
        }
        assertTrue(bioCount >= 5, "swamp biome should produce some bioluminescent creatures, got " + bioCount);
    }

    private void assertColorRange(float r, float g, float b) {
        assertTrue(r >= 0f && r <= 1f, "R out of range: " + r);
        assertTrue(g >= 0f && g <= 1f, "G out of range: " + g);
        assertTrue(b >= 0f && b <= 1f, "B out of range: " + b);
    }
}
