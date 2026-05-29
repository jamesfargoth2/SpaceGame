# Prone Mechanics Design

**Date:** 2026-05-28
**Status:** Approved

## Overview

Adds a prone posture to the FPS player. When prone the physics capsule shrinks to a 0.6m-tall sphere, allowing the player to crawl through tight spaces and take cover under low geometry. Toggle with Z. Lean (Q/E) remains active while prone.

---

## State & Input

### Component changes

**`MovementStateComponent`**
- Add `boolean isProne`

**`PlayerInputComponent`**
- Add `boolean proneToggleRequested` — one-shot flag, reset each frame (same pattern as `jumpRequested`)

**`FPSCameraComponent`**
- Add `float proneEyeHeight = 0.3f` — third target value for the existing eye-height lerp

### State machine

| From | Input | To | Clearance check? |
|------|-------|----|-----------------|
| Standing | Z | Prone | No |
| Crouching | Z | Prone | No (clears `isCrouching`, sets `isProne`) |
| Prone | Z | Standing | Yes — ray 1.2m upward; blocked → stay prone |

CTRL (crouch) behaviour is unchanged.

### Constraints while prone

| Action | Allowed? |
|--------|----------|
| Sprint | No |
| Jump | No |
| Lean (Q/E) | **Yes** |
| Interact (F) | Yes |
| Aim/fire | Yes |

---

## Physics — Capsule Swap

### Shape dimensions

| Posture | `btCapsuleShape` args | Total height |
|---------|-----------------------|--------------|
| Standing | `radius=0.3f, height=1.2f` | 1.8m |
| Prone | `radius=0.3f, height=0.0f` | 0.6m (sphere) |

Same lateral radius in both postures — no horizontal profile change.

### Lifecycle

Both shapes are **pre-allocated** in `GameWorld.createPlayerEntity` alongside the rigid body. A `proneShape` field is added to the component that already holds the `btRigidBody`. Both shapes are disposed with the entity.

### Swap procedure (in `PlayerMovementSystem`)

1. Remove rigid body from dynamics world
2. `rigidBody.setCollisionShape(targetShape)`
3. Recalculate local inertia for the new shape
4. Re-add rigid body to dynamics world

### Clearance check (prone → standing only)

Cast a ray upward from capsule centre by **0.9m** (the standing capsule half-height — how far its top reaches above the body origin). If the ray hits geometry, block the transition — player must move to clear space first. No automatic crouch fallback.

---

## Movement & Camera

### Speed

```
PRONE_SPEED = 0.8f m/s   (half of CROUCH_SPEED 1.5f)
```

### Stamina

No drain while prone. Stamina regen continues normally.

### Eye height target selection (`CameraSystem`)

```java
if (isProne)       target = proneEyeHeight;      // 0.3f
else if (isCrouching) target = crouchEyeHeight;  // 1.0f
else               target = eyeHeight;            // 1.7f
```

Uses the existing `EYE_HEIGHT_LERP_SPEED = 10f`. Standing → Prone covers 1.4m in ~0.14s.

### Head bob while prone

Amplitude reduced to `0.02f` (vs `0.04f` standing) — crawling produces slight movement. Frequency unchanged.

---

## Events & Integration

### Event bus

Publish `PlayerPostureChangedEvent` carrying `PostureType previousPosture` and `PostureType newPosture`.

```java
enum PostureType { STANDING, CROUCHING, PRONE }
```

Subscribers (audio, animation, networking) react independently.

### Cross-system integration

| System | Behaviour |
|--------|-----------|
| Swimming | `isProne` cleared and standing shape restored before entering swim state |
| Boarding / driving | `isProne` cleared and standing shape restored before `PILOTING` / `DRIVING` transition |
| Networking | `isProne` added to replicated movement state payload |

---

## Testing Surface

### Unit tests

- All 4 state transitions (stand→prone, crouch→prone, prone→stand clear, prone→stand blocked)
- Clearance-blocked stand-up stays prone
- Speed selection returns `PRONE_SPEED` when `isProne`
- Eye height target returns `proneEyeHeight` when `isProne`
- Capsule swap does not corrupt inertia tensor
- Stamina does not drain while prone

### Integration tests

- Player fits through a 0.65m gap while prone
- Player cannot stand when ceiling is 0.7m above (ray blocked)
- Lean (Q/E) applies camera offset correctly while prone
