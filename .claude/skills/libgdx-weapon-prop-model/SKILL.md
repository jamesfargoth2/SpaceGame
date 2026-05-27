---
name: libgdx-weapon-prop-model
description: How to add 3D weapon models, props, pickups, and interactive objects to this libGDX game. Use this skill whenever you need to attach a weapon model to a character's hand bone, create first-person weapon view models, add world props (crates, terminals, loot), create pickup items with 3D representation, implement bone/socket attachment for equipment, or render held items. Also use when working with weapon visual representation, prop placement systems, item drop models, or attaching any 3D object to a character skeleton.
---

# Weapon & Prop Model Systems

This skill covers how to render weapons in characters' hands, create first-person view models, place interactive props in the world, and implement a bone attachment system for equipment. It builds on the model pipeline and character model skills.

## Project Context

The project has weapon DATA systems (`RangedWeaponComponent`, `MeleeWeaponComponent`, `ShipWeaponRegistry`) but NO weapon VISUAL systems — weapons exist as stats only, with no 3D representation. Props (crates, terminals) also have no model system — `GameScreen.createScatterBoxes()` uses procedural `ModelBuilder` boxes.

## Bone Attachment System

The foundation for weapons, held items, and character equipment. Attaches a child `ModelInstance` to a named bone on a parent character model.

### How Bone Transforms Work in libGDX

After `AnimationController.update()` runs, bone transforms are baked into the `ModelInstance`. Access them via `Node`:

```java
Node handBone = parentInstance.getNode("Bone_R_Hand", true);
// handBone.globalTransform contains the bone's world-space matrix
// (relative to the ModelInstance's own transform)
```

The `true` parameter in `getNode` means recursive search through the node hierarchy. Bone names come from the model file — they must match exactly (case-sensitive).

### AttachmentComponent

Place in `core/src/main/java/com/galacticodyssey/core/components/`:

```java
package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class AttachmentComponent implements Component {

    public ModelInstance attachedModel;
    public String parentBoneName;
    public final Vector3 localOffset = new Vector3();
    public final Vector3 localRotation = new Vector3();
    public float localScale = 1.0f;

    // Cached transform applied each frame
    public final Matrix4 attachTransform = new Matrix4();
}
```

### AttachmentSystem

Runs AFTER animation systems (priority 7, after PlayerAnimationSystem at 5 and NPCAnimationSystem at 6):

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;
import com.galacticodyssey.core.components.AttachmentComponent;
import com.galacticodyssey.player.components.PlayerModelComponent;
import com.galacticodyssey.npc.components.NPCModelComponent;

public class AttachmentSystem extends IteratingSystem {

    private final ComponentMapper<AttachmentComponent> attachMapper =
        ComponentMapper.getFor(AttachmentComponent.class);
    private final ComponentMapper<PlayerModelComponent> playerModelMapper =
        ComponentMapper.getFor(PlayerModelComponent.class);
    private final ComponentMapper<NPCModelComponent> npcModelMapper =
        ComponentMapper.getFor(NPCModelComponent.class);

    private final Matrix4 tmpMat = new Matrix4();

    public AttachmentSystem() {
        super(Family.all(AttachmentComponent.class).one(
            PlayerModelComponent.class, NPCModelComponent.class
        ).get(), 7);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        AttachmentComponent attachment = attachMapper.get(entity);
        if (attachment.attachedModel == null) return;

        ModelInstance parentInstance = getParentInstance(entity);
        if (parentInstance == null) return;

        Node bone = parentInstance.getNode(attachment.parentBoneName, true);
        if (bone == null) return;

        // Build attachment transform: parent model transform * bone transform * local offset
        attachment.attachTransform.set(parentInstance.transform);
        attachment.attachTransform.mul(bone.globalTransform);

        // Apply local offset and rotation for fine-tuning grip position
        tmpMat.idt();
        tmpMat.translate(attachment.localOffset);
        tmpMat.rotate(1, 0, 0, attachment.localRotation.x);
        tmpMat.rotate(0, 1, 0, attachment.localRotation.y);
        tmpMat.rotate(0, 0, 1, attachment.localRotation.z);
        tmpMat.scl(attachment.localScale);

        attachment.attachTransform.mul(tmpMat);
        attachment.attachedModel.transform.set(attachment.attachTransform);
    }

    private ModelInstance getParentInstance(Entity entity) {
        PlayerModelComponent pm = playerModelMapper.get(entity);
        if (pm != null) return pm.modelInstance;
        NPCModelComponent nm = npcModelMapper.get(entity);
        if (nm != null) return nm.modelInstance;
        return null;
    }
}
```

### Attaching a Weapon to a Character

```java
// When equipping a weapon on an entity:
AttachmentComponent attach = new AttachmentComponent();
attach.parentBoneName = "Bone_R_Hand"; // from ModelDefinition.attachmentBones
attach.attachedModel = new ModelInstance(weaponModel);
attach.localOffset.set(0, 0, -0.1f);  // adjust per weapon to align grip
attach.localRotation.set(0, 0, 0);
entity.add(attach);
```

### Rendering Attached Models

Add attached model rendering to the ModelBatch pass. The `AttachmentSystem` already updated the transforms — just render the instances:

```java
private void renderAttachedModels() {
    ImmutableArray<Entity> entities = gameWorld.getEngine().getEntitiesFor(
        Family.all(AttachmentComponent.class).get());

    modelBatch.begin(camera);
    for (Entity entity : entities) {
        AttachmentComponent attach = entity.getComponent(AttachmentComponent.class);
        if (attach.attachedModel == null) continue;

        // Skip if this is the player in first-person mode
        PlayerModelComponent pm = entity.getComponent(PlayerModelComponent.class);
        if (pm != null) {
            FPSCameraComponent cam = entity.getComponent(FPSCameraComponent.class);
            if (cam != null && cam.currentCameraDistance < 0.1f) continue;
        }

        modelBatch.render(attach.attachedModel, environment);
    }
    modelBatch.end();
}
```

## First-Person Weapon View Model

In first-person mode, the player's body and third-person weapon don't render. Instead, render a dedicated "view model" — a separate model of arms holding the weapon, positioned relative to the camera.

### FPWeaponComponent

```java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.math.Vector3;

public class FPWeaponComponent implements Component {

    public ModelInstance viewModelInstance;
    public AnimationController animationController;
    public FPWeaponAnimState currentAnim = FPWeaponAnimState.IDLE;

    // Position offset from camera
    public final Vector3 offsetFromCamera = new Vector3(0.3f, -0.25f, -0.5f);
    public float bobAmplitude = 0.01f;
    public float bobFrequency = 6.0f;
    public float swayAmount = 0.5f;

    public enum FPWeaponAnimState {
        IDLE("Idle"),
        FIRE("Fire"),
        RELOAD("Reload"),
        AIM("Aim"),
        MELEE("Melee"),
        EQUIP("Equip"),
        UNEQUIP("Unequip");

        public final String id;
        FPWeaponAnimState(String id) { this.id = id; }
    }
}
```

### FPWeaponSystem

Runs after camera system (priority 5, after CameraSystem at 4):

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.FPWeaponComponent;
import com.galacticodyssey.player.components.MovementStateComponent;

public class FPWeaponSystem extends IteratingSystem {

    private PerspectiveCamera camera;
    private float bobTimer;
    private final Vector3 tmpVec = new Vector3();
    private final Matrix4 tmpMat = new Matrix4();

    public FPWeaponSystem() {
        super(Family.all(
            FPWeaponComponent.class,
            FPSCameraComponent.class,
            MovementStateComponent.class
        ).get(), 5);
    }

    public void setCamera(PerspectiveCamera camera) {
        this.camera = camera;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (camera == null) return;

        FPWeaponComponent weapon = ComponentMapper.getFor(FPWeaponComponent.class).get(entity);
        FPSCameraComponent cam = ComponentMapper.getFor(FPSCameraComponent.class).get(entity);
        MovementStateComponent movement = ComponentMapper.getFor(MovementStateComponent.class).get(entity);

        if (weapon.viewModelInstance == null) return;

        // Only show in first-person
        if (cam.currentCameraDistance >= 0.1f) return;

        // Weapon bob while moving
        if (movement.currentSpeed > 0.5f && movement.isGrounded) {
            bobTimer += deltaTime * weapon.bobFrequency;
        } else {
            bobTimer = MathUtils.lerp(bobTimer, 0, deltaTime * 4f);
        }

        float bobX = MathUtils.sin(bobTimer) * weapon.bobAmplitude;
        float bobY = MathUtils.sin(bobTimer * 2f) * weapon.bobAmplitude * 0.5f;

        // Position relative to camera
        tmpVec.set(weapon.offsetFromCamera);
        tmpVec.x += bobX;
        tmpVec.y += bobY;

        // Build transform: camera position + camera rotation + local offset
        tmpMat.set(camera.view).inv();
        weapon.viewModelInstance.transform.set(tmpMat);
        weapon.viewModelInstance.transform.translate(tmpVec);

        // Update animation
        if (weapon.animationController != null) {
            weapon.animationController.update(deltaTime);
        }
    }
}
```

### Rendering First-Person Weapons

Render AFTER the main scene with a cleared depth buffer and tight near-plane:

```java
// In GameScreen.render(), after all world rendering:
private void renderFirstPersonWeapon() {
    if (playerEntity == null) return;
    FPWeaponComponent weapon = playerEntity.getComponent(FPWeaponComponent.class);
    if (weapon == null || weapon.viewModelInstance == null) return;

    FPSCameraComponent cam = playerEntity.getComponent(FPSCameraComponent.class);
    if (cam == null || cam.currentCameraDistance >= 0.1f) return;

    // Clear depth so weapon renders on top of everything
    Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);

    // Use tight near-plane to prevent wall clipping
    float savedNear = camera.near;
    camera.near = 0.01f;
    camera.update();

    modelBatch.begin(camera);
    modelBatch.render(weapon.viewModelInstance, environment);
    modelBatch.end();

    camera.near = savedNear;
    camera.update();
}
```

## World Props

Props are standalone 3D objects placed in the world — crates, terminals, consoles, loot containers, decorations.

### PropModelComponent

```java
package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g3d.ModelInstance;

public class PropModelComponent implements Component {
    public ModelInstance modelInstance;
    public String modelDefinitionId;
    public boolean isInteractable;
    public float interactRadius = 2.0f;
}
```

### Creating Props

In `GameWorld`, add a factory method for props:

```java
public Entity createProp(String modelDefId, float x, float y, float z) {
    Entity entity = engine.createEntity();
    TransformComponent transform = new TransformComponent();
    transform.position.set(x, y, z);
    entity.add(transform);

    PropModelComponent prop = new PropModelComponent();
    prop.modelDefinitionId = modelDefId;
    entity.add(prop);

    engine.addEntity(entity);
    return entity;
}
```

Then initialize the model in GameScreen (same GL-context pattern):

```java
private void initializePropModels() {
    ImmutableArray<Entity> props = gameWorld.getEngine().getEntitiesFor(
        Family.all(PropModelComponent.class, TransformComponent.class).get());

    for (Entity prop : props) {
        PropModelComponent propComp = prop.getComponent(PropModelComponent.class);
        TransformComponent transform = prop.getComponent(TransformComponent.class);
        ModelDefinition def = modelRegistry.get(propComp.modelDefinitionId);
        if (def == null) continue;

        Model model = loadModelWithFallback(def);
        loadedModels.add(model);

        ModelInstance instance = new ModelInstance(model);
        instance.transform.setToTranslation(transform.position);
        instance.transform.scl(def.scale);
        propComp.modelInstance = instance;
    }
}
```

### Rendering Props

Add to the main ModelBatch pass:

```java
private void renderProps() {
    ImmutableArray<Entity> props = gameWorld.getEngine().getEntitiesFor(
        Family.all(PropModelComponent.class).get());

    modelBatch.begin(camera);
    for (Entity prop : props) {
        PropModelComponent propComp = prop.getComponent(PropModelComponent.class);
        if (propComp.modelInstance == null) continue;
        modelBatch.render(propComp.modelInstance, environment);
    }
    modelBatch.end();
}
```

### Batching ModelBatch Calls

When you have multiple model types (player, NPCs, props, attached weapons), batch them into a single `modelBatch.begin()`/`end()` pair for better performance:

```java
private void renderAllModels() {
    modelBatch.begin(camera);

    // Props (static, render first)
    for (Entity prop : propEntities) {
        PropModelComponent pc = prop.getComponent(PropModelComponent.class);
        if (pc != null && pc.modelInstance != null)
            modelBatch.render(pc.modelInstance, environment);
    }

    // NPCs + their attached weapons
    for (Entity npc : npcEntities) {
        NPCModelComponent nm = npc.getComponent(NPCModelComponent.class);
        if (nm != null && nm.modelInstance != null)
            modelBatch.render(nm.modelInstance, environment);
        AttachmentComponent attach = npc.getComponent(AttachmentComponent.class);
        if (attach != null && attach.attachedModel != null)
            modelBatch.render(attach.attachedModel, environment);
    }

    // Player (third-person only)
    if (isThirdPerson()) {
        PlayerModelComponent pm = playerEntity.getComponent(PlayerModelComponent.class);
        if (pm != null && pm.modelInstance != null)
            modelBatch.render(pm.modelInstance, environment);
        AttachmentComponent attach = playerEntity.getComponent(AttachmentComponent.class);
        if (attach != null && attach.attachedModel != null)
            modelBatch.render(attach.attachedModel, environment);
    }

    modelBatch.end();
}
```

## Weapon Data Integration

Connect weapon visuals to the existing weapon data system. Add model paths to weapon definitions:

### Extended Weapon JSON (in `data/weapons/`)

```json
{
  "id": "plasma_rifle_mk1",
  "name": "Plasma Rifle Mk.I",
  "damage": 35,
  "fireRate": 4.5,
  "worldModelId": "plasma_rifle",
  "viewModelId": "plasma_rifle_fp",
  "muzzleBone": "Bone_Muzzle",
  "gripOffset": [0, 0, -0.05],
  "gripRotation": [0, -90, 0]
}
```

- `worldModelId` — the ModelDefinition ID for the third-person world model (what NPCs and the third-person player hold)
- `viewModelId` — the ModelDefinition ID for the first-person view model (arms + weapon)
- `muzzleBone` — bone where muzzle flash VFX spawn
- `gripOffset` / `gripRotation` — fine-tuning for hand alignment

### Equipping a Weapon Visually

```java
public void equipWeaponVisual(Entity entity, String weaponId) {
    WeaponData weaponData = weaponRegistry.getWeapon(weaponId);
    ModelDefinition worldModelDef = modelRegistry.get(weaponData.worldModelId);

    // Third-person world model via attachment
    AttachmentComponent attach = entity.getComponent(AttachmentComponent.class);
    if (attach == null) {
        attach = new AttachmentComponent();
        entity.add(attach);
    }
    Model model = loadModelWithFallback(worldModelDef);
    attach.attachedModel = new ModelInstance(model);
    attach.parentBoneName = "Bone_R_Hand";
    attach.localOffset.set(weaponData.gripOffset);
    attach.localRotation.set(weaponData.gripRotation);

    // First-person view model (player only)
    FPWeaponComponent fpWeapon = entity.getComponent(FPWeaponComponent.class);
    if (fpWeapon != null && weaponData.viewModelId != null) {
        ModelDefinition fpDef = modelRegistry.get(weaponData.viewModelId);
        Model fpModel = loadModelWithFallback(fpDef);
        fpWeapon.viewModelInstance = new ModelInstance(fpModel);
        if (fpWeapon.viewModelInstance.animations.size > 0) {
            fpWeapon.animationController = new AnimationController(fpWeapon.viewModelInstance);
        }
    }
}
```

## Dropped Item / Pickup Model

When a weapon or item is dropped in the world, create a temporary entity with a prop-like model:

```java
public Entity createDroppedItem(String modelDefId, Vector3 position) {
    Entity entity = engine.createEntity();

    TransformComponent transform = new TransformComponent();
    transform.position.set(position);
    entity.add(transform);

    PropModelComponent prop = new PropModelComponent();
    prop.modelDefinitionId = modelDefId;
    prop.isInteractable = true;
    prop.interactRadius = 1.5f;
    entity.add(prop);

    // Optional: add slow rotation for visual polish
    // SpinComponent spin = new SpinComponent();
    // spin.degreesPerSecond = 45f;
    // entity.add(spin);

    engine.addEntity(entity);
    return entity;
}
```

## Render Order Summary

```
1. renderTerrain()           -- custom shader, no ModelBatch
2. renderShips()             -- custom shader, no ModelBatch
3. renderAllModels()         -- single ModelBatch pass:
   a. Props (static world objects)
   b. NPC models + attached weapons
   c. Player model + attached weapon (third-person only)
4. renderBoxes()             -- physics debug boxes
5. renderFirstPersonWeapon() -- FP view model (clear depth, tight near-plane)
6. renderHUD()               -- Scene2D UI overlay
```

## Gotchas

- **Bone transforms update AFTER AnimationController.update().** The `AttachmentSystem` must run after animation systems in ECS priority order, AND `GameScreen` must call `modelBatch.render()` for attached models in the same frame. If you render an attached model before its parent's animation updated, it lags one frame behind.
- **First-person weapons need separate depth buffer clearing.** Without clearing depth, the weapon clips into walls and objects. The `camera.near` adjustment to 0.01f prevents the weapon itself from depth-fighting.
- **`Node.globalTransform` is in model-local space**, not world space. You must multiply by `parentInstance.transform` to get world coordinates. The `AttachmentSystem` does this correctly — don't shortcut it.
- **libGDX `ModelBuilder` shapes have no bones.** Fallback/placeholder models created with `ModelBuilder.createCapsule()` or `createBox()` will not support bone attachment. Attached weapons will simply not appear when using placeholders. This is acceptable during development.
- **Multiple `modelBatch.begin()/end()` per frame is expensive.** Each pair flushes and sorts. Batch all models into one pair where possible (see "Batching ModelBatch Calls" above). The FP weapon is a necessary exception because it needs a different near-plane.
- **Dispose weapon models when unequipping.** If swapping weapons, dispose the old `ModelInstance`'s backing `Model` only if nothing else references it. The model registry / `AssetManager` approach from the pipeline skill handles this — each Model is loaded once and shared.
