package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;

import java.util.List;

/**
 * Concrete loader for the DEEP_SPACE scene: the open-flight baseline with no heavy terrain or
 * interior. It currently carries no prefetch assets (deep space streams star/ship props on
 * demand via the existing StreamingSystem) and removes its tagged entities on unload.
 */
public final class DeepSpaceLoader extends EmptySceneLoader {

    public DeepSpaceLoader(Engine engine, SceneAssetSource assetSource) {
        super(SceneType.DEEP_SPACE, engine, assetSource, List.of(), null);
    }
}
