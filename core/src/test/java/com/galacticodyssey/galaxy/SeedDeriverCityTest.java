package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedDeriverCityTest {
    @Test
    void cityDomainIsDeterministicAndDistinct() {
        long a = SeedDeriver.cityDomain(42L);
        long b = SeedDeriver.cityDomain(42L);
        assertEquals(a, b, "same parent seed -> same city domain");
        assertNotEquals(SeedDeriver.cityDomain(42L), SeedDeriver.cityDomain(43L));
        assertNotEquals(SeedDeriver.cityDomain(42L), SeedDeriver.npcDomain(42L));
    }
}
