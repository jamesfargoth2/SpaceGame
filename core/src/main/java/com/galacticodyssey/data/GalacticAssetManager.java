package com.galacticodyssey.data;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonValue;
import net.mgsx.gltf.loaders.glb.GLBAssetLoader;
import net.mgsx.gltf.loaders.gltf.GLTFAssetLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class GalacticAssetManager implements Disposable {

    private static final int MAX_LOADS_PER_FRAME = 3;

    public record StreamingRadius(float prefetchRadius, float unloadRadius) {}

    private final AssetManager inner;
    private final StreamingQueue queue;
    private final Map<String, AssetManifest> manifests = new HashMap<>();
    private final Map<String, AssetHandle<SceneAsset>> handles = new HashMap<>();
    private final Map<String, String> assetIdToPath = new HashMap<>();
    private final Map<AssetCategory, StreamingRadius> streamingRadii = new EnumMap<>(AssetCategory.class);

    public GalacticAssetManager() {
        this.inner = new AssetManager();
        this.queue = new StreamingQueue();
        registerLoaders();
    }

    /** Test-only constructor: skips GL-dependent initialisation. */
    protected GalacticAssetManager(boolean skipGlInit) {
        this.inner = skipGlInit ? null : new AssetManager();
        this.queue = new StreamingQueue();
        if (!skipGlInit) registerLoaders();
    }

    private void registerLoaders() {
        inner.setLoader(SceneAsset.class, ".gltf", new GLTFAssetLoader());
        inner.setLoader(SceneAsset.class, ".glb", new GLBAssetLoader());
    }

    /** Load a category manifest from a JsonValue (already parsed). */
    public void registerManifest(JsonValue root) {
        AssetManifest manifest = AssetManifest.fromJson(root);
        manifests.put(manifest.getCategory().name(), manifest);
        for (AssetManifest.Entry entry : manifest.getEntries()) {
            assetIdToPath.put(entry.id(), entry.path());
        }
    }

    /** Load streaming radii config from a JsonValue. */
    public void loadStreamingConfig(JsonValue root) {
        JsonValue categories = root.get("categories");
        for (AssetCategory cat : AssetCategory.values()) {
            JsonValue cfg = categories.get(cat.name());
            if (cfg != null) {
                streamingRadii.put(cat, new StreamingRadius(
                    cfg.getFloat("prefetchRadius"),
                    cfg.getFloat("unloadRadius")
                ));
            }
        }
    }

    /**
     * Request an asset to be loaded. Safe to call every frame — idempotent if already loading/loaded.
     * Returns the handle (not yet resident until update() completes loading).
     */
    public AssetHandle<SceneAsset> enqueue(String assetId, AssetCategory category, float distanceToCamera) {
        AssetHandle<SceneAsset> handle = handles.computeIfAbsent(assetId,
            id -> new AssetHandle<>(id, category, this::onHandleReleased));
        if (!handle.isResident() && !inner.isLoaded(assetIdToPath.getOrDefault(assetId, ""))) {
            queue.enqueue(assetId, category, distanceToCamera);
        }
        return handle.retain();
    }

    /**
     * Call once per frame on the GL thread. Dispatches pending load requests and
     * promotes any finished loads into their handles.
     */
    public void update() {
        int dispatched = 0;
        while (!queue.isEmpty() && dispatched < MAX_LOADS_PER_FRAME) {
            StreamingQueue.StreamRequest req = queue.poll();
            String path = assetIdToPath.get(req.assetId);
            if (path != null && !inner.isLoaded(path)) {
                inner.load(path, SceneAsset.class);
            }
            dispatched++;
        }
        inner.update();
        promoteLoadedAssets();
    }

    private void promoteLoadedAssets() {
        for (Map.Entry<String, AssetHandle<SceneAsset>> entry : handles.entrySet()) {
            AssetHandle<SceneAsset> handle = entry.getValue();
            if (!handle.isResident()) {
                String path = assetIdToPath.get(entry.getKey());
                if (path != null && inner.isLoaded(path)) {
                    handle.setAsset(inner.get(path, SceneAsset.class));
                }
            }
        }
    }

    private void onHandleReleased(AssetHandle<SceneAsset> handle) {
        String path = assetIdToPath.get(handle.getAssetId());
        if (path != null && inner.isLoaded(path)) {
            inner.unload(path);
        }
        handles.remove(handle.getAssetId());
    }

    public AssetHandle<SceneAsset> getHandle(String assetId) {
        return handles.get(assetId);
    }

    public StreamingRadius getStreamingRadius(AssetCategory category) {
        return streamingRadii.getOrDefault(category, new StreamingRadius(50f, 100f));
    }

    public boolean isLoading() {
        return !inner.isFinished();
    }

    @Override
    public void dispose() {
        inner.dispose();
    }
}
