package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.tectonic.PlateGenerator;
import com.galacticodyssey.planet.tectonic.TectonicModel;

import java.util.Collections;
import java.util.List;

public final class PlanetTerrainSystem extends EntitySystem implements Disposable {
    private final btDiscreteDynamicsWorld dynamicsWorld;
    private TerrainQuadtree quadtree;
    private final Vector3 cameraPos = new Vector3();
    private float planetRadius;
    private final Vector3 planetCenter = new Vector3();

    public PlanetTerrainSystem(btDiscreteDynamicsWorld dynamicsWorld) {
        this.dynamicsWorld = dynamicsWorld;
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap) {
        loadPlanet(planet, biomeMap, new PlateGenerator().generate(planet), new Vector3(), -1f);
    }

    /**
     * Loads a planet into the terrain system using game-world-scale coordinates.
     *
     * @param gameWorldCenter  the planet's centre position in game-world units (metres)
     * @param gameWorldRadius  sphere radius in game-world units; ≤ 0 falls back to planet.radius * 6371
     */
    public void loadPlanet(Planet planet, BiomeMap biomeMap,
                           Vector3 gameWorldCenter, float gameWorldRadius) {
        loadPlanet(planet, biomeMap, new PlateGenerator().generate(planet),
                   gameWorldCenter, gameWorldRadius);
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap, TectonicModel tectonic) {
        loadPlanet(planet, biomeMap, tectonic, new Vector3(), -1f);
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap, TectonicModel tectonic,
                           Vector3 gameWorldCenter, float gameWorldRadius) {
        if (quadtree != null) quadtree.dispose();
        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        TerrainNoiseStack noise = new TerrainNoiseStack(terrainSeed, tectonic);
        planetRadius = (gameWorldRadius > 0f) ? gameWorldRadius : planet.radius * 6371f;
        planetCenter.set(gameWorldCenter);
        quadtree = new TerrainQuadtree(planetRadius, planetCenter, noise, biomeMap, dynamicsWorld);
    }

    public void unloadPlanet() {
        if (quadtree != null) { quadtree.dispose(); quadtree = null; }
    }

    public void setCameraPosition(Vector3 pos) {
        cameraPos.set(pos);
    }

    /** Convenience: translate a world-space camera position into planet-local space and store it. */
    public void setCameraPositionWorld(Vector3 worldPos) {
        cameraPos.set(worldPos).sub(planetCenter);
    }

    public float getPlanetRadius() {
        return planetRadius;
    }

    public Vector3 getPlanetCenter() {
        return planetCenter;
    }

    public List<TerrainChunk> getVisibleLeaves() {
        if (quadtree == null) return Collections.emptyList();
        return quadtree.getVisibleLeaves();
    }

    @Override
    public void update(float deltaTime) {
        if (quadtree != null) {
            quadtree.update(cameraPos);
        }
    }

    @Override
    public void dispose() {
        unloadPlanet();
    }
}
