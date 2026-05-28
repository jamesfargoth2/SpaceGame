package com.galacticodyssey.core.events;

import com.galacticodyssey.core.scene.SceneType;

/** Published when a transition leaves IDLE. {@code from} is null for the first scene load. */
public final class SceneTransitionBeganEvent {
    public final SceneType from;
    public final SceneType to;

    public SceneTransitionBeganEvent(SceneType from, SceneType to) {
        this.from = from;
        this.to = to;
    }
}
