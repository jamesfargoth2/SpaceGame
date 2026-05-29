package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SeedDeriverTest {

    @Test
    void domainReturnsSameOutputForSameInput() {
        long seed = 12345L;
        long domain = 0x9E3779B97F4A7C15L;
        assertEquals(SeedDeriver.domain(seed, domain), SeedDeriver.domain(seed, domain));
    }

    @Test
    void domainReturnsDifferentOutputForDifferentDomains() {
        long seed = 12345L;
        long domainA = 0x9E3779B97F4A7C15L;
        long domainB = 0x6C62272E07BB0142L;
        assertNotEquals(SeedDeriver.domain(seed, domainA), SeedDeriver.domain(seed, domainB));
    }

    @Test
    void forIdReturnsSameOutputForSameInput() {
        long domainSeed = 99999L;
        assertEquals(SeedDeriver.forId(domainSeed, 42), SeedDeriver.forId(domainSeed, 42));
    }

    @Test
    void forIdReturnsDifferentOutputForDifferentIds() {
        long domainSeed = 99999L;
        assertNotEquals(SeedDeriver.forId(domainSeed, 0), SeedDeriver.forId(domainSeed, 1));
    }

    @Test
    void forChunkReturnsSameOutputForSameCoordinates() {
        long domainSeed = 77777L;
        assertEquals(SeedDeriver.forChunk(domainSeed, 5, 10), SeedDeriver.forChunk(domainSeed, 5, 10));
    }

    @Test
    void forChunkReturnsDifferentOutputForDifferentCoordinates() {
        long domainSeed = 77777L;
        assertNotEquals(SeedDeriver.forChunk(domainSeed, 0, 0), SeedDeriver.forChunk(domainSeed, 1, 0));
        assertNotEquals(SeedDeriver.forChunk(domainSeed, 0, 0), SeedDeriver.forChunk(domainSeed, 0, 1));
    }

    @Test
    void forChunkHandlesNegativeCoordinates() {
        long domainSeed = 77777L;
        long pos = SeedDeriver.forChunk(domainSeed, 5, 5);
        long neg = SeedDeriver.forChunk(domainSeed, -5, -5);
        assertNotEquals(pos, neg);
    }

    @Test
    void allDomainConstantsAreUnique() {
        long seed = 1L;
        long[] domains = {
            SeedDeriver.STAR_DOMAIN, SeedDeriver.PLANET_DOMAIN, SeedDeriver.MOON_DOMAIN,
            SeedDeriver.TERRAIN_DOMAIN, SeedDeriver.ATMOSPHERE_DOMAIN, SeedDeriver.BIOME_DOMAIN,
            SeedDeriver.STATION_DOMAIN, SeedDeriver.INTERIOR_DOMAIN, SeedDeriver.FACTION_DOMAIN,
            SeedDeriver.NAME_DOMAIN, SeedDeriver.NEBULA_DOMAIN, SeedDeriver.DERELICT_DOMAIN,
            SeedDeriver.CAVE_DOMAIN, SeedDeriver.DUNGEON_DOMAIN, SeedDeriver.ASTEROID_SHAPE_DOMAIN,
            SeedDeriver.ENCOUNTER_DOMAIN, SeedDeriver.ECONOMY_DOMAIN, SeedDeriver.CRATER_DOMAIN,
            SeedDeriver.NPC_DOMAIN, SeedDeriver.CLIMATE_DOMAIN, SeedDeriver.DRAINAGE_DOMAIN,
            SeedDeriver.TECTONIC_DOMAIN, SeedDeriver.EROSION_DOMAIN, SeedDeriver.FLORA_DOMAIN,
            SeedDeriver.FAUNA_DOMAIN, SeedDeriver.CITY_DOMAIN, SeedDeriver.SHIP_DOMAIN
        };
        Set<Long> derived = new HashSet<>();
        for (long domain : domains) {
            derived.add(SeedDeriver.domain(seed, domain));
        }
        assertEquals(domains.length, derived.size(),
            "All " + domains.length + " domain constants must produce unique seeds");
    }

    @Test
    void floraDomainReturnsSameOutputForSameSeed() {
        assertEquals(SeedDeriver.floraDomain(12345L), SeedDeriver.floraDomain(12345L));
    }

    @Test
    void floraDomainReturnsDifferentOutputForDifferentSeeds() {
        assertNotEquals(SeedDeriver.floraDomain(12345L), SeedDeriver.floraDomain(12346L));
    }

    @Test
    void shipDomainIsDeterministicAndDistinct() {
        assertEquals(SeedDeriver.shipDomain(777L), SeedDeriver.shipDomain(777L));
        assertNotEquals(SeedDeriver.shipDomain(777L), SeedDeriver.shipDomain(778L));
        // Must not collide with the interior domain it superseded.
        assertNotEquals(SeedDeriver.shipDomain(777L),
                        SeedDeriver.domain(777L, SeedDeriver.INTERIOR_DOMAIN));
    }

    @Test
    void mixProducesGoodDistribution() {
        int buckets = 256;
        int[] counts = new int[buckets];
        int samples = 10000;
        for (int i = 0; i < samples; i++) {
            long seed = SeedDeriver.forId(42L, i);
            int bucket = (int) (Math.abs(seed) % buckets);
            counts[bucket]++;
        }
        float expected = samples / (float) buckets;
        for (int count : counts) {
            assertTrue(count > expected * 0.5f && count < expected * 2f,
                "Bucket count " + count + " deviates too far from expected " + expected);
        }
    }
}
