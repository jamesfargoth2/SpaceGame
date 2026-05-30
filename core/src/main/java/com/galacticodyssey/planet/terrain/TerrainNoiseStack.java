package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.GalaxyNoise;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.tectonic.TectonicModel;

public final class TerrainNoiseStack {
    private final GalaxyNoise continentNoise;
    private final GalaxyNoise ridgeNoise;
    private final GalaxyNoise detailNoise;
    private final TectonicModel tectonic; // nullable: null -> legacy noise-only continents

    public static final class Sample {
        public float height;
        public BiomeType biome;
    }

    public TerrainNoiseStack(long seed) {
        this(seed, null);
    }

    public TerrainNoiseStack(long seed, TectonicModel tectonic) {
        this.continentNoise = new GalaxyNoise(SeedDeriver.domain(seed, SeedDeriver.CONTINENT_NOISE_DOMAIN));
        this.ridgeNoise     = new GalaxyNoise(SeedDeriver.domain(seed, SeedDeriver.RIDGE_NOISE_DOMAIN));
        this.detailNoise    = new GalaxyNoise(SeedDeriver.domain(seed, SeedDeriver.DETAIL_NOISE_DOMAIN));
        this.tectonic = tectonic;
    }

    public float heightAt(Vector3 dir, BiomeMap biomeMap, int lod) {
        Sample s = sampleAt(dir, biomeMap, lod);
        return s.height;
    }

    public Sample sampleAt(Vector3 dir, BiomeMap biomeMap, int lod) {
        float cx = dir.x * 2f;
        float cy = dir.y * 2f;
        float cz = dir.z * 2f;
        float warp = continentNoise.domainWarp3D(cx, cy, cz, 0.7f, 3, 6);
        float continent;
        if (tectonic != null) {
            // Tectonics drives the macro shape; noise only warps coastlines.
            continent = tectonic.baseElevation(dir) + 0.15f * warp;
        } else {
            continent = warp;
        }

        float rx = dir.x * 8f;
        float ry = dir.y * 8f;
        float rz = dir.z * 8f;
        float ridge = ridgeNoise.ridgedFbm(rx, ry, rz, 6, 2.0f, 2.0f);

        // The sphere terrain's top face sits at geographic lat=90° (north pole), but the player
        // spawns there on foot.  Passing the raw latitude produces polar ice over the whole
        // visible surface.  Scale latitude down so biome temperature stays above freezing across
        // the player-facing hemisphere; the remaining variation still drives coastal vs. inland
        // biome differences via the moisture/elevation terms.
        float lat = CubeSphere.latitudeOf(dir) * 0.15f;
        float lon = CubeSphere.longitudeOf(dir);
        // Clamp continent to [-0.5, 0.75] so unnormalized tectonic values above 1.0 don't
        // accidentally trip the snowLine check and render whole mountain ranges as ice.
        float clampedContinent = Math.max(-0.5f, Math.min(0.75f, continent));
        BiomeType biome = biomeMap.getBiome(lat, lon, clampedContinent);
        float amplitude = biome.amplitude;
        float ridgeMix = biome.ridgeMix;

        float height = (continent * (1f - ridgeMix) + ridge * ridgeMix) * amplitude;

        if (lod >= 3) {
            float dx = dir.x * 64f;
            float dy = dir.y * 64f;
            float dz = dir.z * 64f;
            float detail = detailNoise.billowFbm(dx, dy, dz, 4, 0.5f, 2.0f);
            height += detail * amplitude * 0.1f;
        }

        if (lod >= 5) {
            float fx = dir.x * 256f;
            float fy = dir.y * 256f;
            float fz = dir.z * 256f;
            float fine = detailNoise.fbm(fx, fy, fz, 3, 0.5f, 2.0f);
            height += fine * amplitude * 0.02f;
        }

        // SP2: progressive high-frequency octaves so deep chunks carry real meter-scale
        // relief instead of re-tessellating a smooth surface. Each step roughly doubles
        // frequency and halves contribution; amplitudes stay well inside the height
        // envelope so slopes remain walkable.
        if (lod >= 7) {
            float n = detailNoise.fbm(dir.x * 512f, dir.y * 512f, dir.z * 512f, 3, 0.5f, 2.0f);
            height += n * amplitude * 0.010f;
        }
        if (lod >= 9) {
            float n = detailNoise.fbm(dir.x * 1024f, dir.y * 1024f, dir.z * 1024f, 3, 0.5f, 2.0f);
            height += n * amplitude * 0.005f;
        }
        if (lod >= 11) {
            float n = detailNoise.fbm(dir.x * 2048f, dir.y * 2048f, dir.z * 2048f, 2, 0.5f, 2.0f);
            height += n * amplitude * 0.0025f;
        }
        if (lod >= 13) {
            float n = detailNoise.fbm(dir.x * 4096f, dir.y * 4096f, dir.z * 4096f, 2, 0.5f, 2.0f);
            height += n * amplitude * 0.0012f;
        }
        // SP2 (Task 11 tuning): extend the geometric octave series down toward the depth-14
        // mesh resolution (~19 m vertex spacing). On a 6371 km planet, unit-sphere frequency f
        // gives surface wavelength ~R/f, so f=4096→~1.5 km (too coarse to see when walking) and
        // f=65536→~97 m (resolved by the depth-14 mesh, above its ~38 m Nyquist limit). Keeping
        // the freq×2 / amplitude×0.5 ratio holds each octave's slope constant (~walkable), so
        // this adds self-similar walkable relief without cliffs. Gated to the deepest chunks only.
        if (lod >= 12) {
            float n = detailNoise.fbm(dir.x * 8192f, dir.y * 8192f, dir.z * 8192f, 2, 0.5f, 2.0f);
            height += n * amplitude * 0.0006f;
        }
        if (lod >= 13) {
            float n = detailNoise.fbm(dir.x * 16384f, dir.y * 16384f, dir.z * 16384f, 2, 0.5f, 2.0f);
            height += n * amplitude * 0.0003f;
        }
        if (lod >= 13) {
            float n = detailNoise.fbm(dir.x * 32768f, dir.y * 32768f, dir.z * 32768f, 2, 0.5f, 2.0f);
            height += n * amplitude * 0.00015f;
        }
        if (lod >= 14) {
            float n = detailNoise.fbm(dir.x * 65536f, dir.y * 65536f, dir.z * 65536f, 2, 0.5f, 2.0f);
            height += n * amplitude * 0.000075f;
        }

        Sample s = new Sample();
        s.height = height;
        s.biome = biome;
        return s;
    }
}
