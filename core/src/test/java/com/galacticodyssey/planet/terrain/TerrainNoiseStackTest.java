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

    private BiomeMap makeBiomeMap() {
        return makeBiomeMapWith(BiomeType.GRASSLAND);
    }

    private BiomeMap makeBiomeMapWith(BiomeType type) {
        return new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f, EnumSet.of(type));
    }
}
