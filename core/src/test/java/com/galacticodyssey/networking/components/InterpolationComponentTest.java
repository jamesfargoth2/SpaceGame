package com.galacticodyssey.networking.components;

import com.galacticodyssey.networking.interpolation.EntitySnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InterpolationComponentTest {

    @Test
    void defaultsAreEmpty() {
        InterpolationComponent ic = new InterpolationComponent();
        assertEquals(0, ic.getSnapshotBuffer().size());
        assertEquals(0f, ic.extrapolationTimer);
        assertFalse(ic.frozen);
    }

    @Test
    void addSnapshotGoesIntoBuffer() {
        InterpolationComponent ic = new InterpolationComponent();
        ic.getSnapshotBuffer().add(
                new EntitySnapshot(1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0));
        assertEquals(1, ic.getSnapshotBuffer().size());
    }

    @Test
    void extrapolationMaxIsHalfSecond() {
        assertEquals(0.5f, InterpolationComponent.MAX_EXTRAPOLATION_SECONDS, 1e-6f);
    }

    @Test
    void blendFramesCountIsFive() {
        assertEquals(5, InterpolationComponent.BLEND_FRAMES);
    }
}
