package com.galacticodyssey.ui;

import com.galacticodyssey.persistence.ManifestData;

public interface SaveSlotListener {
    void onSlotClicked(ManifestData manifest);
    void onRenameClicked(ManifestData manifest);
    void onCopyClicked(ManifestData manifest);
    void onDeleteClicked(ManifestData manifest);
}
