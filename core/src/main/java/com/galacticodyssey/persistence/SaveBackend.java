package com.galacticodyssey.persistence;

import java.util.List;

public interface SaveBackend {
    void writeSave(String saveId, SaveBundle bundle);
    SaveBundle readSave(String saveId);
    List<ManifestData> listSaves();
    void deleteSave(String saveId);
    void copySave(String sourceId, String destId);
    ManifestData readManifestOnly(String saveId);
}
