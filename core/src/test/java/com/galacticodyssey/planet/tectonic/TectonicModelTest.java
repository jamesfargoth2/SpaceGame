package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TectonicModelTest {

    // Two plates centered on the equator at +X-ish and -X-ish, boundary near +Z.
    private Vector3 boundaryDir() { return new Vector3(0, 0, 1).nor(); }

    @Test
    void plateAtReturnsNearestPlate() {
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), false, 0.3f, new Vector3(0,1,0), 0f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), false, 0.3f, new Vector3(0,1,0), 0f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        assertEquals(0, m.plateAt(new Vector3(1, 0, 0).nor()));
        assertEquals(1, m.plateAt(new Vector3(-1, 0, 0).nor()));
    }

    @Test
    void convergentBoundaryClassified() {
        // Both plates rotate so their surface velocities near +Z point toward each other.
        // Plate A center +X, pole +Y, +speed -> velocity at +Z is +X-ward... we instead
        // pick poles so motion closes the +Z boundary. Use poles +Y and -Y, both +speed:
        // vA at +Z = (Y x Z)=+X ; vB at +Z = (-Y x Z)=-X. n (B->A) ~ +X. vRel=vA-vB=+2X. normalComp>0 => divergent.
        // So to get convergence we flip B's speed sign.
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), false, 0.3f, new Vector3(0,1,0), 1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), false, 0.3f, new Vector3(0,1,0), -1f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        BoundaryQuery q = m.boundaryAt(boundaryDir());
        assertEquals(BoundaryType.CONVERGENT_OCEANIC, q.type);
        assertTrue(q.distanceNormalized < 0.2f, "boundary point should be close to the line");
    }

    @Test
    void convergentContinentalWhenBothContinental() {
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), false, 0.4f, new Vector3(0,1,0), 1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), false, 0.4f, new Vector3(0,1,0), 1f);
        // mark both continental
        Plate ac = new Plate(0, a.center, false, 0.4f, a.eulerPole, 1f);
        Plate bc = new Plate(1, b.center, false, 0.4f, b.eulerPole, -1f);
        TectonicModel m = new TectonicModel(List.of(continental(ac), continental(bc)), List.of(), TectonicConfig.defaults());
        assertEquals(BoundaryType.CONVERGENT_CONTINENTAL, m.boundaryAt(boundaryDir()).type);
    }

    @Test
    void divergentBoundaryClassified() {
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), true, -0.3f, new Vector3(0,1,0), 1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), true, -0.3f, new Vector3(0,1,0), 1f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        assertEquals(BoundaryType.DIVERGENT, m.boundaryAt(boundaryDir()).type);
    }

    @Test
    void interiorPointHasNoBoundary() {
        Plate a = new Plate(0, new Vector3(1, 0, 0), true, -0.3f, new Vector3(0,1,0), 1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0), true, -0.3f, new Vector3(0,1,0), -1f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        // Deep inside plate A (its center) the boundary is far -> NONE, distance saturated.
        BoundaryQuery q = m.boundaryAt(new Vector3(1, 0, 0).nor());
        assertEquals(BoundaryType.NONE, q.type);
        assertEquals(1f, q.distanceNormalized, 1e-4f);
    }

    // helper: rebuild a plate flagged continental (oceanic=false) — used for readability above
    private Plate continental(Plate p) {
        return new Plate(p.id, p.center, false, Math.abs(p.baseElevation), p.eulerPole, p.angularSpeed);
    }
}
