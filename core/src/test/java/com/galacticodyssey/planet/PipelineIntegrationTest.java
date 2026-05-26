package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.*;
import com.galacticodyssey.planet.terrain.*;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PipelineIntegrationTest {
    private static final long GALAXY_SEED = 12345L;

    @Test
    void fullPipelineDeterministic() {
        StarSystem sysA = runPipelineTo(GALAXY_SEED, 42L);
        StarSystem sysB = runPipelineTo(GALAXY_SEED, 42L);

        assertEquals(sysA.spectralClass, sysB.spectralClass);
        assertEquals(sysA.orbits.size(), sysB.orbits.size());
        for (int i = 0; i < sysA.orbits.size(); i++) {
            assertEquals(sysA.orbits.get(i).orbitalRadius,
                         sysB.orbits.get(i).orbitalRadius, 1e-6f);
            Planet pa = sysA.orbits.get(i).planet;
            Planet pb = sysB.orbits.get(i).planet;
            assertEquals(pa.type, pb.type);
            assertEquals(pa.radius, pb.radius, 1e-6f);
        }
    }

    @Test
    void seedToTerrainChunkWithoutGLContext() {
        StarSystemGenerator starGen = new StarSystemGenerator(GALAXY_SEED);
        OrbitalLayoutGenerator layoutGen = new OrbitalLayoutGenerator();
        PlanetGenerator planetGen = new PlanetGenerator(GALAXY_SEED);
        AtmosphereGenerator atmoGen = new AtmosphereGenerator();
        BiomeMapper biomeMapper = new BiomeMapper();

        StarPosition pos = new StarPosition();
        pos.uniqueId = 42L;
        pos.x = 1000.0;
        pos.y = 500.0;
        pos.z = 0.0;
        pos.localDensity = 0.5f;

        StarSystem sys = starGen.generate(pos, GalaxyRegion.INNER_RIM);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        sys.orbits.addAll(orbits);

        Planet planet = null;
        OrbitalSlot planetSlot = null;
        for (OrbitalSlot slot : orbits) {
            Planet p = planetGen.generate(slot, sys);
            slot.planet = p;
            if (p.type.hasSurface()) { planet = p; planetSlot = slot; break; }
        }
        assertNotNull(planet, "Should find at least one surface planet");

        Atmosphere atmo = atmoGen.generate(planet, sys);
        planet.atmosphere = atmo;
        BiomeMap biomeMap = biomeMapper.generate(planet, atmo);

        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        TerrainNoiseStack noise = new TerrainNoiseStack(terrainSeed);
        TerrainMeshBuilder.MeshData mesh = TerrainMeshBuilder.build(
            CubeFace.POS_Z, 0f, 0f, 1f, 1f, noise, biomeMap,
            planet.radius * 6371f, 2, null);

        assertEquals(TerrainMeshBuilder.GRID_SIZE * TerrainMeshBuilder.GRID_SIZE *
                     TerrainMeshBuilder.VERTEX_STRIDE, mesh.vertices.length);
        assertTrue(mesh.indices.length > 0);
    }

    @Test
    void systemGenerationPerformance() {
        StarSystemGenerator starGen = new StarSystemGenerator(GALAXY_SEED);
        OrbitalLayoutGenerator layoutGen = new OrbitalLayoutGenerator();
        PlanetGenerator planetGen = new PlanetGenerator(GALAXY_SEED);
        AtmosphereGenerator atmoGen = new AtmosphereGenerator();

        long start = System.nanoTime();
        for (long id = 0; id < 100; id++) {
            StarPosition pos = new StarPosition();
            pos.uniqueId = id;
            pos.x = id * 10.0;
            pos.y = id * 5.0;
            pos.z = 0.0;
            pos.localDensity = 0.5f;
            StarSystem sys = starGen.generate(pos, GalaxyRegion.INNER_RIM);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            sys.orbits.addAll(orbits);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                slot.planet = p;
                if (p.type.hasSurface()) {
                    Atmosphere atmo = atmoGen.generate(p, sys);
                    p.atmosphere = atmo;
                }
            }
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed < 5000, "100 full system generations took " + elapsed + "ms, expected < 5000ms");
    }

    @Test
    void terrainChunkPerformance() {
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));

        long start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            TerrainMeshBuilder.build(CubeFace.POS_Z, 0f, 0f, 1f, 1f,
                noise, biomeMap, 6371f, 2, null);
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        float perChunk = elapsed / 20f;
        assertTrue(perChunk < 50, "Terrain chunk gen took " + perChunk + "ms avg, expected < 50ms");
    }

    private StarSystem runPipelineTo(long galaxySeed, long starId) {
        StarSystemGenerator starGen = new StarSystemGenerator(galaxySeed);
        OrbitalLayoutGenerator layoutGen = new OrbitalLayoutGenerator();
        PlanetGenerator planetGen = new PlanetGenerator(galaxySeed);

        StarPosition pos = new StarPosition();
        pos.uniqueId = starId;
        pos.x = 1000.0;
        pos.y = 500.0;
        pos.z = 0.0;
        pos.localDensity = 0.5f;

        StarSystem sys = starGen.generate(pos, GalaxyRegion.INNER_RIM);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        sys.orbits.addAll(orbits);
        for (OrbitalSlot slot : orbits) {
            slot.planet = planetGen.generate(slot, sys);
        }
        return sys;
    }
}
