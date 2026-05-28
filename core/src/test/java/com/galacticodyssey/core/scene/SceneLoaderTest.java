package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.core.scene.support.FakeSceneAssetSource;
import com.galacticodyssey.data.AssetCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SceneLoaderTest {

    @Test
    void emptyLoaderPrefetchesDeclaredAssetsAndCompletesImmediately() {
        Engine engine = new Engine();
        FakeSceneAssetSource source = new FakeSceneAssetSource();
        EmptySceneLoader loader = new EmptySceneLoader(SceneType.STATION_INTERIOR, engine, source,
            List.of("prop_crate"), AssetCategory.PROP_SMALL);

        Scene scene = new Scene(1, SceneType.STATION_INTERIOR, new double[]{0, 0, 0});
        loader.begin(scene);
        assertEquals(List.of("prop_crate"), source.acquired);
        assertEquals(1, scene.assets.size);
        assertEquals(1f, loader.step(scene, 8f), 1e-6);
    }

    @Test
    void unloadReleasesHandlesAndRemovesTaggedEntities() {
        Engine engine = new Engine();
        FakeSceneAssetSource source = new FakeSceneAssetSource();
        EmptySceneLoader loader = new EmptySceneLoader(SceneType.STATION_INTERIOR, engine, source,
            List.of("prop_crate"), AssetCategory.PROP_SMALL);

        Scene scene = new Scene(42, SceneType.STATION_INTERIOR, new double[]{0, 0, 0});
        loader.begin(scene);

        // An entity tagged as belonging to this scene, plus one belonging to another scene.
        Entity mine = new Entity();
        mine.add(new SceneComponent(42));
        engine.addEntity(mine);
        Entity other = new Entity();
        other.add(new SceneComponent(99));
        engine.addEntity(other);

        com.galacticodyssey.data.AssetHandle<net.mgsx.gltf.scene3d.scene.SceneAsset> handle =
            scene.assets.get(0);
        assertEquals(1, handle.getRefCount());
        loader.unload(scene);

        // Handle is released and the scene's asset list is emptied on unload.
        assertEquals(0, handle.getRefCount());
        assertEquals(0, scene.assets.size);
        // Only the scene-42 entity is removed.
        assertEquals(1, engine.getEntitiesFor(Family.all(SceneComponent.class).get()).size());
        assertEquals(99, engine.getEntitiesFor(Family.all(SceneComponent.class).get())
            .first().getComponent(SceneComponent.class).sceneId);
    }

    @Test
    void deepSpaceLoaderReportsItsType() {
        DeepSpaceLoader loader = new DeepSpaceLoader(new Engine(), new FakeSceneAssetSource());
        assertEquals(SceneType.DEEP_SPACE, loader.type());
        Scene scene = new Scene(1, SceneType.DEEP_SPACE, new double[]{0, 0, 0});
        loader.begin(scene);
        assertEquals(1f, loader.step(scene, 8f), 1e-6);
    }
}
