package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.player.components.ADSComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.systems.ADSSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ADSSystemTest {

    private Engine engine;
    private Entity player;
    private ADSComponent ads;
    private CombatInputComponent input;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new ADSSystem());

        player = new Entity();
        ads = new ADSComponent();
        ads.adsSpeed = 5f;
        ads.zoomMultiplier = 0.7f;
        ads.spreadMultiplier = 0.3f;
        ads.moveSpeedMultiplier = 0.6f;
        player.add(ads);

        input = new CombatInputComponent();
        player.add(input);

        FPSCameraComponent cam = new FPSCameraComponent();
        player.add(cam);

        engine.addEntity(player);
    }

    @Test
    void aimHeld_increasesAdsProgress() {
        input.aimHeld = true;
        engine.update(0.1f);
        assertTrue(ads.adsProgress > 0f);
    }

    @Test
    void aimReleased_decreasesAdsProgress() {
        ads.adsProgress = 1.0f;
        input.aimHeld = false;
        engine.update(0.1f);
        assertTrue(ads.adsProgress < 1.0f);
    }

    @Test
    void adsProgress_clampedToZeroOne() {
        input.aimHeld = true;
        for (int i = 0; i < 100; i++) {
            engine.update(0.1f);
        }
        assertEquals(1.0f, ads.adsProgress, 0.01f);
    }
}
