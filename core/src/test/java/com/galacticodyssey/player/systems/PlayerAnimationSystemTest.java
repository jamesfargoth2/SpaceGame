package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerModelComponent;
import com.galacticodyssey.player.components.PlayerModelComponent.AnimState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerAnimationSystemTest {

    private Engine engine;
    private PlayerAnimationSystem system;
    private Entity player;
    private MovementStateComponent movement;
    private PlayerModelComponent model;
    private FPSCameraComponent cam;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        system = new PlayerAnimationSystem();
        engine.addSystem(system);

        player = new Entity();
        player.add(new TransformComponent());
        movement = new MovementStateComponent();
        player.add(movement);
        model = new PlayerModelComponent();
        player.add(model);
        cam = new FPSCameraComponent();
        player.add(cam);
        engine.addEntity(player);
    }

    @Test
    void idleWhenStandingStill() {
        movement.isGrounded = true;
        movement.currentSpeed = 0f;
        engine.update(0.016f);
        assertEquals(AnimState.IDLE, model.currentAnim);
    }

    @Test
    void walkWhenMovingSlowly() {
        movement.isGrounded = true;
        movement.currentSpeed = 2f;
        engine.update(0.016f);
        assertEquals(AnimState.WALK, model.currentAnim);
    }

    @Test
    void runWhenMovingFast() {
        movement.isGrounded = true;
        movement.currentSpeed = 6f;
        engine.update(0.016f);
        assertEquals(AnimState.RUN, model.currentAnim);
    }

    @Test
    void crouchIdleWhenCrouchingStill() {
        movement.isGrounded = true;
        movement.isCrouching = true;
        movement.currentSpeed = 0f;
        engine.update(0.016f);
        assertEquals(AnimState.CROUCH_IDLE, model.currentAnim);
    }

    @Test
    void crouchWalkWhenCrouchingAndMoving() {
        movement.isGrounded = true;
        movement.isCrouching = true;
        movement.currentSpeed = 2f;
        engine.update(0.016f);
        assertEquals(AnimState.CROUCH_WALK, model.currentAnim);
    }

    @Test
    void fallWhenAirborneAndDescending() {
        movement.isGrounded = false;
        movement.fallVelocity = -5f;
        engine.update(0.016f);
        assertEquals(AnimState.FALL, model.currentAnim);
    }

    @Test
    void jumpWhenAirborneAndAscending() {
        movement.isGrounded = false;
        movement.fallVelocity = 3f;
        engine.update(0.016f);
        assertEquals(AnimState.JUMP, model.currentAnim);
    }

    @Test
    void resolvesStateEvenWithoutModelInstance() {
        model.modelInstance = null;
        movement.isGrounded = true;
        movement.currentSpeed = 6f;
        engine.update(0.016f);
        assertEquals(AnimState.RUN, model.currentAnim);
    }
}
