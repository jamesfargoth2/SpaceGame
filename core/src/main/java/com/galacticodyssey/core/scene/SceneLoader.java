package com.galacticodyssey.core.scene;

/**
 * Strategy that loads/unloads the entities, assets, and (for interiors) physics world of one
 * {@link SceneType}. Implementations must never block: {@link #step} is time-sliced.
 */
public interface SceneLoader {

    SceneType type();

    /** Called once when preloading begins (acquire assets, kick off generation). */
    void begin(Scene scene);

    /**
     * Advance loading within the given millisecond budget.
     * @return cumulative progress in [0,1]; 1.0 means fully loaded.
     * @throws RuntimeException to signal an unrecoverable load failure (triggers rollback).
     */
    float step(Scene scene, float budgetMs);

    /** Release the scene's assets, remove its entities, and dispose any interior physics world. */
    void unload(Scene scene);
}
