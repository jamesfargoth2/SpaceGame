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
}
