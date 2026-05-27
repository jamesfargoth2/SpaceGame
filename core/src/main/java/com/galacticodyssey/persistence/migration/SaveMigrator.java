package com.galacticodyssey.persistence.migration;

import com.galacticodyssey.persistence.SaveBundle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs registered {@link SaveMigration} steps in version order to bring an old
 * {@link SaveBundle} up to the current schema version.
 */
public class SaveMigrator {
    private final List<SaveMigration> migrations = new ArrayList<>();

    public void addMigration(SaveMigration migration) {
        migrations.add(migration);
        migrations.sort(Comparator.comparingInt(SaveMigration::fromVersion));
    }

    public void migrateToCurrentVersion(SaveBundle bundle, int currentVersion) {
        int saveVersion = bundle.manifest.saveVersion;

        if (saveVersion > currentVersion) {
            throw new IllegalArgumentException(
                "Save version " + saveVersion + " is newer than game version " + currentVersion
                + ". Cannot load saves from a newer build.");
        }

        while (bundle.manifest.saveVersion < currentVersion) {
            int fromVersion = bundle.manifest.saveVersion;
            SaveMigration migration = findMigration(fromVersion);
            if (migration == null) {
                throw new IllegalStateException(
                    "No migration found from version " + fromVersion);
            }
            migration.migrate(bundle);
            bundle.manifest.saveVersion = migration.toVersion();
        }
    }

    private SaveMigration findMigration(int fromVersion) {
        for (SaveMigration m : migrations) {
            if (m.fromVersion() == fromVersion) return m;
        }
        return null;
    }
}
