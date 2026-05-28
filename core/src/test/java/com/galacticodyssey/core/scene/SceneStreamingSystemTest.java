package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.scene.support.FakeSceneLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SceneStreamingSystemTest {

    private Engine engine;
    private SceneManager manager;
    private SceneStreamingSystem system;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        EventBus bus = new EventBus();
        FakeSceneLoader deep = new FakeSceneLoader(SceneType.DEEP_SPACE);
        FakeSceneLoader orbital = new FakeSceneLoader(SceneType.ORBITAL);
        Map<SceneType, SceneLoader> loaders = new EnumMap<>(SceneType.class);
        loaders.put(SceneType.DEEP_SPACE, deep);
        loaders.put(SceneType.ORBITAL, orbital);
        manager = new SceneManager(bus, engine, loaders, deep, 3);
        manager.getController().setDisguiseTimeout(0f);

        // Start in deep space.
        manager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, new double[]{0, 0, 0}));
        for (int i = 0; i < 8 && !manager.getController().isIdle(); i++) manager.update(0.1f);

        system = new SceneStreamingSystem(manager);
        system.configureOrbitalTrigger(
            new SceneDistanceTrigger(1000f, 1500f),
            new Vector3(0, 0, 0),          // body local position
            new double[]{0, 0, 0},          // orbital scene anchor
            new double[]{99999, 0, 0});     // deep-space return anchor
        engine.addSystem(system);
    }

    private void pump(int frames) {
        for (int i = 0; i < frames; i++) engine.update(0.1f);
    }

    @Test
    void crossingEnterRadiusTransitionsToOrbital() {
        system.setPlayerPosition(new Vector3(900f, 0, 0)); // within enter radius
        pump(8);
        assertEquals(SceneType.ORBITAL, manager.getPrimaryScene().type);
    }

    @Test
    void stayingInHysteresisBandDoesNotTransition() {
        system.setPlayerPosition(new Vector3(1200f, 0, 0)); // in the band, still outside
        pump(8);
        assertEquals(SceneType.DEEP_SPACE, manager.getPrimaryScene().type);
    }

    @Test
    void leavingExitRadiusReturnsToDeepSpace() {
        system.setPlayerPosition(new Vector3(900f, 0, 0));
        pump(8);
        assertEquals(SceneType.ORBITAL, manager.getPrimaryScene().type);
        system.setPlayerPosition(new Vector3(1600f, 0, 0)); // beyond exit radius
        pump(8);
        assertEquals(SceneType.DEEP_SPACE, manager.getPrimaryScene().type);
    }
}
