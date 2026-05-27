package com.galacticodyssey.planet;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class CraterFieldGeneratorTest {

    @Test
    void deterministic() {
        List<CraterSpec> a = CraterFieldGenerator.generate(4.0f, 3.0f, 12000f, new Random(42L));
        List<CraterSpec> b = CraterFieldGenerator.generate(4.0f, 3.0f, 12000f, new Random(42L));

        assertEquals(a.size(), b.size(), "Same seed should produce same count");
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).diameterKm, b.get(i).diameterKm, 1e-6f,
                    "Crater " + i + " diameter should match");
            assertEquals(a.get(i).morphology, b.get(i).morphology,
                    "Crater " + i + " morphology should match");
        }
    }

    @Test
    void sizeDistributionPowerLaw() {
        List<CraterSpec> craters = CraterFieldGenerator.generate(4.0f, 3.0f, 12000f, new Random(99L));
        assertFalse(craters.isEmpty(), "Should generate craters");

        float medianDiam = craters.get(craters.size() / 2).diameterKm;
        float maxDiam = craters.get(0).diameterKm;

        // In a power-law distribution, median should be much smaller than max
        assertTrue(medianDiam < maxDiam * 0.5f,
                "Power-law: median (" + medianDiam + ") should be much smaller than max (" + maxDiam + ")");

        // Count small craters (below 10 km) vs large (above 100 km)
        long smallCount = craters.stream().filter(c -> c.diameterKm < 10f).count();
        long largeCount = craters.stream().filter(c -> c.diameterKm > 100f).count();
        assertTrue(smallCount > largeCount,
                "Should have more small craters (" + smallCount + ") than large (" + largeCount + ")");
    }

    @Test
    void sortedLargestFirst() {
        List<CraterSpec> craters = CraterFieldGenerator.generate(4.0f, 3.0f, 12000f, new Random(7L));
        for (int i = 1; i < craters.size(); i++) {
            assertTrue(craters.get(i - 1).diameterKm >= craters.get(i).diameterKm,
                    "Craters should be sorted largest-first at index " + i);
        }
    }

    @Test
    void craterCountScalesWithAge() {
        // Use a small planet so neither case hits the 10000 cap
        List<CraterSpec> young = CraterFieldGenerator.generate(1.0f, 0.1f, 1000f, new Random(1L));
        List<CraterSpec> old = CraterFieldGenerator.generate(4.0f, 4.0f, 1000f, new Random(1L));

        assertTrue(old.size() > young.size(),
                "Older surface should have more craters: old=" + old.size() + " young=" + young.size());
    }

    @Test
    void emptyForZeroAge() {
        List<CraterSpec> craters = CraterFieldGenerator.generate(0f, 0f, 6000f, new Random(1L));
        assertEquals(0, craters.size(), "Zero-age surface should have no craters");
    }

    @Test
    void cappedAtMaximum() {
        // Very old, very large planet — should hit the 10000 cap
        List<CraterSpec> craters = CraterFieldGenerator.generate(10f, 10f, 100000f, new Random(1L));
        assertTrue(craters.size() <= 10000, "Should not exceed 10000 craters");
    }
}
