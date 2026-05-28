package com.galacticodyssey.core.scene;

/** A request to transition the primary scene to {@code targetType} anchored at {@code galaxyAnchor}. */
public final class SceneTransitionRequest {
    public final SceneType targetType;
    public final double[] galaxyAnchor;

    public SceneTransitionRequest(SceneType targetType, double[] galaxyAnchor) {
        this.targetType = targetType;
        this.galaxyAnchor = galaxyAnchor;
    }
}
