package com.galacticodyssey.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SaveBundle {
    public ManifestData manifest;
    public EntitySnapshot playerSnapshot;
    public List<EntitySnapshot> ownedShipSnapshots = new ArrayList<>();
    public Map<UUID, List<EntitySnapshot>> systemSnapshots = new HashMap<>();
    public Map<UUID, WorldModification> worldModifications = new HashMap<>();
    public Map<String, Object> economyState = new HashMap<>();
    public Map<String, Object> factionState = new HashMap<>();
    public Set<UUID> discoveredSystemIds = new HashSet<>();
    public Set<UUID> discoveredPlanetIds = new HashSet<>();
    public List<UUID> recentSystemIds = new ArrayList<>();

    public SaveBundle() {}
}
