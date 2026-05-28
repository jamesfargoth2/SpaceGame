package com.galacticodyssey.core.scene;

import com.galacticodyssey.data.AssetCategory;
import com.galacticodyssey.data.AssetHandle;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/** Narrow seam over GalacticAssetManager so loaders are unit-testable without a GL context. */
@FunctionalInterface
public interface SceneAssetSource {
    /** Acquire (retain) a handle for the asset; the caller releases it on scene unload. */
    AssetHandle<SceneAsset> acquire(String assetId, AssetCategory category);
}
