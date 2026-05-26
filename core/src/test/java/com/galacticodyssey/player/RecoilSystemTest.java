package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.combat.events.RecoilEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.RecoilComponent;
import com.galacticodyssey.player.systems.RecoilSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecoilSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity player;
    private RecoilComponent recoil;
    private FPSCameraComponent camera;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new RecoilSystem(eventBus));

        player = new Entity();
        recoil = new RecoilComponent();
        recoil.recoverySpeed = 5f;
        recoil.maxPunch.set(10f, 5f);
        recoil.pattern = new Vector2[]{
            new Vector2(2f, 0f), new Vector2(1.5f, 0.5f), new Vector2(1f, -0.3f)
        };
        player.add(recoil);

        camera = new FPSCameraComponent();
        player.add(camera);

        engine.addEntity(player);
    }

    @Test
    void recoilEvent_addsPunchToCamera() {
        float initialPitch = camera.pitchAngle;
        eventBus.publish(new RecoilEvent(player, new Vector2(2f, 0f)));
        engine.update(0.016f);

        assertTrue(recoil.currentPunch.x > 0);
    }

    @Test
    void recoilDecays_towardZero() {
        recoil.currentPunch.set(5f, 2f);
        engine.update(1.0f);

        assertTrue(recoil.currentPunch.x < 5f);
        assertTrue(recoil.currentPunch.y < 2f);
    }

    @Test
    void recoilClamped_atMaxPunch() {
        for (int i = 0; i < 20; i++) {
            eventBus.publish(new RecoilEvent(player, new Vector2(2f, 1f)));
        }
        engine.update(0.016f);

        assertTrue(recoil.currentPunch.x <= recoil.maxPunch.x);
        assertTrue(recoil.currentPunch.y <= recoil.maxPunch.y);
    }

    @Test
    void patternIndex_advancesOnConsecutiveShots() {
        eventBus.publish(new RecoilEvent(player, new Vector2(2f, 0f)));
        engine.update(0.016f);
        assertEquals(1, recoil.patternIndex);

        eventBus.publish(new RecoilEvent(player, new Vector2(1.5f, 0.5f)));
        engine.update(0.016f);
        assertEquals(2, recoil.patternIndex);
    }
}
