package com.galacticodyssey.data.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.data.AssetCategory;
import com.galacticodyssey.data.AssetHandle;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

public final class StreamableComponent implements Component {

    /** ID matching an entry in the category's manifest JSON. */
    public String assetId;

    public AssetCategory category;

    /** Non-null once the asset manager has started loading this asset. */
    public AssetHandle<SceneAsset> handle;

    public StreamableComponent() {}

    public StreamableComponent(String assetId, AssetCategory category) {
        this.assetId = assetId;
        this.category = category;
    }
}
