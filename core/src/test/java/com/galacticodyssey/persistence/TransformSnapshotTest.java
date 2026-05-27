package com.galacticodyssey.persistence;

import com.galacticodyssey.persistence.snapshots.TransformSnapshot;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransformSnapshotTest {

    @Test
    void roundTripPreservesLocalPosition() {
        TransformComponent tc = new TransformComponent();
        tc.position.set(1.5f, 2.5f, 3.5f);
        tc.rotation.set(0f, 0.707f, 0f, 0.707f);

        TransformSnapshot snap = tc.takeSnapshot(0.0, 0.0, 0.0);
        assertEquals(1.5, snap.galaxyX, 1e-5);
        assertEquals(2.5, snap.galaxyY, 1e-5);
        assertEquals(3.5, snap.galaxyZ, 1e-5);

        TransformComponent restored = new TransformComponent();
        restored.restoreFromSnapshot(snap, 0.0, 0.0, 0.0);
        assertEquals(1.5f, restored.position.x, 1e-4f);
        assertEquals(2.5f, restored.position.y, 1e-4f);
        assertEquals(3.5f, restored.position.z, 1e-4f);
        assertEquals(0.707f, restored.rotation.y, 1e-3f);
    }

    @Test
    void roundTripWithOriginOffset() {
        TransformComponent tc = new TransformComponent();
        tc.position.set(5f, 10f, 15f);

        double offsetX = 1_000_000.0;
        double offsetY = 2_000_000.0;
        double offsetZ = 3_000_000.0;

        TransformSnapshot snap = tc.takeSnapshot(offsetX, offsetY, offsetZ);
        assertEquals(1_000_005.0, snap.galaxyX, 1e-5);
        assertEquals(2_000_010.0, snap.galaxyY, 1e-5);
        assertEquals(3_000_015.0, snap.galaxyZ, 1e-5);

        TransformComponent restored = new TransformComponent();
        double newOffsetX = 1_000_003.0;
        double newOffsetY = 2_000_008.0;
        double newOffsetZ = 3_000_012.0;
        restored.restoreFromSnapshot(snap, newOffsetX, newOffsetY, newOffsetZ);

        assertEquals(2.0f, restored.position.x, 1e-2f);
        assertEquals(2.0f, restored.position.y, 1e-2f);
        assertEquals(3.0f, restored.position.z, 1e-2f);
    }
}
