package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedDeriverFloraTest {
    @Test
    void floraDomainIsDeterministicAndDistinct() {
        long a = SeedDeriver.floraDomain(12345L);
        long b = SeedDeriver.floraDomain(12345L);
        assertEquals(a, b, "same seed must derive the same flora domain");
        assertNotEquals(SeedDeriver.floraDomain(12345L), SeedDeriver.floraDomain(12346L));
        // distinct from an unrelated domain on the same parent seed
        assertNotEquals(SeedDeriver.floraDomain(12345L), SeedDeriver.npcDomain(12345L));
    }
}
