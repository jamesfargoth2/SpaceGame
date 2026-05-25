package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;

public class PlayerInputSystem extends IteratingSystem {

    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);

    private float accumulatedMouseDeltaX;
    private float accumulatedMouseDeltaY;
    private boolean jumpPressed;
    private boolean cursorCatched = true;

    private final InputAdapter inputAdapter = new InputAdapter() {
        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            accumulatedMouseDeltaX += -Gdx.input.getDeltaX();
            accumulatedMouseDeltaY += -Gdx.input.getDeltaY();
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            accumulatedMouseDeltaX += -Gdx.input.getDeltaX();
            accumulatedMouseDeltaY += -Gdx.input.getDeltaY();
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.SPACE) {
                jumpPressed = true;
                return true;
            }
            if (keycode == Input.Keys.ESCAPE) {
                cursorCatched = !cursorCatched;
                Gdx.input.setCursorCatched(cursorCatched);
                return true;
            }
            return false;
        }
    };

    public PlayerInputSystem() {
        super(Family.all(PlayerInputComponent.class, PlayerTagComponent.class).get(), 0);
    }

    public void initialize() {
        Gdx.input.setInputProcessor(inputAdapter);
        Gdx.input.setCursorCatched(true);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerInputComponent input = inputMapper.get(entity);

        input.moveForward = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) input.moveForward += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) input.moveForward -= 1f;

        input.moveStrafe = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) input.moveStrafe -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) input.moveStrafe += 1f;

        input.sprint = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);
        input.crouch = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT);

        if (jumpPressed) {
            input.jumpRequested = true;
            jumpPressed = false;
        }

        input.mouseDeltaX = accumulatedMouseDeltaX;
        input.mouseDeltaY = accumulatedMouseDeltaY;
        accumulatedMouseDeltaX = 0;
        accumulatedMouseDeltaY = 0;
    }
}
