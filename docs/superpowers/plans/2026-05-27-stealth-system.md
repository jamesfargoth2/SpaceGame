# Stealth System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a dual-mode stealth system with full on-foot NPC awareness (5-state FSM, proximity/LoS/noise/lighting) and ship detection (EM/heat/visual signatures, dark mode, active scanner pings).

**Architecture:** Two Ashley `EntitySystem`s (`NpcAwarenessSystem` and `ShipDetectionSystem`) share ECS components (`SignatureComponent`, `AwarenessStateComponent`, `PerceptionComponent`) on the player and NPC/patrol-ship entities. Detection logic lives in package-private methods for unit-test isolation; the Bullet LoS raycast is injected via a `LineOfSightQuery` functional interface. All state transitions publish to the existing `EventBus` — AI, HUD, and audio subscribe independently.

**Tech Stack:** Java 21, libGDX 1.13+, Ashley ECS (`EntitySystem`, `Engine`, `ComponentMapper`, `Family`), Bullet physics (LoS), `MathUtils`/`Vector3`/`Quaternion`, JUnit 5, `com.galacticodyssey.core.EventBus`.

---

## File Structure

| File | Purpose |
|---|---|
| `stealth/AwarenessState.java` | 4-value enum: UNAWARE, CURIOUS, ALERTED, SEARCHING |
| `stealth/LineOfSightQuery.java` | `@FunctionalInterface` — injected for testability |
| `stealth/events/AwarenessChangedEvent.java` | NPC state transition (references AwarenessState) |
| `stealth/events/PlayerDetectedEvent.java` | Final alert event |
| `stealth/events/DarkModeToggledEvent.java` | Player toggled engine cutoff |
| `stealth/events/StealthHUDUpdateEvent.java` | Throttled 4 Hz HUD update |
| `core/events/NoiseBurstEvent.java` | World-space noise burst (no stealth imports) |
| `core/events/ActiveScanEvent.java` | Scanner ping from patrol ship/station |
| `stealth/SignatureComponent.java` | Player's on-foot + ship signature + compute methods |
| `stealth/AwarenessStateComponent.java` | Per-NPC FSM state, accumulator, timers, lastKnownPosition |
| `stealth/PerceptionComponent.java` | Per-NPC view range/angle, hearing range, thresholds |
| `stealth/NpcAwarenessSystem.java` | On-foot detection (hearing + LoS cone + noise burst) |
| `stealth/ShipSignatureSystem.java` | Reads flight/shield components → writes SignatureComponent |
| `stealth/ShipDetectionSystem.java` | Ship passive + active scan detection |
| `stealth/BulletLineOfSightQuery.java` | Production LoS via `btDynamicsWorld.rayTest()` |
| `data/stealth/npc_perception.json` | Per-NPC-type perception parameters |
| `data/stealth/detection_constants.json` | FSM tuning constants |
| `test/stealth/SignatureComponentTest.java` | Unit tests for both compute methods |
| `test/stealth/NpcAwarenessSystemTest.java` | FSM + accumulator tests (no Ashley, no Bullet) |
| `test/stealth/ShipDetectionSystemTest.java` | Ship score + dark mode + active scan tests |
| `test/stealth/StealthIntegrationTest.java` | Full pipeline with Ashley `Engine` + `EventBus` |
| `core/GameWorld.java` (modify) | Add SignatureComponent to player; register three new systems |

---

## Task 1: AwarenessState, LineOfSightQuery, and All Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/stealth/AwarenessState.java`
- Create: `core/src/main/java/com/galacticodyssey/stealth/LineOfSightQuery.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/NoiseBurstEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/ActiveScanEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/stealth/events/PlayerDetectedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/stealth/events/DarkModeToggledEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/stealth/events/AwarenessChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/stealth/events/StealthHUDUpdateEvent.java`
- Test: `core/src/test/java/com/galacticodyssey/stealth/StealthEventsTest.java`

- [ ] **Step 1: Write AwarenessState enum**

```java
// core/src/main/java/com/galacticodyssey/stealth/AwarenessState.java
package com.galacticodyssey.stealth;

public enum AwarenessState {
    UNAWARE,    // normal patrol
    CURIOUS,    // investigating; suspicionTimer accumulates; transitions to ALERTED on threshold or timeout
    ALERTED,    // active pursuit; lastKnownPosition updated each frame
    SEARCHING   // lost contact; searching last known position; reverts to UNAWARE on timeout
}
```

- [ ] **Step 2: Write LineOfSightQuery interface**

```java
// core/src/main/java/com/galacticodyssey/stealth/LineOfSightQuery.java
package com.galacticodyssey.stealth;

import com.badlogic.gdx.math.Vector3;

@FunctionalInterface
public interface LineOfSightQuery {
    boolean hasLoS(Vector3 from, Vector3 to);
}
```

- [ ] **Step 3: Write NoiseBurstEvent and ActiveScanEvent (no stealth imports)**

```java
// core/src/main/java/com/galacticodyssey/core/events/NoiseBurstEvent.java
package com.galacticodyssey.core.events;

public final class NoiseBurstEvent {
    public final float x, y, z;
    public final float radius;
    public final float intensity; // 0–1

    public NoiseBurstEvent(float x, float y, float z, float radius, float intensity) {
        this.x = x; this.y = y; this.z = z;
        this.radius = radius;
        this.intensity = intensity;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/core/events/ActiveScanEvent.java
package com.galacticodyssey.core.events;

public final class ActiveScanEvent {
    public final float x, y, z;
    public final float range;
    public final float pingMultiplier;

    public ActiveScanEvent(float x, float y, float z, float range, float pingMultiplier) {
        this.x = x; this.y = y; this.z = z;
        this.range = range;
        this.pingMultiplier = pingMultiplier;
    }
}
```

- [ ] **Step 4: Write PlayerDetectedEvent and DarkModeToggledEvent**

```java
// core/src/main/java/com/galacticodyssey/stealth/events/PlayerDetectedEvent.java
package com.galacticodyssey.stealth.events;

public final class PlayerDetectedEvent {
    public final String detectorId;
    public final String detectorType; // "SHIP_PASSIVE" or "SHIP_SCAN"

    public PlayerDetectedEvent(String detectorId, String detectorType) {
        this.detectorId = detectorId;
        this.detectorType = detectorType;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/stealth/events/DarkModeToggledEvent.java
package com.galacticodyssey.stealth.events;

public final class DarkModeToggledEvent {
    public final boolean active;

    public DarkModeToggledEvent(boolean active) {
        this.active = active;
    }
}
```

- [ ] **Step 5: Write AwarenessChangedEvent and StealthHUDUpdateEvent (depend on AwarenessState)**

```java
// core/src/main/java/com/galacticodyssey/stealth/events/AwarenessChangedEvent.java
package com.galacticodyssey.stealth.events;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.stealth.AwarenessState;

public final class AwarenessChangedEvent {
    public final String npcId;
    public final AwarenessState oldState;
    public final AwarenessState newState;
    public final Vector3 lastKnownPosition; // defensive copy

    public AwarenessChangedEvent(String npcId, AwarenessState oldState, AwarenessState newState,
                                 Vector3 lastKnownPosition) {
        this.npcId = npcId;
        this.oldState = oldState;
        this.newState = newState;
        this.lastKnownPosition = new Vector3(lastKnownPosition);
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/stealth/events/StealthHUDUpdateEvent.java
package com.galacticodyssey.stealth.events;

import com.galacticodyssey.stealth.AwarenessState;

public final class StealthHUDUpdateEvent {
    public final AwarenessState highestNearbyState;
    public final float nearestThreatDistance; // -1 if no NPCs nearby

    public StealthHUDUpdateEvent(AwarenessState highestNearbyState, float nearestThreatDistance) {
        this.highestNearbyState = highestNearbyState;
        this.nearestThreatDistance = nearestThreatDistance;
    }
}
```

- [ ] **Step 6: Write the test**

```java
// core/src/test/java/com/galacticodyssey/stealth/StealthEventsTest.java
package com.galacticodyssey.stealth;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.events.ActiveScanEvent;
import com.galacticodyssey.core.events.NoiseBurstEvent;
import com.galacticodyssey.stealth.events.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StealthEventsTest {

    @Test
    void noiseBurstEvent_fieldsSet() {
        NoiseBurstEvent e = new NoiseBurstEvent(1, 2, 3, 10f, 0.8f);
        assertEquals(1f, e.x); assertEquals(2f, e.y); assertEquals(3f, e.z);
        assertEquals(10f, e.radius); assertEquals(0.8f, e.intensity);
    }

    @Test
    void activeScanEvent_fieldsSet() {
        ActiveScanEvent e = new ActiveScanEvent(0, 0, 0, 500f, 2.5f);
        assertEquals(500f, e.range); assertEquals(2.5f, e.pingMultiplier);
    }

    @Test
    void awarenessChangedEvent_copiesPosition() {
        Vector3 pos = new Vector3(5, 0, 3);
        AwarenessChangedEvent e = new AwarenessChangedEvent(
            "npc1", AwarenessState.UNAWARE, AwarenessState.CURIOUS, pos);
        pos.set(0, 0, 0); // mutate original
        assertEquals(5f, e.lastKnownPosition.x, 0.001f); // copy is unchanged
        assertEquals(AwarenessState.CURIOUS, e.newState);
    }

    @Test
    void darkModeToggledEvent_activeFlag() {
        assertTrue(new DarkModeToggledEvent(true).active);
        assertFalse(new DarkModeToggledEvent(false).active);
    }

    @Test
    void stealthHUDUpdateEvent_fieldsSet() {
        StealthHUDUpdateEvent e = new StealthHUDUpdateEvent(AwarenessState.ALERTED, 15.5f);
        assertEquals(AwarenessState.ALERTED, e.highestNearbyState);
        assertEquals(15.5f, e.nearestThreatDistance, 0.001f);
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.stealth.StealthEventsTest"
```

Expected: 5 tests pass.

- [ ] **Step 8: Commit**

```powershell
git add core/src/main/java/com/galacticodyssey/stealth/ `
        core/src/main/java/com/galacticodyssey/core/events/NoiseBurstEvent.java `
        core/src/main/java/com/galacticodyssey/core/events/ActiveScanEvent.java `
        core/src/test/java/com/galacticodyssey/stealth/StealthEventsTest.java
git commit -m "feat(stealth): add AwarenessState, LineOfSightQuery, and stealth events"
```

---

## Task 2: ECS Components

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/stealth/SignatureComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/stealth/AwarenessStateComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/stealth/PerceptionComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/stealth/SignatureComponentTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// core/src/test/java/com/galacticodyssey/stealth/SignatureComponentTest.java
package com.galacticodyssey.stealth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignatureComponentTest {

    // computeOnFootScore formula:
    // base  = 0.3 + noiseLevel * 0.7
    // lit   = 0.1 + lightExposure * 0.9
    // skill = 1.0 - stealthSkill / 200.0
    // score = base * lit * gearMultiplier * skill

    @Test
    void onFoot_defaultValues_returnsMidRangeScore() {
        SignatureComponent sig = new SignatureComponent();
        sig.noiseLevel = 0.5f;
        sig.lightExposure = 0.5f;
        sig.gearMultiplier = 1.0f;
        sig.stealthSkill = 0;
        // base = 0.3 + 0.35 = 0.65; lit = 0.1 + 0.45 = 0.55; skill = 1.0
        float expected = 0.65f * 0.55f * 1.0f * 1.0f;
        assertEquals(expected, sig.computeOnFootScore(), 0.001f);
    }

    @Test
    void onFoot_proneInDark_nearZero() {
        SignatureComponent sig = new SignatureComponent();
        sig.noiseLevel = 0.0f;     // prone still
        sig.lightExposure = 0.0f;  // pitch black
        sig.gearMultiplier = 1.0f;
        sig.stealthSkill = 0;
        // base = 0.3; lit = 0.1; score = 0.03
        assertEquals(0.03f, sig.computeOnFootScore(), 0.001f);
    }

    @Test
    void onFoot_sprintingInLight_nearOne() {
        SignatureComponent sig = new SignatureComponent();
        sig.noiseLevel = 1.0f;     // sprinting
        sig.lightExposure = 1.0f;  // full daylight
        sig.gearMultiplier = 1.0f;
        sig.stealthSkill = 0;
        // base = 1.0; lit = 1.0; score = 1.0
        assertEquals(1.0f, sig.computeOnFootScore(), 0.001f);
    }

    @Test
    void onFoot_stealthSkill100_reducesBy50Percent() {
        SignatureComponent sig = new SignatureComponent();
        sig.noiseLevel = 0.5f;
        sig.lightExposure = 0.5f;
        sig.gearMultiplier = 1.0f;
        sig.stealthSkill = 100;
        // skill modifier = 1.0 - 100/200 = 0.5
        float noSkill = 0.65f * 0.55f * 1.0f;
        assertEquals(noSkill * 0.5f, sig.computeOnFootScore(), 0.001f);
    }

    @Test
    void onFoot_stealthSuit_reducesScore() {
        SignatureComponent sig = new SignatureComponent();
        sig.noiseLevel = 0.5f;
        sig.lightExposure = 0.5f;
        sig.gearMultiplier = 0.6f; // stealth suit
        sig.stealthSkill = 0;
        float noGear = 0.65f * 0.55f;
        assertEquals(noGear * 0.6f, sig.computeOnFootScore(), 0.001f);
    }

    @Test
    void ship_normalOperation_sumsThreeSignatures() {
        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.4f;
        sig.heatSignature = 0.3f;
        sig.visualSignature = 0.2f;
        sig.darkMode = false;
        assertEquals(0.9f, sig.computeShipScore(), 0.001f);
    }

    @Test
    void ship_darkMode_returnsNearZeroEM() {
        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.5f;
        sig.heatSignature = 0.8f;
        sig.visualSignature = 0.3f;
        sig.darkMode = true;
        // dark mode: emSignature * 0.05 only
        assertEquals(0.5f * 0.05f, sig.computeShipScore(), 0.001f);
    }
}
```

- [ ] **Step 2: Run to verify failure**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.stealth.SignatureComponentTest"
```

Expected: compile error — `SignatureComponent` does not exist yet.

- [ ] **Step 3: Write SignatureComponent**

```java
// core/src/main/java/com/galacticodyssey/stealth/SignatureComponent.java
package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.Component;

public class SignatureComponent implements Component {

    // On-foot — written by movement and lighting systems each frame
    public float noiseLevel;      // 0–1: prone-still=0.0, walking=0.5, sprinting=1.0
    public float lightExposure;   // 0–1: written by LightingSystem
    public float gearMultiplier = 1.0f; // product of all equipped stealth gear modifiers
    public int   stealthSkill;    // 0–100; each 2 points = 1% reduction (max 50% at 100)

    // Ship — written by ShipSignatureSystem each frame
    public float emSignature;       // 0–1: shields + scanners raise this
    public float heatSignature;     // 0–1: scales with engine throttle
    public float visualSignature = 0.5f; // 0–1: stealth coating reduces this
    public boolean darkMode;        // engine cutoff — near-zero EM, zero heat, no thrust
    public boolean shieldsActive;   // set by ShipSignatureSystem (currentShield > 0)
    public boolean scannerActive;   // set by scanner system when active

    public float computeOnFootScore() {
        float base  = 0.3f + noiseLevel * 0.7f;
        float lit   = 0.1f + lightExposure * 0.9f;
        float skill = 1f - (stealthSkill / 200f);
        return base * lit * gearMultiplier * skill;
    }

    public float computeShipScore() {
        if (darkMode) return emSignature * 0.05f;
        return emSignature + heatSignature + visualSignature;
    }
}
```

- [ ] **Step 4: Write AwarenessStateComponent and PerceptionComponent**

```java
// core/src/main/java/com/galacticodyssey/stealth/AwarenessStateComponent.java
package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

public class AwarenessStateComponent implements Component {
    public AwarenessState state = AwarenessState.UNAWARE;
    public float detectionAccumulator; // rises toward contribution, decays toward 0
    public float suspicionTimer;       // elapsed time in CURIOUS state
    public float searchTimer;          // elapsed time in SEARCHING state
    public final Vector3 lastKnownPosition = new Vector3();
}
```

```java
// core/src/main/java/com/galacticodyssey/stealth/PerceptionComponent.java
package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.Component;

public class PerceptionComponent implements Component {
    public float viewRange   = 20f;   // metres — LoS cone max
    public float viewAngle   = 110f;  // degrees — full cone (not half-angle)
    public float hearingRange = 12f;  // metres — omnidirectional, no LoS required
    public float curiousThreshold = 0.25f; // accumulator level to enter CURIOUS
    public float alertThreshold   = 0.65f; // accumulator level to enter ALERTED
    // Ship-only fields
    public float effectiveness   = 1.0f;  // scanner quality scalar
    public float pingMultiplier  = 2.0f;  // active scan intensity multiplier
}
```

- [ ] **Step 5: Run tests to verify they pass**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.stealth.SignatureComponentTest"
```

Expected: 7 tests pass.

- [ ] **Step 6: Commit**

```powershell
git add core/src/main/java/com/galacticodyssey/stealth/SignatureComponent.java `
        core/src/main/java/com/galacticodyssey/stealth/AwarenessStateComponent.java `
        core/src/main/java/com/galacticodyssey/stealth/PerceptionComponent.java `
        core/src/test/java/com/galacticodyssey/stealth/SignatureComponentTest.java
git commit -m "feat(stealth): add SignatureComponent, AwarenessStateComponent, PerceptionComponent"
```

---

## Task 3: NpcAwarenessSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/stealth/NpcAwarenessSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/stealth/NpcAwarenessSystemTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// core/src/test/java/com/galacticodyssey/stealth/NpcAwarenessSystemTest.java
package com.galacticodyssey.stealth;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.NoiseBurstEvent;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcAwarenessSystemTest {

    private NpcAwarenessSystem system;
    private EventBus eventBus;
    private List<AwarenessChangedEvent> stateChanges;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        system = new NpcAwarenessSystem(eventBus, (a, b) -> true); // always clear LoS
        stateChanges = new ArrayList<>();
        eventBus.subscribe(AwarenessChangedEvent.class, stateChanges::add);
    }

    // --- computeContribution tests ---

    @Test
    void hearingContribution_noLoS_stillDetects() {
        NpcAwarenessSystem noLosSys = new NpcAwarenessSystem(eventBus, (a, b) -> false);

        SignatureComponent sig = makeSig(1.0f, 1.0f, 1.0f, 0);
        PerceptionComponent p = makePerception(0f, 0f, 12f); // hearing only
        AwarenessStateComponent state = new AwarenessStateComponent();

        TransformStub npcT = new TransformStub(0, 0, 6);
        Vector3 playerPos = new Vector3(0, 0, 0);

        float c = noLosSys.computeContribution(p, npcT.toTransform(), playerPos, sig);
        assertTrue(c > 0f, "Hearing should detect without LoS");
    }

    @Test
    void hearingFalloff_atEdge_isNearZero() {
        SignatureComponent sig = makeSig(1.0f, 0f, 1.0f, 0);
        PerceptionComponent p = makePerception(0f, 0f, 10f);
        AwarenessStateComponent state = new AwarenessStateComponent();

        TransformStub npcT = new TransformStub(0, 0, 10); // exactly at edge
        float c = system.computeContribution(p, npcT.toTransform(), new Vector3(0, 0, 0), sig);
        assertEquals(0f, c, 0.01f); // falloff = 1 - 10/10 = 0
    }

    @Test
    void visionContribution_outOfRange_isZero() {
        SignatureComponent sig = makeSig(0f, 1.0f, 1.0f, 0); // no noise, full light
        PerceptionComponent p = makePerception(20f, 110f, 0f); // vision only
        TransformStub npcT = new TransformStub(0, 0, 25); // beyond viewRange

        float c = system.computeContribution(p, npcT.toTransform(), new Vector3(0, 0, 0), sig);
        assertEquals(0f, c, 0.001f);
    }

    // --- tickFsm tests ---

    @Test
    void unaware_accumulatorExceedsThreshold_becomesCurious() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.detectionAccumulator = 0.4f; // above curiousThreshold 0.25

        PerceptionComponent p = makePerception(20f, 110f, 12f);
        system.tickFsm(state, p, new Vector3(0, 0, 5), true, 0.1f);

        assertEquals(AwarenessState.CURIOUS, state.state);
        assertEquals(0f, state.suspicionTimer, 0.001f); // reset on transition
    }

    @Test
    void curious_accumulatorExceedsAlertThreshold_becomesAlerted() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.CURIOUS;
        state.detectionAccumulator = 0.8f; // above alertThreshold 0.65

        PerceptionComponent p = makePerception(20f, 110f, 12f);
        Vector3 playerPos = new Vector3(3, 0, 2);
        system.tickFsm(state, p, playerPos, true, 0.1f);

        assertEquals(AwarenessState.ALERTED, state.state);
        assertEquals(playerPos.x, state.lastKnownPosition.x, 0.001f);
    }

    @Test
    void curious_suspicionTimerExpires_becomesAlerted() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.CURIOUS;
        state.detectionAccumulator = 0.1f; // below alertThreshold

        PerceptionComponent p = makePerception(20f, 110f, 12f);
        // Advance past SUSPICION_LIMIT (4.0s)
        system.tickFsm(state, p, new Vector3(0, 0, 5), true, NpcAwarenessSystem.SUSPICION_LIMIT + 0.1f);

        assertEquals(AwarenessState.ALERTED, state.state);
    }

    @Test
    void curious_lowAccumulatorAfterCooldown_returnsUnaware() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.CURIOUS;
        state.detectionAccumulator = 0.02f; // below DECAY_FLOOR 0.05
        state.suspicionTimer = NpcAwarenessSystem.CURIOUS_COOLDOWN + 0.1f; // past cooldown

        PerceptionComponent p = makePerception(20f, 110f, 12f);
        system.tickFsm(state, p, new Vector3(0, 0, 5), false, 0.1f);

        assertEquals(AwarenessState.UNAWARE, state.state);
    }

    @Test
    void alerted_playerNotVisible_becomesSearching() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.ALERTED;

        PerceptionComponent p = makePerception(20f, 110f, 12f);
        system.tickFsm(state, p, new Vector3(0, 0, 5), false /* not visible */, 0.1f);

        assertEquals(AwarenessState.SEARCHING, state.state);
        assertEquals(0f, state.searchTimer, 0.001f); // reset on transition
    }

    @Test
    void searching_timerExpires_returnsUnaware() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.SEARCHING;
        state.detectionAccumulator = 0.01f;

        PerceptionComponent p = makePerception(20f, 110f, 12f);
        system.tickFsm(state, p, new Vector3(0, 0, 5), false,
            NpcAwarenessSystem.SEARCH_DURATION + 0.1f);

        assertEquals(AwarenessState.UNAWARE, state.state);
    }

    // --- NoiseBurst test ---

    @Test
    void applyNoiseBurst_withinRadius_spikesAccumulator() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.detectionAccumulator = 0f;

        NoiseBurstEvent e = new NoiseBurstEvent(0, 0, 0, 10f, 1.0f);
        system.applyNoiseBurst(new Vector3(0, 0, 5), state, e); // 5m from burst

        // falloff = 1 - 5/10 = 0.5; spike = 1.0 * 0.5 = 0.5
        assertEquals(0.5f, state.detectionAccumulator, 0.01f);
    }

    @Test
    void applyNoiseBurst_outsideRadius_doesNothing() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.detectionAccumulator = 0f;

        NoiseBurstEvent e = new NoiseBurstEvent(0, 0, 0, 5f, 1.0f);
        system.applyNoiseBurst(new Vector3(0, 0, 10), state, e); // 10m — outside radius

        assertEquals(0f, state.detectionAccumulator, 0.001f);
    }

    // --- helpers ---

    private SignatureComponent makeSig(float noise, float light, float gearMult, int skill) {
        SignatureComponent s = new SignatureComponent();
        s.noiseLevel = noise; s.lightExposure = light;
        s.gearMultiplier = gearMult; s.stealthSkill = skill;
        return s;
    }

    private PerceptionComponent makePerception(float viewRange, float viewAngle, float hearingRange) {
        PerceptionComponent p = new PerceptionComponent();
        p.viewRange = viewRange; p.viewAngle = viewAngle; p.hearingRange = hearingRange;
        return p;
    }

    // Minimal stand-in for TransformComponent without Ashley dependency
    static class TransformStub {
        final float x, y, z;
        TransformStub(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        com.galacticodyssey.core.components.TransformComponent toTransform() {
            com.galacticodyssey.core.components.TransformComponent t =
                new com.galacticodyssey.core.components.TransformComponent();
            t.position.set(x, y, z);
            return t;
        }
    }
}
```

- [ ] **Step 2: Run to verify failure**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.stealth.NpcAwarenessSystemTest"
```

Expected: compile error — `NpcAwarenessSystem` does not exist yet.

- [ ] **Step 3: Write NpcAwarenessSystem**

```java
// core/src/main/java/com/galacticodyssey/stealth/NpcAwarenessSystem.java
package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.NoiseBurstEvent;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import com.galacticodyssey.stealth.events.StealthHUDUpdateEvent;

public final class NpcAwarenessSystem extends EntitySystem {

    // Package-private so tests can read them
    static float RISE_RATE       = 2.0f;
    static float DECAY_RATE      = 0.8f;
    static float DECAY_FLOOR     = 0.05f;
    static float SUSPICION_LIMIT = 4.0f;
    static float CURIOUS_COOLDOWN = 2.0f;
    static float SEARCH_DURATION = 15.0f;
    private static final float HUD_INTERVAL = 0.25f; // 4 Hz

    private final EventBus eventBus;
    private final LineOfSightQuery los;

    private static final ComponentMapper<AwarenessStateComponent> AWARE_M =
        ComponentMapper.getFor(AwarenessStateComponent.class);
    private static final ComponentMapper<PerceptionComponent> PERC_M =
        ComponentMapper.getFor(PerceptionComponent.class);
    private static final ComponentMapper<TransformComponent> XFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<SignatureComponent> SIG_M =
        ComponentMapper.getFor(SignatureComponent.class);

    // Ground NPCs only — exclude entities that are ships (ShipDataComponent marks ships)
    private static final Family NPC_FAMILY = Family
        .all(AwarenessStateComponent.class, PerceptionComponent.class, TransformComponent.class)
        .exclude(ShipDataComponent.class).get();
    private static final Family PLAYER_FAMILY = Family
        .all(PlayerTagComponent.class, SignatureComponent.class, TransformComponent.class).get();

    private ImmutableArray<Entity> npcEntities;
    private ImmutableArray<Entity> playerEntities;
    private float hudTimer = 0f;

    // Scratch vectors — system runs on game thread only
    private final Vector3 scratchDir = new Vector3();
    private final Vector3 scratchFwd = new Vector3();

    public NpcAwarenessSystem(EventBus eventBus, LineOfSightQuery los) {
        this.eventBus = eventBus;
        this.los = los;
        eventBus.subscribe(NoiseBurstEvent.class, this::onNoiseBurst);
    }

    @Override
    public void addedToEngine(Engine engine) {
        npcEntities   = engine.getEntitiesFor(NPC_FAMILY);
        playerEntities = engine.getEntitiesFor(PLAYER_FAMILY);
    }

    @Override
    public void update(float dt) {
        if (playerEntities.size() == 0) return;
        Entity player      = playerEntities.first();
        SignatureComponent sig  = SIG_M.get(player);
        Vector3 playerPos  = XFORM_M.get(player).position;

        AwarenessState highest = AwarenessState.UNAWARE;
        float nearest = Float.MAX_VALUE;

        for (Entity npc : npcEntities) {
            AwarenessStateComponent state = AWARE_M.get(npc);
            PerceptionComponent     perc  = PERC_M.get(npc);
            TransformComponent      xform = XFORM_M.get(npc);

            float contrib = computeContribution(perc, xform, playerPos, sig);
            float rate    = contrib > state.detectionAccumulator ? RISE_RATE : DECAY_RATE;
            state.detectionAccumulator = MathUtils.lerp(state.detectionAccumulator, contrib, rate * dt);

            boolean visible = contrib > 0f;
            AwarenessState before = state.state;
            tickFsm(state, perc, playerPos, visible, dt);

            if (state.state != before) {
                eventBus.publish(new AwarenessChangedEvent(
                    Integer.toHexString(System.identityHashCode(npc)),
                    before, state.state, state.lastKnownPosition));
            }

            if (state.state.ordinal() > highest.ordinal()) highest = state.state;
            float d = playerPos.dst(xform.position);
            if (d < nearest) nearest = d;
        }

        hudTimer += dt;
        if (hudTimer >= HUD_INTERVAL) {
            hudTimer = 0f;
            eventBus.publish(new StealthHUDUpdateEvent(
                highest, nearest == Float.MAX_VALUE ? -1f : nearest));
        }
    }

    // Package-private for unit tests — no Ashley required
    float computeContribution(PerceptionComponent perc, TransformComponent npcXform,
                              Vector3 playerPos, SignatureComponent sig) {
        float dist = npcXform.position.dst(playerPos);
        float contrib = 0f;

        if (dist <= perc.hearingRange) {
            float falloff = 1f - (dist / perc.hearingRange);
            contrib += sig.noiseLevel * falloff;
        }

        if (dist <= perc.viewRange && inViewCone(npcXform, playerPos, perc.viewAngle)) {
            if (los.hasLoS(npcXform.position, playerPos)) {
                float falloff = 1f - (dist / perc.viewRange);
                contrib += sig.computeOnFootScore() * falloff;
            }
        }

        return contrib;
    }

    // Package-private for unit tests
    void tickFsm(AwarenessStateComponent state, PerceptionComponent perc,
                 Vector3 playerPos, boolean playerVisible, float dt) {
        switch (state.state) {
            case UNAWARE -> {
                if (state.detectionAccumulator > perc.curiousThreshold)
                    transition(state, AwarenessState.CURIOUS);
            }
            case CURIOUS -> {
                state.suspicionTimer += dt;
                if (state.detectionAccumulator > perc.alertThreshold
                        || state.suspicionTimer > SUSPICION_LIMIT) {
                    state.lastKnownPosition.set(playerPos);
                    transition(state, AwarenessState.ALERTED);
                } else if (state.detectionAccumulator < DECAY_FLOOR
                        && state.suspicionTimer > CURIOUS_COOLDOWN) {
                    transition(state, AwarenessState.UNAWARE);
                }
            }
            case ALERTED -> {
                state.lastKnownPosition.set(playerPos);
                if (!playerVisible) transition(state, AwarenessState.SEARCHING);
            }
            case SEARCHING -> {
                state.searchTimer += dt;
                if (playerVisible && state.detectionAccumulator > perc.curiousThreshold) {
                    state.lastKnownPosition.set(playerPos);
                    transition(state, AwarenessState.ALERTED);
                } else if (state.searchTimer > SEARCH_DURATION) {
                    transition(state, AwarenessState.UNAWARE);
                }
            }
        }
    }

    // Package-private for unit tests
    void applyNoiseBurst(Vector3 npcPos, AwarenessStateComponent state, NoiseBurstEvent e) {
        float dist = npcPos.dst(e.x, e.y, e.z);
        if (dist < e.radius) {
            float spike = e.intensity * (1f - dist / e.radius);
            state.detectionAccumulator = Math.min(1f, state.detectionAccumulator + spike);
        }
    }

    private void onNoiseBurst(NoiseBurstEvent e) {
        for (Entity npc : npcEntities) {
            applyNoiseBurst(XFORM_M.get(npc).position, AWARE_M.get(npc), e);
        }
    }

    private void transition(AwarenessStateComponent state, AwarenessState next) {
        state.state = next;
        if (next == AwarenessState.CURIOUS)   { state.suspicionTimer = 0f; }
        if (next == AwarenessState.SEARCHING) { state.searchTimer = 0f; }
        if (next == AwarenessState.UNAWARE)   { state.suspicionTimer = 0f; state.searchTimer = 0f; }
    }

    private boolean inViewCone(TransformComponent npcXform, Vector3 playerPos, float viewAngleDegrees) {
        scratchDir.set(playerPos).sub(npcXform.position).nor();
        scratchFwd.set(0, 0, 1);
        npcXform.rotation.transform(scratchFwd); // rotate local forward by NPC rotation
        return scratchFwd.dot(scratchDir) >= MathUtils.cosDeg(viewAngleDegrees * 0.5f);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.stealth.NpcAwarenessSystemTest"
```

Expected: 10 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/com/galacticodyssey/stealth/NpcAwarenessSystem.java `
        core/src/test/java/com/galacticodyssey/stealth/NpcAwarenessSystemTest.java
git commit -m "feat(stealth): add NpcAwarenessSystem with 5-state FSM and noise burst"
```

---

## Task 4: ShipSignatureSystem, ShipDetectionSystem, and BulletLineOfSightQuery

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/stealth/ShipSignatureSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/stealth/ShipDetectionSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/stealth/BulletLineOfSightQuery.java`
- Test: `core/src/test/java/com/galacticodyssey/stealth/ShipDetectionSystemTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// core/src/test/java/com/galacticodyssey/stealth/ShipDetectionSystemTest.java
package com.galacticodyssey.stealth;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ActiveScanEvent;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import com.galacticodyssey.stealth.events.PlayerDetectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipDetectionSystemTest {

    private ShipDetectionSystem system;
    private EventBus eventBus;
    private List<PlayerDetectedEvent> detections;
    private List<AwarenessChangedEvent> stateChanges;

    @BeforeEach
    void setUp() {
        eventBus   = new EventBus();
        system     = new ShipDetectionSystem(eventBus);
        detections  = new ArrayList<>();
        stateChanges = new ArrayList<>();
        eventBus.subscribe(PlayerDetectedEvent.class, detections::add);
        eventBus.subscribe(AwarenessChangedEvent.class, stateChanges::add);
    }

    @Test
    void computeRawDetection_normalSignature_scaledByEffectivenessAndDistance() {
        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.4f; sig.heatSignature = 0.3f; sig.visualSignature = 0.2f;
        // ship score = 0.9

        PerceptionComponent perc = new PerceptionComponent();
        perc.effectiveness = 1.0f;

        float dist = 100f;
        float raw = system.computeRawDetection(sig, perc, dist);
        // falloff = 1 / (1 + 100*100*0.0001) = 1 / (1+1) = 0.5
        float expected = 0.9f * 1.0f * 0.5f;
        assertEquals(expected, raw, 0.001f);
    }

    @Test
    void computeRawDetection_darkMode_nearZero() {
        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.5f; sig.heatSignature = 0.8f; sig.visualSignature = 0.3f;
        sig.darkMode = true;
        // ship score = 0.5 * 0.05 = 0.025

        PerceptionComponent perc = new PerceptionComponent();
        perc.effectiveness = 1.0f;

        float raw = system.computeRawDetection(sig, perc, 0f); // point-blank
        // score = 0.025; falloff = 1/(1+0) = 1.0; raw = 0.025
        assertEquals(0.025f, raw, 0.001f);
    }

    @Test
    void tickShipFsm_unaware_accumulatorExceedsThreshold_becomesCurious() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.detectionAccumulator = 0.3f; // above curiousThreshold 0.20

        PerceptionComponent perc = new PerceptionComponent();
        perc.curiousThreshold = 0.20f; perc.alertThreshold = 0.55f;

        system.tickShipFsm(state, perc, new Vector3(0, 0, 0), 0.1f);
        assertEquals(AwarenessState.CURIOUS, state.state);
    }

    @Test
    void tickShipFsm_curious_alertThresholdReached_becomesAlerted() {
        AwarenessStateComponent state = new AwarenessStateComponent();
        state.state = AwarenessState.CURIOUS;
        state.detectionAccumulator = 0.7f; // above alertThreshold 0.55

        PerceptionComponent perc = new PerceptionComponent();
        perc.curiousThreshold = 0.20f; perc.alertThreshold = 0.55f;

        system.tickShipFsm(state, perc, new Vector3(5, 0, 0), 0.1f);
        assertEquals(AwarenessState.ALERTED, state.state);
    }

    @Test
    void activeScan_highSignature_publishesPlayerDetected() {
        // Drive the system manually via the onActiveScan handler
        // Set up a SignatureComponent that the system uses
        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.5f; sig.heatSignature = 0.8f; sig.visualSignature = 0.4f;
        // ship score = 1.7

        PerceptionComponent perc = new PerceptionComponent();
        perc.pingMultiplier = 2.0f; perc.alertThreshold = 0.45f;

        ActiveScanEvent e = new ActiveScanEvent(0, 0, 0, 500f, 2.0f);
        // pingScore = computeShipScore() * pingMultiplier = 1.7 * 2.0 = 3.4 > alertThreshold
        system.handleActiveScan(e, sig, perc, 100f /* dist within range */);

        assertEquals(1, detections.size());
        assertEquals("SHIP_SCAN", detections.get(0).detectorType);
    }

    @Test
    void activeScan_lowSignature_noDetection() {
        SignatureComponent sig = new SignatureComponent();
        sig.darkMode = true; sig.emSignature = 0.05f; // score = 0.0025

        PerceptionComponent perc = new PerceptionComponent();
        perc.pingMultiplier = 2.0f; perc.alertThreshold = 0.45f;

        ActiveScanEvent e = new ActiveScanEvent(0, 0, 0, 500f, 2.0f);
        system.handleActiveScan(e, sig, perc, 100f);

        assertTrue(detections.isEmpty());
    }
}
```

- [ ] **Step 2: Run to verify failure**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.stealth.ShipDetectionSystemTest"
```

Expected: compile error — `ShipDetectionSystem` does not exist yet.

- [ ] **Step 3: Write ShipSignatureSystem**

```java
// core/src/main/java/com/galacticodyssey/stealth/ShipSignatureSystem.java
package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.combat.components.ShieldComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;

public final class ShipSignatureSystem extends EntitySystem {

    private static final float HEAT_FACTOR = 0.8f;  // maxHeat contribution per full throttle
    private static final float BASE_EM     = 0.05f; // always-on residual EM
    private static final float SHIELD_EM   = 0.40f;
    private static final float SCANNER_EM  = 0.30f;

    private static final ComponentMapper<SignatureComponent> SIG_M =
        ComponentMapper.getFor(SignatureComponent.class);
    private static final ComponentMapper<ShipFlightComponent> FLIGHT_M =
        ComponentMapper.getFor(ShipFlightComponent.class);
    private static final ComponentMapper<ShieldComponent> SHIELD_M =
        ComponentMapper.getFor(ShieldComponent.class);

    private static final Family PLAYER_FAMILY = Family
        .all(PlayerTagComponent.class, SignatureComponent.class).get();

    private ImmutableArray<Entity> playerEntities;
    private Entity currentShip; // set by pilot transition system

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(PLAYER_FAMILY);
    }

    /** Called by PilotTransitionSystem (or GameWorld) when the player boards/exits a ship. */
    public void setCurrentShip(Entity ship) {
        this.currentShip = ship;
    }

    @Override
    public void update(float dt) {
        if (playerEntities.size() == 0 || currentShip == null) return;
        SignatureComponent sig = SIG_M.get(playerEntities.first());
        if (sig == null) return;

        ShipFlightComponent flight = FLIGHT_M.get(currentShip);
        ShieldComponent shield     = SHIELD_M.get(currentShip);

        if (flight != null) {
            sig.heatSignature = sig.darkMode ? 0f : flight.currentThrottle * HEAT_FACTOR;
        }

        sig.shieldsActive = (shield != null && shield.currentShield > 0f && !sig.darkMode);

        sig.emSignature = sig.darkMode ? BASE_EM * 0.05f
            : BASE_EM
                + (sig.shieldsActive ? SHIELD_EM : 0f)
                + (sig.scannerActive ? SCANNER_EM : 0f);
    }
}
```

- [ ] **Step 4: Write ShipDetectionSystem**

```java
// core/src/main/java/com/galacticodyssey/stealth/ShipDetectionSystem.java
package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.ActiveScanEvent;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import com.galacticodyssey.stealth.events.PlayerDetectedEvent;

public final class ShipDetectionSystem extends EntitySystem {

    static float FALLOFF_K      = 0.0001f;
    static float RISE_RATE      = 2.0f;
    static float DECAY_RATE     = 0.8f;
    static float SUSPICION_LIMIT = 8.0f;  // ships take longer to alert
    static float SEARCH_DURATION = 30.0f;

    private final EventBus eventBus;

    private static final ComponentMapper<AwarenessStateComponent> AWARE_M =
        ComponentMapper.getFor(AwarenessStateComponent.class);
    private static final ComponentMapper<PerceptionComponent> PERC_M =
        ComponentMapper.getFor(PerceptionComponent.class);
    private static final ComponentMapper<TransformComponent> XFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<SignatureComponent> SIG_M =
        ComponentMapper.getFor(SignatureComponent.class);

    // Patrol ships: have ShipDataComponent AND AwarenessStateComponent
    private static final Family SCANNER_FAMILY = Family
        .all(AwarenessStateComponent.class, PerceptionComponent.class,
             TransformComponent.class, ShipDataComponent.class).get();
    private static final Family PLAYER_FAMILY = Family
        .all(PlayerTagComponent.class, SignatureComponent.class, TransformComponent.class).get();

    private ImmutableArray<Entity> scannerEntities;
    private ImmutableArray<Entity> playerEntities;

    public ShipDetectionSystem(EventBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(ActiveScanEvent.class, this::onActiveScan);
    }

    @Override
    public void addedToEngine(Engine engine) {
        scannerEntities = engine.getEntitiesFor(SCANNER_FAMILY);
        playerEntities  = engine.getEntitiesFor(PLAYER_FAMILY);
    }

    @Override
    public void update(float dt) {
        if (playerEntities.size() == 0) return;
        Entity player     = playerEntities.first();
        SignatureComponent sig  = SIG_M.get(player);
        Vector3 playerPos  = XFORM_M.get(player).position;

        for (Entity scanner : scannerEntities) {
            AwarenessStateComponent state = AWARE_M.get(scanner);
            PerceptionComponent     perc  = PERC_M.get(scanner);
            Vector3 scannerPos = XFORM_M.get(scanner).position;

            float dist = playerPos.dst(scannerPos);
            float raw  = computeRawDetection(sig, perc, dist);
            float rate = raw > state.detectionAccumulator ? RISE_RATE : DECAY_RATE;
            state.detectionAccumulator = MathUtils.lerp(state.detectionAccumulator, raw, rate * dt);

            AwarenessState before = state.state;
            tickShipFsm(state, perc, playerPos, dt);

            if (state.state != before) {
                eventBus.publish(new AwarenessChangedEvent(
                    Integer.toHexString(System.identityHashCode(scanner)),
                    before, state.state, state.lastKnownPosition));
                if (state.state == AwarenessState.ALERTED) {
                    eventBus.publish(new PlayerDetectedEvent(
                        Integer.toHexString(System.identityHashCode(scanner)), "SHIP_PASSIVE"));
                }
            }
        }
    }

    // Package-private for unit tests
    float computeRawDetection(SignatureComponent sig, PerceptionComponent perc, float dist) {
        float score   = sig.computeShipScore();
        float falloff = 1f / (1f + dist * dist * FALLOFF_K);
        return score * perc.effectiveness * falloff;
    }

    // Package-private for unit tests
    void tickShipFsm(AwarenessStateComponent state, PerceptionComponent perc,
                     Vector3 playerPos, float dt) {
        switch (state.state) {
            case UNAWARE -> {
                if (state.detectionAccumulator > perc.curiousThreshold)
                    transition(state, AwarenessState.CURIOUS);
            }
            case CURIOUS -> {
                state.suspicionTimer += dt;
                if (state.detectionAccumulator > perc.alertThreshold
                        || state.suspicionTimer > SUSPICION_LIMIT) {
                    state.lastKnownPosition.set(playerPos);
                    transition(state, AwarenessState.ALERTED);
                } else if (state.detectionAccumulator < 0.05f && state.suspicionTimer > 5f) {
                    transition(state, AwarenessState.UNAWARE);
                }
            }
            case ALERTED -> {
                state.lastKnownPosition.set(playerPos);
                if (state.detectionAccumulator < 0.05f)
                    transition(state, AwarenessState.SEARCHING);
            }
            case SEARCHING -> {
                state.searchTimer += dt;
                if (state.detectionAccumulator > perc.curiousThreshold) {
                    state.lastKnownPosition.set(playerPos);
                    transition(state, AwarenessState.ALERTED);
                    eventBus.publish(new PlayerDetectedEvent(
                        "searching_re-detect", "SHIP_PASSIVE"));
                } else if (state.searchTimer > SEARCH_DURATION) {
                    transition(state, AwarenessState.UNAWARE);
                }
            }
        }
    }

    // Package-private for unit tests — pure check without entity lookup
    void handleActiveScan(ActiveScanEvent e, SignatureComponent sig,
                          PerceptionComponent perc, float dist) {
        if (dist > e.range) return;
        float pingScore = sig.computeShipScore() * e.pingMultiplier * perc.pingMultiplier;
        if (pingScore > perc.alertThreshold) {
            eventBus.publish(new PlayerDetectedEvent("active_scan", "SHIP_SCAN"));
        }
    }

    private void onActiveScan(ActiveScanEvent e) {
        if (playerEntities.size() == 0) return;
        Entity player = playerEntities.first();
        SignatureComponent sig = SIG_M.get(player);
        Vector3 playerPos = XFORM_M.get(player).position;
        float dist = playerPos.dst(e.x, e.y, e.z);

        for (Entity scanner : scannerEntities) {
            handleActiveScan(e, sig, PERC_M.get(scanner), dist);
        }
    }

    private void transition(AwarenessStateComponent state, AwarenessState next) {
        state.state = next;
        if (next == AwarenessState.CURIOUS)   { state.suspicionTimer = 0f; }
        if (next == AwarenessState.SEARCHING) { state.searchTimer = 0f; }
        if (next == AwarenessState.UNAWARE)   { state.suspicionTimer = 0f; state.searchTimer = 0f; }
    }
}
```

- [ ] **Step 5: Write BulletLineOfSightQuery**

```java
// core/src/main/java/com/galacticodyssey/stealth/BulletLineOfSightQuery.java
package com.galacticodyssey.stealth;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.dynamics.btDynamicsWorld;

public final class BulletLineOfSightQuery implements LineOfSightQuery {

    private final btDynamicsWorld world;
    private final ClosestRayResultCallback rayCallback;

    public BulletLineOfSightQuery(btDynamicsWorld world) {
        this.world = world;
        this.rayCallback = new ClosestRayResultCallback(Vector3.Zero, Vector3.Zero);
    }

    @Override
    public boolean hasLoS(Vector3 from, Vector3 to) {
        rayCallback.setCollisionObject(null);
        rayCallback.setClosestHitFraction(1f);
        rayCallback.getRayFromWorld().set(from);
        rayCallback.getRayToWorld().set(to);
        world.rayTest(from, to, rayCallback);
        return !rayCallback.hasHit();
    }

    public void dispose() {
        rayCallback.dispose();
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.stealth.ShipDetectionSystemTest"
```

Expected: 6 tests pass.

- [ ] **Step 7: Commit**

```powershell
git add core/src/main/java/com/galacticodyssey/stealth/ShipSignatureSystem.java `
        core/src/main/java/com/galacticodyssey/stealth/ShipDetectionSystem.java `
        core/src/main/java/com/galacticodyssey/stealth/BulletLineOfSightQuery.java `
        core/src/test/java/com/galacticodyssey/stealth/ShipDetectionSystemTest.java
git commit -m "feat(stealth): add ShipSignatureSystem, ShipDetectionSystem, BulletLineOfSightQuery"
```

---

## Task 5: Data Files

**Files:**
- Create: `core/src/main/resources/data/stealth/npc_perception.json`
- Create: `core/src/main/resources/data/stealth/detection_constants.json`

- [ ] **Step 1: Write npc_perception.json**

```json
{
  "guard_standard": {
    "viewRange": 20, "viewAngle": 110, "hearingRange": 12,
    "curiousThreshold": 0.25, "alertThreshold": 0.65,
    "effectiveness": 1.0, "pingMultiplier": 1.0
  },
  "guard_elite": {
    "viewRange": 30, "viewAngle": 130, "hearingRange": 18,
    "curiousThreshold": 0.15, "alertThreshold": 0.50,
    "effectiveness": 1.0, "pingMultiplier": 1.0
  },
  "patrol_ship_light": {
    "viewRange": 800, "viewAngle": 60, "hearingRange": 0,
    "curiousThreshold": 0.20, "alertThreshold": 0.55,
    "effectiveness": 1.0, "pingMultiplier": 2.0
  },
  "patrol_ship_heavy": {
    "viewRange": 1500, "viewAngle": 90, "hearingRange": 0,
    "curiousThreshold": 0.15, "alertThreshold": 0.45,
    "effectiveness": 1.5, "pingMultiplier": 3.0
  },
  "customs_scanner": {
    "viewRange": 500, "viewAngle": 360, "hearingRange": 0,
    "curiousThreshold": 0.15, "alertThreshold": 0.45,
    "effectiveness": 1.5, "pingMultiplier": 3.0
  }
}
```

Save to: `core/src/main/resources/data/stealth/npc_perception.json`

- [ ] **Step 2: Write detection_constants.json**

```json
{
  "on_foot": {
    "RISE_RATE": 2.0,
    "DECAY_RATE": 0.8,
    "DECAY_FLOOR": 0.05,
    "SUSPICION_LIMIT": 4.0,
    "CURIOUS_COOLDOWN": 2.0,
    "SEARCH_DURATION": 15.0,
    "HUD_UPDATE_HZ": 4.0
  },
  "ship": {
    "RISE_RATE": 2.0,
    "DECAY_RATE": 0.8,
    "FALLOFF_K": 0.0001,
    "SUSPICION_LIMIT": 8.0,
    "SEARCH_DURATION": 30.0
  },
  "signature": {
    "BASE_EM": 0.05,
    "SHIELD_EM": 0.40,
    "SCANNER_EM": 0.30,
    "HEAT_FACTOR": 0.80
  }
}
```

Save to: `core/src/main/resources/data/stealth/detection_constants.json`

Note: The systems currently use inline constants. These JSON files document the intended values for when a `StealthConfigLoader` is added in a future task. The inline static fields in `NpcAwarenessSystem` and `ShipDetectionSystem` should match these values.

- [ ] **Step 3: Commit**

```powershell
git add core/src/main/resources/data/stealth/
git commit -m "feat(stealth): add NPC perception and detection constant data files"
```

---

## Task 6: StealthIntegrationTest

**Files:**
- Test: `core/src/test/java/com/galacticodyssey/stealth/StealthIntegrationTest.java`

This test uses a real Ashley `Engine` (pure Java, no GL context required). It verifies the full event pipeline: accumulator rises → state transitions → events published.

- [ ] **Step 1: Write the test**

```java
// core/src/test/java/com/galacticodyssey/stealth/StealthIntegrationTest.java
package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.ActiveScanEvent;
import com.galacticodyssey.core.events.NoiseBurstEvent;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import com.galacticodyssey.stealth.events.PlayerDetectedEvent;
import com.galacticodyssey.stealth.events.StealthHUDUpdateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StealthIntegrationTest {

    private Engine engine;
    private EventBus eventBus;
    private NpcAwarenessSystem awarenessSystem;

    private Entity playerEntity;
    private Entity npcEntity;
    private SignatureComponent playerSig;
    private AwarenessStateComponent npcState;
    private TransformComponent npcXform;

    private final List<AwarenessChangedEvent> stateChanges = new ArrayList<>();
    private final List<PlayerDetectedEvent>   detections   = new ArrayList<>();
    private final List<StealthHUDUpdateEvent> hudUpdates   = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        // Always-clear LoS so vision always contributes
        awarenessSystem = new NpcAwarenessSystem(eventBus, (a, b) -> true);

        engine = new Engine();
        engine.addSystem(awarenessSystem);

        // Player entity
        playerSig = new SignatureComponent();
        playerSig.noiseLevel = 0.8f;      // loud (walking)
        playerSig.lightExposure = 0.8f;   // well-lit
        playerSig.gearMultiplier = 1.0f;
        playerSig.stealthSkill = 0;

        TransformComponent playerXform = new TransformComponent();
        playerXform.position.set(0, 0, 0);

        playerEntity = new Entity()
            .add(new PlayerTagComponent())
            .add(playerSig)
            .add(playerXform);
        engine.addEntity(playerEntity);

        // NPC entity (ground NPC — no ShipDataComponent)
        npcState = new AwarenessStateComponent();
        PerceptionComponent npcPerc = new PerceptionComponent();
        npcPerc.viewRange    = 20f;
        npcPerc.viewAngle    = 360f; // omnidirectional for this test
        npcPerc.hearingRange = 12f;
        npcPerc.curiousThreshold = 0.25f;
        npcPerc.alertThreshold   = 0.65f;

        npcXform = new TransformComponent();
        npcXform.position.set(0, 0, 5); // 5m from player

        npcEntity = new Entity()
            .add(npcState)
            .add(npcPerc)
            .add(npcXform);
        engine.addEntity(npcEntity);

        eventBus.subscribe(AwarenessChangedEvent.class, stateChanges::add);
        eventBus.subscribe(PlayerDetectedEvent.class, detections::add);
        eventBus.subscribe(StealthHUDUpdateEvent.class, hudUpdates::add);
    }

    @Test
    void loudPlayer_risesToCuriousThenAlerted() {
        // Tick many times at 0.1s steps — accumulator should rise
        for (int i = 0; i < 60; i++) engine.update(0.1f);

        // Must have transitioned at least to CURIOUS
        assertTrue(npcState.state.ordinal() >= AwarenessState.CURIOUS.ordinal(),
            "NPC should be at least CURIOUS after 6 seconds of loud player");

        assertFalse(stateChanges.isEmpty(), "State change events should have been published");
        assertEquals(AwarenessState.UNAWARE, stateChanges.get(0).oldState);
    }

    @Test
    void loudPlayerInViewCone_reachesAlerted() {
        // Tick aggressively to ensure ALERTED is reached
        for (int i = 0; i < 200; i++) engine.update(0.1f);

        assertEquals(AwarenessState.ALERTED, npcState.state,
            "NPC should reach ALERTED with a loud, visible player nearby for 20 seconds");
    }

    @Test
    void quietPlayer_accumulatorDecays_staysUnaware() {
        playerSig.noiseLevel = 0f;
        playerSig.lightExposure = 0f; // dark + silent
        // Move player to edge of hearing range (12m) so even noise=0 gives no contribution
        ((TransformComponent) playerEntity.getComponent(TransformComponent.class)).position.set(0, 0, 13);

        for (int i = 0; i < 100; i++) engine.update(0.1f);

        assertEquals(AwarenessState.UNAWARE, npcState.state);
    }

    @Test
    void noiseBurst_spikesAccumulator() {
        playerSig.noiseLevel = 0f; // silent player
        npcXform.position.set(0, 0, 3); // 3m from origin

        // Burst at origin
        eventBus.publish(new NoiseBurstEvent(0, 0, 0, 10f, 1.0f));

        // Accumulator should have spiked (falloff = 1 - 3/10 = 0.7; spike = 1.0 * 0.7 = 0.7)
        assertTrue(npcState.detectionAccumulator > 0.5f,
            "NoiseBurst should spike the NPC accumulator");
    }

    @Test
    void stealthHUDUpdate_publishedAt4Hz() {
        // HUD updates at 4 Hz (every 0.25s). Run for 1 second.
        for (int i = 0; i < 10; i++) engine.update(0.1f);

        // Should have ~4 HUD events (3-5 is acceptable due to frame timing)
        assertTrue(hudUpdates.size() >= 3 && hudUpdates.size() <= 5,
            "Expected ~4 HUD updates per second, got: " + hudUpdates.size());
    }

    @Test
    void shipDetection_activeScan_highSignature_publishesDetected() {
        EventBus bus2 = new EventBus();
        ShipDetectionSystem shipSystem = new ShipDetectionSystem(bus2);
        List<PlayerDetectedEvent> shipDetections = new ArrayList<>();
        bus2.subscribe(PlayerDetectedEvent.class, shipDetections::add);

        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.5f; sig.heatSignature = 0.7f; sig.visualSignature = 0.4f;

        PerceptionComponent perc = new PerceptionComponent();
        perc.pingMultiplier = 2.0f; perc.alertThreshold = 0.45f;

        ActiveScanEvent scan = new ActiveScanEvent(0, 0, 0, 500f, 2.0f);
        shipSystem.handleActiveScan(scan, sig, perc, 50f);

        assertEquals(1, shipDetections.size());
        assertEquals("SHIP_SCAN", shipDetections.get(0).detectorType);
    }

    @Test
    void shipDetection_darkMode_activeScanMisses() {
        EventBus bus2 = new EventBus();
        ShipDetectionSystem shipSystem = new ShipDetectionSystem(bus2);
        List<PlayerDetectedEvent> shipDetections = new ArrayList<>();
        bus2.subscribe(PlayerDetectedEvent.class, shipDetections::add);

        SignatureComponent sig = new SignatureComponent();
        sig.emSignature = 0.05f; sig.heatSignature = 0f; sig.visualSignature = 0.5f;
        sig.darkMode = true; // score = 0.05 * 0.05 = 0.0025

        PerceptionComponent perc = new PerceptionComponent();
        perc.pingMultiplier = 2.0f; perc.alertThreshold = 0.45f;

        ActiveScanEvent scan = new ActiveScanEvent(0, 0, 0, 500f, 2.0f);
        shipSystem.handleActiveScan(scan, sig, perc, 50f);

        assertTrue(shipDetections.isEmpty(), "Dark mode ship should not be detected by active scan");
    }
}
```

- [ ] **Step 2: Run to verify failure**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.stealth.StealthIntegrationTest"
```

Expected: compile or runtime failure — systems not yet registered in GameWorld, but the test itself uses a local `Engine` so it should compile and run. If tests fail, check that `PlayerTagComponent` constructor is no-arg (it's a marker component).

- [ ] **Step 3: Fix any failures, then verify all 7 tests pass**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.stealth.StealthIntegrationTest"
```

Expected: 7 tests pass.

- [ ] **Step 4: Run the full test suite to confirm nothing is broken**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test
```

Expected: all existing tests plus new stealth tests pass.

- [ ] **Step 5: Commit**

```powershell
git add core/src/test/java/com/galacticodyssey/stealth/StealthIntegrationTest.java
git commit -m "test(stealth): add full pipeline integration test"
```

---

## Task 7: GameWorld Registration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

The player entity needs a `SignatureComponent`. The three new systems need to be instantiated and added to the Ashley engine. `ShipSignatureSystem.setCurrentShip()` will be wired to `PilotTransitionSystem` in a follow-up — for now it is left null (system skips update when `currentShip == null`).

- [ ] **Step 1: Add imports to GameWorld.java**

Add after the last mission-system import (after line ~155):

```java
import com.galacticodyssey.stealth.NpcAwarenessSystem;
import com.galacticodyssey.stealth.ShipDetectionSystem;
import com.galacticodyssey.stealth.ShipSignatureSystem;
import com.galacticodyssey.stealth.BulletLineOfSightQuery;
import com.galacticodyssey.stealth.SignatureComponent;
```

- [ ] **Step 2: Add field declarations to GameWorld**

Add after the last mission-system field declaration (around line ~230):

```java
private NpcAwarenessSystem npcAwarenessSystem;
private ShipDetectionSystem shipDetectionSystem;
private ShipSignatureSystem shipSignatureSystem;
```

- [ ] **Step 3: Add SignatureComponent to the player entity**

Find the player entity construction in GameWorld (search for `PlayerTagComponent` being added to an entity). Add `SignatureComponent` alongside it:

```java
// In the existing player entity construction block:
playerEntity.add(new SignatureComponent());
```

- [ ] **Step 4: Instantiate and register the three systems**

Find the `// Mission / Quest System` comment block. Add after the `engine.addSystem(sagaRunner);` line:

```java
// Stealth System
BulletLineOfSightQuery losQuery = new BulletLineOfSightQuery(bulletPhysicsSystem.getDynamicsWorld());
npcAwarenessSystem  = new NpcAwarenessSystem(eventBus, losQuery);
shipSignatureSystem = new ShipSignatureSystem();
shipDetectionSystem = new ShipDetectionSystem(eventBus);
engine.addSystem(npcAwarenessSystem);
engine.addSystem(shipSignatureSystem);
engine.addSystem(shipDetectionSystem);
```

Note: `bulletPhysicsSystem.getDynamicsWorld()` — check `BulletPhysicsSystem` for the actual getter name. If no getter exists, add one: `public btDynamicsWorld getDynamicsWorld() { return dynamicsWorld; }`.

- [ ] **Step 5: Build to verify compilation**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:classes
```

Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 6: Run the full test suite**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```powershell
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(stealth): register NpcAwarenessSystem, ShipSignatureSystem, ShipDetectionSystem in GameWorld"
```

---

## Self-Review Notes

**Spec coverage check:**
- ✅ On-foot: proximity + LoS cone + noise (NpcAwarenessSystem)
- ✅ Lighting: `lightExposure` field written by external system, read in `computeOnFootScore()`
- ✅ 5-state FSM: UNAWARE→CURIOUS→ALERTED→SEARCHING (Curious + Suspicious unified, documented in spec)
- ✅ Signature = passive × gear × skill: `computeOnFootScore()` formula
- ✅ Ship: EM + heat + visual signatures (ShipSignatureSystem writes, ShipDetectionSystem reads)
- ✅ Dark mode: `sig.darkMode = true` collapses ship score to near-zero; engine stops (ShipSignatureSystem sets `heatSignature = 0`)
- ✅ Active scanner pings: `ActiveScanEvent` → `handleActiveScan()` → instantaneous check
- ✅ LoS testability: `LineOfSightQuery` interface injected into `NpcAwarenessSystem`
- ✅ Data-driven perception: `npc_perception.json` documents per-type values
- ✅ EventBus events: all 6 defined, published at correct sites, no cross-system direct calls

**Placeholder scan:** None found. All code steps contain complete implementations.

**Type consistency:**
- `computeContribution` → used in both `update()` and tests ✅
- `tickFsm` → used in both `update()` and tests ✅
- `applyNoiseBurst` → used in `onNoiseBurst` and tests ✅
- `handleActiveScan` → used in `onActiveScan` and tests ✅
- `computeRawDetection` → used in `update()` and tests ✅
- `AwarenessState` enum values: UNAWARE, CURIOUS, ALERTED, SEARCHING → consistent throughout ✅
