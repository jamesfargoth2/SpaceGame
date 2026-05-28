package com.galacticodyssey.core.scene.support;

import com.galacticodyssey.core.scene.Scene;
import com.galacticodyssey.core.scene.SceneLoader;
import com.galacticodyssey.core.scene.SceneType;

/** Scriptable loader: control how many steps until done, whether to throw, and record unloads. */
public final class FakeSceneLoader implements SceneLoader {

    private final SceneType type;
    public int stepsToComplete = 1;
    public boolean throwOnStep = false;
    public int beginCount = 0;
    public int unloadCount = 0;
    private int stepsTaken = 0;

    public FakeSceneLoader(SceneType type) {
        this.type = type;
    }

    @Override public SceneType type() { return type; }

    @Override public void begin(Scene scene) {
        beginCount++;
        stepsTaken = 0;
    }

    @Override public float step(Scene scene, float budgetMs) {
        if (throwOnStep) {
            throw new RuntimeException("simulated load failure");
        }
        stepsTaken++;
        return Math.min(1f, (float) stepsTaken / stepsToComplete);
    }

    @Override public void unload(Scene scene) {
        unloadCount++;
    }
}
