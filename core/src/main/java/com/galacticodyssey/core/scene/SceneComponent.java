package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Component;

/** Tags an entity with the id of the {@link Scene} that owns it, so scene unload is deterministic. */
public class SceneComponent implements Component {
    public int sceneId;

    public SceneComponent() {}

    public SceneComponent(int sceneId) {
        this.sceneId = sceneId;
    }
}
