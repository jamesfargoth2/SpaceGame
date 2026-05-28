package com.galacticodyssey.core.scene.support;

import com.galacticodyssey.core.scene.SceneAssetSource;
import com.galacticodyssey.data.AssetCategory;
import com.galacticodyssey.data.AssetHandle;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

import java.util.ArrayList;
import java.util.List;

/** Headless asset source: hands back retained handles and records every acquire. */
public final class FakeSceneAssetSource implements SceneAssetSource {
    public final List<String> acquired = new ArrayList<>();

    @Override
    public AssetHandle<SceneAsset> acquire(String assetId, AssetCategory category) {
        acquired.add(assetId);
        AssetHandle<SceneAsset> handle = new AssetHandle<>(assetId, category, h -> {});
        return handle.retain();
    }
}
