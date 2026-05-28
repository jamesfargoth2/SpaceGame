package com.galacticodyssey.core.events;

import com.galacticodyssey.core.scene.SceneType;

/** Published when a preload throws; the transition rolls back and the source scene stays active. */
public final class SceneLoadFailedEvent {
    public final SceneType type;
    public final String reason;

    public SceneLoadFailedEvent(SceneType type, String reason) {
        this.type = type;
        this.reason = reason;
    }
}
