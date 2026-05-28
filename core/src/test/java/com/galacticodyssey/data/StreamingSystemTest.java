package com.galacticodyssey.data;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.data.components.StreamableComponent;
import com.galacticodyssey.data.systems.StreamingSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StreamingSystemTest {

    /** Minimal stub that records enqueue calls without needing GL. */
    static class StubAssetManager extends GalacticAssetManager {
        final List<String> enqueuedIds = new ArrayList<>();
        final List<String> releasedIds = new ArrayList<>();

        StubAssetManager() {
            super(true); // skip GL init
        }

        @Override
        public AssetHandle<net.mgsx.gltf.scene3d.scene.SceneAsset> enqueue(
                String assetId, AssetCategory category, float distance) {
            enqueuedIds.add(assetId);
            @SuppressWarnings("unchecked")
            AssetHandle<net.mgsx.gltf.scene3d.scene.SceneAsset> handle =
                new AssetHandle<>(assetId, category, h -> releasedIds.add(h.getAssetId()));
            return handle.retain();
        }

        @Override
        public StreamingRadius getStreamingRadius(AssetCategory category) {
            return new StreamingRadius(50f, 100f);
        }
    }

    private Engine engine;
    private StubAssetManager manager;
    private StreamingSystem system;

    @BeforeEach
    void setUp() {
        manager = new StubAssetManager();
        system = new StreamingSystem(manager);
        engine = new Engine();
        engine.addSystem(system);
    }

    @Test
    void entityWithinPrefetchRadiusGetsEnqueued() {
        Entity entity = new Entity();
        StreamableComponent sc = new StreamableComponent("char1", AssetCategory.CHARACTER);
        entity.add(sc);
        engine.addEntity(entity);

        system.setCameraPosition(new Vector3(0, 0, 0));
        // Place entity at 30m — within the 50m prefetch radius
        entity.add(createTransform(30f, 0f, 0f));
        engine.update(0.016f);

        assertTrue(manager.enqueuedIds.contains("char1"));
    }

    @Test
    void entityBeyondPrefetchRadiusNotEnqueued() {
        Entity entity = new Entity();
        entity.add(new StreamableComponent("char2", AssetCategory.CHARACTER));
        engine.addEntity(entity);

        system.setCameraPosition(new Vector3(0, 0, 0));
        entity.add(createTransform(200f, 0f, 0f)); // beyond 50m prefetch radius
        engine.update(0.016f);

        assertFalse(manager.enqueuedIds.contains("char2"));
    }

    private com.badlogic.ashley.core.Component createTransform(float x, float y, float z) {
        com.galacticodyssey.core.components.TransformComponent tc =
            new com.galacticodyssey.core.components.TransformComponent();
        tc.position.set(x, y, z);
        return tc;
    }
}
