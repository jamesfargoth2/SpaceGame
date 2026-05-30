package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class TerrainNoiseStackTest {

    @Test
    void sameSeedProducesIdenticalHeight() {
        TerrainNoiseStack a = new TerrainNoiseStack(12345L);
        TerrainNoiseStack b = new TerrainNoiseStack(12345L);
        BiomeMap biomeMap = makeBiomeMap();
        Vector3 dir = new Vector3(0.5f, 0.3f, 0.8f).nor();

        float ha = a.heightAt(dir, biomeMap, 3);
        float hb = b.heightAt(dir, biomeMap, 3);
        assertEquals(ha, hb, 1e-6f);
    }

    @Test
    void differentSeedsProduceDifferentHeight() {
        TerrainNoiseStack a = new TerrainNoiseStack(111L);
        TerrainNoiseStack b = new TerrainNoiseStack(222L);
        BiomeMap biomeMap = makeBiomeMap();
        Vector3 dir = new Vector3(0.5f, 0.3f, 0.8f).nor();

        float ha = a.heightAt(dir, biomeMap, 3);
        float hb = b.heightAt(dir, biomeMap, 3);
        assertNotEquals(ha, hb, 1e-3f);
    }

    @Test
    void heightWithinReasonableBounds() {
        TerrainNoiseStack stack = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = makeBiomeMap();
        for (float theta = 0; theta < 6.28f; theta += 0.5f) {
            for (float phi = -1.5f; phi < 1.5f; phi += 0.5f) {
                Vector3 dir = new Vector3(
                    (float)(Math.cos(theta) * Math.cos(phi)),
                    (float)(Math.sin(phi)),
                    (float)(Math.sin(theta) * Math.cos(phi))
                ).nor();
                float h = stack.heightAt(dir, biomeMap, 3);
                assertTrue(h > -2f && h < 2f,
                    "Height " + h + " at theta=" + theta + " phi=" + phi + " out of bounds");
            }
        }
    }

    @Test
    void volcanicBiomeProducesHigherAmplitude() {
        TerrainNoiseStack stack = new TerrainNoiseStack(42L);
        BiomeMap flat = makeBiomeMapWith(BiomeType.DESERT);
        BiomeMap rough = makeBiomeMapWith(BiomeType.VOLCANIC);

        float sumFlat = 0f, sumRough = 0f;
        int n = 50;
        for (int i = 0; i < n; i++) {
            Vector3 d = new Vector3(1f, i * 0.02f, i * 0.03f).nor();
            sumFlat += Math.abs(stack.heightAt(d, flat, 3));
            sumRough += Math.abs(stack.heightAt(d, rough, 3));
        }
        assertTrue(sumRough > sumFlat,
            "Volcanic avg amplitude " + sumRough/n + " should exceed desert " + sumFlat/n);
    }

    @Test
    void highLodAddsSubTwentyMetreRelief() {
        // Heights at lod 13 must differ measurably from heights at lod 5, proving that the
        // fine octaves add real terrain relief that coarse octaves alone do not carry.
        // Averaged over a small neighbourhood on the sphere to avoid sign-cancellation from
        // any single point pair.
        TerrainNoiseStack noise = new TerrainNoiseStack(12345L);
        // Fixed biome parameters: seaLevel=0.2f keeps sample points above ocean; high-amplitude
        // ROCKY_WASTE/BADLANDS biomes dominate when allBiomes is allowed, giving amplitude ≥ 0.2.
        BiomeMap biome = new BiomeMap(0L, 0.2f, 0.8f, 0.5f, 293f,
            java.util.EnumSet.allOf(com.galacticodyssey.planet.BiomeType.class));

        // Sample 8 directions near the +Z pole; for each, measure how much the height
        // changes between lod 5 and lod 13 (the extra high-frequency octaves).
        float totalDiff = 0f;
        int samples = 8;
        for (int i = 0; i < samples; i++) {
            float angle = (float)(i * 2 * Math.PI / samples);
            float r = 0.01f; // small angular offset — within a single terrain chunk
            Vector3 dir = new Vector3(r * (float)Math.cos(angle), r * (float)Math.sin(angle), 1f).nor();
            float h5  = noise.heightAt(dir, biome, 5);
            float h13 = noise.heightAt(dir, biome, 13);
            totalDiff += Math.abs(h13 - h5);
        }
        float avgDiff = totalDiff / samples;

        // Four fine octaves (lod>=7/9/11/13) together contribute amplitude*(0.01+0.005+0.0025+0.0012).
        // With amplitude ≥ 0.1 (minimum land biome) the average absolute change reliably exceeds 5e-4.
        assertTrue(avgDiff > 5e-4f,
            "average height change from fine octaves (" + avgDiff + ") should exceed 5e-4");
    }

    @Test
    void highLodIsDeterministic() {
        TerrainNoiseStack n1 = new TerrainNoiseStack(777L);
        TerrainNoiseStack n2 = new TerrainNoiseStack(777L);
        BiomeMap biome = new BiomeMap(0L, 0.2f, 0.8f, 0.5f, 293f,
            java.util.EnumSet.allOf(com.galacticodyssey.planet.BiomeType.class));
        Vector3 dir = new Vector3(0.3f, 0.2f, 1f).nor();
        assertEquals(n1.heightAt(dir, biome, 13), n2.heightAt(dir, biome, 13), 0f);
    }

    private BiomeMap makeBiomeMap() {
        return makeBiomeMapWith(BiomeType.GRASSLAND);
    }

    private BiomeMap makeBiomeMapWith(BiomeType type) {
        return new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f, EnumSet.of(type));
    }
}
