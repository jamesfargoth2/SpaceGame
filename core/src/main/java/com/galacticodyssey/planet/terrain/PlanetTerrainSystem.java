package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
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
    private PlanetCoordsKM cameraKm = PlanetCoordsKM.ORIGIN;
    private double radiusKm;
    private PlanetCoordsKM originPlanetKm = PlanetCoordsKM.ORIGIN;

    public PlanetTerrainSystem(btDiscreteDynamicsWorld dynamicsWorld) {
        this.dynamicsWorld = dynamicsWorld;
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap) {
        loadPlanet(planet, biomeMap, new PlateGenerator().generate(planet), PlanetCoordsKM.ORIGIN, -1.0);
    }

    /**
     * Loads a planet into the terrain system using planet-space kilometres.
     *
     * @param originKm    the floating-origin position in planet-space km
     * @param radiusKmArg sphere radius in km; ≤ 0 falls back to planet.radius * 6371
     */
    public void loadPlanet(Planet planet, BiomeMap biomeMap,
                           PlanetCoordsKM originKm, double radiusKmArg) {
        loadPlanet(planet, biomeMap, new PlateGenerator().generate(planet), originKm, radiusKmArg);
    }

    /** Legacy overload: world-space centre in metres, radius in metres. Converts to km. */
    public void loadPlanet(Planet planet, BiomeMap biomeMap,
                           Vector3 gameWorldCenter, float gameWorldRadius) {
        PlanetCoordsKM origin = new PlanetCoordsKM(
            gameWorldCenter.x * 0.001, gameWorldCenter.y * 0.001, gameWorldCenter.z * 0.001);
        double r = gameWorldRadius > 0f ? gameWorldRadius * 0.001 : planet.radius * 6371.0;
        loadPlanet(planet, biomeMap, new PlateGenerator().generate(planet), origin, r);
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap, TectonicModel tectonic) {
        loadPlanet(planet, biomeMap, tectonic, PlanetCoordsKM.ORIGIN, -1.0);
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap, TectonicModel tectonic,
                           PlanetCoordsKM originKm, double radiusKmArg) {
        if (quadtree != null) quadtree.dispose();
        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        TerrainNoiseStack noise = new TerrainNoiseStack(terrainSeed, tectonic);
        this.radiusKm = (radiusKmArg > 0.0) ? radiusKmArg : planet.radius * 6371.0;
        this.originPlanetKm = originKm;
        quadtree = new TerrainQuadtree(this.radiusKm, this.originPlanetKm, noise, biomeMap, dynamicsWorld);
    }

    /** Legacy overload: tectonic + game-world-space positions in metres. */
    public void loadPlanet(Planet planet, BiomeMap biomeMap, TectonicModel tectonic,
                           Vector3 gameWorldCenter, float gameWorldRadius) {
        PlanetCoordsKM origin = new PlanetCoordsKM(
            gameWorldCenter.x * 0.001, gameWorldCenter.y * 0.001, gameWorldCenter.z * 0.001);
        double r = gameWorldRadius > 0f ? gameWorldRadius * 0.001 : planet.radius * 6371.0;
        loadPlanet(planet, biomeMap, tectonic, origin, r);
    }

    public void unloadPlanet() {
        if (quadtree != null) { quadtree.dispose(); quadtree = null; }
    }

    /** Set camera in planet-space km. */
    public void setCameraPositionKm(PlanetCoordsKM km) {
        this.cameraKm = km;
    }

    /** Legacy: set camera position in world metres (relative to planet centre). */
    public void setCameraPosition(Vector3 pos) {
        this.cameraKm = new PlanetCoordsKM(pos.x * 0.001, pos.y * 0.001, pos.z * 0.001);
    }

    /** Convenience: translate a world-space camera position into planet-local space and store it. */
    public void setCameraPositionWorld(Vector3 worldPos) {
        // worldPos is metres relative to scene origin; originPlanetKm gives the scene-origin in planet space.
        // Convert metres -> km and add originPlanetKm to get full planet-space km.
        this.cameraKm = new PlanetCoordsKM(
            originPlanetKm.x() + worldPos.x * 0.001,
            originPlanetKm.y() + worldPos.y * 0.001,
            originPlanetKm.z() + worldPos.z * 0.001);
    }

    public double getRadiusKm() {
        return radiusKm;
    }

    /** Legacy accessor (returns metres). */
    public float getPlanetRadius() {
        return (float) (radiusKm * 1000.0);
    }

    public PlanetCoordsKM getOriginPlanetKm() {
        return originPlanetKm;
    }

    /** Legacy accessor: returns the floating-origin position as a Vector3 in metres (local scene). */
    public Vector3 getPlanetCenter() {
        // In the floating-origin frame the scene origin is (0,0,0); return zero as the "centre".
        return new Vector3(0, 0, 0);
    }

    /** Trigger a floating-origin rebase; updates all chunk placements in O(chunk count). */
    public void setOrigin(PlanetCoordsKM newOrigin) {
        this.originPlanetKm = newOrigin;
        if (quadtree != null) quadtree.setOrigin(newOrigin);
    }

    public List<TerrainChunk> getVisibleLeaves() {
        if (quadtree == null) return Collections.emptyList();
        return quadtree.getVisibleLeaves();
    }

    @Override
    public void update(float deltaTime) {
        if (quadtree != null) {
            quadtree.update(cameraKm);
        }
    }

    @Override
    public void dispose() {
        unloadPlanet();
    }
}
