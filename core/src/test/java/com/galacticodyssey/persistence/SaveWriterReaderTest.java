package com.galacticodyssey.persistence;

import com.galacticodyssey.persistence.snapshots.HealthSnapshot;
import com.galacticodyssey.persistence.snapshots.TransformSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SaveWriterReaderTest {

    @TempDir
    File tempDir;

    @Test
    void roundTripSaveBundle() {
        SaveBundle original = new SaveBundle();
        original.manifest = new ManifestData("test", 12345L,
            UUID.randomUUID(), UUID.randomUUID());

        EntitySnapshot playerSnap = new EntitySnapshot(original.manifest.playerEntityId);
        HealthSnapshot hs = new HealthSnapshot();
        hs.currentHP = 80f;
        hs.maxHP = 100f;
        hs.alive = true;
        playerSnap.putSnapshot("Health", hs);

        TransformSnapshot ts = new TransformSnapshot(1e6, 2e6, 3e6, 0, 0, 0, 1);
        playerSnap.putSnapshot("Transform", ts);
        original.playerSnapshot = playerSnap;

        UUID systemId = UUID.randomUUID();
        EntitySnapshot npcSnap = new EntitySnapshot(UUID.randomUUID());
        npcSnap.addTag("HostileTagComponent");
        original.systemSnapshots.put(systemId, List.of(npcSnap));

        File saveDir = new File(tempDir, "test-save");

        SaveWriter writer = new SaveWriter();
        writer.write(original, saveDir);

        assertTrue(new File(saveDir, "manifest.bin").exists());
        assertTrue(new File(saveDir, "player.bin").exists());

        SaveReader reader = new SaveReader();
        SaveBundle restored = reader.read(saveDir);

        assertEquals("test", restored.manifest.saveName);
        assertEquals(12345L, restored.manifest.galaxySeed);
        assertEquals(original.manifest.playerEntityId, restored.manifest.playerEntityId);

        HealthSnapshot rhs = restored.playerSnapshot.getSnapshot("Health", HealthSnapshot.class);
        assertEquals(80f, rhs.currentHP);

        TransformSnapshot rts = restored.playerSnapshot.getSnapshot("Transform", TransformSnapshot.class);
        assertEquals(1e6, rts.galaxyX, 1e-5);

        assertTrue(restored.systemSnapshots.containsKey(systemId));
        assertEquals(1, restored.systemSnapshots.get(systemId).size());
        assertTrue(restored.systemSnapshots.get(systemId).get(0).hasTag("HostileTagComponent"));
    }
}
