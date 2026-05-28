package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HullDesignTest {
    private HullDesign hull;

    @BeforeEach
    void setUp() {
        hull = new HullDesign();
        hull.spinePoints.add(new Vector3(0, 0, 0));
        hull.spinePoints.add(new Vector3(0, 0, 5));
        hull.spinePoints.add(new Vector3(0, 0, 10));
        hull.addCrossSection(new CrossSectionDef(0f, 1f, 1f, 2f));
        hull.addCrossSection(new CrossSectionDef(0.5f, 3f, 2f, 2.5f));
        hull.addCrossSection(new CrossSectionDef(1f, 1.5f, 1f, 2f));
    }

    @Test
    void estimateSpineLength_returnsChordSum() {
        assertEquals(10f, hull.estimateSpineLength(), 0.01f);
    }

    @Test
    void estimateMaxWidth_returnsLargestCrossSection() {
        assertEquals(3f, hull.estimateMaxWidth(), 0.01f);
    }

    @Test
    void addSpinePoint_insertsAtIndex() {
        hull.addSpinePoint(1, new Vector3(0, 1, 2.5f));
        assertEquals(4, hull.spinePoints.size());
        assertEquals(2.5f, hull.spinePoints.get(1).z, 0.01f);
    }

    @Test
    void removeSpinePoint_throwsAtMinimum() {
        hull.removeSpinePoint(1);
        assertThrows(IllegalStateException.class, () -> hull.removeSpinePoint(0));
    }

    @Test
    void addCrossSection_maintainsSortByT() {
        hull.addCrossSection(new CrossSectionDef(0.25f, 2f, 1.5f, 2f));
        assertEquals(0f, hull.crossSections.get(0).t, 0.001f);
        assertEquals(0.25f, hull.crossSections.get(1).t, 0.001f);
        assertEquals(0.5f, hull.crossSections.get(2).t, 0.001f);
    }

    @Test
    void removeCrossSection_throwsAtMinimum() {
        hull.removeCrossSection(0);
        assertThrows(IllegalStateException.class, () -> hull.removeCrossSection(0));
    }

    @Test
    void countWingPairs_countsWingTypes() {
        hull.addAppendage(new AppendageDef(AppendageDef.AppendageType.SWEPT_WING, 0.4f, AppendageDef.Side.BOTH, 1f));
        hull.addAppendage(new AppendageDef(AppendageDef.AppendageType.ENGINE_POD, 0.8f, AppendageDef.Side.BOTH, 1f));
        assertEquals(1, hull.countWingPairs());
        assertEquals(1, hull.countEnginePods());
    }

    @Test
    void copy_isDeepCopy() {
        HullDesign copy = hull.copy();
        copy.spinePoints.get(0).set(99, 99, 99);
        assertEquals(0, hull.spinePoints.get(0).x, 0.01f);
    }
}
