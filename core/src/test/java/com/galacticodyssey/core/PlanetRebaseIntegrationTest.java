package com.galacticodyssey.core;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.core.events.OriginRebasedEvent;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.terrain.PlanetTerrainSystem;
import com.galacticodyssey.planet.terrain.PlanetTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks the SP2 contract that floating-origin rebase fires while standing on a loaded planet
 * (see {@code GameWorld.update}, where the planet gate was removed). Wires {@link CoordinateManager}
 * and {@link PlanetTerrainSystem} together via the {@link EventBus} the same way GameWorld does, then
 * asserts that crossing the rebase threshold shifts the planet-space origin.
 */
class PlanetRebaseIntegrationTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    @Test
    void walkingPastThresholdRebasesPlanetOrigin() {
        EventBus bus = new EventBus();
        CoordinateManager coords = new CoordinateManager(bus);
        PlanetTerrainSystem terrain = new PlanetTerrainSystem(null);
        bus.subscribe(OriginRebasedEvent.class, e -> terrain.onOriginRebased(e.deltaX, e.deltaY, e.deltaZ));
        AtomicInteger rebases = new AtomicInteger();
        bus.subscribe(OriginRebasedEvent.class, e -> rebases.incrementAndGet());

        double radiusKm = PlanetTestSupport.earthLike().radius * PlanetTerrainSystem.EARTH_RADIUS_KM;
        BiomeMap biome = new BiomeMap(0L, 0f, 6000f, 0.5f, 293f, EnumSet.allOf(BiomeType.class));
        terrain.loadPlanet(PlanetTestSupport.earthLike(), biome, new PlanetCoordsKM(0, radiusKm, 0));

        PlanetCoordsKM originBefore = terrain.getOriginPlanetKm();
        coords.checkRebase(new Vector3(2000f, 0f, 0f)); // 2 km — past the 1000 m threshold

        assertTrue(rebases.get() >= 1, "crossing the threshold should fire a rebase on a planet");
        PlanetCoordsKM originAfter = terrain.getOriginPlanetKm();
        assertNotEquals(originBefore.x(), originAfter.x(), 1e-9, "planet-space origin should shift with the rebase");
    }
}
