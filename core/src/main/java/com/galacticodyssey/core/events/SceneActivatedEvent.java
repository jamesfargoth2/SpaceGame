package com.galacticodyssey.core.events;

import com.galacticodyssey.core.scene.SceneType;

/** Published when the target scene becomes the primary scene (persistent entities re-tagged). */
public final class SceneActivatedEvent {
    public final int sceneId;
    public final SceneType type;

    public SceneActivatedEvent(int sceneId, SceneType type) {
        this.sceneId = sceneId;
        this.type = type;
    }
}
