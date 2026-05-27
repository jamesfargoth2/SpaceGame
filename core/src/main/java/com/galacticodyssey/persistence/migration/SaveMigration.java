package com.galacticodyssey.persistence.migration;

import com.galacticodyssey.persistence.SaveBundle;

/** A single schema migration step that transforms a {@link SaveBundle} from one version to the next. */
public interface SaveMigration {
    int fromVersion();
    int toVersion();
    void migrate(SaveBundle bundle);
}
