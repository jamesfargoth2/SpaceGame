package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.player.components.ADSComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.systems.WeaponSwaySystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponSwaySystemTest {

    private Engine engine;
    private Entity player;
    private FPSCameraComponent camera;
    private MovementStateComponent movement;
    private ADSComponent ads;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new WeaponSwaySystem());

        player = new Entity();
        camera = new FPSCameraComponent();
        player.add(camera);
        movement = new MovementStateComponent();
        player.add(movement);
        ads = new ADSComponent();
        player.add(ads);
        engine.addEntity(player);
    }

    @Test
    void moving_producesHeadBob() {
        movement.currentSpeed = 5f;
        movement.isGrounded = true;
        float initialPhase = camera.headBobPhase;
        engine.update(0.1f);
        assertNotEquals(initialPhase, camera.headBobPhase);
    }

    @Test
    void stationary_noHeadBobPhaseAdvance() {
        movement.currentSpeed = 0f;
        movement.isGrounded = true;
        float initialPhase = camera.headBobPhase;
        engine.update(0.1f);
        assertEquals(initialPhase, camera.headBobPhase, 0.01f);
    }

    @Test
    void aiming_suppressesSway() {
        movement.currentSpeed = 5f;
        movement.isGrounded = true;
        ads.adsProgress = 1.0f;
        engine.update(0.5f);

        float aimingAmplitude = camera.headBobAmplitude * (1f - ads.adsProgress);
        assertEquals(0f, aimingAmplitude, 0.01f);
    }
}
