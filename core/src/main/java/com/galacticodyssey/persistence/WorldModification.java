package com.galacticodyssey.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorldModification {
    public UUID systemId;
    public List<EntitySnapshot> addedEntities = new ArrayList<>();
    public List<UUID> removedEntityIds = new ArrayList<>();
    public List<EntitySnapshot> modifiedEntities = new ArrayList<>();

    public WorldModification() {}

    public WorldModification(UUID systemId) {
        this.systemId = systemId;
    }
}
