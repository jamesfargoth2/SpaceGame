---
name: libgdx-ship-flight-input
description: >
  Enforces Elite Dangerous-style ship flight controls for a libGDX 3D space
  game: the complete input-to-thruster pipeline including flight-assist ON/OFF
  toggle with a proper velocity-target feedback controller (not simple damping),
  6-DOF thruster command generation, throttle with 50% notch and FA-off
  behaviour, boost surge with ENG-bank cost and speed-cap override, configurable
  speed cap that respects pip allocation and hardpoint penalty, hardpoint
  deploy/stow toggle, silent running, and the combat drift techniques (boost
  strafe, FA-off turns) that emerge from this model. Use this skill whenever
  writing or modifying: ship input processing, flight assist mode switching,
  the velocity target controller, boost implementation, throttle behaviour,
  speed cap logic, input deadzone/response curves, or anything that translates
  raw player input into the six thruster axis commands the physics engine
  consumes. Reads raw axis values from the input layer and writes
  ShipThrusterCommands consumed by libgdx-ship-flight-physics. Read that
  skill first for the physics integration it drives.
---

# Elite Dangerous-Style Ship Flight Controls

## How ED Flight Assist Actually Works

FA+ is **not** simple velocity decay. It is a **velocity-target controller**:

1. Player input defines a *desired velocity* in ship-local space (throttle → forward target, sticks → rotation targets).
2. The controller computes the delta between current velocity and target.
3. It fires thrusters proportionally to close that delta — it's a PD controller, not friction.
4. When you release the stick in FA+, the target drops to zero, so the controller
   actively fires *opposite* thrusters to kill residual velocity. This is why the
   ship feels like it's "grabbing" your velocity, not sliding to a stop.

FA- removes the target entirely. Thrusters apply force only while you hold input.
The ship retains momentum in all directions indefinitely.

**Why this matters for feel:**
- In FA+, releasing thrust feels like applying a handbrake.
- Boosting in FA+ adds velocity beyond the cap; FA+ then bleeds it back over ~2 s.
- This bleed-back is why boost-strafe works in combat: boost → rotate → FA+ bleeds the old vector while you face a new direction.

---

## Data Structures

```java
/** Raw axis values from input hardware. All axes in [-1, +1] or [0, 1]. */
public class RawFlightInput {
    public float throttle;        // 0=off, 1=full fwd, -1=full reverse (if ship allows)
    public float pitch;           // +1 = nose up
    public float yaw;             // +1 = nose right
    public float roll;            // +1 = roll right (clockwise from cockpit)
    public float strafeX;         // +1 = strafe right
    public float strafeY;         // +1 = strafe up
    public boolean boostPressed;
    public boolean faTogglePressed;
    public boolean hardpointToggle;
    public boolean silentRunningToggle;
    public boolean throttleHalfNotch; // sets throttle to exactly 0.5 (mapped button)
}

/** Processed commands for the physics engine (libgdx-ship-flight-physics). */
public class ShipThrusterCommands {
    public float forwardThrust;   // [-1, +1]
    public float lateralThrust;   // [-1, +1] strafe X
    public float verticalThrust;  // [-1, +1] strafe Y
    public float pitchRate;       // [-1, +1]
    public float yawRate;         // [-1, +1]
    public float rollRate;        // [-1, +1]
    public boolean boostActive;
}

/** Per-ship flight characteristics, loaded from ship data JSON. */
public class ShipFlightProfile {
    public float maxSpeedBase;          // m/s at 2 pips ENG, hardpoints stowed
    public float maxReverseSpeed;       // usually 40–60% of forward max
    public float maxBoostSpeed;         // m/s during boost surge (exceeds cap)
    public float boostSurgeDuration;    // seconds the speed cap is lifted after boost
    public float boostDrainPct;         // fraction of ENG bank consumed per boost (e.g. 0.25)
    public float boostCooldownBase;     // seconds between boosts at 2 ENG pips
    public float pitchRate;             // deg/s at full deflection
    public float yawRate;               // deg/s
    public float rollRate;              // deg/s (usually faster than pitch/yaw)
    public float faLinearResponseHz;    // FA+ linear correction bandwidth (≈ 3–8 Hz)
    public float faAngularResponseHz;   // FA+ angular correction bandwidth (≈ 4–10 Hz)
    public float hardpointSpeedPenalty; // fraction of maxSpeed removed when hardpoints out (e.g. 0.08)
}
```

---

## Flight Assist Controller

```java
public class FlightAssistController {

    private boolean faEnabled = true;

    // Velocity targets in ship-LOCAL space (m/s)
    private final Vector3 linearTarget  = new Vector3();
    // Angular targets in ship-LOCAL space (rad/s)
    private final Vector3 angularTarget = new Vector3();

    /**
     * Main update: translate raw input into thruster commands.
     * Call every physics tick before ShipFlightSystem.update().
     *
     * @param input        processed (deadzoned + curved) pilot input
     * @param body         current physics state (velocity, angularVel)
     * @param profile      ship flight characteristics
     * @param currentSpeedCap  effective max speed this tick (from SpeedCapSystem)
     * @param dt           physics timestep (seconds)
     */
    public ShipThrusterCommands update(ProcessedFlightInput input,
                                        ShipPhysicsBody body,
                                        ShipFlightProfile profile,
                                        float currentSpeedCap,
                                        float dt) {
        ShipThrusterCommands cmd = new ShipThrusterCommands();

        if (faEnabled) {
            updateFA_On(input, body, profile, currentSpeedCap, dt, cmd);
        } else {
            updateFA_Off(input, body, profile, dt, cmd);
        }

        cmd.pitchRate = input.pitch * (profile.pitchRate * MathUtils.degreesToRadians);
        cmd.yawRate   = input.yaw   * (profile.yawRate   * MathUtils.degreesToRadians);
        cmd.rollRate  = input.roll  * (profile.rollRate   * MathUtils.degreesToRadians);

        cmd.boostActive = input.boostPressed;
        return cmd;
    }

    // ── FA+ ────────────────────────────────────────────────────────────────

    private void updateFA_On(ProcessedFlightInput input, ShipPhysicsBody body,
                              ShipFlightProfile profile, float speedCap,
                              float dt, ShipThrusterCommands cmd) {
        // 1. Compute current velocity in local space
        Vector3 localVel = toLocalSpace(body.velocity, body.orientation);

        // 2. Desired velocity: throttle sets Z (forward), strafes set X/Y
        //    Speed cap applies only to forward axis; lateral strafe uncapped (but smaller thrusters)
        float desiredZ = input.throttle * speedCap;
        float desiredX = input.strafeX  * profile.maxSpeedBase * 0.5f; // strafes are weaker
        float desiredY = input.strafeY  * profile.maxSpeedBase * 0.5f;

        // 3. Error = desired − current (in local space)
        float errZ = desiredZ - localVel.z;
        float errX = desiredX - localVel.x;
        float errY = desiredY - localVel.y;

        // 4. Proportional response — bandwidth sets how aggressively we correct
        //    bandwidth * 2π gives angular frequency; divide by max to get [−1,+1] command
        float gain = profile.faLinearResponseHz * MathUtils.PI2;
        cmd.forwardThrust  = MathUtils.clamp(errZ * gain * dt, -1f, 1f);
        cmd.lateralThrust  = MathUtils.clamp(errX * gain * dt, -1f, 1f);
        cmd.verticalThrust = MathUtils.clamp(errY * gain * dt, -1f, 1f);
    }

    // ── FA- ────────────────────────────────────────────────────────────────

    private void updateFA_Off(ProcessedFlightInput input, ShipPhysicsBody body,
                               ShipFlightProfile profile, float dt,
                               ShipThrusterCommands cmd) {
        // FA-: input maps directly to thruster throttle. No velocity feedback.
        // No speed cap enforcement — pilot must manage their own momentum.
        cmd.forwardThrust  = input.throttle;
        cmd.lateralThrust  = input.strafeX;
        cmd.verticalThrust = input.strafeY;
    }

    // ── Angular correction (shared between FA+ and FA-) ──────────────────

    /**
     * FA+ angular: when pilot releases stick, fire RCS to kill angular velocity.
     * FA-: angular velocity persists; RCS only fires while stick is held.
     * This is called after the linear controller sets pitch/yaw/rollRate in cmd.
     */
    public void applyAngularCorrection(ShipPhysicsBody body, ShipFlightProfile profile,
                                        ShipThrusterCommands cmd, float dt) {
        if (!faEnabled) return; // FA-: no auto-correction

        // PD controller on angular velocity.
        // If pilot input is small, damp existing rotation toward zero.
        float angGain = profile.faAngularResponseHz * MathUtils.PI2;

        if (Math.abs(cmd.pitchRate) < 0.05f) {
            float correction = -body.angularVel.x * angGain * dt;
            cmd.pitchRate = MathUtils.clamp(correction, -1f, 1f);
        }
        if (Math.abs(cmd.yawRate) < 0.05f) {
            float correction = -body.angularVel.y * angGain * dt;
            cmd.yawRate = MathUtils.clamp(correction, -1f, 1f);
        }
        if (Math.abs(cmd.rollRate) < 0.05f) {
            float correction = -body.angularVel.z * angGain * dt;
            cmd.rollRate = MathUtils.clamp(correction, -1f, 1f);
        }
    }

    public void toggleFA() { faEnabled = !faEnabled; }
    public boolean isFAEnabled() { return faEnabled; }

    private Vector3 toLocalSpace(Vector3 worldVec, Quaternion orientation) {
        return worldVec.cpy().mul(orientation.cpy().conjugate());
    }
}
```

---

## Speed Cap System

```java
/**
 * Computes the effective speed cap each tick.
 * Base cap modified by ENG pip allocation, hardpoint state, and boost surge.
 */
public class SpeedCapSystem {

    private float boostSurgeTimer = 0f; // counts down; >0 = in surge
    private float boostCooldown   = 0f; // counts down; >0 = boost blocked

    public float update(ShipFlightProfile profile, int engPips,
                         boolean hardpointsDeployed, float dt) {
        boostSurgeTimer = Math.max(0f, boostSurgeTimer - dt);
        boostCooldown   = Math.max(0f, boostCooldown   - dt);

        float cap = profile.maxSpeedBase;

        // ENG pip speed bonus: 0–4 pips gives ≈ −10% to +15% speed
        cap *= enginePipSpeedFactor(engPips);

        // Hardpoint penalty: deployed weapons reduce manoeuvring speed
        if (hardpointsDeployed) {
            cap *= (1f - profile.hardpointSpeedPenalty);
        }

        // Boost surge: temporarily lift the cap to boost max
        if (boostSurgeTimer > 0f) {
            cap = profile.maxBoostSpeed;
        }

        return cap;
    }

    /**
     * Attempt a boost. Returns true if boost fired.
     * @param engBank   current ENG capacitor charge [0–1]
     */
    public boolean tryBoost(ShipFlightProfile profile, EngineCapacitor engCap) {
        if (boostCooldown > 0f) return false;
        if (engCap.charge < profile.boostDrainPct) return false;

        engCap.charge -= profile.boostDrainPct;
        boostSurgeTimer = profile.boostSurgeDuration;

        // Cooldown scales with ENG pip allocation (more pips = faster cooldown)
        boostCooldown = profile.boostCooldownBase / engCap.rechargeFactor;

        return true;
    }

    public boolean canBoost() { return boostCooldown <= 0f; }

    /**
     * ENG pip speed factor table (Elite Dangerous approximate):
     *  0 pips: 0.84×    1 pip: 0.92×    2 pips: 1.00×
     *  3 pips: 1.08×    4 pips: 1.15×
     */
    private float enginePipSpeedFactor(int engPips) {
        return switch (Math.max(0, Math.min(4, engPips))) {
            case 0  -> 0.84f;
            case 1  -> 0.92f;
            case 2  -> 1.00f;
            case 3  -> 1.08f;
            default -> 1.15f;
        };
    }
}
```

---

## Boost Application

```java
public class BoostSystem {

    /**
     * When a boost fires, apply an immediate velocity impulse in the forward
     * direction and add heat to the ship's thermal budget.
     * FA+ will bleed this velocity back toward the cap over the surge window.
     * FA- preserves it indefinitely — the pilot must manage the vector manually.
     */
    public void applyBoostImpulse(ShipPhysicsBody body, ShipFlightProfile profile,
                                   ShipThermalModel thermal) {
        // Direction: always the ship's forward axis in world space
        Vector3 forward = new Vector3(0, 0, 1).mul(body.orientation);

        // Velocity delta: boost target − current forward component
        float currentFwdSpeed = body.velocity.dot(forward);
        float impulse         = Math.max(0f, profile.maxBoostSpeed - currentFwdSpeed);

        body.velocity.mulAdd(forward, impulse);

        // Boost generates significant heat (ships can overheat from boosting)
        thermal.addHeat(profile.boostHeatGeneration);
    }
}
```

---

## Throttle Processing

```java
public class ThrottleProcessor {

    private static final float NOTCH_ZONE  = 0.04f; // deadzone around 50% notch
    private static final float DEADZONE    = 0.05f; // inner deadzone for all axes
    private static final float THROTTLE_HALF = 0.50f;

    /**
     * Process raw throttle axis value:
     * - Apply deadzone
     * - Snap to 50% notch if within NOTCH_ZONE (makes holding half-throttle easy)
     * - Apply response curve (quadratic for better fine control near zero)
     */
    public float processThrottle(float rawThrottle, boolean halfNotchButton) {
        if (halfNotchButton) return THROTTLE_HALF;

        // Deadzone
        float t = rawThrottle;
        if (Math.abs(t) < DEADZONE) return 0f;

        // 50% notch snap
        if (Math.abs(t - THROTTLE_HALF) < NOTCH_ZONE) return THROTTLE_HALF;

        // Quadratic response curve (more precision at low speed)
        return Math.signum(t) * t * t;
    }

    /**
     * Process a rotation axis (pitch, yaw, roll, strafe).
     * Centre deadzone + exponential response curve (ED uses expo for feel).
     */
    public float processAxis(float raw, float expo) {
        // expo in [0, 1]: 0 = linear, 1 = full exponential
        if (Math.abs(raw) < DEADZONE) return 0f;
        float linear = (Math.abs(raw) - DEADZONE) / (1f - DEADZONE) * Math.signum(raw);
        return linear * (1f - expo) + (linear * linear * linear) * expo;
    }
}
```

---

## Hardpoint Deploy / Stow

```java
public class HardpointSystem {

    private boolean deployed = false;
    private float   deployTimer = 0f;
    private static final float DEPLOY_TIME_S = 0.8f; // animation time

    /** Toggle hardpoints. Returns true if the toggle was accepted. */
    public boolean toggle(ShipState state) {
        // Cannot deploy in supercruise or while landing
        if (state.inSupercruise || state.landingGearDown) return false;
        deployed = !deployed;
        deployTimer = DEPLOY_TIME_S;
        EventBus.post(new HardpointEvent(deployed));
        return true;
    }

    public boolean isDeployed() { return deployed; }

    /**
     * Hardpoints being deployed has three effects:
     *  1. Speed cap penalty (see SpeedCapSystem.hardpointSpeedPenalty)
     *  2. Weapons fire becomes available
     *  3. Heat generation increases (weapons warm the hull)
     */
}
```

---

## Silent Running

```java
public class SilentRunningSystem {

    private boolean silentRunning = false;

    /**
     * Silent running: shuts down thermal radiators, drastically reducing
     * heat signature at the cost of rapid heat accumulation.
     * - Shields shut down immediately when SR activates
     * - Heat builds quickly; ship will overheat if maintained too long
     * - Reduces detection range from other ships
     * - Useful for stealth approaches or avoiding targeting
     */
    public void toggle(ShipThermalModel thermal, ShieldSystem shields) {
        silentRunning = !silentRunning;
        thermal.radiatorsActive = !silentRunning;
        if (silentRunning) shields.shutdown();
        else               shields.restore();
    }

    /** Heat buildup rate modifier when SR is active (multiplied by base heat gen). */
    public float heatBuildupMultiplier() { return silentRunning ? 3.5f : 1.0f; }

    public boolean isSilentRunning() { return silentRunning; }
}
```

---

## Combat Drift Techniques (ED Emergent Mechanics)

These are not separate systems — they emerge from the above. Document here for AI agents writing NPC flight AI:

```java
/**
 * Key combat manoeuvres that emerge from FA+/FA- and boost interaction.
 * NPC flight AI must be able to execute these.
 */
public class CombatFlightManoeuvres {

    /**
     * BOOST STRAFE
     * 1. Ensure FA+ ON.
     * 2. Fire boost. Ship surges forward beyond speed cap.
     * 3. Immediately rotate (yaw/pitch) to new heading.
     * 4. FA+ bleeds old forward vector while thrusters build new heading velocity.
     * 5. Allows rapid repositioning while maintaining fire window.
     *
     * Implementation: NPC boosts when distance > 600 m and angle to target > 45°.
     */

    /**
     * FA-OFF TURN (Speed Turn)
     * 1. Pilot disables FA (faTogglePressed).
     * 2. Ship retains all current velocity.
     * 3. Pilot pitches/yaws aggressively — no drag slows rotation.
     * 4. After facing new heading, pilot re-enables FA+.
     * 5. FA+ kills old vector while building new one.
     *
     * Far faster 180° than attempting it in FA+.
     * Effective at: close-range jousting, reversing to pursue escaped target.
     *
     * NPC use: switch to FA- when angular error > 90° and closing speed > 200 m/s.
     */

    /**
     * DRIFT ATTACK
     * 1. Pilot keeps FA- ON throughout.
     * 2. Maintains velocity vector aimed "past" target.
     * 3. Rotates to keep guns on target regardless of actual movement direction.
     * 4. Target cannot predict ship's actual trajectory from facing direction.
     *
     * NPC use: FA- mode when target is within 200 m and health < 30%.
     */
}
```

---

## Input Binding Reference (Default ED-style)

```java
/** Default control bindings — load from settings; these are fallbacks. */
public class DefaultFlightBindings {
    // Keyboard + Mouse
    public static final int KEY_PITCH_UP        = Input.Keys.S;
    public static final int KEY_PITCH_DOWN      = Input.Keys.W;
    public static final int KEY_YAW_LEFT        = Input.Keys.A;
    public static final int KEY_YAW_RIGHT       = Input.Keys.D;
    public static final int KEY_ROLL_LEFT       = Input.Keys.Q;
    public static final int KEY_ROLL_RIGHT      = Input.Keys.E;
    public static final int KEY_STRAFE_UP       = Input.Keys.R;
    public static final int KEY_STRAFE_DOWN     = Input.Keys.F;
    public static final int KEY_THROTTLE_UP     = Input.Keys.PAGE_UP;
    public static final int KEY_THROTTLE_DOWN   = Input.Keys.PAGE_DOWN;
    public static final int KEY_BOOST           = Input.Keys.TAB;
    public static final int KEY_FA_TOGGLE       = Input.Keys.Z;
    public static final int KEY_HARDPOINTS      = Input.Keys.U;
    public static final int KEY_SILENT_RUNNING  = Input.Keys.DEL;
    public static final int KEY_THROTTLE_HALF   = Input.Keys.HOME; // 50% notch

    // Gamepad axis mapping (Xbox layout)
    public static final int PAD_PITCH           = 1; // Left stick Y
    public static final int PAD_ROLL            = 0; // Left stick X
    public static final int PAD_YAW             = 2; // Right stick X
    public static final int PAD_STRAFE_Y        = 3; // Right stick Y
    public static final int PAD_THROTTLE        = 4; // Left trigger (−1) / Right trigger (+1)
    public static final int PAD_STRAFE_X        = 5; // D-pad X
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Implementing FA+ as `velocity *= dampFactor` | FA+ is a velocity-target PD controller, not decay. Kill it with `errV * gain * dt` toward the desired target. |
| Boost as raw velocity clamp removal | Boost fires an impulse (`addVelocity`) and lifts the cap for `boostSurgeDuration`; cap re-applies after |
| Speed cap enforced by clamping `body.velocity` | Never clamp velocity directly — it causes visible snapping. Cap is enforced through the FA+ controller's target velocity |
| Same speed cap regardless of pip allocation | ENG pip count changes max speed by ±15%; read from `SpeedCapSystem.enginePipSpeedFactor(engPips)` |
| Forgetting hardpoint speed penalty | Deployed hardpoints must reduce speed cap; pilots notice immediately if missing |
| FA toggle during supercruise | Block FA+ toggle while in supercruise — SC has its own flight model entirely |
| NPC AI using only FA+ | NPCs should use FA- off-turns and boost strafe or they will be trivially predictable |
| Boost available with empty ENG bank | Check `engCap.charge >= profile.boostDrainPct` before firing; deny and play a "no power" audio cue |
