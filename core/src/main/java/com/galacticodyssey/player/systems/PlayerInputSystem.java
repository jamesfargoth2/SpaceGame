package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.galacticodyssey.combat.systems.CombatInputSystem;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;

public class PlayerInputSystem extends IteratingSystem {

    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);

    private float accumulatedMouseDeltaX;
    private float accumulatedMouseDeltaY;
    private boolean jumpPressed;
    private boolean interactPressed;
    private boolean cameraTogglePressed;
    private boolean enabled = true;

    private CombatInputSystem combatInputSystem;

    private final InputAdapter inputAdapter = new InputAdapter() {
        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            if (!enabled) return false;
            accumulatedMouseDeltaX += -Gdx.input.getDeltaX();
            accumulatedMouseDeltaY += -Gdx.input.getDeltaY();
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (!enabled) return false;
            accumulatedMouseDeltaX += -Gdx.input.getDeltaX();
            accumulatedMouseDeltaY += -Gdx.input.getDeltaY();
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (!enabled) return false;
            if (button == Input.Buttons.LEFT) {
                if (combatInputSystem != null) {
                    combatInputSystem.setFireInput(true);
                    combatInputSystem.setFireHeldInput(true);
                }
                return true;
            }
            if (button == Input.Buttons.RIGHT) {
                if (combatInputSystem != null) {
                    combatInputSystem.setBlockInput(true);
                    combatInputSystem.setBlockHeldInput(true);
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (!enabled) return false;
            if (button == Input.Buttons.LEFT) {
                if (combatInputSystem != null) {
                    combatInputSystem.setFireHeldInput(false);
                }
                return true;
            }
            if (button == Input.Buttons.RIGHT) {
                if (combatInputSystem != null) {
                    combatInputSystem.setBlockHeldInput(false);
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (!enabled) return false;
            if (keycode == Input.Keys.SPACE) {
                jumpPressed = true;
                return true;
            }
            if (keycode == Input.Keys.E) {
                interactPressed = true;
                return true;
            }
            if (keycode == Input.Keys.V) {
                cameraTogglePressed = true;
                if (combatInputSystem != null) {
                    combatInputSystem.setQuickMeleeInput();
                }
                return true;
            }
            if (keycode == Input.Keys.R) {
                if (combatInputSystem != null) {
                    combatInputSystem.setReloadInput();
                }
                return true;
            }
            if (keycode == Input.Keys.NUM_1) {
                if (combatInputSystem != null) {
                    combatInputSystem.setSwitchSlotInput(0);
                }
                return true;
            }
            if (keycode == Input.Keys.NUM_2) {
                if (combatInputSystem != null) {
                    combatInputSystem.setSwitchSlotInput(1);
                }
                return true;
            }
            if (keycode == Input.Keys.NUM_3) {
                if (combatInputSystem != null) {
                    combatInputSystem.setSwitchSlotInput(2);
                }
                return true;
            }
            return false;
        }
    };

    public void setCombatInputSystem(CombatInputSystem system) {
        this.combatInputSystem = system;
    }

    public PlayerInputSystem() {
        super(Family.all(PlayerInputComponent.class, PlayerTagComponent.class).get(), 0);
    }

    public void initialize() {
        Gdx.input.setCursorCatched(true);
    }

    public InputAdapter getInputAdapter() {
        return inputAdapter;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            accumulatedMouseDeltaX = 0;
            accumulatedMouseDeltaY = 0;
            jumpPressed = false;
            interactPressed = false;
            cameraTogglePressed = false;
        }
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
        if (combatInputSystem != null) {
            combatInputSystem.setMouseDeltaForMelee(accumulatedMouseDeltaX, accumulatedMouseDeltaY);
        }
        accumulatedMouseDeltaX = 0;
        accumulatedMouseDeltaY = 0;

        input.rollLeft = Gdx.input.isKeyPressed(Input.Keys.Z);
        input.rollRight = Gdx.input.isKeyPressed(Input.Keys.C);
        input.thrustUp = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        input.thrustDown = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT);

        if (interactPressed) { input.interactPressed = true; interactPressed = false; }
        if (cameraTogglePressed) { input.cameraTogglePressed = true; cameraTogglePressed = false; }
    }
}
