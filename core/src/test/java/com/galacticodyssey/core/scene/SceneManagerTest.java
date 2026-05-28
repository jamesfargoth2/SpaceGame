package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.SceneTransitionRejectedEvent;
import com.galacticodyssey.core.scene.support.FakeSceneLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SceneManagerTest {

    private EventBus bus;
    private Engine engine;
    private Map<SceneType, SceneLoader> loaders;
    private FakeSceneLoader deepLoader;
    private FakeSceneLoader orbitalLoader;
    private SceneManager manager;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        engine = new Engine();
        deepLoader = new FakeSceneLoader(SceneType.DEEP_SPACE);
        orbitalLoader = new FakeSceneLoader(SceneType.ORBITAL);
        loaders = new EnumMap<>(SceneType.class);
        loaders.put(SceneType.DEEP_SPACE, deepLoader);
        loaders.put(SceneType.ORBITAL, orbitalLoader);
        manager = new SceneManager(bus, engine, loaders, deepLoader, 3);
        manager.getController().setDisguiseTimeout(0f);
    }

    private void runToIdle() {
        for (int i = 0; i < 8 && !manager.getController().isIdle(); i++) {
            manager.update(0.1f);
        }
    }

    @Test
    void firstRequestLoadsInitialSceneWithNoSource() {
        assertTrue(manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0})));
        runToIdle();
        assertNotNull(manager.getPrimaryScene());
        assertEquals(SceneType.DEEP_SPACE, manager.getPrimaryScene().type);
        assertEquals(1, manager.getActiveScenes().size());
    }

    @Test
    void transitionSwapsPrimaryAndUnloadsSource() {
        manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
        runToIdle();
        int firstId = manager.getPrimaryScene().id;

        assertTrue(manager.requestTransition(new SceneTransitionRequest(SceneType.ORBITAL, new double[]{1, 0, 0})));
        runToIdle();

        assertEquals(SceneType.ORBITAL, manager.getPrimaryScene().type);
        assertNotEquals(firstId, manager.getPrimaryScene().id);
        assertEquals(1, manager.getActiveScenes().size());
        assertEquals(1, deepLoader.unloadCount);
    }

    @Test
    void rejectsConcurrentTransition() {
        List<SceneTransitionRejectedEvent> rejects = new ArrayList<>();
        bus.subscribe(SceneTransitionRejectedEvent.class, rejects::add);

        manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
        runToIdle();
        // Start a transition and leave it mid-flight (orbital loader needs many steps).
        orbitalLoader.stepsToComplete = 10;
        assertTrue(manager.requestTransition(new SceneTransitionRequest(SceneType.ORBITAL, new double[]{1, 0, 0})));
        manager.update(0.1f); // REQUESTED -> PRELOADING, still in flight

        assertFalse(manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0})));
        assertEquals(1, rejects.size());
        assertTrue(rejects.get(0).reason.toLowerCase().contains("progress"));
    }

    @Test
    void rejectsWhenMaxActiveScenesReached() {
        List<SceneTransitionRejectedEvent> rejects = new ArrayList<>();
        bus.subscribe(SceneTransitionRejectedEvent.class, rejects::add);

        SceneManager tight = new SceneManager(bus, engine, loaders, deepLoader, 1);
        tight.getController().setDisguiseTimeout(0f);
        tight.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
        for (int i = 0; i < 8 && !tight.getController().isIdle(); i++) tight.update(0.1f);

        // 1 active + 1 target = 2 > max 1 -> rejected
        assertFalse(tight.requestTransition(new SceneTransitionRequest(SceneType.ORBITAL, new double[]{1, 0, 0})));
        assertFalse(rejects.isEmpty());
        assertTrue(rejects.get(rejects.size() - 1).reason.toLowerCase().contains("max"));
    }

    @Test
    void unknownSceneTypeUsesFallbackLoader() {
        // No loader registered for PLANET_SURFACE -> fallback (deepLoader) is used; transition still completes.
        manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
        runToIdle();
        assertTrue(manager.requestTransition(new SceneTransitionRequest(SceneType.PLANET_SURFACE, new double[]{2, 0, 0})));
        runToIdle();
        assertEquals(SceneType.PLANET_SURFACE, manager.getPrimaryScene().type);
    }

    @Test
    void notifyDisguiseCompleteSkipsTimeoutForSourcelessBoot() {
        // Mirrors the GameWorld boot: a fresh manager with the DEFAULT 5s disguise timeout.
        SceneManager boot = new SceneManager(bus, engine, loaders, deepLoader, 3);
        // notifyDisguiseComplete() is called immediately after the sourceless boot request,
        // while the controller is still in REQUESTED. The flag must persist to READY_OVERLAP
        // so the scene activates without waiting out the 5s timeout.
        boot.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
        boot.notifyDisguiseComplete();

        // Advance only a handful of small frames (~0.08s total, far below the 5s timeout).
        for (int i = 0; i < 5 && !boot.getController().isIdle(); i++) {
            boot.update(1f / 60f);
        }

        assertTrue(boot.getController().isIdle(), "boot transition should have completed promptly");
        assertNotNull(boot.getPrimaryScene());
        assertEquals(SceneType.DEEP_SPACE, boot.getPrimaryScene().type);
    }
}
