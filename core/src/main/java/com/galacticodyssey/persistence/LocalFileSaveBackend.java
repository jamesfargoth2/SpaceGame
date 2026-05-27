package com.galacticodyssey.persistence;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LocalFileSaveBackend implements SaveBackend {
    private final File savesRoot;
    private final SaveWriter writer;
    private final SaveReader reader;

    public LocalFileSaveBackend(File savesRoot) {
        this.savesRoot = savesRoot;
        this.writer = new SaveWriter();
        this.reader = new SaveReader();
        if (!savesRoot.exists()) {
            savesRoot.mkdirs();
        }
    }

    @Override
    public void writeSave(String saveId, SaveBundle bundle) {
        File saveDir = new File(savesRoot, saveId);
        writer.write(bundle, saveDir);
    }

    @Override
    public SaveBundle readSave(String saveId) {
        File saveDir = new File(savesRoot, saveId);
        if (!saveDir.exists()) {
            throw new RuntimeException("Save not found: " + saveId);
        }
        return reader.read(saveDir);
    }

    @Override
    public List<ManifestData> listSaves() {
        List<ManifestData> result = new ArrayList<>();
        File[] dirs = savesRoot.listFiles(File::isDirectory);
        if (dirs == null) return result;

        for (File dir : dirs) {
            File manifestFile = new File(dir, "manifest.bin");
            if (manifestFile.exists()) {
                try {
                    SaveBundle bundle = reader.read(dir);
                    result.add(bundle.manifest);
                } catch (Exception e) {
                    // Corrupted save — skip
                }
            }
        }

        result.sort((a, b) -> Long.compare(b.timestampMillis, a.timestampMillis));
        return result;
    }

    @Override
    public void deleteSave(String saveId) {
        File saveDir = new File(savesRoot, saveId);
        if (saveDir.exists()) {
            deleteRecursive(saveDir);
        }
    }

    private void deleteRecursive(File file) {
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
