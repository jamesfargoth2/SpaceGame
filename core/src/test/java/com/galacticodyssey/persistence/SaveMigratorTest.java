package com.galacticodyssey.persistence;

import com.galacticodyssey.persistence.migration.SaveMigration;
import com.galacticodyssey.persistence.migration.SaveMigrator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SaveMigratorTest {

    @Test
    void migratesFromOldVersion() {
        SaveMigrator migrator = new SaveMigrator();
        migrator.addMigration(new SaveMigration() {
            @Override public int fromVersion() { return 1; }
            @Override public int toVersion() { return 2; }
            @Override public void migrate(SaveBundle bundle) {
                bundle.manifest.saveName = bundle.manifest.saveName + "-migrated";
            }
        });

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("old-save", 0L, UUID.randomUUID(), UUID.randomUUID());
        bundle.manifest.saveVersion = 1;

        migrator.migrateToCurrentVersion(bundle, 2);

        assertEquals(2, bundle.manifest.saveVersion);
        assertEquals("old-save-migrated", bundle.manifest.saveName);
    }

    @Test
    void chainsMultipleMigrations() {
        SaveMigrator migrator = new SaveMigrator();
        migrator.addMigration(new SaveMigration() {
            @Override public int fromVersion() { return 1; }
            @Override public int toVersion() { return 2; }
            @Override public void migrate(SaveBundle bundle) {
                bundle.manifest.saveName = bundle.manifest.saveName + "-v2";
            }
        });
        migrator.addMigration(new SaveMigration() {
            @Override public int fromVersion() { return 2; }
            @Override public int toVersion() { return 3; }
            @Override public void migrate(SaveBundle bundle) {
                bundle.manifest.saveName = bundle.manifest.saveName + "-v3";
            }
        });

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("test", 0L, UUID.randomUUID(), UUID.randomUUID());
        bundle.manifest.saveVersion = 1;

        migrator.migrateToCurrentVersion(bundle, 3);

        assertEquals(3, bundle.manifest.saveVersion);
        assertEquals("test-v2-v3", bundle.manifest.saveName);
    }

    @Test
    void rejectsFutureSaveVersion() {
        SaveMigrator migrator = new SaveMigrator();

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("future", 0L, UUID.randomUUID(), UUID.randomUUID());
        bundle.manifest.saveVersion = 99;

        assertThrows(IllegalArgumentException.class, () ->
            migrator.migrateToCurrentVersion(bundle, 1));
    }

    @Test
    void noOpIfAlreadyCurrent() {
        SaveMigrator migrator = new SaveMigrator();

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("current", 0L, UUID.randomUUID(), UUID.randomUUID());
        bundle.manifest.saveVersion = 1;

        migrator.migrateToCurrentVersion(bundle, 1);
        assertEquals("current", bundle.manifest.saveName);
    }
}
