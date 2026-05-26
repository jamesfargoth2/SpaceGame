package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.player.components.ScreenShakeComponent;
import com.galacticodyssey.player.systems.ScreenShakeSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScreenShakeSystemTest {

    private Engine engine;
    private Entity camera;
    private ScreenShakeComponent shake;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new ScreenShakeSystem());

        camera = new Entity();
        shake = new ScreenShakeComponent();
        shake.decayRate = 1f;
        camera.add(shake);
        engine.addEntity(camera);
    }

    @Test
    void traumaDecays_overTime() {
        shake.trauma = 0.8f;
        engine.update(0.5f);
        assertEquals(0.3f, shake.trauma, 0.01f);
    }

    @Test
    void traumaClamped_atZero() {
        shake.trauma = 0.1f;
        engine.update(1.0f);
        assertEquals(0f, shake.trauma, 0.01f);
    }

    @Test
    void traumaClamped_atOne() {
        shake.trauma = 0.8f;
        shake.addTrauma(0.5f);
        assertEquals(1.0f, shake.trauma, 0.01f);
    }

    @Test
    void shakeIntensity_isTraumaSquared() {
        shake.trauma = 0.5f;
        assertEquals(0.25f, shake.getIntensity(), 0.01f);
    }
}
