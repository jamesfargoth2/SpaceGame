package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;

public final class PlanetTerrainSystem extends EntitySystem implements Disposable {
    private TerrainQuadtree quadtree;
    private final Vector3 cameraPos = new Vector3();
    private btDiscreteDynamicsWorld dynamicsWorld;

    public void setDynamicsWorld(btDiscreteDynamicsWorld dynamicsWorld) {
        this.dynamicsWorld = dynamicsWorld;
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap) {
        if (quadtree != null) quadtree.dispose();
        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        TerrainNoiseStack noise = new TerrainNoiseStack(terrainSeed);
        float radiusKm = planet.radius * 6371f;
        quadtree = new TerrainQuadtree(radiusKm, noise, biomeMap, dynamicsWorld);
    }

    public void unloadPlanet() {
        if (quadtree != null) { quadtree.dispose(); quadtree = null; }
    }

    public void setCameraPosition(Vector3 pos) {
        cameraPos.set(pos);
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
