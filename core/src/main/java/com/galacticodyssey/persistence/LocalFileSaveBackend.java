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
                    result.add(reader.readManifest(manifestFile));
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

    @Override
    public void copySave(String sourceId, String destId) {
        File sourceDir = new File(savesRoot, sourceId);
        File destDir = new File(savesRoot, destId);
        if (!sourceDir.exists()) {
            throw new RuntimeException("Source save not found: " + sourceId);
        }
        copyDirectoryRecursive(sourceDir, destDir);
    }

    @Override
    public ManifestData readManifestOnly(String saveId) {
        File saveDir = new File(savesRoot, saveId);
        File manifestFile = new File(saveDir, "manifest.bin");
        if (!manifestFile.exists()) {
            throw new RuntimeException("Manifest not found: " + saveId);
        }
        return reader.readManifest(manifestFile);
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

    private void copyDirectoryRecursive(File source, File dest) {
        dest.mkdirs();
        File[] files = source.listFiles();
        if (files == null) return;
        for (File file : files) {
            File destFile = new File(dest, file.getName());
            if (file.isDirectory()) {
                copyDirectoryRecursive(file, destFile);
            } else {
                copyFile(file, destFile);
            }
        }
    }

    private void copyFile(File source, File dest) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(source);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to copy " + source.getName(), e);
        }
    }
}
