package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.Planet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class PlanetSpawnPrewarmTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    @Test
    void prewarmCreatesLeafGeometryUnderSpawnDirection() {
        Planet planet = PlanetTestSupport.earthLike();
        BiomeMap biome = new BiomeMap(0L, 0f, 6000f, 0.5f, 293f, EnumSet.allOf(BiomeType.class));
        PlanetTerrainSystem sys = new PlanetTerrainSystem(null);
        double radiusKm = planet.radius * PlanetTerrainSystem.EARTH_RADIUS_KM;
        sys.loadPlanet(planet, biome, new PlanetCoordsKM(0, radiusKm, 0));

        Vector3 spawnDir = new Vector3(0, 1, 0).nor();
        sys.prewarmAround(spawnDir);

        boolean hasDeepLeaf = sys.getVisibleLeaves().stream().anyMatch(c ->
            !c.hasChildren() && c.depth >= TerrainChunk.MAX_DEPTH - 2
            && c.state == TerrainChunk.State.READY);
        assertTrue(hasDeepLeaf, "pre-warm should subdivide to near-max depth under the spawn point with READY geometry");
    }
}
