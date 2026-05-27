package com.galacticodyssey.galaxy.asteroid;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AsteroidGeneratorTest {

    private final AsteroidGenerator generator = new AsteroidGenerator();

    @Test
    void deterministic() {
        AsteroidConfig cfg = new AsteroidConfig(42L, AsteroidType.S_TYPE, 5f, 3, 2, 4);
        GeneratedAsteroid a = generator.generate(cfg);
        GeneratedAsteroid b = generator.generate(cfg);

        assertArrayEquals(a.vertices, b.vertices, 1e-6f);
        assertArrayEquals(a.indices, b.indices);
        assertEquals(a.veins.size(), b.veins.size());
        for (int i = 0; i < a.veins.size(); i++) {
            assertEquals(a.veins.get(i).mineral, b.veins.get(i).mineral);
            assertEquals(a.veins.get(i).centreX, b.veins.get(i).centreX, 1e-6f);
        }
    }

    @Test
    void elongationApplied() {
        AsteroidConfig cfg = new AsteroidConfig(99L, AsteroidType.S_TYPE, 1f, 0, 0, 0);
        GeneratedAsteroid result = generator.generate(cfg);

        // Measure max extent along each axis
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < result.vertices.length; i += 3) {
            minX = Math.min(minX, result.vertices[i]);
            maxX = Math.max(maxX, result.vertices[i]);
            minY = Math.min(minY, result.vertices[i + 1]);
            maxY = Math.max(maxY, result.vertices[i + 1]);
            minZ = Math.min(minZ, result.vertices[i + 2]);
            maxZ = Math.max(maxZ, result.vertices[i + 2]);
        }
        float extentX = maxX - minX;
        float extentY = maxY - minY;
        float extentZ = maxZ - minZ;
        float maxExtent = Math.max(extentX, Math.max(extentY, extentZ));
        float minExtent = Math.min(extentX, Math.min(extentY, extentZ));

        assertTrue(maxExtent > minExtent * 1.2f,
                "Asteroid should be elongated: max=" + maxExtent + " min=" + minExtent);
    }

    @Test
    void typeSpecificDeformation() {
        AsteroidConfig cfgM = new AsteroidConfig(123L, AsteroidType.M_TYPE, 2f, 0, 0, 0);
        AsteroidConfig cfgC = new AsteroidConfig(123L, AsteroidType.C_TYPE, 2f, 0, 0, 0);
        GeneratedAsteroid mResult = generator.generate(cfgM);
        GeneratedAsteroid cResult = generator.generate(cfgC);

        // With same seed but different types, vertices should differ due to different factors
        boolean different = false;
        for (int i = 0; i < Math.min(mResult.vertices.length, cResult.vertices.length); i++) {
            if (Math.abs(mResult.vertices[i] - cResult.vertices[i]) > 0.001f) {
                different = true;
                break;
            }
        }
        assertTrue(different, "M_TYPE and C_TYPE should have different deformation results");
    }

    @Test
    void veinPaletteMatchesType() {
        // C_TYPE should get CARBON, SILICATE, WATER_ICE
        AsteroidConfig cfgC = new AsteroidConfig(55L, AsteroidType.C_TYPE, 5f, 0, 0, 20);
        GeneratedAsteroid cResult = generator.generate(cfgC);
        Set<MineralType> cMinerals = new HashSet<>();
        for (VeinDeposit v : cResult.veins) {
            cMinerals.add(v.mineral);
        }
        Set<MineralType> expectedC = Set.of(MineralType.CARBON, MineralType.SILICATE, MineralType.WATER_ICE);
        for (MineralType m : cMinerals) {
            assertTrue(expectedC.contains(m), "C_TYPE should not contain " + m);
        }

        // M_TYPE should get IRON_NICKEL, PLATINUM_GROUP, COBALT
        AsteroidConfig cfgM = new AsteroidConfig(55L, AsteroidType.M_TYPE, 5f, 0, 0, 20);
        GeneratedAsteroid mResult = generator.generate(cfgM);
        Set<MineralType> mMinerals = new HashSet<>();
        for (VeinDeposit v : mResult.veins) {
            mMinerals.add(v.mineral);
        }
        Set<MineralType> expectedM = Set.of(MineralType.IRON_NICKEL, MineralType.PLATINUM_GROUP, MineralType.COBALT);
        for (MineralType m : mMinerals) {
            assertTrue(expectedM.contains(m), "M_TYPE should not contain " + m);
        }
    }

    @Test
    void craterCount() {
        int craters = 5;
        AsteroidConfig cfg = new AsteroidConfig(77L, AsteroidType.S_TYPE, 5f, craters, 0, 0);
        GeneratedAsteroid noCraters = generator.generate(
                new AsteroidConfig(77L, AsteroidType.S_TYPE, 5f, 0, 0, 0));
        GeneratedAsteroid withCraters = generator.generate(cfg);

        // With craters applied, some vertices should be displaced inward
        boolean hasDifference = false;
        for (int i = 0; i < withCraters.vertices.length; i++) {
            if (Math.abs(withCraters.vertices[i] - noCraters.vertices[i]) > 0.001f) {
                hasDifference = true;
                break;
            }
        }
        assertTrue(hasDifference, "Craters should modify vertex positions");
    }
}
