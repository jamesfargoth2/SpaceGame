package com.galacticodyssey.core.coords;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoordConvertTest {
    @Test
    void planetToLocalSubtractsInDoubleThenScalesToMetres() {
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 6371.0, 0);
        PlanetCoordsKM point  = new PlanetCoordsKM(0.001, 6371.0, 0.002);
        LocalCoordsM local = CoordConvert.planetToLocal(point, origin);
        assertEquals(1.0f, local.x(), 1e-3f);
        assertEquals(0.0f, local.y(), 1e-3f);
        assertEquals(2.0f, local.z(), 1e-3f);
    }

    @Test
    void roundTripPlanetLocalPlanet() {
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 6371.0, 0);
        PlanetCoordsKM point  = new PlanetCoordsKM(0.5, 6371.2, -0.3);
        LocalCoordsM local = CoordConvert.planetToLocal(point, origin);
        PlanetCoordsKM back = CoordConvert.localToPlanet(local, origin);
        assertEquals(point.x(), back.x(), 1e-4);
        assertEquals(point.y(), back.y(), 1e-4);
        assertEquals(point.z(), back.z(), 1e-4);
    }

    @Test
    void subtractInDoubleSurvivesLargeCoordinatesWhereFloatWouldFail() {
        // At 1,000,000 km the float ULP is ~125 m, so (float)1_000_000.0005 == (float)1_000_000.0.
        // A float-first subtraction would yield 0; double-first preserves the 0.5 m offset.
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 1_000_000.0, 0);
        PlanetCoordsKM point  = new PlanetCoordsKM(0, 1_000_000.0005, 0);
        // Sanity: prove the float-first path really would lose it (guards against a future "simplification").
        float floatFirst = ((float) point.y() - (float) origin.y()) * 1000f;
        assertEquals(0f, floatFirst, 0f, "precondition: float-first subtraction must lose the offset");
        // The real conversion must preserve it.
        LocalCoordsM local = CoordConvert.planetToLocal(point, origin);
        assertEquals(0.5f, local.y(), 1e-2f);
    }

    @Test
    void surfaceUpLocalIsRadialUnitVector() {
        PlanetCoordsKM atPole = new PlanetCoordsKM(0, 6371.0, 0);
        Vector3 up = CoordConvert.surfaceUpLocal(atPole);
        assertEquals(0f, up.x, 1e-6f);
        assertEquals(1f, up.y, 1e-6f);
        assertEquals(0f, up.z, 1e-6f);
        assertEquals(1f, up.len(), 1e-6f);
    }
}
