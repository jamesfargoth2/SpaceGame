package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class HullStyleTest {

    @Test
    void generatorTypeHasLoftedAndFaceted() {
        assertEquals(2, GeneratorType.values().length);
        assertEquals(GeneratorType.LOFTED, GeneratorType.valueOf("LOFTED"));
        assertEquals(GeneratorType.FACETED, GeneratorType.valueOf("FACETED"));
    }

    @Test
    void shipRoleHasFiveValues() {
        assertEquals(5, ShipRole.values().length);
        assertEquals(ShipRole.WARSHIP, ShipRole.valueOf("WARSHIP"));
        assertEquals(ShipRole.CIVILIAN, ShipRole.valueOf("CIVILIAN"));
    }
}
