package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.SceneActivatedEvent;
import com.galacticodyssey.core.events.SceneLoadFailedEvent;
import com.galacticodyssey.core.events.SceneTransitionCompletedEvent;
import com.galacticodyssey.core.events.SceneTransitionRejectedEvent;

import java.util.Map;

/**
 * Facade over the scene system: owns the active-scene set and the {@link SceneTransitionController},
 * validates transition requests (single in-flight; max active scenes), resolves a {@link SceneLoader}
 * per scene type, and tracks the primary scene by observing lifecycle events.
 */
public final class SceneManager {

    private final EventBus eventBus;
    private final Map<SceneType, SceneLoader> loaders;
    private final SceneLoader fallbackLoader;
    private final int maxActiveScenes;
    private final SceneTransitionController controller;

    private final Array<Scene> activeScenes = new Array<>();
    private final ImmutableArray<Scene> activeScenesView = new ImmutableArray<>(activeScenes);
    private Scene primaryScene;
    private int nextSceneId = 1;

    public SceneManager(EventBus eventBus, Engine engine, Map<SceneType, SceneLoader> loaders,
                        SceneLoader fallbackLoader, int maxActiveScenes) {
        this.eventBus = eventBus;
        this.loaders = loaders;
        this.fallbackLoader = fallbackLoader;
        this.maxActiveScenes = maxActiveScenes;
        this.controller = new SceneTransitionController(eventBus, engine);

        eventBus.subscribe(SceneActivatedEvent.class, this::onSceneActivated);
        eventBus.subscribe(SceneTransitionCompletedEvent.class, e -> pruneUnloaded());
        eventBus.subscribe(SceneLoadFailedEvent.class, e -> pruneUnloaded());
    }

    public SceneTransitionController getController() { return controller; }
    public Scene getPrimaryScene() { return primaryScene; }
    public ImmutableArray<Scene> getActiveScenes() { return activeScenesView; }

    /**
     * Request a transition to a new primary scene. Returns false (and publishes a
     * {@link SceneTransitionRejectedEvent}) if a transition is already in flight or the active
     * scene budget would be exceeded.
     */
    public boolean requestTransition(SceneTransitionRequest request) {
        if (!controller.isIdle()) {
            eventBus.publish(new SceneTransitionRejectedEvent("transition already in progress"));
            return false;
        }
        if (activeScenes.size + 1 > maxActiveScenes) {
            eventBus.publish(new SceneTransitionRejectedEvent(
                "max active scenes reached (" + maxActiveScenes + ")"));
            return false;
        }
        Scene target = new Scene(nextSceneId++, request.targetType, request.galaxyAnchor);
        activeScenes.add(target);
        SceneLoader targetLoader = loaderFor(request.targetType);
        SceneLoader sourceLoader = primaryScene != null ? loaderFor(primaryScene.type) : null;
        controller.begin(primaryScene, sourceLoader, target, targetLoader);
        return true;
    }

    public void update(float dt) {
        controller.update(dt);
    }

    /** Forwarded from a disguise system once its transition animation finishes. */
    public void notifyDisguiseComplete() {
        controller.notifyDisguiseComplete();
    }

    private SceneLoader loaderFor(SceneType type) {
        return loaders.getOrDefault(type, fallbackLoader);
    }

    private void onSceneActivated(SceneActivatedEvent e) {
        for (Scene s : activeScenes) {
            if (s.id == e.sceneId) {
                primaryScene = s;
                return;
            }
        }
    }

    private void pruneUnloaded() {
        for (int i = activeScenes.size - 1; i >= 0; i--) {
            if (activeScenes.get(i).state == SceneState.UNLOADED) {
                activeScenes.removeIndex(i);
            }
        }
    }
}
