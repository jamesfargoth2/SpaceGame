package com.galacticodyssey.persistence;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

/**
 * Deserializes a save folder written by {@link SaveWriter} back into a
 * {@link SaveBundle}.
 * <p>
 * The reader is tolerant of optional files that were omitted by the writer
 * (e.g. empty ship or modification lists). Missing optional files leave the
 * corresponding {@code SaveBundle} collection at its default empty state.
 */
public class SaveReader {

    private final Kryo kryo;

    public SaveReader() {
        this.kryo = new Kryo();
        KryoRegistrar.register(kryo);
    }

    /**
     * Reads the save folder at {@code saveDir} and returns a populated
     * {@link SaveBundle}.
     *
     * @param saveDir directory previously written by {@link SaveWriter}
     * @return a fully populated {@code SaveBundle}
     * @throws RuntimeException wrapping any I/O or deserialization failure
     */
    public SaveBundle read(File saveDir) {
        SaveBundle bundle = new SaveBundle();

        // Required files
        bundle.manifest = readObject(new File(saveDir, "manifest.bin"), ManifestData.class);
        bundle.playerSnapshot = readObject(new File(saveDir, "player.bin"), EntitySnapshot.class);

        // Optional: owned ships
        File shipsFile = new File(saveDir, "ships.bin");
        if (shipsFile.exists()) {
            @SuppressWarnings("unchecked")
            ArrayList<EntitySnapshot> ships = readObject(shipsFile, ArrayList.class);
            bundle.ownedShipSnapshots = ships;
        }

        // Optional: per-system entity snapshots
        File[] systemFiles = saveDir.listFiles(
            (dir, name) -> name.startsWith("system_") && name.endsWith(".bin"));
        if (systemFiles != null) {
            for (File sf : systemFiles) {
                String uuidStr = sf.getName()
                    .replace("system_", "")
                    .replace(".bin", "");
                UUID systemId = UUID.fromString(uuidStr);
                @SuppressWarnings("unchecked")
                ArrayList<EntitySnapshot> entities = readObject(sf, ArrayList.class);
                bundle.systemSnapshots.put(systemId, entities);
            }
        }

        // Optional: world modifications
        File modsFile = new File(saveDir, "modifications.bin");
        if (modsFile.exists()) {
            @SuppressWarnings("unchecked")
            HashMap<UUID, WorldModification> mods = readObject(modsFile, HashMap.class);
            bundle.worldModifications = mods;
        }

        // Optional: economy state
        File econFile = new File(saveDir, "economy.bin");
        if (econFile.exists()) {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> econ = readObject(econFile, HashMap.class);
            bundle.economyState = econ;
        }

        // Optional: faction state
        File factionFile = new File(saveDir, "factions.bin");
        if (factionFile.exists()) {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> factions = readObject(factionFile, HashMap.class);
            bundle.factionState = factions;
        }

        // Optional: discovered IDs
        File discoveredFile = new File(saveDir, "discovered.bin");
        if (discoveredFile.exists()) {
            DiscoveredIds discovered = readObject(discoveredFile, DiscoveredIds.class);
            bundle.discoveredSystemIds = new HashSet<>(discovered.systemIds);
            bundle.discoveredPlanetIds = new HashSet<>(discovered.planetIds);
        }

        return bundle;
    }

    /**
     * Reads only the manifest from a save folder's {@code manifest.bin} file,
     * without loading any other save data.
     *
     * @param manifestFile the {@code manifest.bin} file to read
     * @return the deserialized {@link ManifestData}
     * @throws RuntimeException wrapping any I/O or deserialization failure
     */
    public ManifestData readManifest(File manifestFile) {
        try (com.esotericsoftware.kryo.io.Input input =
                 new com.esotericsoftware.kryo.io.Input(new java.io.FileInputStream(manifestFile))) {
            return kryo.readObject(input, ManifestData.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read manifest: " + manifestFile, e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private <T> T readObject(File file, Class<T> type) {
        try (Input input = new Input(new FileInputStream(file))) {
            return kryo.readObject(input, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + file.getName(), e);
        }
    }
}
