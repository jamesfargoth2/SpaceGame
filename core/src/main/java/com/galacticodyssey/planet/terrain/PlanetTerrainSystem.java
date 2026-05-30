package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.coords.CoordConvert;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.tectonic.PlateGenerator;
import com.galacticodyssey.planet.tectonic.TectonicModel;

import java.util.Collections;
import java.util.List;

public final class PlanetTerrainSystem extends EntitySystem implements Disposable {
    public static final double EARTH_RADIUS_KM = 6371.0;

    private final btDiscreteDynamicsWorld dynamicsWorld;
    private TerrainQuadtree quadtree;
    private double radiusKm;
    private PlanetCoordsKM originPlanetKm = PlanetCoordsKM.ORIGIN;
    private final Vector3 cameraLocal = new Vector3();
    /**
     * Cached planet centre in the current LOCAL frame — updated only on loadPlanet/onOriginRebased.
     * Shared/read-only: callers must not mutate the returned instance (see getPlanetCenterLocal()).
     */
    private final Vector3 planetCenterLocal = new Vector3();

    public PlanetTerrainSystem(btDiscreteDynamicsWorld dynamicsWorld) {
        this.dynamicsWorld = dynamicsWorld;
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap, PlanetCoordsKM originPlanetKm) {
        loadPlanet(planet, biomeMap, new PlateGenerator().generate(planet), originPlanetKm);
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap, TectonicModel tectonic,
                           PlanetCoordsKM originPlanetKm) {
        if (quadtree != null) quadtree.dispose();
        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        TerrainNoiseStack noise = new TerrainNoiseStack(terrainSeed, tectonic);
        this.radiusKm = planet.radius * EARTH_RADIUS_KM;
        this.originPlanetKm = originPlanetKm;
        recomputePlanetCenterLocal();
        quadtree = new TerrainQuadtree(radiusKm, originPlanetKm, noise, biomeMap, dynamicsWorld);
    }

    public void unloadPlanet() {
        if (quadtree != null) { quadtree.dispose(); quadtree = null; }
    }

    /** Player/camera position in the current LOCAL_SCENE (metres). */
    public void setCameraPositionLocal(Vector3 localMetres) { cameraLocal.set(localMetres); }

    public PlanetCoordsKM getCameraPlanetKm() {
        // Inline conversion: avoids allocating an intermediate LocalCoordsM wrapper.
        return new PlanetCoordsKM(
            originPlanetKm.x() + cameraLocal.x * CoordConvert.M_TO_KM,
            originPlanetKm.y() + cameraLocal.y * CoordConvert.M_TO_KM,
            originPlanetKm.z() + cameraLocal.z * CoordConvert.M_TO_KM);
    }

    /** Apply a floating-origin rebase (metre deltas) — shift the planet-space origin. */
    public void onOriginRebased(float dxM, float dyM, float dzM) {
        originPlanetKm = new PlanetCoordsKM(
            originPlanetKm.x() + dxM * CoordConvert.M_TO_KM,
            originPlanetKm.y() + dyM * CoordConvert.M_TO_KM,
            originPlanetKm.z() + dzM * CoordConvert.M_TO_KM);
        recomputePlanetCenterLocal();
        if (quadtree != null) quadtree.setOrigin(originPlanetKm);
    }

    public double getRadiusKm() { return radiusKm; }
    /** Planet radius in LOCAL metres (for far-plane / fade scaling). */
    public float getRadiusLocalMetres() { return (float) (radiusKm * CoordConvert.KM_TO_M); }
    public PlanetCoordsKM getOriginPlanetKm() { return originPlanetKm; }

    /** Planet centre in the current LOCAL frame (large magnitude — fade/far-plane use only, NOT meshes).
     *  Returns the shared cached instance — callers must not mutate it (both known call sites only read/copy). */
    public Vector3 getPlanetCenterLocal() {
        return planetCenterLocal;
    }

    /** Recompute {@link #planetCenterLocal} from the current {@code originPlanetKm}.
     *  Call whenever {@code originPlanetKm} changes (loadPlanet, onOriginRebased). */
    private void recomputePlanetCenterLocal() {
        // Planet centre is at PlanetCoordsKM.ORIGIN (0,0,0); convert to local metres.
        // Equivalent to CoordConvert.planetToLocal(ORIGIN, originPlanetKm) inlined to avoid allocation.
        planetCenterLocal.set(
            (float) (-originPlanetKm.x() * CoordConvert.KM_TO_M),
            (float) (-originPlanetKm.y() * CoordConvert.KM_TO_M),
            (float) (-originPlanetKm.z() * CoordConvert.KM_TO_M));
    }

    public List<TerrainChunk> getVisibleLeaves() {
        if (quadtree == null) return Collections.emptyList();
        return quadtree.getVisibleLeaves();
    }

    @Override
    public void update(float deltaTime) {
        if (quadtree != null) quadtree.update(getCameraPlanetKm());
    }

    @Override
    public void dispose() { unloadPlanet(); }
}
