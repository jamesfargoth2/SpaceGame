package com.galacticodyssey.core.events;

/** Published each preload step. {@code progress} is cumulative in [0,1]. */
public final class SceneLoadProgressEvent {
    public final int sceneId;
    public final float progress;

    public SceneLoadProgressEvent(int sceneId, float progress) {
        this.sceneId = sceneId;
        this.progress = progress;
    }
}
