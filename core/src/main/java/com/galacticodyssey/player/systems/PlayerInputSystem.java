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
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;

public class PlayerInputSystem extends IteratingSystem {

    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<PlayerStateComponent> stateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<ShipFlightInputComponent> flightInputMapper =
        ComponentMapper.getFor(ShipFlightInputComponent.class);

    private float accumulatedMouseDeltaX;
    private float accumulatedMouseDeltaY;
    private float accumulatedScrollDelta;
    private boolean jumpPressed;
    private boolean interactPressed;
    private boolean cameraTogglePressed;
    private boolean enabled = true;

    private boolean fireGroup0Held;
    private boolean fireGroup1Held;
    private boolean targetLockPressed;
    private boolean nextTargetPressed;

    private CombatInputSystem combatInputSystem;

    private final InputAdapter inputAdapter = new InputAdapter() {
        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            if (!enabled) return false;
            accumulatedMouseDeltaX += Gdx.input.getDeltaX();
            accumulatedMouseDeltaY += -Gdx.input.getDeltaY();
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (!enabled) return false;
            accumulatedMouseDeltaX += Gdx.input.getDeltaX();
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
                fireGroup0Held = true;
                return true;
            }
            if (button == Input.Buttons.RIGHT) {
                if (combatInputSystem != null) {
                    combatInputSystem.setBlockInput(true);
                    combatInputSystem.setBlockHeldInput(true);
                }
                fireGroup1Held = true;
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
                fireGroup0Held = false;
                return true;
            }
            if (button == Input.Buttons.RIGHT) {
                if (combatInputSystem != null) {
                    combatInputSystem.setBlockHeldInput(false);
                }
                fireGroup1Held = false;
                return true;
            }
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (!enabled) return false;
            accumulatedScrollDelta += amountY;
            return true;
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
            if (keycode == Input.Keys.T) {
                targetLockPressed = true;
                return true;
            }
            if (keycode == Input.Keys.TAB) {
                nextTargetPressed = true;
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
            accumulatedScrollDelta = 0;
            jumpPressed = false;
            interactPressed = false;
            cameraTogglePressed = false;
            fireGroup0Held = false;
            fireGroup1Held = false;
            targetLockPressed = false;
            nextTargetPressed = false;
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerStateComponent state = stateMapper.get(entity);
        if (state != null && state.currentMode == PlayerMode.PILOTING) {
            processFlightInput(entity);
        } else {
            processFootInput(entity);
        }
    }

    private void processFootInput(Entity entity) {
        PlayerInputComponent input = inputMapper.get(entity);

        input.moveForward = 0;
        input.moveStrafe = 0;
        input.sprint = false;
        input.crouch = false;
        input.rollLeft = false;
        input.rollRight = false;
        input.thrustUp = false;
        input.thrustDown = false;

        if (Gdx.input != null) {
            if (Gdx.input.isKeyPressed(Input.Keys.W)) input.moveForward += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) input.moveForward -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) input.moveStrafe -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) input.moveStrafe += 1f;
            input.sprint = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);
            input.crouch = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT);
            input.rollLeft = Gdx.input.isKeyPressed(Input.Keys.Z);
            input.rollRight = Gdx.input.isKeyPressed(Input.Keys.C);
            input.thrustUp = Gdx.input.isKeyPressed(Input.Keys.SPACE);
            input.thrustDown = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT);
        }

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

        input.scrollDelta = accumulatedScrollDelta;
        accumulatedScrollDelta = 0;

        if (interactPressed) { input.interactPressed = true; interactPressed = false; }
        if (cameraTogglePressed) { input.cameraTogglePressed = true; cameraTogglePressed = false; }

        targetLockPressed = false;
        nextTargetPressed = false;
    }

    private void processFlightInput(Entity entity) {
        ShipFlightInputComponent flight = flightInputMapper.get(entity);
        if (flight == null) return;

        flight.throttle = 0;
        flight.strafe = 0;
        flight.verticalThrust = 0;
        flight.rollInput = 0;

        if (Gdx.input != null) {
            if (Gdx.input.isKeyPressed(Input.Keys.W)) flight.throttle += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) flight.throttle -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) flight.strafe -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) flight.strafe += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) flight.verticalThrust += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) flight.verticalThrust -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.Z)) flight.rollInput += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.C)) flight.rollInput -= 1f;
        }

        flight.pitchInput = accumulatedMouseDeltaY;
        flight.yawInput = accumulatedMouseDeltaX;
        accumulatedMouseDeltaX = 0;
        accumulatedMouseDeltaY = 0;

        flight.scrollDelta = accumulatedScrollDelta;
        accumulatedScrollDelta = 0;

        flight.fireHeld[0] = fireGroup0Held;
        flight.fireHeld[1] = fireGroup1Held;

        if (targetLockPressed) { flight.targetLockPressed = true; targetLockPressed = false; }
        if (nextTargetPressed) { flight.nextTargetPressed = true; nextTargetPressed = false; }

        if (cameraTogglePressed) { flight.cameraTogglePressed = true; cameraTogglePressed = false; }
        if (interactPressed) {
            PlayerInputComponent input = inputMapper.get(entity);
            if (input != null) input.interactPressed = true;
            interactPressed = false;
        }

        jumpPressed = false;
    }
}
