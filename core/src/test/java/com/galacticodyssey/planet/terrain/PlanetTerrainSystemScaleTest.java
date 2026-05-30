package com.galacticodyssey.planet.terrain;

import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class PlanetTerrainSystemScaleTest {
    private Planet earthLike() {
        return new Planet(123L, PlanetType.TERRAN, 1.0f, 1.0f, 5.5f, 24f, 23f, false);
    }

    private BiomeMap biomeMap(long seed) {
        return new BiomeMap(seed, 0.2f, 0.8f, 0.5f, 288f, EnumSet.allOf(BiomeType.class));
    }

    @Test
    void radiusKmDerivesFromEarthRadii() {
        PlanetTerrainSystem sys = new PlanetTerrainSystem(null);
        sys.loadPlanet(earthLike(), biomeMap(123L), new PlanetCoordsKM(0, 6371.0, 0));
        assertEquals(6371.0, sys.getRadiusKm(), 1e-6);
    }

    @Test
    void moonScaleIsHundredsOfKm() {
        Planet moon = new Planet(9L, PlanetType.BARREN, 0.05f, 0.01f, 3.0f, 100f, 5f, false);
        PlanetTerrainSystem sys = new PlanetTerrainSystem(null);
        sys.loadPlanet(moon, biomeMap(9L), new PlanetCoordsKM(0, 0.05 * 6371.0, 0));
        // Expected uses the SAME float radius the system reads (0.05f != 0.05 in double).
        assertEquals(moon.radius * PlanetTerrainSystem.EARTH_RADIUS_KM, sys.getRadiusKm(), 1e-6);
        assertEquals(318.55, sys.getRadiusKm(), 0.01); // sanity: hundreds of km
    }

    @Test
    void cameraLocalIsConvertedToPlanetKm() {
        PlanetTerrainSystem sys = new PlanetTerrainSystem(null);
        sys.loadPlanet(earthLike(), biomeMap(123L), new PlanetCoordsKM(0, 6371.0, 0));
        sys.setCameraPositionLocal(new com.badlogic.gdx.math.Vector3(0, 10f, 0));
        PlanetCoordsKM cam = sys.getCameraPlanetKm();
        assertEquals(0.0, cam.x(), 1e-9);
        assertEquals(6371.0 + 0.010, cam.y(), 1e-9);
        assertEquals(0.0, cam.z(), 1e-9);
    }
}
