package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.OrbitalConstants;
import com.galacticodyssey.galaxy.OrbitalMechanics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoonOrbitalTest {

    @Test
    void moonHasOrbitalElements() {
        Moon moon = new Moon(42L, PlanetType.BARREN, 0.1f, 0.01f,
            0.005f, 0.05f, 0.1f);

        assertEquals(0.005f, moon.orbitalRadius);
        assertEquals(0.05f, moon.orbitalEccentricity);
        moon.computeOrbitalPeriod(OrbitalConstants.EARTH_MASS_KG);
        assertTrue(moon.orbitalPeriod > 0f);
    }

    @Test
    void moonOrbitalPeriodDerivesFromRadiusAndParentMass() {
        float moonRadiusAU = 0.003f;
        float parentMassKg = 10f * OrbitalConstants.EARTH_MASS_KG;
        float GM = OrbitalConstants.G * parentMassKg;
        float radiusGameUnits = moonRadiusAU * OrbitalConstants.AU_TO_GAME_UNITS;
        float expectedPeriod = OrbitalMechanics.orbitalPeriod(GM, radiusGameUnits);

        Moon moon = new Moon(1L, PlanetType.BARREN, 0.1f, 0.01f,
            moonRadiusAU, 0.0f, 0.0f);
        moon.computeOrbitalPeriod(parentMassKg);

        assertEquals(expectedPeriod, moon.orbitalPeriod, expectedPeriod * 0.001f);
    }
}
