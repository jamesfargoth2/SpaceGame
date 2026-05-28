# Mech Systems

The `mech` package implements multi-legged mech locomotion, independent torso orientation, ground contact forces, and joint angle constraints.

---

## Overview

Mechs are heavy bipedal or multi-legged combat machines. Their physics differ from the player capsule or ship rigid body:

- **Legs** are driven independently by IK-based gait control.
- **Torso** can rotate independently of the leg assembly (allowing the player to aim in one direction while the mech walks in another).
- **Ground contact** is simulated per-foot rather than as a single capsule, enabling stable footing on uneven terrain.

---

## Locomotion

**`MechLocomotionSystem`**

Controls the gait: the pattern and timing of leg lifts and placements. Each frame it:
1. Reads `MechInputComponent` (move direction, speed).
2. Determines target foot placements via IK based on the body's forward velocity and lean.
3. If a foot needs to move (its current position is behind its stride limit), lifts it and moves it to the next target position over a configurable swing time.
4. Applies ground-contact forces through `MechGroundContactSystem`.
5. Writes per-leg state to `MechLegStateComponent` (contact flag, current foot world position, target foot position).

The gait is parameterised so it works for bipeds and four-legged variants by adjusting the number of legs and their phase offsets.

---

## Torso Orientation

**`MechOrientationSystem`**

Decouples the upper body (torso, weapon mounts) from the lower body (legs, hip pivot). Reads aim input and rotates the torso around the vertical axis independently within a configurable arc limit. This allows strafing while keeping weapons aimed at a target.

Torso rotation is stored separately in `MechPhysicsComponent` and composited with leg-body orientation before rendering.

---

## Ground Contact

**`MechGroundContactSystem`**

For each foot that is in the swing-down or planted phase, casts a short ray from the foot toward the ground. On contact:
- Applies a spring-damper force upward to support the mech's weight at that foot.
- Records the surface normal for IK foot alignment (feet orient to slope angle).
- Publishes slip or terrain-hazard events if the surface material triggers them.

Without this per-foot contact system, the mech would be modelled as a single rigid body, which would not handle stairs, rubble, or uneven terrain correctly.

---

## Joint Limits

**`JointLimitSystem`**

Enforces angular limits on each leg joint (hip, knee, ankle) stored in `MechPhysicsComponent.jointTorques`. When a joint approaches its limit angle, applies a restoring torque to prevent hyperextension. This prevents the IK solver from producing physically impossible poses under extreme terrain.

---

## Components Reference

| Component | Key fields |
|---|---|
| `MechPhysicsComponent` | Per-joint torques, angular velocities, torso rotation offset, joint limit angles |
| `MechInputComponent` | Move direction (2D), speed scalar, aim direction, fire flag |
| `MechLegStateComponent` | Per-leg: `isGrounded`, current foot world position, target foot position, swing progress |
