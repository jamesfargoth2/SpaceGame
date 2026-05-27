---
name: libgdx-character-model
description: How to integrate 3D character models (player and NPC) into the Ashley ECS in this libGDX project. Use this skill whenever you need to add a player model, NPC model, enemy model, or crew member model to the game. Covers ModelComponent design, animation state machines, first-person/third-person camera switching, NPC animation with AI states, bone structure for equipment, and rendering character models via ModelBatch. Also use when modifying PlayerModelComponent, PlayerAnimationSystem, adding new animation states, creating NPC visual systems, or troubleshooting character model rendering.
---

# Character Model Rendering (Player & NPC)

This skill covers how to integrate 3D character models into the Ashley ECS for both player-controlled and AI-controlled entities. It builds on the model loading pipeline (see `libgdx-3d-model-pipeline` skill) and the existing `PlayerModelComponent` + `PlayerAnimationSystem` patterns.

## Existing Infrastructure

The project already has a working player model system:

- **`PlayerModelComponent`** (`player/components/`) — Stores `ModelInstance`, `AnimationController`, current `AnimState`, and `modelYOffset`. Defines 7 animation states: IDLE, WALK, RUN, CROUCH_IDLE, CROUCH_WALK, FALL, JUMP.
- **`PlayerAnimationSystem`** (`player/systems/`, priority 5) — Resolves animation state from `MovementStateComponent`, crossfades animations (0.2s), syncs model transform with entity position + yaw rotation.
- **`GameScreen.initializePlayerModel()`** — Loads `.g3db` model with fallback to procedural capsule, creates `ModelInstance` and `AnimationController`.
- **`GameScreen.renderPlayerModel()`** — Renders player model only in third-person (when `cam.currentCameraDistance >= 0.1f`).

NPCs currently have NO model representation — `GameWorld.createHostileNPC()` creates entities with combat components but no visual.

## Player Model Architecture

### Component Design

`PlayerModelComponent` is intentionally player-specific because it couples to `FPSCameraComponent` for yaw rotation and first/third-person switching. Don't generalize it to NPCs — create a separate component instead (see NPC section below).

The player model component already handles:
- **Animation state enum** with string IDs matching model animation names
- **Model Y offset** (-0.9f) to align the model's feet with the physics capsule bottom
- **AnimationController** for crossfade-based animation playback

### Adding New Player Animation States

To add a new animation state (e.g., SPRINT, CLIMB, INTERACT):

1. Add the enum value to `PlayerModelComponent.AnimState` with the animation ID string that matches the model file:
```java
SPRINT("Sprint"),
CLIMB("Climb"),
INTERACT("Interact");
```

2. Add the state resolution logic in `PlayerAnimationSystem.resolveAnimState()`. States are checked in priority order — put more specific states first:
```java
private AnimState resolveAnimState(MovementStateComponent movement) {
    if (!movement.isGrounded) {
        return movement.fallVelocity > 0 ? AnimState.JUMP : AnimState.FALL;
    }
    // Interaction overrides movement
    if (movement.isInteracting) return AnimState.INTERACT;
    if (movement.isCrouching) {
        return movement.currentSpeed > WALK_SPEED_THRESHOLD
            ? AnimState.CROUCH_WALK : AnimState.CROUCH_IDLE;
    }
    if (movement.isSprinting && movement.currentSpeed > RUN_SPEED_THRESHOLD) {
        return AnimState.SPRINT;
    }
    // ... existing speed-based states
}
```

3. Set the loop count in `processEntity()` — one-shot animations (jump, interact) use `loopCount = 1`, looping animations use `-1`:
```java
int loopCount = (desired == AnimState.JUMP || desired == AnimState.INTERACT) ? 1 : -1;
```

### First-Person / Third-Person Switching

The camera system (`CameraSystem`, priority 4) controls this via `FPSCameraComponent.currentCameraDistance`:
- **First-person** (`distance < 0.1f`): Player model is NOT rendered. Consider rendering first-person arm/weapon model instead (see `libgdx-weapon-prop-model` skill).
- **Third-person** (`distance >= 0.1f`): Full player model renders via `GameScreen.renderPlayerModel()`.

The toggle is on the `V` key (captured in `PlayerInputComponent.toggleCameraView`). The camera smoothly interpolates distance.

When adding first-person arms: render them in a separate pass AFTER the main scene, with a closer near-plane (0.01f) to prevent clipping into walls. Reset the depth buffer between passes:

```java
// In GameScreen.render():
renderScene();              // terrain, ships, NPCs, props
renderPlayerModel();        // third-person body (skipped in FP mode)

// First-person overlay
if (isFirstPerson) {
    Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
    renderFirstPersonArms(); // arms + held weapon, near clip = 0.01
}
```

## NPC Model Architecture

### NPCModelComponent

Create a separate component for NPC models. NPCs don't have camera coupling or FP/TP switching — they always render their full model. They do need:
- Facing direction from AI (not camera yaw)
- More animation states (attack, death, patrol, cover)
- LOD-based animation simplification at distance

Place in `core/src/main/java/com/galacticodyssey/npc/components/`:

```java
package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;

public class NPCModelComponent implements Component {

    public ModelInstance modelInstance;
    public AnimationController animationController;
    public NPCAnimState currentAnim = NPCAnimState.IDLE;
    public String modelDefinitionId;
    public float modelYOffset = -0.9f;
    public float facingAngle;

    public enum NPCAnimState {
        IDLE("Idle"),
        WALK("Walk"),
        RUN("Run"),
        PATROL("Patrol"),
        ATTACK_MELEE("AttackMelee"),
        ATTACK_RANGED("AttackRanged"),
        TAKE_COVER("TakeCover"),
        TAKE_HIT("TakeHit"),
        DEATH("Death"),
        INTERACT("Interact");

        public final String id;
        NPCAnimState(String id) { this.id = id; }
    }
}
```

### NPCAnimationSystem

Place in `core/src/main/java/com/galacticodyssey/npc/systems/`:

```java
package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Quaternion;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.npc.components.NPCModelComponent;
import com.galacticodyssey.npc.components.NPCModelComponent.NPCAnimState;

public class NPCAnimationSystem extends IteratingSystem {

    private static final float CROSSFADE_DURATION = 0.25f;

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<NPCModelComponent> modelMapper =
        ComponentMapper.getFor(NPCModelComponent.class);
    private final ComponentMapper<CombatAIComponent> aiMapper =
        ComponentMapper.getFor(CombatAIComponent.class);
    private final ComponentMapper<HealthComponent> healthMapper =
        ComponentMapper.getFor(HealthComponent.class);

    private final Quaternion tmpQuat = new Quaternion();

    public NPCAnimationSystem() {
        super(Family.all(
            TransformComponent.class,
            NPCModelComponent.class
        ).get(), 6);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = transformMapper.get(entity);
        NPCModelComponent model = modelMapper.get(entity);

        NPCAnimState desired = resolveAnimState(entity, model);

        if (desired != model.currentAnim) {
            model.currentAnim = desired;
            if (model.animationController != null) {
                int loopCount = isOneShot(desired) ? 1 : -1;
                model.animationController.animate(
                    desired.id, loopCount, 1f, null, CROSSFADE_DURATION);
            }
        }

        if (model.modelInstance == null) return;

        if (model.animationController != null) {
            model.animationController.update(deltaTime);
        }

        tmpQuat.setFromAxis(0, 1, 0, -model.facingAngle);
        model.modelInstance.transform.idt();
        model.modelInstance.transform.translate(
            transform.position.x,
            transform.position.y + model.modelYOffset,
            transform.position.z);
        model.modelInstance.transform.rotate(tmpQuat);
    }

    private NPCAnimState resolveAnimState(Entity entity, NPCModelComponent model) {
        HealthComponent health = healthMapper.get(entity);
        if (health != null && health.currentHP <= 0) return NPCAnimState.DEATH;

        CombatAIComponent ai = aiMapper.get(entity);
        if (ai == null) return NPCAnimState.IDLE;

        // Map AI state to animation — adapt these to your CombatAIComponent state fields
        if (ai.isAttacking) {
            return ai.isMelee ? NPCAnimState.ATTACK_MELEE : NPCAnimState.ATTACK_RANGED;
        }
        if (ai.isTakingCover) return NPCAnimState.TAKE_COVER;
        if (ai.isPatrolling) return NPCAnimState.PATROL;
        if (ai.isMoving) return ai.isRunning ? NPCAnimState.RUN : NPCAnimState.WALK;

        return NPCAnimState.IDLE;
    }

    private boolean isOneShot(NPCAnimState state) {
        return state == NPCAnimState.ATTACK_MELEE
            || state == NPCAnimState.ATTACK_RANGED
            || state == NPCAnimState.TAKE_HIT
            || state == NPCAnimState.DEATH;
    }
}
```

### Initializing NPC Models

Add model initialization when creating NPCs. This happens in `GameScreen` (GL context required), not `GameWorld`:

```java
// In GameScreen, after gameWorld creates NPC entities:
private void initializeNPCModels() {
    ImmutableArray<Entity> npcs = gameWorld.getEngine().getEntitiesFor(
        Family.all(NPCModelComponent.class, TransformComponent.class).get());

    for (Entity npc : npcs) {
        NPCModelComponent modelComp = npc.getComponent(NPCModelComponent.class);
        ModelDefinition def = modelRegistry.get(modelComp.modelDefinitionId);
        if (def == null) continue;

        Model model = loadModelWithFallback(def);
        loadedModels.add(model);

        ModelInstance instance = new ModelInstance(model);
        instance.transform.scl(def.scale);
        modelComp.modelInstance = instance;
        modelComp.modelYOffset = def.yOffset;

        if (instance.animations.size > 0) {
            modelComp.animationController = new AnimationController(instance);
            modelComp.animationController.setAnimation(
                instance.animations.get(0).id, -1);
        }
    }
}
```

### Rendering NPC Models

Add NPC rendering to the main ModelBatch pass in `GameScreen.render()`:

```java
private void renderNPCModels() {
    ImmutableArray<Entity> npcs = gameWorld.getEngine().getEntitiesFor(
        Family.all(NPCModelComponent.class).get());

    modelBatch.begin(camera);
    for (Entity npc : npcs) {
        NPCModelComponent model = npc.getComponent(NPCModelComponent.class);
        if (model.modelInstance == null) continue;
        modelBatch.render(model.modelInstance, environment);
    }
    modelBatch.end();
}
```

Call this in the render method alongside existing render calls:

```java
// In render():
renderTerrain();
renderBoxes();
renderShips();
renderNPCModels();     // <-- add here
renderPlayerModel();
```

Render NPCs BEFORE the player model so the player draws on top in edge cases.

### NPC Facing Direction

NPCs need to face their movement direction or their combat target. Update `facingAngle` in the AI system or a dedicated `NPCFacingSystem`:

```java
// In AI system or NPC update:
if (targetPosition != null) {
    float dx = targetPosition.x - transform.position.x;
    float dz = targetPosition.z - transform.position.z;
    npcModel.facingAngle = MathUtils.atan2(dx, dz) * MathUtils.radiansToDegrees;
}
```

## LOD Animation

For NPCs far from the camera, skip expensive animation updates:

```java
// In NPCAnimationSystem.processEntity():
float distSq = transform.position.dst2(cameraPosition);
if (distSq > LOD_ANIM_SKIP_DIST_SQ) {
    // Still update transform position but skip animation tick
    syncTransformOnly(model, transform);
    return;
}
if (distSq > LOD_ANIM_HALF_RATE_DIST_SQ) {
    // Half-rate animation: only update every other frame
    if (frameCount % 2 != 0) {
        syncTransformOnly(model, transform);
        return;
    }
}
// Full-rate animation for nearby NPCs
```

Set thresholds based on your game's scale — for this project with 500x500 terrain, reasonable defaults are:
- Full animation: < 50m (`LOD_ANIM_HALF_RATE_DIST_SQ = 2500f`)
- Half-rate: 50-100m (`LOD_ANIM_SKIP_DIST_SQ = 10000f`)
- Skip animation: > 100m

## Wiring Into GameWorld

Register the `NPCAnimationSystem` in `GameWorld.initializeSystems()`:

```java
engine.addSystem(new NPCAnimationSystem());
```

When creating hostile NPCs in `GameWorld.createHostileNPC()`, add the model component:

```java
entity.add(new NPCModelComponent());
NPCModelComponent modelComp = entity.getComponent(NPCModelComponent.class);
modelComp.modelDefinitionId = archetypeId; // or a separate modelId from the archetype
```

## Gotchas

- **Player model uses camera yaw; NPC models use AI facing angle.** These are fundamentally different rotation sources — don't try to unify them into one component.
- **AnimationController.animate() does nothing if the animation ID doesn't exist in the model.** Silently fails. Log a warning when the model has no matching animation.
- **One AnimationController per ModelInstance.** Don't share controllers across instances. Each NPC needs its own.
- **Death animation must be one-shot (loopCount=1).** Otherwise the death animation loops and the corpse twitches.
- **NPC model init must happen after GL context is available.** The pattern is: `GameWorld` creates the entity with the component, `GameScreen` fills in the ModelInstance and AnimationController. This split is the same as the existing player model pattern.
- **Don't render dead NPCs forever.** After the death animation finishes, either remove the entity, swap to a static corpse model, or fade out. The `AnimationController.Listener` callback fires when a one-shot animation ends.
