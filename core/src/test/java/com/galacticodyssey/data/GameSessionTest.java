package com.galacticodyssey.data;

import com.galacticodyssey.galaxy.GalaxySize;
import com.galacticodyssey.galaxy.GalaxyType;
import com.galacticodyssey.galaxy.StartingRegion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {

    @Test
    void configFieldsPreservedAfterConstruction() {
        GameSession s = new GameSession(999L, "Test Galaxy",
            GalaxyType.SPIRAL, GalaxySize.SMALL, StartingRegion.INNER_RIM);
        assertEquals(999L, s.seed);
        assertEquals("Test Galaxy", s.galaxyName);
        assertEquals(GalaxyType.SPIRAL, s.galaxyType);
        assertEquals(GalaxySize.SMALL, s.galaxySize);
        assertEquals(StartingRegion.INNER_RIM, s.startingRegion);
        assertFalse(s.complete);
        assertFalse(s.failed);
        assertNull(s.error);
        assertTrue(s.log.isEmpty());
    }

    @Test
    void terrainSeedFormulaIsDeterministic() {
        long seed = 777L;
        float mass = 0.8f;
        float day = 100.0f;
        long s1 = seed ^ Long.reverse(Float.floatToRawIntBits(mass)) ^ Float.floatToRawIntBits(day);
        long s2 = seed ^ Long.reverse(Float.floatToRawIntBits(mass)) ^ Float.floatToRawIntBits(day);
        assertEquals(s1, s2);
    }

    @Test
    void terrainSeedFormulaDistinctForDistinctPlanets() {
        long seed = 12345L;
        long s1 = seed ^ Long.reverse(Float.floatToRawIntBits(1.0f)) ^ Float.floatToRawIntBits(24.0f);
        long s2 = seed ^ Long.reverse(Float.floatToRawIntBits(1.5f)) ^ Float.floatToRawIntBits(36.0f);
        assertNotEquals(s1, s2);
    }
}
