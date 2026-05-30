package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.coords.CoordConvert;
import com.galacticodyssey.core.coords.LocalCoordsM;
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
    /** Maximum quadtree LOD level — used by surfaceRadiusKmAt for full-detail height queries. */
    public static final int MAX_LOD = TerrainChunk.MAX_DEPTH;

    private final btDiscreteDynamicsWorld dynamicsWorld;
    private TerrainQuadtree quadtree;
    private double radiusKm;
    private PlanetCoordsKM originPlanetKm = PlanetCoordsKM.ORIGIN;
    /** Noise stack retained so surface-height queries work without a live quadtree. Null until loadPlanet. */
    private TerrainNoiseStack noise;
    /** BiomeMap retained alongside noise for surface-height queries. Null until loadPlanet. */
    private BiomeMap loadedBiomeMap;
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
        this.noise = new TerrainNoiseStack(terrainSeed, tectonic);
        this.loadedBiomeMap = biomeMap;
        this.radiusKm = planet.radius * EARTH_RADIUS_KM;
        this.originPlanetKm = originPlanetKm;
        recomputePlanetCenterLocal();
        quadtree = new TerrainQuadtree(radiusKm, originPlanetKm, noise, biomeMap, dynamicsWorld);
    }

    public void unloadPlanet() {
        if (quadtree != null) { quadtree.dispose(); quadtree = null; }
        noise = null;
        loadedBiomeMap = null;
        radiusKm = 0; // no planet loaded → surfaceRadiusKmAt returns 0, matching the null-guard contract
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

    /** Surface radius in km (including terrain height) at a unit direction from the planet centre.
     *  Returns {@code radiusKm} when no planet is loaded. */
    public double surfaceRadiusKmAt(Vector3 unitDir) {
        if (noise == null) return radiusKm;
        float h = noise.heightAt(unitDir, loadedBiomeMap, MAX_LOD);
        return radiusKm + h * radiusKm * 0.002;
    }

    /** Surface point in the current LOCAL frame (metres) along the given unit direction. */
    public LocalCoordsM surfacePointLocal(Vector3 unitDir) {
        double rKm = surfaceRadiusKmAt(unitDir);
        PlanetCoordsKM p = new PlanetCoordsKM(
            unitDir.x * rKm, unitDir.y * rKm, unitDir.z * rKm);
        return CoordConvert.planetToLocal(p, originPlanetKm);
    }

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

    public java.util.List<TerrainChunk> getRenderableLeaves() {
        if (quadtree == null) return java.util.Collections.emptyList();
        return quadtree.getRenderableLeaves();
    }

    /**
     * Synchronously subdivide and build the chunk chain under a spawn direction down to near-max
     * depth, draining the streamer until those leaves are READY, so a collision body exists
     * before the player is placed (prevents fall-through while async streaming catches up).
     *
     * <p>This is a SETTLE LOOP, not a fixed number of passes: {@code quadtree.pump()} is
     * non-blocking (it drains queued results but does not wait on still-running workers), so a
     * single update+pump does not guarantee the just-submitted chunks are built. We repeatedly
     * feed the synthetic spawn camera and pump until a deep READY leaf exists under it, bounded
     * by {@code maxPasses} so a stalled streamer cannot hang the caller.
     */
    public void prewarmAround(Vector3 unitDir) {
        if (quadtree == null) return;
        double rKm = surfaceRadiusKmAt(unitDir);
        PlanetCoordsKM camKm = new PlanetCoordsKM(unitDir.x * rKm, unitDir.y * rKm, unitDir.z * rKm);
        final int maxPasses = 400;
        for (int pass = 0; pass < maxPasses; pass++) {
            quadtree.update(camKm);
            quadtree.pump();
            boolean ready = getVisibleLeaves().stream().anyMatch(c ->
                !c.hasChildren() && c.depth >= TerrainChunk.MAX_DEPTH - 2
                && c.state == TerrainChunk.State.READY);
            if (ready) return;
            try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    @Override
    public void update(float deltaTime) {
        if (quadtree != null) quadtree.update(getCameraPlanetKm());
    }

    @Override
    public void dispose() { unloadPlanet(); }
}
