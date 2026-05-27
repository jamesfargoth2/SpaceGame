package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Component;
import java.util.UUID;

public class PersistenceIdComponent implements Component {
    public final UUID uuid;

    public PersistenceIdComponent() {
        this.uuid = UUID.randomUUID();
    }

    public PersistenceIdComponent(UUID uuid) {
        this.uuid = uuid;
    }
}
