package com.galacticodyssey.planet.thermal;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.Atmosphere;
import com.galacticodyssey.planet.AtmoHazard;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.Gas;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanetSurfaceEnvironmentTest {

    private Atmosphere atmosphere() {
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        comp.put(Gas.N2, 0.78f);
        comp.put(Gas.O2, 0.21f);
        return new Atmosphere(comp, 101325f, 1f, 255f, 288f, true, EnumSet.noneOf(AtmoHazard.class));
    }

    @Test
    void ambientTempComesFromBiomeMapAtSceneLatLon() {
        // BiomeMap with no ClimateData falls back to surfaceTemp * (1 - 0.4 sin^2 lat).
        BiomeMap biome = new BiomeMap(1L, 0f, 9999f, 0.5f, 300f,
                EnumSet.of(BiomeType.GRASSLAND));
        PlanetSurfaceEnvironment env = new PlanetSurfaceEnvironment(
                biome, atmosphere(), 0f /*lat*/, 0f /*lon*/, 6_371_000f /*radius*/);

        float t = env.ambientTemp(new Vector3(0, 0, 0));
        assertEquals(300f, t, 1f); // at equator sin(lat)=0
    }

    @Test
    void oxygenFractionReadsAtmosphereComposition() {
        PlanetSurfaceEnvironment env = new PlanetSurfaceEnvironment(
                new BiomeMap(1L, 0f, 9999f, 0.5f, 300f, EnumSet.of(BiomeType.GRASSLAND)),
                atmosphere(), 0f, 0f, 6_371_000f);
        assertEquals(0.21f, env.oxygenFraction(new Vector3()), 0.001f);
    }
}
