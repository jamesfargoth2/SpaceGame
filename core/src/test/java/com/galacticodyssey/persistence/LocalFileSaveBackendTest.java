package com.galacticodyssey.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileSaveBackendTest {

    @TempDir
    File tempDir;

    @Test
    void writeAndReadSave() {
        LocalFileSaveBackend backend = new LocalFileSaveBackend(tempDir);

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("my-save", 42L,
            UUID.randomUUID(), UUID.randomUUID());
        bundle.playerSnapshot = new EntitySnapshot(bundle.manifest.playerEntityId);

        backend.writeSave("my-save", bundle);

        SaveBundle restored = backend.readSave("my-save");
        assertEquals("my-save", restored.manifest.saveName);
        assertEquals(42L, restored.manifest.galaxySeed);
    }

    @Test
    void listSaves() {
        LocalFileSaveBackend backend = new LocalFileSaveBackend(tempDir);

        SaveBundle b1 = new SaveBundle();
        b1.manifest = new ManifestData("save-a", 1L, UUID.randomUUID(), UUID.randomUUID());
        b1.playerSnapshot = new EntitySnapshot(b1.manifest.playerEntityId);
        backend.writeSave("save-a", b1);

        SaveBundle b2 = new SaveBundle();
        b2.manifest = new ManifestData("save-b", 2L, UUID.randomUUID(), UUID.randomUUID());
        b2.playerSnapshot = new EntitySnapshot(b2.manifest.playerEntityId);
        backend.writeSave("save-b", b2);

        List<ManifestData> saves = backend.listSaves();
        assertEquals(2, saves.size());
    }

    @Test
    void deleteSave() {
        LocalFileSaveBackend backend = new LocalFileSaveBackend(tempDir);

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("doomed", 0L, UUID.randomUUID(), UUID.randomUUID());
        bundle.playerSnapshot = new EntitySnapshot(bundle.manifest.playerEntityId);
        backend.writeSave("doomed", bundle);

        backend.deleteSave("doomed");
        assertEquals(0, backend.listSaves().size());
    }

    @Test
    void copySaveCreatesIdenticalCopy() {
        LocalFileSaveBackend backend = new LocalFileSaveBackend(tempDir);

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("original", 42L, UUID.randomUUID(), UUID.randomUUID());
        bundle.playerSnapshot = new EntitySnapshot(bundle.manifest.playerEntityId);
        backend.writeSave("original", bundle);

        backend.copySave("original", "copy-of-original");

        SaveBundle copied = backend.readSave("copy-of-original");
        assertEquals("original", copied.manifest.saveName);
        assertTrue(new File(tempDir, "copy-of-original").exists());
    }

    @Test
    void readManifestOnlyReturnsManifestWithoutFullLoad() {
        LocalFileSaveBackend backend = new LocalFileSaveBackend(tempDir);

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("quick-read", 42L, UUID.randomUUID(), UUID.randomUUID());
        bundle.playerSnapshot = new EntitySnapshot(bundle.manifest.playerEntityId);
        backend.writeSave("quick-read", bundle);

        ManifestData manifest = backend.readManifestOnly("quick-read");
        assertEquals("quick-read", manifest.saveName);
    }
}
