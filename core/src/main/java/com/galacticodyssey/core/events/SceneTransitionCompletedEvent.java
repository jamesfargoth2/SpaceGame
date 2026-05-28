package com.galacticodyssey.core.events;

import com.galacticodyssey.core.scene.SceneType;

/** Published when the source scene has been unloaded and the transition returns to IDLE. */
public final class SceneTransitionCompletedEvent {
    public final SceneType type;

    public SceneTransitionCompletedEvent(SceneType type) {
        this.type = type;
    }
}
