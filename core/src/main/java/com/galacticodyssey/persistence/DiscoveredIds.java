package com.galacticodyssey.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Thin wrapper used by {@link SaveWriter} / {@link SaveReader} to persist
 * discovered system and planet UUID sets as a single registered Kryo object.
 * <p>
 * Stored as {@code ArrayList<UUID>} internally so Kryo only needs types that
 * are already registered in {@link KryoRegistrar} (UUID at ID 10,
 * ArrayList at ID 11).
 */
public class DiscoveredIds {

    /** Discovered star-system IDs. */
    public ArrayList<UUID> systemIds = new ArrayList<>();

    /** Discovered planet IDs. */
    public ArrayList<UUID> planetIds = new ArrayList<>();

    /** No-arg constructor required by Kryo. */
    public DiscoveredIds() {}

    public DiscoveredIds(ArrayList<UUID> systemIds, ArrayList<UUID> planetIds) {
        this.systemIds = systemIds;
        this.planetIds = planetIds;
    }
}
