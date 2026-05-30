package com.galacticodyssey.core.coords;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanetCoordsKMTest {
    @Test
    void subAndLenComputeDistanceInKm() {
        PlanetCoordsKM a = new PlanetCoordsKM(6371.0, 0, 0);
        PlanetCoordsKM b = new PlanetCoordsKM(6370.0, 0, 0);
        assertEquals(1.0, a.sub(b).len(), 1e-9);
    }

    @Test
    void dstMatchesManualDistance() {
        PlanetCoordsKM a = new PlanetCoordsKM(0, 6371.0, 0);
        PlanetCoordsKM b = new PlanetCoordsKM(0, 6371.0, 3.0);
        assertEquals(3.0, a.dst(b), 1e-9);
    }

    @Test
    void norProducesUnitLength() {
        PlanetCoordsKM n = new PlanetCoordsKM(0, 6371.0, 0).nor();
        assertEquals(1.0, n.len(), 1e-12);
        assertEquals(1.0, n.y(), 1e-12);
    }
}
