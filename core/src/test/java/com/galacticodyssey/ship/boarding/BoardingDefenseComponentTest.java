package com.galacticodyssey.ship.boarding;

import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardingDefenseComponentTest {

    @Test
    void defendersScaleWithSizeClass() {
        BoardingDefenseComponent small = BoardingDefenseComponent.forSizeClass(ShipSizeClass.SMALL);
        BoardingDefenseComponent medium = BoardingDefenseComponent.forSizeClass(ShipSizeClass.MEDIUM);
        BoardingDefenseComponent large = BoardingDefenseComponent.forSizeClass(ShipSizeClass.LARGE);

        assertTrue(small.defenderCount >= 1);
        assertTrue(medium.defenderCount > small.defenderCount);
        assertTrue(large.defenderCount > medium.defenderCount);
        assertTrue(small.defenderHealth > 0f);
        assertTrue(small.defenderDamage > 0f);
        assertEquals("independent", small.factionId);
    }
}
