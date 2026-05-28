# Stealth System Design

**Date:** 2026-05-27
**Branch:** procgen

---

## Overview

A dual-mode stealth system covering both on-foot (FPS) and ship (6DOF) play. On-foot uses a full detection model — proximity, line-of-sight, noise, and lighting — with a 5-state NPC awareness FSM. Ship stealth uses EM/heat/visual signature scaling, active scanner pings for traffic-control scenarios, and a dark-mode mechanic that cuts all power for near-zero signature at the cost of mobility.

Player signature is determined by three stacking layers: passive environment state (movement speed, ambient lighting), gear multipliers (stealth suit, noise dampeners), and a Stealth skill (0–100) that provides a global reduction of up to 50%.

---

## Architecture

### Two systems, one shared data layer

```
NpcAwarenessSystem       ShipDetectionSystem
       │                        │
       └────────┬───────────────┘
                │
       SignatureComponent      ← on the player entity
       AwarenessStateComponent ← on each NPC / patrol ship
       PerceptionComponent     ← on each NPC / patrol ship
                │
           EventBus
     AwarenessChangedEvent
     PlayerDetectedEvent
     DarkModeToggledEvent
     NoiseBurstEvent
     StealthHUDUpdateEvent
```

`NpcAwarenessSystem` and `ShipDetectionSystem` are independent Ashley `EntitySystem` subclasses. They share ECS components but never call each other. Communication is exclusively through the EventBus.

---

## Components

### SignatureComponent (on player entity)

```java
package com.galacticodyssey.stealth;

public class SignatureComponent implements Component {
    // On-foot — written by movement and lighting systems
    public float noiseLevel;      // 0–1: prone-still=0.0, walking=0.5, sprinting=1.0
    public float lightExposure;   // 0–1: written by LightingSystem each frame
    public float gearMultiplier;  // product of equipped stealth gear modifiers
    public int   stealthSkill;    // 0–100; each 2 points = 1% signature reduction

    // Ship — written by ShipSignatureSystem
    public float emSignature;     // 0–1: shields+scanners raise this
    public float heatSignature;   // 0–1: scales with engine throttle
    public float visualSignature; // 0–1: stealth coating reduces this
    public boolean darkMode;      // engine cutoff — near-zero EM, zero heat, no thrust

    public float computeOnFootScore() {
        float base  = 0.3f + noiseLevel * 0.7f;    // still = 0.3 baseline
        float lit   = 0.1f + lightExposure * 0.9f;  // pitch-black = 0.1 floor
        float skill = 1f - (stealthSkill / 200f);    // max 50% reduction at 100
        return base * lit * gearMultiplier * skill;
    }

    public float computeShipScore() {
        if (darkMode) return emSignature * 0.05f;   // near-zero residual
        return emSignature + heatSignature + visualSignature;
    }
}
```

### AwarenessStateComponent (on each NPC / patrol ship)

```java
package com.galacticodyssey.stealth;

public class AwarenessStateComponent implements Component {
    public AwarenessState state = AwarenessState.UNAWARE;
    public float detectionAccumulator; // rises when player is detectable, decays otherwise
    public float suspicionTimer;       // elapsed time in CURIOUS state
    public float searchTimer;          // elapsed time in SEARCHING state
    public Vector3 lastKnownPosition = new Vector3();
}
```

### PerceptionComponent (on each NPC / patrol ship)

```java
package com.galacticodyssey.stealth;

public class PerceptionComponent implements Component {
    public float viewRange;         // metres — LoS cone max
    public float viewAngle;         // degrees — typically 90–120°
    public float hearingRange;      // metres — noise-only detection, no LoS
    public float curiousThreshold;  // accumulator level to enter CURIOUS
    public float alertThreshold;    // accumulator level to enter ALERTED
    public float pingMultiplier;    // ships only — active scan intensity multiplier
    public float effectiveness;     // ships only — scanner quality scalar
}
```

---

## 5-State Awareness FSM

### States

The design uses 4 named states. The original "Curious" and "Suspicious" phases are unified into a single `CURIOUS` state — the `suspicionTimer` tracks how long the NPC has been in this state, and exceeding `SUSPICION_LIMIT` seconds forces the transition to `ALERTED` even if the accumulator hasn't crossed the alert threshold. This avoids a redundant state while preserving the intended behaviour.

| State | NPC behaviour |
|---|---|
| `UNAWARE` | Normal patrol |
| `CURIOUS` | Pauses, looks toward noise/movement source; suspicionTimer accumulates |
| `ALERTED` | Calls for backup, attacks or pursues; records lastKnownPosition |
| `SEARCHING` | Investigates last known position; transitions back to UNAWARE on timeout |

### Transitions

```
UNAWARE ──(accumulator > curiousThreshold)──► CURIOUS
CURIOUS ──(accumulator > alertThreshold OR suspicionTimer > SUSPICION_LIMIT)──► ALERTED
CURIOUS ──(accumulator < DECAY_FLOOR AND suspicionTimer > CURIOUS_COOLDOWN)──► UNAWARE
ALERTED ──(player leaves LoS)──► SEARCHING
ALERTED ──(player re-enters LoS)──► ALERTED (reset lastKnownPosition)
SEARCHING ──(re-detection while searching)──► ALERTED
SEARCHING ──(searchTimer > SEARCH_DURATION without re-detection)──► UNAWARE
```

Every transition publishes `AwarenessChangedEvent`. NPC AI behavior trees subscribe and switch patrol/search/combat branches. The awareness system never calls AI code directly.

---

## NpcAwarenessSystem

### Detection algorithm (per NPC per frame)

```java
float contribution = 0f;

// Hearing: omnidirectional, no LoS required
if (dist <= perception.hearingRange) {
    float falloff = 1f - (dist / perception.hearingRange);
    contribution += sig.noiseLevel * falloff;
}

// Vision: cone check first, then Bullet LoS raycast
if (dist <= perception.viewRange && inViewCone(npc, playerPos)) {
    if (lineOfSight.hasLoS(npc.position, playerPos)) {
        float falloff = 1f - (dist / perception.viewRange);
        contribution += sig.computeOnFootScore() * falloff;
    }
}

// Accumulator: rises toward contribution, decays toward 0 when no input
float rate = contribution > state.detectionAccumulator ? RISE_RATE : DECAY_RATE;
state.detectionAccumulator = MathUtils.lerp(state.detectionAccumulator, contribution, rate * dt);
```

### Noise bursts

Environmental or action noise (explosions, forced doors) fires `NoiseBurstEvent(position, radius, intensity)`. `NpcAwarenessSystem` subscribes and spikes the accumulator for all NPCs within radius, bypassing LoS.

### Throttled HUD update

`StealthHUDUpdateEvent` is published at ~4 Hz (not every frame) carrying the highest awareness state among all nearby NPCs. The HUD renders a stealth indicator from this event.

---

## ShipDetectionSystem

### Signature inputs (written by ShipSignatureSystem each frame)

```java
sig.heatSignature   = engineThrottle * hullHeatFactor;
sig.emSignature     = (shieldsActive ? 0.4f : 0f)
                    + (scannerActive ? 0.3f : 0f)
                    + BASE_EM;
sig.visualSignature = hullVisualBase * stealthCoatingMultiplier;
```

### Dark mode

Player action. Cuts all power systems:
- `engineThrottle → 0`, shields off, scanner off
- `heatSignature → 0`, `emSignature → BASE_EM * 0.05f`, `visualSignature` unchanged
- No thrust — player drifts on existing momentum only
- Cancelled by any thrust or power input
- Publishes `DarkModeToggledEvent(active)` for HUD, audio, and engine VFX

### Passive detection (per patrol ship / scanner station)

```java
float score = sig.computeShipScore();
float distanceFalloff = 1f / (1f + dist * dist * FALLOFF_K);
float rawDetection = score * perception.effectiveness * distanceFalloff;
state.detectionAccumulator = MathUtils.lerp(
    state.detectionAccumulator, rawDetection, RISE_RATE * dt);
// same 5-state FSM, different threshold values tuned for ship scale
```

### Active scanner pings (traffic control / customs)

`ActiveScanEvent(scannerId, range)` triggers an instantaneous detection check — not accumulator-based:

```java
if (dist <= e.range) {
    float pingScore = sig.computeShipScore() * perception.pingMultiplier;
    if (pingScore > perception.alertThreshold)
        eventBus.publish(new PlayerDetectedEvent(e.scannerId, "SHIP_SCAN"));
}
```

Traffic-control flow: patrol ship hails player (dialogue event) → player has a grace window to respond → if no response, `ActiveScanEvent` fires.

---

## EventBus Events

| Event | Fields | Published by | Subscribed by |
|---|---|---|---|
| `AwarenessChangedEvent` | npcId, oldState, newState, lastKnownPosition | NpcAwarenessSystem | AI, audio, HUD |
| `PlayerDetectedEvent` | detectorId, detectorType | Both systems | AI, mission system, audio |
| `DarkModeToggledEvent` | active (boolean) | Player input handler | ShipDetectionSystem, HUD, audio, VFX |
| `NoiseBurstEvent` | position, radius, intensity | Any system | NpcAwarenessSystem |
| `StealthHUDUpdateEvent` | highestNearbyState, nearestThreatDistance | NpcAwarenessSystem | HUD only |
| `ActiveScanEvent` | scannerId, range | Patrol ship AI | ShipDetectionSystem |

---

## Data / Configuration

### NPC perception (data/stealth/npc_perception.json)

```json
{
  "guard_standard":    { "viewRange": 20,  "viewAngle": 110, "hearingRange": 12, "curiousThreshold": 0.25, "alertThreshold": 0.65 },
  "guard_elite":       { "viewRange": 30,  "viewAngle": 130, "hearingRange": 18, "curiousThreshold": 0.15, "alertThreshold": 0.50 },
  "patrol_ship_light": { "viewRange": 800, "viewAngle": 60,  "hearingRange": 0,  "curiousThreshold": 0.20, "alertThreshold": 0.55, "effectiveness": 1.0, "pingMultiplier": 2.0 },
  "customs_scanner":   { "viewRange": 500, "viewAngle": 360, "hearingRange": 0,  "curiousThreshold": 0.15, "alertThreshold": 0.45, "effectiveness": 1.5, "pingMultiplier": 3.0 }
}
```

### Gear modifiers (added to existing item data files)

Stealth gear entries add a `stealthMultiplier` field (multiplicative):
- Stealth Suit: `"stealthMultiplier": 0.6`
- Noise Dampeners: `"stealthMultiplier": 0.75`
- Stealth Coating (ship armor): sets `stealthCoatingMultiplier` on the hull component

### FSM tuning constants (data/stealth/detection_constants.json)

```json
{
  "RISE_RATE": 2.0,
  "DECAY_RATE": 0.8,
  "DECAY_FLOOR": 0.05,
  "SUSPICION_LIMIT": 4.0,
  "CURIOUS_COOLDOWN": 2.0,
  "SEARCH_DURATION": 15.0,
  "FALLOFF_K": 0.0001,
  "BASE_EM": 0.05,
  "HUD_UPDATE_HZ": 4.0
}
```

---

## LoS Abstraction (testability)

The Bullet physics raycast is injected as a functional interface so unit tests substitute a lambda:

```java
@FunctionalInterface
public interface LineOfSightQuery {
    boolean hasLoS(Vector3 from, Vector3 to);
}
```

`NpcAwarenessSystem` accepts a `LineOfSightQuery` in its constructor. Production code passes the Bullet wrapper; tests pass `(a, b) -> true` or `(a, b) -> false`.

---

## Testing Approach

### Unit tests (no GL / no Ashley)

- `SignatureComponent.computeOnFootScore()` — parametric input/output
- `SignatureComponent.computeShipScore()` — dark mode branch
- Accumulator lerp math — verify rise/decay rates
- State machine transitions in isolation — construct `AwarenessStateComponent` + `PerceptionComponent` directly, call system logic methods

### Integration tests (same pattern as MissionIntegrationTest)

- Fire `NoiseBurstEvent` → verify nearby NPC accumulator spiked
- Drive `NpcAwarenessSystem.update()` in a loop with mocked LoS → capture `AwarenessChangedEvent` sequence: UNAWARE → CURIOUS → ALERTED
- Toggle dark mode → verify `ShipDetectionSystem` computes near-zero score
- Fire `ActiveScanEvent` → verify `PlayerDetectedEvent` published when score exceeds threshold

---

## Out of Scope

- Stealth takedowns / non-lethal combat (separate combat system concern)
- AI communication between NPCs (sharing last-known position) — deferred to NPC AI phase
- Rendering: light-exposure value written by `LightingSystem` — stealth system reads it, does not compute it
- Stealth skill levelling / XP — progression system concern; this system reads `stealthSkill` as an integer
