package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalConstantsTest {

    @Test
    void auToGameUnitsIsPositive() {
        assertTrue(OrbitalConstants.AU_TO_GAME_UNITS > 0f);
        assertEquals(1000f, OrbitalConstants.AU_TO_GAME_UNITS);
    }

    @Test
    void earthMassKgMatchesPhysics() {
        assertEquals(5.972e24f, OrbitalConstants.EARTH_MASS_KG, 1e20f);
    }

    @Test
    void solarMassKgMatchesPhysics() {
        assertEquals(1.989e30f, OrbitalConstants.SOLAR_MASS_KG, 1e26f);
    }

    @Test
    void gravitationalConstantMatchesGravitySystem() {
        assertEquals(OrbitalMechanics.G, OrbitalConstants.G);
    }
}
