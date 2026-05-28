package com.galacticodyssey.core.events;

/**
 * Published when the target scene is fully loaded and both scenes are active. Disguise systems
 * (camera/VFX/animation) subscribe and call {@code SceneManager.notifyDisguiseComplete(sceneId)}
 * when their animation finishes.
 */
public final class SceneTransitionReadyEvent {
    public final int sceneId;

    public SceneTransitionReadyEvent(int sceneId) {
        this.sceneId = sceneId;
    }
}
