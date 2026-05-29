package com.galacticodyssey.planet;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.terrain.TerrainNoiseStack;
import com.galacticodyssey.planet.tectonic.PlateGenerator;
import com.galacticodyssey.planet.tectonic.TectonicModel;
import org.junit.jupiter.api.Test;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** End-to-end: one shared TectonicModel drives both terrain heights and biome sea level,
 *  fully deterministic, with no GL context. */
class TectonicPipelineIntegrationTest {

    private Planet planet(long seed) {
        return new Planet(seed, PlanetType.TERRAN, 0.8f, 1.5f, 0.7f, 24f, 0.4f, false);
    }

    private Atmosphere atmo() {
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        comp.put(Gas.N2, 0.78f);
        comp.put(Gas.O2, 0.22f);
        return new Atmosphere(comp, 1.0f, 1.0f, 255f, 288f, true, EnumSet.noneOf(AtmoHazard.class));
    }

    @Test
    void sharedModelDrivesTerrainAndSeaLevelDeterministically() {
        Planet p = planet(2024L);
        Atmosphere atmosphere = atmo();

        // Build the model ONCE and share it (the production assembly contract).
        TectonicModel model = new PlateGenerator().generate(p);

        BiomeMap biomeMap = new BiomeMapper().generate(p, atmosphere, (lon, lat) -> 0f, model);
        TerrainNoiseStack noise = new TerrainNoiseStack(
            com.galacticodyssey.galaxy.SeedDeriver.forId(
                com.galacticodyssey.galaxy.SeedDeriver.domain(p.seed,
                    com.galacticodyssey.galaxy.SeedDeriver.TERRAIN_DOMAIN), 0),
            model);

        // Sea level consistent with the model.
        float expectedSea = Math.max(0f, Math.min(0.3f, 0.3f * (1f - model.continentalFraction())));
        assertEquals(expectedSea, biomeMap.seaLevel, 1e-5f);

        // Terrain sampling is deterministic and bounded.
        for (int i = 0; i < 50; i++) {
            Vector3 d = new Vector3(i - 25, 5, i * 0.7f - 10).nor();
            float h = noise.heightAt(d, biomeMap, 3);
            assertTrue(Float.isFinite(h), "height must be finite at sample " + i);
        }
        Vector3 probe = new Vector3(0.3f, 0.5f, 0.8f).nor();
        assertEquals(noise.heightAt(probe, biomeMap, 4), noise.heightAt(probe, biomeMap, 4), 1e-6f);
    }

    @Test
    void differentPlanetSeedsProduceDifferentTerrain() {
        Vector3 d = new Vector3(0.2f, 0.4f, 0.9f).nor();
        TectonicModel m1 = new PlateGenerator().generate(planet(1L));
        TectonicModel m2 = new PlateGenerator().generate(planet(2L));
        assertNotEquals(m1.baseElevation(d), m2.baseElevation(d), 1e-4f);
    }
}
