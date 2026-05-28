package com.galacticodyssey.core.events;

/** Published when a transition request is refused (already in progress, or max scenes reached). */
public final class SceneTransitionRejectedEvent {
    public final String reason;

    public SceneTransitionRejectedEvent(String reason) {
        this.reason = reason;
    }
}
