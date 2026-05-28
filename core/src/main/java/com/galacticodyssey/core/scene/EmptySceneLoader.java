package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.data.AssetCategory;
import com.galacticodyssey.data.AssetHandle;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

import java.util.List;

/**
 * A complete, no-entity loader: prefetches a fixed list of assets and, on unload, releases them
 * and removes any entities tagged with this scene's id. Used as the registry fallback for scene
 * types that do not yet have a bespoke procgen-backed loader, so the orchestration engine runs
 * end-to-end for every {@link SceneType} today.
 */
public class EmptySceneLoader implements SceneLoader {

    private final SceneType type;
    private final Engine engine;
    private final SceneAssetSource assetSource;
    private final List<String> prefetchAssetIds;
    private final AssetCategory prefetchCategory;

    private static final Family SCENE_FAMILY = Family.all(SceneComponent.class).get();

    public EmptySceneLoader(SceneType type, Engine engine, SceneAssetSource assetSource,
                            List<String> prefetchAssetIds, AssetCategory prefetchCategory) {
        this.type = type;
        this.engine = engine;
        this.assetSource = assetSource;
        this.prefetchAssetIds = prefetchAssetIds;
        this.prefetchCategory = prefetchCategory;
    }

    @Override
    public SceneType type() { return type; }

    @Override
    public void begin(Scene scene) {
        for (String id : prefetchAssetIds) {
            scene.assets.add(assetSource.acquire(id, prefetchCategory));
        }
    }

    @Override
    public float step(Scene scene, float budgetMs) {
        return 1f;
    }

    @Override
    public void unload(Scene scene) {
        for (AssetHandle<SceneAsset> handle : scene.assets) {
            handle.release();
        }
        scene.assets.clear();
        removeTaggedEntities(scene.id);
    }

    private void removeTaggedEntities(int sceneId) {
        Array<Entity> toRemove = new Array<>();
        for (Entity e : engine.getEntitiesFor(SCENE_FAMILY)) {
            if (e.getComponent(SceneComponent.class).sceneId == sceneId) {
                toRemove.add(e);
            }
        }
        for (Entity e : toRemove) {
            engine.removeEntity(e);
        }
    }
}
