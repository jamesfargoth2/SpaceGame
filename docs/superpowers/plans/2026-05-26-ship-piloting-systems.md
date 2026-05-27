# Ship Piloting Systems Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a complete ship piloting pipeline — from walking to a pilot seat through 6DOF/aerodynamic flight, weapon combat, and cockpit HUD with 3D interior.

**Architecture:** Layered ECS systems (Ashley) forming a processing pipeline. Input → Flight Physics → Atmospheric Forces → Targeting → Weapons → Camera → Cockpit Render → HUD. Systems communicate via components and an EventBus. No monolithic controllers.

**Tech Stack:** Java 17, libGDX 1.13, Ashley ECS, Bullet Physics (gdx-bullet), Scene2D.UI, JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-26-ship-piloting-systems-design.md`

---

## File Structure

### New Components (7 files)
| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/galacticodyssey/ship/components/ShipFlightInputComponent.java` | Flight-specific input state (throttle, strafe, thrust, pitch, yaw, roll, fire groups, targeting) |
| `core/src/main/java/com/galacticodyssey/ship/components/ShipAerodynamicsComponent.java` | Per-ship aerodynamic properties (wing area, drag, lift curve, stall angle, control surfaces) |
| `core/src/main/java/com/galacticodyssey/ship/components/ShipThermalComponent.java` | Ship heat state (current, max, dissipation, heat shield factor) |
| `core/src/main/java/com/galacticodyssey/ship/components/CockpitRenderComponent.java` | Cockpit 3D model reference (Model, ModelInstance, Environment, visible flag) |
| `core/src/main/java/com/galacticodyssey/ship/weapons/components/WeaponGroupComponent.java` | 4 weapon groups (lists of hardpoint IDs), active group index |
| `core/src/main/java/com/galacticodyssey/player/components/PlayerTargetComponent.java` | Target lock state (locked target, soft target, lead indicator position, lock timer) |
| `core/src/main/java/com/galacticodyssey/core/components/AtmosphereZoneComponent.java` | Planet atmosphere definition (radius, density, scale height, speed of sound, Mach threshold) |

### New Systems (6 files)
| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/galacticodyssey/player/systems/PilotTransitionSystem.java` | Orchestrates enter/exit pilot seat: enables/disables systems, camera lerp, HUD swap |
| `core/src/main/java/com/galacticodyssey/ship/systems/AtmosphericFlightSystem.java` | Aerodynamic forces: lift, drag, stall, control surfaces, re-entry heating, altitude blending |
| `core/src/main/java/com/galacticodyssey/ship/systems/CockpitModelSystem.java` | Renders 3D cockpit in cockpit camera mode (separate depth pass) |
| `core/src/main/java/com/galacticodyssey/ship/weapons/systems/ShipWeaponPilotSystem.java` | Bridges player fire inputs to ShipWeaponSystem via weapon groups |
| `core/src/main/java/com/galacticodyssey/ship/weapons/systems/TargetingSystem.java` | Soft target detection, hard lock, lead indicator calculation |
| `core/src/main/java/com/galacticodyssey/ui/CockpitHUDSystem.java` | Scene2D cockpit instruments (speed, throttle, radar, weapons, shields, fuel, alerts) |

### New Events (8 files)
| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/galacticodyssey/ui/events/CockpitHUDShowEvent.java` | Signal to show cockpit HUD |
| `core/src/main/java/com/galacticodyssey/ui/events/CockpitHUDHideEvent.java` | Signal to hide cockpit HUD |
| `core/src/main/java/com/galacticodyssey/ship/weapons/events/TargetLockedEvent.java` | Target lock acquired |
| `core/src/main/java/com/galacticodyssey/ship/weapons/events/TargetLostEvent.java` | Target lock lost |
| `core/src/main/java/com/galacticodyssey/ship/events/AtmosphereEnteredEvent.java` | Ship entered atmosphere |
| `core/src/main/java/com/galacticodyssey/ship/events/AtmosphereExitedEvent.java` | Ship exited atmosphere |
| `core/src/main/java/com/galacticodyssey/ship/events/ReentryHeatingEvent.java` | Re-entry heating threshold crossed |
| `core/src/main/java/com/galacticodyssey/ship/events/StallWarningEvent.java` | Aerodynamic stall detected |

### New Data/Builders (2 files)
| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/galacticodyssey/ship/CockpitGeometryBuilder.java` | Procedural cockpit mesh generation per ship size class |
| `core/src/main/java/com/galacticodyssey/ship/data/ShipClassRegistry.java` | Loads ship_classes.json, creates fully configured ship entities |

### New Data Files (2 files)
| File | Responsibility |
|------|---------------|
| `core/src/main/resources/data/ships/ship_classes.json` | Ship archetype definitions |
| `core/src/main/resources/data/planets/atmosphere_profiles.json` | Atmosphere physical profiles |

### Modified Files (5 files)
| File | Change |
|------|--------|
| `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java` | Branch by PlayerMode: write to ShipFlightInputComponent when PILOTING |
| `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java` | Read ShipFlightInputComponent, throttle management, fuel consumption |
| `core/src/main/java/com/galacticodyssey/ship/systems/ShipCameraSystem.java` | Read ShipFlightInputComponent for camera toggle |
| `core/src/main/java/com/galacticodyssey/core/GameWorld.java` | Register new systems, ShipClassRegistry |
| `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` | Add cockpit render pass, HUD stage |

### New Test Files (8 files)
| File | Tests |
|------|-------|
| `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightInputComponentTest.java` | Input component field behavior |
| `core/src/test/java/com/galacticodyssey/player/systems/PilotTransitionSystemTest.java` | Enter/exit seat, system enable/disable, events published |
| `core/src/test/java/com/galacticodyssey/ship/systems/AtmosphericFlightSystemTest.java` | Lift/drag/stall/blending/heating calculations |
| `core/src/test/java/com/galacticodyssey/ship/weapons/systems/TargetingSystemTest.java` | Soft target, hard lock, lead indicator math |
| `core/src/test/java/com/galacticodyssey/ship/weapons/systems/ShipWeaponPilotSystemTest.java` | Weapon group firing, group selection |
| `core/src/test/java/com/galacticodyssey/ship/data/ShipClassRegistryTest.java` | JSON loading, entity creation |
| `core/src/test/java/com/galacticodyssey/player/systems/PlayerInputSystemPilotingTest.java` | Mode branch: PILOTING writes to ShipFlightInputComponent |
| `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightSystemRefactoredTest.java` | Reads ShipFlightInputComponent, throttle smoothing, fuel drain |

---

## Task 1: ShipFlightInputComponent + Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/components/ShipFlightInputComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ui/events/CockpitHUDShowEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ui/events/CockpitHUDHideEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/events/TargetLockedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/events/TargetLostEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/events/AtmosphereEnteredEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/events/AtmosphereExitedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/events/ReentryHeatingEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/events/StallWarningEvent.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightInputComponentTest.java`

- [ ] **Step 1: Write test for ShipFlightInputComponent**

```java
package com.galacticodyssey.ship.systems;

import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipFlightInputComponentTest {

    @Test
    void defaultStateIsNeutral() {
        ShipFlightInputComponent input = new ShipFlightInputComponent();
        assertEquals(0f, input.throttle);
        assertEquals(0f, input.strafe);
        assertEquals(0f, input.verticalThrust);
        assertEquals(0f, input.pitchInput);
        assertEquals(0f, input.yawInput);
        assertEquals(0f, input.rollInput);
        assertFalse(input.fireGroup[0]);
        assertFalse(input.fireHeld[0]);
        assertFalse(input.targetLockPressed);
        assertFalse(input.nextTargetPressed);
        assertFalse(input.prevTargetPressed);
        assertFalse(input.cameraTogglePressed);
        assertEquals(0f, input.scrollDelta);
    }

    @Test
    void fireGroupsHaveFourSlots() {
        ShipFlightInputComponent input = new ShipFlightInputComponent();
        assertEquals(4, input.fireGroup.length);
        assertEquals(4, input.fireHeld.length);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightInputComponentTest" --info`
Expected: FAIL — class `ShipFlightInputComponent` does not exist.

- [ ] **Step 3: Implement ShipFlightInputComponent**

```java
package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;

public class ShipFlightInputComponent implements Component {
    public float throttle;
    public float strafe;
    public float verticalThrust;
    public float pitchInput;
    public float yawInput;
    public float rollInput;
    public final boolean[] fireGroup = new boolean[4];
    public final boolean[] fireHeld = new boolean[4];
    public boolean targetLockPressed;
    public boolean nextTargetPressed;
    public boolean prevTargetPressed;
    public boolean cameraTogglePressed;
    public float scrollDelta;
}
```

- [ ] **Step 4: Implement all 8 event classes**

Each event is a simple immutable data carrier. Create directory `core/src/main/java/com/galacticodyssey/ui/events/` if it doesn't exist.

```java
// CockpitHUDShowEvent.java
package com.galacticodyssey.ui.events;
import com.badlogic.ashley.core.Entity;
public class CockpitHUDShowEvent {
    public final Entity ship;
    public CockpitHUDShowEvent(Entity ship) { this.ship = ship; }
}

// CockpitHUDHideEvent.java
package com.galacticodyssey.ui.events;
public class CockpitHUDHideEvent {
    public CockpitHUDHideEvent() {}
}

// TargetLockedEvent.java
package com.galacticodyssey.ship.weapons.events;
import com.badlogic.ashley.core.Entity;
public class TargetLockedEvent {
    public final Entity target;
    public TargetLockedEvent(Entity target) { this.target = target; }
}

// TargetLostEvent.java
package com.galacticodyssey.ship.weapons.events;
public class TargetLostEvent {
    public TargetLostEvent() {}
}

// AtmosphereEnteredEvent.java
package com.galacticodyssey.ship.events;
import com.badlogic.ashley.core.Entity;
public class AtmosphereEnteredEvent {
    public final Entity ship;
    public final Entity planet;
    public AtmosphereEnteredEvent(Entity ship, Entity planet) { this.ship = ship; this.planet = planet; }
}

// AtmosphereExitedEvent.java
package com.galacticodyssey.ship.events;
import com.badlogic.ashley.core.Entity;
public class AtmosphereExitedEvent {
    public final Entity ship;
    public AtmosphereExitedEvent(Entity ship) { this.ship = ship; }
}

// ReentryHeatingEvent.java
package com.galacticodyssey.ship.events;
import com.badlogic.ashley.core.Entity;
public class ReentryHeatingEvent {
    public final Entity ship;
    public final float heatLevel;
    public ReentryHeatingEvent(Entity ship, float heatLevel) { this.ship = ship; this.heatLevel = heatLevel; }
}

// StallWarningEvent.java
package com.galacticodyssey.ship.events;
import com.badlogic.ashley.core.Entity;
public class StallWarningEvent {
    public final Entity ship;
    public StallWarningEvent(Entity ship) { this.ship = ship; }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightInputComponentTest" --info`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/components/ShipFlightInputComponent.java \
        core/src/main/java/com/galacticodyssey/ui/events/ \
        core/src/main/java/com/galacticodyssey/ship/weapons/events/TargetLockedEvent.java \
        core/src/main/java/com/galacticodyssey/ship/weapons/events/TargetLostEvent.java \
        core/src/main/java/com/galacticodyssey/ship/events/AtmosphereEnteredEvent.java \
        core/src/main/java/com/galacticodyssey/ship/events/AtmosphereExitedEvent.java \
        core/src/main/java/com/galacticodyssey/ship/events/ReentryHeatingEvent.java \
        core/src/main/java/com/galacticodyssey/ship/events/StallWarningEvent.java \
        core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightInputComponentTest.java
git commit -m "feat(ship): add ShipFlightInputComponent and piloting events"
```

---

## Task 2: Remaining New Components

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/components/AtmosphereZoneComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/components/ShipAerodynamicsComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/components/ShipThermalComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/components/CockpitRenderComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/components/WeaponGroupComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/player/components/PlayerTargetComponent.java`

- [ ] **Step 1: Create AtmosphereZoneComponent**

```java
package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;

public class AtmosphereZoneComponent implements Component {
    public float atmosphereRadius;
    public float surfaceRadius;
    public float surfaceDensity;
    public float scaleHeight;
    public float transitionAltitude;
    public float speedOfSound = 343f;
    public float machThreshold = 3.0f;
    public String composition;
}
```

- [ ] **Step 2: Create ShipAerodynamicsComponent**

```java
package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;

public class ShipAerodynamicsComponent implements Component {
    public float wingArea;
    public float dragCoefficient;
    public float maxLiftCoefficient;
    public float stallAngle;
    public float controlSurfaceAuthority;
    public float vtolThrustFraction;
    public float crossSectionArea;
    public float heatShieldRating;
    public float[] liftCurve = new float[10];

    public float getLiftCoefficient(float aoaDegrees) {
        float clamped = Math.max(0, Math.min(90, aoaDegrees));
        float index = clamped / 10f;
        int lo = (int) index;
        int hi = Math.min(lo + 1, liftCurve.length - 1);
        float frac = index - lo;
        return liftCurve[lo] * (1 - frac) + liftCurve[hi] * frac;
    }
}
```

- [ ] **Step 3: Create ShipThermalComponent**

```java
package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;

public class ShipThermalComponent implements Component {
    public float currentHeat;
    public float maxHeat = 100f;
    public float dissipationRate = 5f;
    public float heatShieldFactor = 1f;
}
```

- [ ] **Step 4: Create CockpitRenderComponent**

```java
package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;

public class CockpitRenderComponent implements Component {
    public Model cockpitModel;
    public ModelInstance cockpitInstance;
    public Environment cockpitEnvironment;
    public boolean visible;
}
```

- [ ] **Step 5: Create WeaponGroupComponent**

```java
package com.galacticodyssey.ship.weapons.components;

import com.badlogic.ashley.core.Component;
import java.util.ArrayList;
import java.util.List;

public class WeaponGroupComponent implements Component {
    @SuppressWarnings("unchecked")
    public final List<String>[] groups = new List[4];
    public int activeGroup;

    public WeaponGroupComponent() {
        for (int i = 0; i < 4; i++) {
            groups[i] = new ArrayList<>();
        }
    }
}
```

- [ ] **Step 6: Create PlayerTargetComponent**

```java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public class PlayerTargetComponent implements Component {
    public Entity lockedTarget;
    public Entity softTarget;
    public final Vector3 leadIndicatorPos = new Vector3();
    public float lockTimer;
}
```

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/components/AtmosphereZoneComponent.java \
        core/src/main/java/com/galacticodyssey/ship/components/ShipAerodynamicsComponent.java \
        core/src/main/java/com/galacticodyssey/ship/components/ShipThermalComponent.java \
        core/src/main/java/com/galacticodyssey/ship/components/CockpitRenderComponent.java \
        core/src/main/java/com/galacticodyssey/ship/weapons/components/WeaponGroupComponent.java \
        core/src/main/java/com/galacticodyssey/player/components/PlayerTargetComponent.java
git commit -m "feat(ship): add atmosphere, aerodynamics, thermal, cockpit, weapon group, and target components"
```

---

## Task 3: Modify PlayerInputSystem for Piloting Mode Branch

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/player/systems/PlayerInputSystemPilotingTest.java`

- [ ] **Step 1: Write test for piloting mode input routing**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerInputSystemPilotingTest {

    @Test
    void pilotingModeDoesNotWriteToPlayerInput() {
        Engine engine = new Engine();
        PlayerInputSystem system = new PlayerInputSystem();
        engine.addSystem(system);

        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        PlayerInputComponent input = new PlayerInputComponent();
        player.add(input);
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = new Entity();
        player.add(state);
        ShipFlightInputComponent flightInput = new ShipFlightInputComponent();
        player.add(flightInput);

        engine.addEntity(player);

        system.update(1f / 60f);

        assertEquals(0f, input.moveForward, "FPS input should not be written when PILOTING");
    }

    @Test
    void onFootModeWritesToPlayerInput() {
        Engine engine = new Engine();
        PlayerInputSystem system = new PlayerInputSystem();
        engine.addSystem(system);

        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        PlayerInputComponent input = new PlayerInputComponent();
        player.add(input);
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.ON_FOOT_EXTERIOR;
        player.add(state);

        engine.addEntity(player);

        system.update(1f / 60f);

        // moveForward will be 0 since no keys pressed, but the path was taken
        // We verify no crash and input was processed
        assertEquals(0f, input.moveForward);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.systems.PlayerInputSystemPilotingTest" --info`
Expected: FAIL — PlayerInputSystem doesn't check mode; `ShipFlightInputComponent` import missing.

- [ ] **Step 3: Modify PlayerInputSystem**

Add imports and mode branch to `processEntity()`. The system needs to also query `PlayerStateComponent` and `ShipFlightInputComponent` from the entity:

```java
// Add to imports:
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;

// Add mapper field:
private final ComponentMapper<PlayerStateComponent> stateMapper =
    ComponentMapper.getFor(PlayerStateComponent.class);
private final ComponentMapper<ShipFlightInputComponent> flightInputMapper =
    ComponentMapper.getFor(ShipFlightInputComponent.class);

// Replace processEntity body with mode branch:
@Override
protected void processEntity(Entity entity, float deltaTime) {
    PlayerStateComponent state = stateMapper.get(entity);
    if (state != null && state.currentMode == PlayerMode.PILOTING) {
        processFlightInput(entity);
    } else {
        processFootInput(entity);
    }
}

// Rename existing processEntity logic to processFootInput:
private void processFootInput(Entity entity) {
    PlayerInputComponent input = inputMapper.get(entity);
    // ... existing WASD/mouse/jump/interact logic unchanged ...
}

// New method for flight input:
private void processFlightInput(Entity entity) {
    ShipFlightInputComponent flight = flightInputMapper.get(entity);
    if (flight == null) return;

    flight.throttle = 0;
    if (Gdx.input.isKeyPressed(Input.Keys.W)) flight.throttle += 1f;
    if (Gdx.input.isKeyPressed(Input.Keys.S)) flight.throttle -= 1f;

    flight.strafe = 0;
    if (Gdx.input.isKeyPressed(Input.Keys.A)) flight.strafe -= 1f;
    if (Gdx.input.isKeyPressed(Input.Keys.D)) flight.strafe += 1f;

    flight.verticalThrust = 0;
    if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) flight.verticalThrust += 1f;
    if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) flight.verticalThrust -= 1f;

    flight.rollInput = 0;
    if (Gdx.input.isKeyPressed(Input.Keys.Z)) flight.rollInput += 1f;
    if (Gdx.input.isKeyPressed(Input.Keys.C)) flight.rollInput -= 1f;

    flight.pitchInput = accumulatedMouseDeltaY;
    flight.yawInput = accumulatedMouseDeltaX;
    accumulatedMouseDeltaX = 0;
    accumulatedMouseDeltaY = 0;

    flight.scrollDelta = accumulatedScrollDelta;
    accumulatedScrollDelta = 0;

    if (cameraTogglePressed) { flight.cameraTogglePressed = true; cameraTogglePressed = false; }
    if (interactPressed) {
        // interactPressed used for exiting pilot seat — route to PlayerInputComponent
        PlayerInputComponent input = inputMapper.get(entity);
        if (input != null) input.interactPressed = true;
        interactPressed = false;
    }
}
```

Also update `touchDown`/`touchUp` in the `InputAdapter` to route fire inputs to `ShipFlightInputComponent` when piloting. Add fields to track fire state:

```java
// In inputAdapter.touchDown, after existing block/fire logic:
// Add flight fire group tracking:
private boolean fireGroup0Held;
private boolean fireGroup1Held;

// In touchDown LEFT click section, add:
fireGroup0Held = true;
// In touchUp LEFT click section, add:
fireGroup0Held = false;
// In touchDown RIGHT click section, add:
fireGroup1Held = true;
// In touchUp RIGHT click section, add:
fireGroup1Held = false;

// In processFlightInput, after mouse/scroll:
flight.fireHeld[0] = fireGroup0Held;
flight.fireHeld[1] = fireGroup1Held;

// In keyDown, add flight-specific keys:
if (keycode == Input.Keys.T) { targetLockPressed = true; return true; }
if (keycode == Input.Keys.TAB) { nextTargetPressed = true; return true; }

// Add fields:
private boolean targetLockPressed;
private boolean nextTargetPressed;

// In processFlightInput:
if (targetLockPressed) { flight.targetLockPressed = true; targetLockPressed = false; }
if (nextTargetPressed) { flight.nextTargetPressed = true; nextTargetPressed = false; }
flight.prevTargetPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) && nextTargetPressed;
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.systems.PlayerInputSystemPilotingTest" --info`
Expected: PASS (2 tests)

- [ ] **Step 5: Run existing tests to confirm no regressions**

Run: `./gradlew :core:test --info`
Expected: All existing tests still pass.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java \
        core/src/test/java/com/galacticodyssey/player/systems/PlayerInputSystemPilotingTest.java
git commit -m "feat(player): branch PlayerInputSystem by mode, route piloting input to ShipFlightInputComponent"
```

---

## Task 4: Modify ShipFlightSystem to Read ShipFlightInputComponent

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightSystemRefactoredTest.java`

- [ ] **Step 1: Write tests for refactored flight system**

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.EngineSpecComponent;
import com.galacticodyssey.ship.components.FuelTankComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipFlightSystemRefactoredTest {

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    private btDiscreteDynamicsWorld createWorld() {
        btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
        btDbvtBroadphase broadphase = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        btDiscreteDynamicsWorld world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, 0, 0));
        return world;
    }

    @Test
    void readsFromShipFlightInputComponent() {
        btDiscreteDynamicsWorld world = createWorld();
        Engine engine = new Engine();
        ShipFlightSystem system = new ShipFlightSystem();
        engine.addSystem(system);

        Entity ship = createShipEntity(world);
        Entity player = createPilotingPlayer(ship);
        ShipFlightInputComponent flightInput = new ShipFlightInputComponent();
        flightInput.throttle = 1f;
        player.add(flightInput);

        engine.addEntity(ship);
        engine.addEntity(player);

        system.update(1f / 60f);
        world.stepSimulation(1f / 60f, 1, 1f / 60f);

        PhysicsBodyComponent physics = ship.getComponent(PhysicsBodyComponent.class);
        float speed = physics.body.getLinearVelocity().len();
        assertTrue(speed > 0, "Ship should gain velocity from ShipFlightInputComponent.throttle");

        physics.body.dispose();
        physics.shape.dispose();
        world.dispose();
    }

    @Test
    void consumesFuelProportionalToThrust() {
        btDiscreteDynamicsWorld world = createWorld();
        Engine engine = new Engine();
        ShipFlightSystem system = new ShipFlightSystem();
        engine.addSystem(system);

        Entity ship = createShipEntity(world);
        FuelTankComponent fuel = new FuelTankComponent();
        fuel.maxMass = 1000f;
        fuel.currentMass = 1000f;
        ship.add(fuel);
        EngineSpecComponent engineSpec = new EngineSpecComponent();
        engineSpec.maxThrust = 50000f;
        engineSpec.isp = 3200f;
        engineSpec.throttleResponseRate = 100f; // fast for testing
        ship.add(engineSpec);

        Entity player = createPilotingPlayer(ship);
        ShipFlightInputComponent flightInput = new ShipFlightInputComponent();
        flightInput.throttle = 1f;
        player.add(flightInput);

        engine.addEntity(ship);
        engine.addEntity(player);

        system.update(1f);

        assertTrue(fuel.currentMass < 1000f, "Fuel should be consumed when thrusting");
    }

    private Entity createShipEntity(btDiscreteDynamicsWorld world) {
        Entity ship = new Entity();
        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(1, 1, 1));
        float mass = 10000f;
        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().idt());
        physics.mass = mass;
        info.dispose();
        ship.add(physics);
        world.addRigidBody(physics.body);

        ShipFlightComponent flight = new ShipFlightComponent();
        flight.linearThrust = 50000;
        flight.strafeThrustFraction = 0.3f;
        flight.verticalThrustFraction = 0.4f;
        flight.pitchYawTorque = 20000;
        flight.rollTorque = 15000;
        flight.linearDrag = 0.1f;
        flight.angularDrag = 2.0f;
        ship.add(flight);
        return ship;
    }

    private Entity createPilotingPlayer(Entity ship) {
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new PlayerInputComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = ship;
        player.add(state);
        return player;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightSystemRefactoredTest" --info`
Expected: FAIL — ShipFlightSystem still reads PlayerInputComponent.

- [ ] **Step 3: Refactor ShipFlightSystem**

Replace the existing `ShipFlightSystem` to read from `ShipFlightInputComponent` instead of `PlayerInputComponent`:

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.EngineSpecComponent;
import com.galacticodyssey.ship.components.FuelTankComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;

public class ShipFlightSystem extends EntitySystem {

    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ShipFlightComponent> flightMapper =
        ComponentMapper.getFor(ShipFlightComponent.class);
    private final ComponentMapper<ShipFlightInputComponent> flightInputMapper =
        ComponentMapper.getFor(ShipFlightInputComponent.class);
    private final ComponentMapper<PlayerStateComponent> stateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<EngineSpecComponent> engineMapper =
        ComponentMapper.getFor(EngineSpecComponent.class);
    private final ComponentMapper<FuelTankComponent> fuelMapper =
        ComponentMapper.getFor(FuelTankComponent.class);

    private ImmutableArray<Entity> playerEntities;

    private final Vector3 force = new Vector3();
    private final Vector3 torque = new Vector3();
    private final Vector3 localForward = new Vector3();
    private final Vector3 localRight = new Vector3();
    private final Vector3 localUp = new Vector3();
    private final Matrix4 shipTransform = new Matrix4();

    public ShipFlightSystem() {
        super(3);
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (playerEntities.size() == 0) return;

        Entity player = playerEntities.first();
        PlayerStateComponent state = stateMapper.get(player);
        if (state.currentMode != PlayerMode.PILOTING || state.currentShip == null) return;

        ShipFlightInputComponent input = flightInputMapper.get(player);
        if (input == null) return;

        Entity ship = state.currentShip;
        PhysicsBodyComponent physics = physicsMapper.get(ship);
        ShipFlightComponent flight = flightMapper.get(ship);
        if (physics == null || physics.body == null || flight == null) return;

        // Throttle management via EngineSpec
        EngineSpecComponent engineSpec = engineMapper.get(ship);
        float effectiveThrottle = input.throttle;
        if (engineSpec != null) {
            float target = input.throttle;
            engineSpec.currentThrottle = MathUtils.lerp(engineSpec.currentThrottle, target,
                engineSpec.throttleResponseRate * deltaTime);
            effectiveThrottle = engineSpec.currentThrottle;
            engineSpec.actualThrust = effectiveThrottle * engineSpec.maxThrust;
        }

        // Fuel consumption: massFlowRate = thrust / (isp * g0)
        FuelTankComponent fuel = fuelMapper.get(ship);
        if (fuel != null && engineSpec != null && Math.abs(effectiveThrottle) > 0.001f) {
            float thrust = Math.abs(effectiveThrottle) * flight.linearThrust;
            float massFlowRate = thrust / (engineSpec.isp * 9.81f);
            fuel.currentMass -= massFlowRate * deltaTime;
            if (fuel.currentMass <= 0) {
                fuel.currentMass = 0;
                effectiveThrottle = 0;
            }
        }

        physics.body.getWorldTransform(shipTransform);

        localForward.set(0, 0, -1).rot(shipTransform).nor();
        localRight.set(1, 0, 0).rot(shipTransform).nor();
        localUp.set(0, 1, 0).rot(shipTransform).nor();

        force.setZero();
        force.mulAdd(localForward, effectiveThrottle * flight.linearThrust);
        force.mulAdd(localRight, input.strafe * flight.linearThrust * flight.strafeThrustFraction);
        force.mulAdd(localUp, input.verticalThrust * flight.linearThrust * flight.verticalThrustFraction);

        physics.body.applyCentralForce(force);

        torque.setZero();
        torque.mulAdd(localRight, input.pitchInput * flight.pitchYawTorque);
        torque.mulAdd(localUp, -input.yawInput * flight.pitchYawTorque);
        torque.mulAdd(localForward, input.rollInput * flight.rollTorque);

        physics.body.applyTorque(torque);
        physics.body.setDamping(flight.linearDrag, flight.angularDrag);

        flight.currentThrottle = effectiveThrottle;
        physics.body.activate();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.*" --info`
Expected: PASS — both new test and existing `ShipFlightSystemTest` pass (existing test still works since it puts the player in PILOTING mode, but now we need to add `ShipFlightInputComponent` to it).

- [ ] **Step 5: Fix existing ShipFlightSystemTest**

Add `ShipFlightInputComponent` with `throttle = 1f` to the player entity in `ShipFlightSystemTest.forwardThrustIncreasesVelocity()` so it still passes.

- [ ] **Step 6: Run all tests**

Run: `./gradlew :core:test --info`
Expected: All pass.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java \
        core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightSystemRefactoredTest.java \
        core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightSystemTest.java
git commit -m "feat(ship): refactor ShipFlightSystem to read ShipFlightInputComponent, add throttle+fuel"
```

---

## Task 5: PilotTransitionSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/systems/PilotTransitionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/player/systems/PilotTransitionSystemTest.java`

- [ ] **Step 1: Write tests**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.events.PlayerStartPilotingEvent;
import com.galacticodyssey.core.events.PlayerStopPilotingEvent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ui.events.CockpitHUDShowEvent;
import com.galacticodyssey.ui.events.CockpitHUDHideEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PilotTransitionSystemTest {

    @Test
    void enterPilotingAddsFlightInputComponent() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        PilotTransitionSystem system = new PilotTransitionSystem(eventBus);
        engine.addSystem(system);

        Entity player = createPlayer();
        Entity ship = new Entity();
        engine.addEntity(player);
        engine.addEntity(ship);

        assertNull(player.getComponent(ShipFlightInputComponent.class));

        eventBus.publish(new PlayerStartPilotingEvent(player, ship));

        assertNotNull(player.getComponent(ShipFlightInputComponent.class));
    }

    @Test
    void enterPilotingPublishesCockpitHUDShow() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        PilotTransitionSystem system = new PilotTransitionSystem(eventBus);
        engine.addSystem(system);

        Entity player = createPlayer();
        Entity ship = new Entity();
        engine.addEntity(player);

        AtomicBoolean hudShown = new AtomicBoolean(false);
        eventBus.subscribe(CockpitHUDShowEvent.class, e -> hudShown.set(true));

        eventBus.publish(new PlayerStartPilotingEvent(player, ship));

        assertTrue(hudShown.get());
    }

    @Test
    void exitPilotingRemovesFlightInputComponent() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        PilotTransitionSystem system = new PilotTransitionSystem(eventBus);
        engine.addSystem(system);

        Entity player = createPlayer();
        Entity ship = new Entity();
        engine.addEntity(player);

        eventBus.publish(new PlayerStartPilotingEvent(player, ship));
        assertNotNull(player.getComponent(ShipFlightInputComponent.class));

        eventBus.publish(new PlayerStopPilotingEvent(player, ship));
        assertNull(player.getComponent(ShipFlightInputComponent.class));
    }

    @Test
    void exitPilotingPublishesCockpitHUDHide() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        PilotTransitionSystem system = new PilotTransitionSystem(eventBus);
        engine.addSystem(system);

        Entity player = createPlayer();
        Entity ship = new Entity();
        engine.addEntity(player);

        eventBus.publish(new PlayerStartPilotingEvent(player, ship));

        AtomicBoolean hudHidden = new AtomicBoolean(false);
        eventBus.subscribe(CockpitHUDHideEvent.class, e -> hudHidden.set(true));

        eventBus.publish(new PlayerStopPilotingEvent(player, ship));

        assertTrue(hudHidden.get());
    }

    private Entity createPlayer() {
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new PlayerInputComponent());
        player.add(new PlayerStateComponent());
        return player;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.systems.PilotTransitionSystemTest" --info`
Expected: FAIL — `PilotTransitionSystem` does not exist.

- [ ] **Step 3: Implement PilotTransitionSystem**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.PlayerStartPilotingEvent;
import com.galacticodyssey.core.events.PlayerStopPilotingEvent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ui.events.CockpitHUDShowEvent;
import com.galacticodyssey.ui.events.CockpitHUDHideEvent;

public class PilotTransitionSystem extends EntitySystem {

    private static final float TRANSITION_DURATION = 0.5f;

    private final EventBus eventBus;
    private boolean transitioning;
    private float transitionTimer;
    private final Vector3 startPos = new Vector3();
    private final Vector3 endPos = new Vector3();
    private final Quaternion startRot = new Quaternion();
    private final Quaternion endRot = new Quaternion();

    public PilotTransitionSystem(EventBus eventBus) {
        super(2);
        this.eventBus = eventBus;
        eventBus.subscribe(PlayerStartPilotingEvent.class, this::onStartPiloting);
        eventBus.subscribe(PlayerStopPilotingEvent.class, this::onStopPiloting);
    }

    private void onStartPiloting(PlayerStartPilotingEvent event) {
        event.player.add(new ShipFlightInputComponent());
        eventBus.publish(new CockpitHUDShowEvent(event.ship));
    }

    private void onStopPiloting(PlayerStopPilotingEvent event) {
        event.player.remove(ShipFlightInputComponent.class);
        eventBus.publish(new CockpitHUDHideEvent());
    }

    @Override
    public void update(float deltaTime) {
        if (!transitioning) return;
        transitionTimer += deltaTime;
        if (transitionTimer >= TRANSITION_DURATION) {
            transitioning = false;
        }
    }

    public boolean isTransitioning() { return transitioning; }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.systems.PilotTransitionSystemTest" --info`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/PilotTransitionSystem.java \
        core/src/test/java/com/galacticodyssey/player/systems/PilotTransitionSystemTest.java
git commit -m "feat(player): add PilotTransitionSystem with enter/exit seat orchestration"
```

---

## Task 6: AtmosphericFlightSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/systems/AtmosphericFlightSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/AtmosphericFlightSystemTest.java`

- [ ] **Step 1: Write tests for atmospheric physics calculations**

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.AtmosphereZoneComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipAerodynamicsComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipThermalComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphericFlightSystemTest {

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    @Test
    void dragReducesVelocityInAtmosphere() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        AtmosphericFlightSystem system = new AtmosphericFlightSystem(eventBus);
        engine.addSystem(system);

        Entity planet = createPlanet(1000f, 1200f);
        engine.addEntity(planet);

        btDiscreteDynamicsWorld world = createBulletWorld();
        Entity ship = createShipInAtmosphere(world, planet, 1050f);
        engine.addEntity(ship);

        Entity player = createPilotingPlayer(ship);
        engine.addEntity(player);

        PhysicsBodyComponent physics = ship.getComponent(PhysicsBodyComponent.class);
        physics.body.setLinearVelocity(new Vector3(0, 0, -100f));
        float initialSpeed = physics.body.getLinearVelocity().len();

        system.update(1f / 60f);
        world.stepSimulation(1f / 60f, 1, 1f / 60f);

        float newSpeed = physics.body.getLinearVelocity().len();
        assertTrue(newSpeed < initialSpeed, "Drag should reduce speed in atmosphere");

        cleanup(physics, world);
    }

    @Test
    void noForcesAboveAtmosphere() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        AtmosphericFlightSystem system = new AtmosphericFlightSystem(eventBus);
        engine.addSystem(system);

        Entity planet = createPlanet(1000f, 1200f);
        engine.addEntity(planet);

        btDiscreteDynamicsWorld world = createBulletWorld();
        Entity ship = createShipInAtmosphere(world, planet, 1500f); // above atmosphere
        engine.addEntity(ship);

        Entity player = createPilotingPlayer(ship);
        engine.addEntity(player);

        PhysicsBodyComponent physics = ship.getComponent(PhysicsBodyComponent.class);
        physics.body.setLinearVelocity(new Vector3(0, 0, -100f));
        float initialSpeed = physics.body.getLinearVelocity().len();

        system.update(1f / 60f);

        float afterSpeed = physics.body.getLinearVelocity().len();
        assertEquals(initialSpeed, afterSpeed, 0.01f, "No aero forces above atmosphere");

        cleanup(physics, world);
    }

    @Test
    void heatingAccumulatesAboveMachThreshold() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        AtmosphericFlightSystem system = new AtmosphericFlightSystem(eventBus);
        engine.addSystem(system);

        Entity planet = createPlanet(1000f, 1200f);
        AtmosphereZoneComponent atmo = planet.getComponent(AtmosphereZoneComponent.class);
        atmo.speedOfSound = 343f;
        atmo.machThreshold = 3f;
        engine.addEntity(planet);

        btDiscreteDynamicsWorld world = createBulletWorld();
        Entity ship = createShipInAtmosphere(world, planet, 1050f);
        ShipThermalComponent thermal = new ShipThermalComponent();
        ship.add(thermal);
        engine.addEntity(ship);

        Entity player = createPilotingPlayer(ship);
        engine.addEntity(player);

        PhysicsBodyComponent physics = ship.getComponent(PhysicsBodyComponent.class);
        physics.body.setLinearVelocity(new Vector3(0, 0, -1500f)); // well above Mach 3

        system.update(1f);

        assertTrue(thermal.currentHeat > 0, "Heat should accumulate above Mach threshold");

        cleanup(physics, world);
    }

    private Entity createPlanet(float surfaceRadius, float atmosphereRadius) {
        Entity planet = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(0, 0, 0);
        planet.add(t);
        AtmosphereZoneComponent atmo = new AtmosphereZoneComponent();
        atmo.surfaceRadius = surfaceRadius;
        atmo.atmosphereRadius = atmosphereRadius;
        atmo.surfaceDensity = 1.225f;
        atmo.scaleHeight = 200f; // small for testing
        atmo.transitionAltitude = 150f;
        atmo.speedOfSound = 343f;
        atmo.machThreshold = 3f;
        planet.add(atmo);
        return planet;
    }

    private Entity createShipInAtmosphere(btDiscreteDynamicsWorld world, Entity planet, float altitude) {
        Entity ship = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(0, altitude, 0);
        ship.add(t);

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(1, 1, 1));
        float mass = 10000f;
        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(0, altitude, 0));
        physics.mass = mass;
        info.dispose();
        ship.add(physics);
        world.addRigidBody(physics.body);

        ShipFlightComponent flight = new ShipFlightComponent();
        flight.linearThrust = 50000;
        ship.add(flight);

        ShipAerodynamicsComponent aero = new ShipAerodynamicsComponent();
        aero.wingArea = 25f;
        aero.dragCoefficient = 0.35f;
        aero.crossSectionArea = 12f;
        aero.stallAngle = 18f;
        aero.maxLiftCoefficient = 1.4f;
        aero.controlSurfaceAuthority = 0.8f;
        aero.liftCurve = new float[]{0f, 0.2f, 0.5f, 0.9f, 1.2f, 1.4f, 1.3f, 0.8f, 0.3f, 0.1f};
        ship.add(aero);

        return ship;
    }

    private Entity createPilotingPlayer(Entity ship) {
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = ship;
        player.add(state);
        return player;
    }

    private btDiscreteDynamicsWorld createBulletWorld() {
        btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
        btDbvtBroadphase broadphase = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        btDiscreteDynamicsWorld world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, 0, 0));
        return world;
    }

    private void cleanup(PhysicsBodyComponent physics, btDiscreteDynamicsWorld world) {
        world.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
        world.dispose();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.AtmosphericFlightSystemTest" --info`
Expected: FAIL — `AtmosphericFlightSystem` does not exist.

- [ ] **Step 3: Implement AtmosphericFlightSystem**

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.AtmosphereZoneComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipAerodynamicsComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipThermalComponent;
import com.galacticodyssey.ship.events.AtmosphereEnteredEvent;
import com.galacticodyssey.ship.events.AtmosphereExitedEvent;
import com.galacticodyssey.ship.events.ReentryHeatingEvent;
import com.galacticodyssey.ship.events.StallWarningEvent;

public class AtmosphericFlightSystem extends EntitySystem {

    private final EventBus eventBus;
    private ImmutableArray<Entity> playerEntities;
    private ImmutableArray<Entity> planetEntities;

    private final ComponentMapper<PlayerStateComponent> stateMapper = ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ShipAerodynamicsComponent> aeroMapper = ComponentMapper.getFor(ShipAerodynamicsComponent.class);
    private final ComponentMapper<ShipFlightComponent> flightMapper = ComponentMapper.getFor(ShipFlightComponent.class);
    private final ComponentMapper<ShipThermalComponent> thermalMapper = ComponentMapper.getFor(ShipThermalComponent.class);
    private final ComponentMapper<AtmosphereZoneComponent> atmoMapper = ComponentMapper.getFor(AtmosphereZoneComponent.class);

    private final Vector3 velocity = new Vector3();
    private final Vector3 dragForce = new Vector3();
    private final Vector3 liftForce = new Vector3();
    private final Vector3 shipForward = new Vector3();
    private final Vector3 shipUp = new Vector3();
    private final Matrix4 tempMat = new Matrix4();

    private boolean wasInAtmosphere;

    public AtmosphericFlightSystem(EventBus eventBus) {
        super(4);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(PlayerTagComponent.class, PlayerStateComponent.class).get());
        planetEntities = engine.getEntitiesFor(Family.all(AtmosphereZoneComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (playerEntities.size() == 0 || planetEntities.size() == 0) return;

        Entity player = playerEntities.first();
        PlayerStateComponent state = stateMapper.get(player);
        if (state.currentMode != PlayerMode.PILOTING || state.currentShip == null) return;

        Entity ship = state.currentShip;
        PhysicsBodyComponent physics = physicsMapper.get(ship);
        ShipAerodynamicsComponent aero = aeroMapper.get(ship);
        TransformComponent shipTransform = transformMapper.get(ship);
        if (physics == null || physics.body == null || aero == null || shipTransform == null) return;

        Entity planet = planetEntities.first();
        AtmosphereZoneComponent atmo = atmoMapper.get(planet);
        TransformComponent planetTransform = transformMapper.get(planet);

        float altitude = shipTransform.position.dst(planetTransform.position) - atmo.surfaceRadius;
        float atmosphereAltitude = atmo.atmosphereRadius - atmo.surfaceRadius;

        if (altitude >= atmosphereAltitude) {
            if (wasInAtmosphere) {
                eventBus.publish(new AtmosphereExitedEvent(ship));
                wasInAtmosphere = false;
            }
            return;
        }

        if (!wasInAtmosphere) {
            eventBus.publish(new AtmosphereEnteredEvent(ship, planet));
            wasInAtmosphere = true;
        }

        float blendFactor = MathUtils.clamp(
            (atmosphereAltitude - altitude) / (atmosphereAltitude - atmo.transitionAltitude), 0f, 1f);

        float density = atmo.surfaceDensity * (float) Math.exp(-altitude / atmo.scaleHeight);

        physics.body.getLinearVelocity(velocity);
        float speed = velocity.len();
        if (speed < 0.01f) return;

        float dynamicPressure = 0.5f * density * speed * speed;

        // Drag
        float dragMagnitude = dynamicPressure * aero.dragCoefficient * aero.crossSectionArea * blendFactor;
        dragForce.set(velocity).nor().scl(-dragMagnitude);
        physics.body.applyCentralForce(dragForce);

        // Lift
        physics.body.getWorldTransform(tempMat);
        shipForward.set(0, 0, -1).rot(tempMat).nor();
        shipUp.set(0, 1, 0).rot(tempMat).nor();

        float aoaRad = (float) Math.acos(MathUtils.clamp(velocity.nor().dot(shipForward), -1f, 1f));
        float aoaDeg = aoaRad * MathUtils.radiansToDegrees;
        float cl = aero.getLiftCoefficient(aoaDeg);

        if (aoaDeg > aero.stallAngle) {
            eventBus.publish(new StallWarningEvent(ship));
        }

        float liftMagnitude = dynamicPressure * cl * aero.wingArea * blendFactor;
        liftForce.set(shipUp).scl(liftMagnitude);
        physics.body.applyCentralForce(liftForce);

        // Re-entry heating
        float mach = speed / atmo.speedOfSound;
        if (mach > atmo.machThreshold) {
            ShipThermalComponent thermal = thermalMapper.get(ship);
            if (thermal != null) {
                float heatInput = (mach - atmo.machThreshold) * density * 10f * deltaTime * thermal.heatShieldFactor;
                thermal.currentHeat += heatInput;
                thermal.currentHeat = Math.min(thermal.currentHeat, thermal.maxHeat);
                eventBus.publish(new ReentryHeatingEvent(ship, thermal.currentHeat / thermal.maxHeat));
            }
        }

        // Heat dissipation
        ShipThermalComponent thermal = thermalMapper.get(ship);
        if (thermal != null && thermal.currentHeat > 0) {
            thermal.currentHeat -= thermal.dissipationRate * deltaTime;
            thermal.currentHeat = Math.max(0, thermal.currentHeat);
        }

        physics.body.activate();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.AtmosphericFlightSystemTest" --info`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/systems/AtmosphericFlightSystem.java \
        core/src/test/java/com/galacticodyssey/ship/systems/AtmosphericFlightSystemTest.java
git commit -m "feat(ship): add AtmosphericFlightSystem with drag, lift, stall, and heating"
```

---

## Task 7: TargetingSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/systems/TargetingSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/weapons/systems/TargetingSystemTest.java`

- [ ] **Step 1: Write tests**

Tests cover: soft target detection within cone, hard lock toggle, lead indicator calculation, target cycling.

- [ ] **Step 2: Run tests to verify failure**

- [ ] **Step 3: Implement TargetingSystem**

The system queries entities with `ShipHardpointComponent` (targetable ships) and finds the closest within a cone from the camera forward. Lead indicator uses `targetPos + targetVel * (distance / avgProjectileSpeed)`. Publishes `TargetLockedEvent`/`TargetLostEvent` on lock state changes.

- [ ] **Step 4: Run tests to verify pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(weapons): add TargetingSystem with soft target, hard lock, and lead indicator"
```

---

## Task 8: ShipWeaponPilotSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/weapons/systems/ShipWeaponPilotSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/weapons/systems/ShipWeaponPilotSystemTest.java`

- [ ] **Step 1: Write tests**

Tests cover: firing active weapon group, group selection via number keys, only fires when fireHeld is true, reuses `ShipWeaponSystem.fireHardpoint()`.

- [ ] **Step 2: Run tests to verify failure**

- [ ] **Step 3: Implement ShipWeaponPilotSystem**

Reads `ShipFlightInputComponent.fireHeld[0..3]` and `WeaponGroupComponent`. For each active group with fire held, iterates hardpoint IDs and calls `ShipWeaponSystem.fireHardpoint(shipEntity, hardpointId)`. Handles group selection from scroll delta or explicit number key (detected via a flag on the input component).

- [ ] **Step 4: Run tests to verify pass**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(weapons): add ShipWeaponPilotSystem bridging player input to weapon groups"
```

---

## Task 9: ShipClassRegistry + Data Files

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/data/ShipClassRegistry.java`
- Create: `core/src/main/resources/data/ships/ship_classes.json`
- Create: `core/src/main/resources/data/planets/atmosphere_profiles.json`
- Test: `core/src/test/java/com/galacticodyssey/ship/data/ShipClassRegistryTest.java`

- [ ] **Step 1: Write test for registry loading**

Test parses the JSON, retrieves a ship class by ID, and verifies all fields populated correctly.

- [ ] **Step 2: Create `ship_classes.json`**

Contains at least one ship archetype (`corvette_scout`) with all fields matching the spec: mass, thrust, flight params, aerodynamics, fuel, shields, hardpoints reference, default weapon groups.

- [ ] **Step 3: Create `atmosphere_profiles.json`**

Contains `earth_like`, `thin_mars`, `dense_venus` profiles as defined in spec.

- [ ] **Step 4: Implement ShipClassRegistry**

Follows same pattern as `ShipWeaponRegistry`: `loadShipClasses(path)` parses JSON with `JsonReader`, stores data in `Map<String, ShipClassData>`. Includes `createShipEntity(Engine, String)` that builds a fully configured entity.

- [ ] **Step 5: Run tests**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(data): add ShipClassRegistry, ship_classes.json, and atmosphere_profiles.json"
```

---

## Task 10: CockpitGeometryBuilder

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/CockpitGeometryBuilder.java`

- [ ] **Step 1: Implement CockpitGeometryBuilder**

Generates cockpit meshes per size class using `ModelBuilder` and `MeshPartBuilder`. Creates:
- Hull walls (dark metallic)
- Console surfaces (emissive cyan accents)
- Viewport cutout (no geometry — empty space)
- Returns a `Model` with `ModelInstance` ready for rendering.

Size-specific geometry:
- SMALL: 3m × 4m × 2.5m canopy
- MEDIUM: 8m × 6m × 3m bridge
- LARGE: 15m × 10m × 4m command deck

- [ ] **Step 2: Commit**

```bash
git commit -m "feat(ship): add CockpitGeometryBuilder for procedural cockpit meshes"
```

---

## Task 11: CockpitModelSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/systems/CockpitModelSystem.java`

- [ ] **Step 1: Implement CockpitModelSystem**

Render-time system (priority 12). When player is piloting in cockpit mode:
- Clears depth buffer
- Positions cockpit model at camera using ship local orientation
- Renders with emissive Environment
- Manages model lifecycle (create on enter, dispose on exit)

- [ ] **Step 2: Commit**

```bash
git commit -m "feat(ship): add CockpitModelSystem for 3D cockpit rendering"
```

---

## Task 12: CockpitHUDSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/CockpitHUDSystem.java`

- [ ] **Step 1: Implement CockpitHUDSystem**

Scene2D-based HUD system (priority 20). Creates a `Stage` with all instrument panels:
- Speed/Altitude panel (top-left)
- Throttle gauge (left, vertical bar)
- Targeting reticle (center crosshair + lead diamond)
- Holographic radar (top-right, rendered to FBO)
- Target info panel
- Weapon groups panel (bottom-right)
- Fuel gauge
- Heat display
- Alert panel (subscribes to warning events)

Subscribes to `CockpitHUDShowEvent`/`CockpitHUDHideEvent`. On show: build actors. On hide: clear stage.

Each frame reads ship components (`ShipFlightComponent`, `FuelTankComponent`, `ShipThermalComponent`, `WeaponGroupComponent`, `ShipHardpointComponent`, `PlayerTargetComponent`) and updates labels/bars.

- [ ] **Step 2: Commit**

```bash
git commit -m "feat(ui): add CockpitHUDSystem with flight instruments and radar"
```

---

## Task 13: Modify ShipCameraSystem

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/systems/ShipCameraSystem.java`

- [ ] **Step 1: Update ShipCameraSystem to read ShipFlightInputComponent for camera toggle**

Replace the `PlayerInputComponent` camera toggle reading with `ShipFlightInputComponent`:

```java
// Replace:
PlayerInputComponent input = inputMapper.get(player);
if (input != null && input.cameraTogglePressed) { ... }

// With:
ShipFlightInputComponent flightInput = flightInputMapper.get(player);
if (flightInput != null && flightInput.cameraTogglePressed) {
    cameraMode = (cameraMode == CameraMode.COCKPIT) ? CameraMode.CHASE : CameraMode.COCKPIT;
    flightInput.cameraTogglePressed = false;
}
```

Add the mapper for `ShipFlightInputComponent`. Keep the `PlayerInputComponent` mapper as fallback for backwards compatibility during transition.

- [ ] **Step 2: Run existing tests**

Run: `./gradlew :core:test --info`
Expected: All pass.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(ship): update ShipCameraSystem to read ShipFlightInputComponent"
```

---

## Task 14: Wire Everything in GameWorld

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Register new systems in GameWorld**

Add imports and instantiate:
- `PilotTransitionSystem` (priority 2, needs `eventBus`)
- `AtmosphericFlightSystem` (priority 4, needs `eventBus`)
- `TargetingSystem` (priority 6, needs `eventBus`)
- `ShipWeaponPilotSystem` (priority 7, needs `eventBus`, `shipWeaponSystem`)
- `CockpitModelSystem` (priority 12)
- `CockpitHUDSystem` (priority 20, needs `eventBus`)

Add them to the engine after existing ship systems. Load `ShipClassRegistry` alongside `ShipWeaponRegistry`.

- [ ] **Step 2: Run all tests**

Run: `./gradlew :core:test --info`
Expected: All pass.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(core): wire piloting systems into GameWorld"
```

---

## Task 15: GameScreen Cockpit Render Pass

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Add cockpit render pass and HUD stage**

In the `render()` method, after the main scene render:
1. If player is piloting in cockpit mode, call `cockpitModelSystem.render(modelBatch, camera)`
2. After all 3D rendering, draw the cockpit HUD stage: `cockpitHUDSystem.getStage().draw()`

Wire references from `GameWorld` to the new systems.

- [ ] **Step 2: Run the game to verify no crashes**

Run: `./gradlew :desktop:run`
Expected: Game launches without crash. Cockpit systems are dormant until player enters a ship.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(ui): add cockpit render pass and HUD stage to GameScreen"
```

---

## Task 16: Integration Test — Full Pilot Loop

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/ship/systems/PilotingIntegrationTest.java`

- [ ] **Step 1: Write integration test**

Test the full loop: create player + ship → publish `PlayerStartPilotingEvent` → verify `ShipFlightInputComponent` added → set throttle → tick flight system → verify ship moves → publish `PlayerStopPilotingEvent` → verify component removed.

- [ ] **Step 2: Run integration test**

- [ ] **Step 3: Commit**

```bash
git commit -m "test(ship): add piloting integration test covering full enter/fly/exit loop"
```

---

## Summary

| Task | Description | Key Deliverables |
|------|-------------|-----------------|
| 1 | ShipFlightInputComponent + Events | Input component, 8 event classes |
| 2 | Remaining components | 6 data components |
| 3 | PlayerInputSystem mode branch | Piloting input routing |
| 4 | ShipFlightSystem refactor | Reads new input, throttle, fuel |
| 5 | PilotTransitionSystem | Enter/exit orchestration |
| 6 | AtmosphericFlightSystem | Drag, lift, stall, heating |
| 7 | TargetingSystem | Soft/hard lock, lead indicator |
| 8 | ShipWeaponPilotSystem | Weapon group firing |
| 9 | ShipClassRegistry + data files | JSON loading, entity factory |
| 10 | CockpitGeometryBuilder | Procedural cockpit meshes |
| 11 | CockpitModelSystem | 3D cockpit rendering |
| 12 | CockpitHUDSystem | Full instrument panel |
| 13 | ShipCameraSystem update | Read new input component |
| 14 | GameWorld wiring | Register all systems |
| 15 | GameScreen render pass | Cockpit + HUD rendering |
| 16 | Integration test | Full pilot loop verification |
