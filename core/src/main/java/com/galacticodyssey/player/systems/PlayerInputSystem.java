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
    private boolean boardPressed;
    private boolean cameraTogglePressed;
    private boolean flightAssistTogglePressed;
    private boolean boostPressed;
    private boolean enabled = true;

    private static final float THROTTLE_RAMP_RATE = 1.5f;     // full-range in ~0.9s held
    // Keyboard ramp allows full reverse; ShipFlightSystem enforces the ship's
    // per-class reverseFraction cap authoritatively, so the ramp must not under-limit it.
    private static final float REVERSE_FRACTION_INPUT = 1f;

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
            if (keycode == Input.Keys.F) {
                interactPressed = true;
                return true;
            }
            if (keycode == Input.Keys.G) {
                boardPressed = true;
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
                nextTargetPressed = true;
                return true;
            }
            if (keycode == Input.Keys.Z) { flightAssistTogglePressed = true; return true; }
            if (keycode == Input.Keys.TAB) { boostPressed = true; return true; }
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
            boardPressed = false;
            cameraTogglePressed = false;
            flightAssistTogglePressed = false;
            boostPressed = false;
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
            input.leanLeft = Gdx.input.isKeyPressed(Input.Keys.Q);
            input.leanRight = Gdx.input.isKeyPressed(Input.Keys.E);
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

        if (input.crouch && accumulatedScrollDelta != 0) {
            input.crouchScrollSteps = Math.round(accumulatedScrollDelta);
            input.scrollDelta = 0;
        } else {
            input.crouchScrollSteps = 0;
            input.scrollDelta = accumulatedScrollDelta;
        }
        accumulatedScrollDelta = 0;

        if (interactPressed) { input.interactPressed = true; interactPressed = false; }
        if (cameraTogglePressed) { input.cameraTogglePressed = true; cameraTogglePressed = false; }

        targetLockPressed = false;
        nextTargetPressed = false;
    }

    // Piloting keys: W/S throttle±, X throttle-zero, A/D strafe, Space/Ctrl vertical,
    // Q/E roll, mouse pitch/yaw, Z flight-assist toggle, Tab boost.
    private void processFlightInput(Entity entity) {
        ShipFlightInputComponent flight = flightInputMapper.get(entity);
        if (flight == null) return;

        flight.strafe = 0;
        flight.verticalThrust = 0;
        flight.rollInput = 0;

        if (Gdx.input != null) {
            boolean throttleUp = Gdx.input.isKeyPressed(Input.Keys.W);
            boolean throttleDown = Gdx.input.isKeyPressed(Input.Keys.S);
            boolean throttleZero = Gdx.input.isKeyPressed(Input.Keys.X);
            flight.throttle = com.galacticodyssey.ship.systems.FlightControlMath.stepThrottle(
                flight.throttle, throttleUp, throttleDown, throttleZero,
                THROTTLE_RAMP_RATE, REVERSE_FRACTION_INPUT, Gdx.graphics.getDeltaTime());

            if (Gdx.input.isKeyPressed(Input.Keys.A)) flight.strafe -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) flight.strafe += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) flight.verticalThrust += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) flight.verticalThrust -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.Q)) flight.rollInput -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.E)) flight.rollInput += 1f;
        }

        float sensitivity = 0.15f;
        flight.pitchInput = accumulatedMouseDeltaY * sensitivity;
        flight.yawInput = accumulatedMouseDeltaX * sensitivity;
        accumulatedMouseDeltaX = 0;
        accumulatedMouseDeltaY = 0;

        flight.scrollDelta = accumulatedScrollDelta;
        accumulatedScrollDelta = 0;

        flight.fireHeld[0] = fireGroup0Held;
        flight.fireHeld[1] = fireGroup1Held;

        if (targetLockPressed) { flight.targetLockPressed = true; targetLockPressed = false; }
        if (nextTargetPressed) { flight.nextTargetPressed = true; nextTargetPressed = false; }

        if (cameraTogglePressed) { flight.cameraTogglePressed = true; cameraTogglePressed = false; }
        if (flightAssistTogglePressed) { flight.flightAssistTogglePressed = true; flightAssistTogglePressed = false; }
        if (boostPressed) { flight.boostPressed = true; boostPressed = false; }
        if (interactPressed) {
            PlayerInputComponent input = inputMapper.get(entity);
            if (input != null) input.interactPressed = true;
            interactPressed = false;
        }
        if (boardPressed) {
            PlayerInputComponent input = inputMapper.get(entity);
            if (input != null) input.boardPressed = true;
            boardPressed = false;
        }

        jumpPressed = false;
    }
}
