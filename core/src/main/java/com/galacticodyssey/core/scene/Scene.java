package com.galacticodyssey.core.scene;

import com.badlogic.gdx.physics.bullet.dynamics.btDynamicsWorld;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.data.AssetHandle;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Lifecycle data holder for one loaded gameplay context. Pure data — loading/unloading is
 * delegated to a {@link SceneLoader}. The galaxy anchor is the 64-bit double-precision origin
 * of this scene in galaxy space; loaders convert it to local float space via CoordinateManager.
 */
public final class Scene {
    public final int id;
    public final SceneType type;
    public final double[] galaxyAnchor;

    public SceneState state = SceneState.UNLOADED;
    public final Array<AssetHandle<SceneAsset>> assets = new Array<>();
    /** Non-null only for interior scenes that own a separate Bullet world. */
    public btDynamicsWorld interiorWorld;

    public Scene(int id, SceneType type, double[] galaxyAnchor) {
        this.id = id;
        this.type = type;
        this.galaxyAnchor = galaxyAnchor;
    }
}
