package com.galacticodyssey.persistence;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serializes a {@link SaveBundle} to a folder of {@code .bin} files using Kryo.
 * <p>
 * Writes are atomic: data goes to a temp folder first, then that folder is
 * renamed over the destination once all files are flushed. If any write fails
 * the temp folder is deleted and the original save (if present) is untouched.
 * <p>
 * File layout:
 * <pre>
 *   manifest.bin        — ManifestData
 *   player.bin          — EntitySnapshot (player)
 *   ships.bin           — ArrayList&lt;EntitySnapshot&gt; (omitted when empty)
 *   system_&lt;uuid&gt;.bin  — ArrayList&lt;EntitySnapshot&gt; per system (omitted when empty)
 *   modifications.bin   — HashMap&lt;UUID, WorldModification&gt; (omitted when empty)
 *   economy.bin         — HashMap&lt;String, Object&gt; (omitted when empty)
 *   factions.bin        — HashMap&lt;String, Object&gt; (omitted when empty)
 *   discovered.bin      — DiscoveredIds wrapper (omitted when both sets are empty)
 * </pre>
 */
public class SaveWriter {

    private final Kryo kryo;

    public SaveWriter() {
        this.kryo = new Kryo();
        KryoRegistrar.register(kryo);
    }

    /**
     * Writes {@code bundle} to {@code saveDir} atomically.
     *
     * @param bundle  the save data to write
     * @param saveDir the target directory (created if it does not exist)
     * @throws RuntimeException wrapping any I/O or serialization failure
     */
    public void write(SaveBundle bundle, File saveDir) {
        File tempDir = new File(saveDir.getParentFile(),
            saveDir.getName() + ".tmp." + System.currentTimeMillis());
        tempDir.mkdirs();

        try {
            writeObject(new File(tempDir, "manifest.bin"), bundle.manifest);
            writeObject(new File(tempDir, "player.bin"), bundle.playerSnapshot);

            if (!bundle.ownedShipSnapshots.isEmpty()) {
                writeObject(new File(tempDir, "ships.bin"),
                    new ArrayList<>(bundle.ownedShipSnapshots));
            }

            for (Map.Entry<UUID, List<EntitySnapshot>> entry : bundle.systemSnapshots.entrySet()) {
                // List.of() returns an immutable list; Kryo needs a registered concrete type.
                writeObject(new File(tempDir, "system_" + entry.getKey() + ".bin"),
                    new ArrayList<>(entry.getValue()));
            }

            if (!bundle.worldModifications.isEmpty()) {
                writeObject(new File(tempDir, "modifications.bin"),
                    new HashMap<>(bundle.worldModifications));
            }
            if (!bundle.economyState.isEmpty()) {
                writeObject(new File(tempDir, "economy.bin"),
                    new HashMap<>(bundle.economyState));
            }
            if (!bundle.factionState.isEmpty()) {
                writeObject(new File(tempDir, "factions.bin"),
                    new HashMap<>(bundle.factionState));
            }
            if (!bundle.discoveredSystemIds.isEmpty() || !bundle.discoveredPlanetIds.isEmpty()) {
                // Use DiscoveredIds to avoid registering Object[] with Kryo.
                DiscoveredIds discovered = new DiscoveredIds(
                    new ArrayList<>(bundle.discoveredSystemIds),
                    new ArrayList<>(bundle.discoveredPlanetIds));
                writeObject(new File(tempDir, "discovered.bin"), discovered);
            }

            // Atomic swap: remove old save, rename temp to final destination.
            if (saveDir.exists()) {
                deleteRecursive(saveDir);
            }
            if (!tempDir.renameTo(saveDir)) {
                throw new IOException("Failed to rename temp dir to " + saveDir);
            }
        } catch (Exception e) {
            deleteRecursive(tempDir);
            throw new RuntimeException("Save failed for " + saveDir.getName(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void writeObject(File file, Object obj) {
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write " + file.getName(), e);
        }
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
