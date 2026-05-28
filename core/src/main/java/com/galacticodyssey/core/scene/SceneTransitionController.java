package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.SceneActivatedEvent;
import com.galacticodyssey.core.events.SceneLoadProgressEvent;
import com.galacticodyssey.core.events.SceneTransitionBeganEvent;
import com.galacticodyssey.core.events.SceneTransitionCompletedEvent;
import com.galacticodyssey.core.events.SceneTransitionReadyEvent;

/**
 * Drives a single in-flight scene transition through its phases, one phase per {@link #update}
 * call (PRELOADING repeats until the target loader reports done). Re-tags persistent entities at
 * ACTIVATING and unloads the source at UNLOADING_OLD. Disguise gating (Task 7) and failure
 * rollback (Task 8) are layered on later.
 */
public class SceneTransitionController {

    private static final float DEFAULT_BUDGET_MS = 8f;

    private static final ComponentMapper<SceneComponent> SCENE_MAP =
        ComponentMapper.getFor(SceneComponent.class);
    private static final Family PERSISTENT_FAMILY =
        Family.all(PersistentSceneMemberComponent.class, SceneComponent.class).get();

    private final EventBus eventBus;
    private final Engine engine;

    private float budgetMs = DEFAULT_BUDGET_MS;
    private float disguiseTimeout = 5f;
    private boolean disguiseComplete = false;
    private float disguiseTimer = 0f;

    private TransitionPhase phase = TransitionPhase.IDLE;
    private Scene source;
    private SceneLoader sourceLoader;
    private Scene target;
    private SceneLoader targetLoader;

    public SceneTransitionController(EventBus eventBus, Engine engine) {
        this.eventBus = eventBus;
        this.engine = engine;
    }

    public void setBudgetMs(float budgetMs) { this.budgetMs = budgetMs; }
    public void setDisguiseTimeout(float seconds) { this.disguiseTimeout = seconds; }

    public boolean isIdle() { return phase == TransitionPhase.IDLE; }
    public TransitionPhase getPhase() { return phase; }
    public Scene getTargetScene() { return target; }

    /** Start a transition. {@code source}/{@code sourceLoader} may be null for the first scene. */
    public void begin(Scene source, SceneLoader sourceLoader, Scene target, SceneLoader targetLoader) {
        this.source = source;
        this.sourceLoader = sourceLoader;
        this.target = target;
        this.targetLoader = targetLoader;
        this.phase = TransitionPhase.REQUESTED;
        this.disguiseComplete = false;
        this.disguiseTimer = 0f;
    }

    /** Called by a disguise system (camera/VFX/animation) when its transition animation finishes. */
    public void notifyDisguiseComplete() {
        this.disguiseComplete = true;
    }

    public void update(float dt) {
        switch (phase) {
            case IDLE:
                return;
            case REQUESTED:
                targetLoader.begin(target);
                target.state = SceneState.LOADING;
                eventBus.publish(new SceneTransitionBeganEvent(
                    source != null ? source.type : null, target.type));
                phase = TransitionPhase.PRELOADING;
                return;
            case PRELOADING: {
                float progress = targetLoader.step(target, budgetMs);
                eventBus.publish(new SceneLoadProgressEvent(target.id, progress));
                if (progress >= 1f) {
                    target.state = SceneState.ACTIVE;
                    eventBus.publish(new SceneTransitionReadyEvent(target.id));
                    phase = TransitionPhase.READY_OVERLAP;
                }
                return;
            }
            case READY_OVERLAP:
                disguiseTimer += dt;
                if (disguiseComplete || disguiseTimer >= disguiseTimeout) {
                    phase = TransitionPhase.ACTIVATING;
                }
                return;
            case ACTIVATING:
                reTagPersistentEntities(target.id);
                eventBus.publish(new SceneActivatedEvent(target.id, target.type));
                phase = TransitionPhase.UNLOADING_OLD;
                return;
            case UNLOADING_OLD:
                if (source != null) {
                    source.state = SceneState.UNLOADING;
                    if (sourceLoader != null) sourceLoader.unload(source);
                    source.state = SceneState.UNLOADED;
                }
                eventBus.publish(new SceneTransitionCompletedEvent(target.type));
                reset();
                return;
        }
    }

    private void reTagPersistentEntities(int targetSceneId) {
        for (Entity e : engine.getEntitiesFor(PERSISTENT_FAMILY)) {
            SCENE_MAP.get(e).sceneId = targetSceneId;
        }
    }

    private void reset() {
        phase = TransitionPhase.IDLE;
        source = null;
        sourceLoader = null;
        target = null;
        targetLoader = null;
    }
}
