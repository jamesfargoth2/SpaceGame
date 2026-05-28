package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.core.scene.support.FakeSceneLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SceneTransitionControllerTest {

    private EventBus bus;
    private Engine engine;
    private SceneTransitionController controller;
    private List<Object> events;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        engine = new Engine();
        controller = new SceneTransitionController(bus, engine);
        controller.setDisguiseTimeout(0f); // happy-path: no disguise wait unless a test sets it
        events = new ArrayList<>();
        bus.subscribe(SceneTransitionBeganEvent.class, events::add);
        bus.subscribe(SceneTransitionReadyEvent.class, events::add);
        bus.subscribe(SceneActivatedEvent.class, events::add);
        bus.subscribe(SceneTransitionCompletedEvent.class, events::add);
    }

    private Scene scene(int id, SceneType type, SceneState state) {
        Scene s = new Scene(id, type, new double[]{0, 0, 0});
        s.state = state;
        return s;
    }

    @Test
    void happyPathWalksAllPhasesAndEmitsEventsInOrder() {
        Scene source = scene(1, SceneType.DEEP_SPACE, SceneState.ACTIVE);
        Scene target = scene(2, SceneType.ORBITAL, SceneState.UNLOADED);
        FakeSceneLoader sourceLoader = new FakeSceneLoader(SceneType.DEEP_SPACE);
        FakeSceneLoader targetLoader = new FakeSceneLoader(SceneType.ORBITAL);

        controller.begin(source, sourceLoader, target, targetLoader);
        assertEquals(TransitionPhase.REQUESTED, controller.getPhase());

        controller.update(0.1f); // REQUESTED -> PRELOADING (begin called)
        assertEquals(TransitionPhase.PRELOADING, controller.getPhase());
        assertEquals(1, targetLoader.beginCount);
        assertEquals(SceneState.LOADING, target.state);

        controller.update(0.1f); // PRELOADING done (1 step) -> READY_OVERLAP
        assertEquals(TransitionPhase.READY_OVERLAP, controller.getPhase());
        // both scenes active during overlap
        assertEquals(SceneState.ACTIVE, source.state);
        assertEquals(SceneState.ACTIVE, target.state);

        controller.update(0.1f); // READY_OVERLAP -> ACTIVATING (timeout 0 -> proceed)
        assertEquals(TransitionPhase.ACTIVATING, controller.getPhase());

        controller.update(0.1f); // ACTIVATING -> UNLOADING_OLD
        assertEquals(TransitionPhase.UNLOADING_OLD, controller.getPhase());

        controller.update(0.1f); // UNLOADING_OLD -> IDLE (source unloaded)
        assertEquals(TransitionPhase.IDLE, controller.getPhase());
        assertEquals(1, sourceLoader.unloadCount);
        assertEquals(SceneState.UNLOADED, source.state);
        assertTrue(controller.isIdle());

        assertEquals(4, events.size());
        assertTrue(events.get(0) instanceof SceneTransitionBeganEvent);
        assertTrue(events.get(1) instanceof SceneTransitionReadyEvent);
        assertTrue(events.get(2) instanceof SceneActivatedEvent);
        assertTrue(events.get(3) instanceof SceneTransitionCompletedEvent);
    }

    @Test
    void activatingReTagsPersistentEntitiesExactlyOnceToTarget() {
        Scene source = scene(1, SceneType.DEEP_SPACE, SceneState.ACTIVE);
        Scene target = scene(2, SceneType.SHIP_INTERIOR, SceneState.UNLOADED);

        Entity player = new Entity();
        player.add(new PersistentSceneMemberComponent());
        player.add(new SceneComponent(1));
        engine.addEntity(player);

        controller.begin(source, new FakeSceneLoader(SceneType.DEEP_SPACE),
            target, new FakeSceneLoader(SceneType.SHIP_INTERIOR));
        for (int i = 0; i < 6; i++) controller.update(0.1f);

        assertEquals(2, player.getComponent(SceneComponent.class).sceneId);
        assertTrue(controller.isIdle());
    }

    @Test
    void nullSourceSkipsUnloadStep() {
        Scene target = scene(2, SceneType.ORBITAL, SceneState.UNLOADED);
        FakeSceneLoader targetLoader = new FakeSceneLoader(SceneType.ORBITAL);
        controller.begin(null, null, target, targetLoader);
        for (int i = 0; i < 6; i++) controller.update(0.1f);
        assertTrue(controller.isIdle());
        assertEquals(SceneState.ACTIVE, target.state);
    }

    @Test
    void readyOverlapWaitsForDisguiseThenProceeds() {
        controller.setDisguiseTimeout(5f);
        Scene source = scene(1, SceneType.DEEP_SPACE, SceneState.ACTIVE);
        Scene target = scene(2, SceneType.ORBITAL, SceneState.UNLOADED);
        controller.begin(source, new FakeSceneLoader(SceneType.DEEP_SPACE),
            target, new FakeSceneLoader(SceneType.ORBITAL));

        controller.update(0.1f); // -> PRELOADING
        controller.update(0.1f); // -> READY_OVERLAP
        assertEquals(TransitionPhase.READY_OVERLAP, controller.getPhase());

        controller.update(0.1f); // still waiting on disguise
        assertEquals(TransitionPhase.READY_OVERLAP, controller.getPhase());

        controller.notifyDisguiseComplete();
        controller.update(0.1f); // disguise done -> ACTIVATING
        assertEquals(TransitionPhase.ACTIVATING, controller.getPhase());
    }

    @Test
    void readyOverlapProceedsOnTimeoutWithoutDisguiseSignal() {
        controller.setDisguiseTimeout(0.25f);
        Scene source = scene(1, SceneType.DEEP_SPACE, SceneState.ACTIVE);
        Scene target = scene(2, SceneType.ORBITAL, SceneState.UNLOADED);
        controller.begin(source, new FakeSceneLoader(SceneType.DEEP_SPACE),
            target, new FakeSceneLoader(SceneType.ORBITAL));

        controller.update(0.1f); // -> PRELOADING
        controller.update(0.1f); // -> READY_OVERLAP
        controller.update(0.1f); // timer 0.1 < 0.25, wait
        assertEquals(TransitionPhase.READY_OVERLAP, controller.getPhase());
        controller.update(0.2f); // timer 0.3 >= 0.25, proceed
        assertEquals(TransitionPhase.ACTIVATING, controller.getPhase());
    }

    @Test
    void loadFailureRollsBackAndKeepsSourceActive() {
        List<SceneLoadFailedEvent> failures = new ArrayList<>();
        bus.subscribe(SceneLoadFailedEvent.class, failures::add);

        Scene source = scene(1, SceneType.DEEP_SPACE, SceneState.ACTIVE);
        Scene target = scene(2, SceneType.PLANET_SURFACE, SceneState.UNLOADED);
        FakeSceneLoader sourceLoader = new FakeSceneLoader(SceneType.DEEP_SPACE);
        FakeSceneLoader targetLoader = new FakeSceneLoader(SceneType.PLANET_SURFACE);
        targetLoader.throwOnStep = true;

        controller.begin(source, sourceLoader, target, targetLoader);
        controller.update(0.1f); // REQUESTED -> PRELOADING
        controller.update(0.1f); // step throws -> rollback

        assertTrue(controller.isIdle());
        assertEquals(SceneState.ACTIVE, source.state);
        assertEquals(SceneState.UNLOADED, target.state);
        assertEquals(1, targetLoader.unloadCount); // partial target cleaned up
        assertEquals(0, sourceLoader.unloadCount);  // source untouched
        assertEquals(1, failures.size());
        assertEquals(SceneType.PLANET_SURFACE, failures.get(0).type);
    }

    @Test
    void preloadingRepeatsUntilDoneAndEmitsProgressEachStep() {
        List<SceneLoadProgressEvent> progress = new ArrayList<>();
        bus.subscribe(SceneLoadProgressEvent.class, progress::add);

        Scene source = scene(1, SceneType.DEEP_SPACE, SceneState.ACTIVE);
        Scene target = scene(2, SceneType.PLANET_SURFACE, SceneState.UNLOADED);
        FakeSceneLoader targetLoader = new FakeSceneLoader(SceneType.PLANET_SURFACE);
        targetLoader.stepsToComplete = 3;

        controller.begin(source, new FakeSceneLoader(SceneType.DEEP_SPACE), target, targetLoader);

        controller.update(0.1f); // REQUESTED -> PRELOADING (begin called)
        assertEquals(TransitionPhase.PRELOADING, controller.getPhase());

        controller.update(0.1f); // step 1/3 -> still PRELOADING
        assertEquals(TransitionPhase.PRELOADING, controller.getPhase());
        controller.update(0.1f); // step 2/3 -> still PRELOADING
        assertEquals(TransitionPhase.PRELOADING, controller.getPhase());
        assertEquals(SceneState.LOADING, target.state);

        controller.update(0.1f); // step 3/3 -> done -> READY_OVERLAP
        assertEquals(TransitionPhase.READY_OVERLAP, controller.getPhase());
        assertEquals(SceneState.ACTIVE, target.state);

        // One progress event per preload step, monotonically increasing to 1.0.
        assertEquals(3, progress.size());
        assertEquals(1f / 3f, progress.get(0).progress, 1e-6);
        assertEquals(2f / 3f, progress.get(1).progress, 1e-6);
        assertEquals(1f, progress.get(2).progress, 1e-6);
        assertEquals(2, progress.get(0).sceneId);
    }
}
