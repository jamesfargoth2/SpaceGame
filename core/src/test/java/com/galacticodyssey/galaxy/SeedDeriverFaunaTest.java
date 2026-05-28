package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedDeriverFaunaTest {
    @Test
    void faunaDomainIsStableAndDistinct() {
        long a = SeedDeriver.faunaDomain(12345L);
        long b = SeedDeriver.faunaDomain(12345L);
        assertEquals(a, b, "same input must yield same domain");
        assertNotEquals(SeedDeriver.faunaDomain(1L), SeedDeriver.faunaDomain(2L));
        // distinct from an existing domain for the same parent seed
        assertNotEquals(SeedDeriver.domain(12345L, SeedDeriver.NPC_DOMAIN),
                        SeedDeriver.faunaDomain(12345L));
    }
}
