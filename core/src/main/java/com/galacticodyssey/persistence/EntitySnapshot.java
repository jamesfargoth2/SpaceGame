package com.galacticodyssey.persistence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EntitySnapshot {
    public UUID entityId;
    public Map<String, Object> componentSnapshots = new HashMap<>();
    public Set<String> tagComponents = new HashSet<>();

    public EntitySnapshot() {}

    public EntitySnapshot(UUID entityId) {
        this.entityId = entityId;
    }

    public void putSnapshot(String componentType, Object snapshot) {
        componentSnapshots.put(componentType, snapshot);
    }

    public <T> T getSnapshot(String componentType, Class<T> snapshotClass) {
        Object raw = componentSnapshots.get(componentType);
        if (raw == null) return null;
        return snapshotClass.cast(raw);
    }

    public void addTag(String tagComponentType) {
        tagComponents.add(tagComponentType);
    }

    public boolean hasTag(String tagComponentType) {
        return tagComponents.contains(tagComponentType);
    }
}
