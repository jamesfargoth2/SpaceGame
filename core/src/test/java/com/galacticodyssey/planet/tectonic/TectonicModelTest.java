package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TectonicModelTest {

    // Plate A near +X, plate B near -X, shared boundary near +Z. Both use Euler pole +Y.
    // velocityAt the +Z point: pole +Y with +speed -> +X ; with -speed -> -X.
    // CONVERGENT (plates close on the boundary): A moves -X (toward boundary) AND B moves +X
    //   -> A has -speed, B has +speed.
    // DIVERGENT (plates pull apart): A moves +X (toward its own center) AND B moves -X
    //   -> A has +speed, B has -speed.
    // The boundary normal n points from B's center toward A's center (~ +X here), so
    // vRel = vA - vB has normalComp < 0 for the convergent case and > 0 for the divergent case.
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
    void convergentOceanicWhenSubducting() {
        // Both oceanic, plates closing on the +Z boundary -> subduction.
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), true, -0.3f, new Vector3(0,1,0), -1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), true, -0.3f, new Vector3(0,1,0), 1f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        BoundaryQuery q = m.boundaryAt(boundaryDir());
        assertEquals(BoundaryType.CONVERGENT_OCEANIC, q.type);
        assertTrue(q.distanceNormalized < 0.2f, "boundary point should be close to the line");
    }

    @Test
    void convergentContinentalWhenBothContinental() {
        // Both continental, plates closing -> mountain collision.
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), false, 0.4f, new Vector3(0,1,0), -1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), false, 0.4f, new Vector3(0,1,0), 1f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        assertEquals(BoundaryType.CONVERGENT_CONTINENTAL, m.boundaryAt(boundaryDir()).type);
    }

    @Test
    void divergentBoundaryClassified() {
        // Plates moving apart at the +Z boundary -> spreading.
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), true, -0.3f, new Vector3(0,1,0), 1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), true, -0.3f, new Vector3(0,1,0), -1f);
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
}
