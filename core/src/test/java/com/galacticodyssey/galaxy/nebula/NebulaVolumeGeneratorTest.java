package com.galacticodyssey.galaxy.nebula;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NebulaVolumeGeneratorTest {

    private final NebulaVolumeGenerator generator = new NebulaVolumeGenerator();

    @Test
    void deterministic() {
        NebulaVolume a = generator.generate(42L, 10f, "EMISSION");
        NebulaVolume b = generator.generate(42L, 10f, "EMISSION");

        assertEquals(a.peakDensity, b.peakDensity, 1e-30f);
        assertEquals(a.dustFraction, b.dustFraction, 1e-6f);
        assertEquals(a.ionZones.size(), b.ionZones.size());
        assertEquals(a.hazards.size(), b.hazards.size());
        assertEquals(a.embeddedObjects.size(), b.embeddedObjects.size());
        assertEquals(a.glowIntensity, b.glowIntensity, 1e-6f);
    }

    @Test
    void densityFallsOffAtEdge() {
        NebulaVolume vol = generator.generate(42L, 10f, "EMISSION");
        NebulaDensityField field = new NebulaDensityField();

        float centerDensity = field.density(0f, 0f, 0f, vol, 42L);
        float edgeDensity = field.density(1f, 0f, 0f, vol, 42L);

        assertTrue(centerDensity > edgeDensity,
                "Center density (" + centerDensity + ") should exceed edge density (" + edgeDensity + ")");
        assertTrue(edgeDensity < vol.peakDensity * 0.2f,
                "Edge density should be near zero, was " + edgeDensity);
    }

    @Test
    void emissionNebulaHasIonZones() {
        NebulaVolume vol = generator.generate(42L, 10f, "EMISSION");
        assertFalse(vol.ionZones.isEmpty(), "EMISSION nebula should have ionisation zones");
    }

    @Test
    void darkNebulaHighDust() {
        NebulaVolume vol = generator.generate(42L, 10f, "DARK");
        assertTrue(vol.dustFraction > 0.5f,
                "DARK nebula dustFraction should exceed 0.5, was " + vol.dustFraction);
    }

    @Test
    void embeddedObjectsPresent() {
        NebulaVolume vol = generator.generate(42L, 10f, "EMISSION");
        boolean hasYso = vol.embeddedObjects.stream()
                .anyMatch(o -> o.type == EmbeddedObjectType.YOUNG_STELLAR_OBJECT);
        assertTrue(hasYso, "Should contain at least 1 young stellar object");
    }
}
