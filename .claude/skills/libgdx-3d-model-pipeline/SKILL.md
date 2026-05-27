---
name: libgdx-3d-model-pipeline
description: How to load, register, cache, and manage 3D model assets (.g3db, .gltf) in this libGDX project. Use this skill whenever you need to add a new 3D model to the game, create a model registry, set up AssetManager for async model loading, handle model disposal/lifecycle, create placeholder/fallback models, or work with the model loading pipeline. Also use when adding new entity types that need 3D visual representation, setting up LOD for models, or debugging model loading issues.
---

# 3D Model Loading Pipeline

This skill covers how to load, register, cache, and dispose of 3D model assets in Galactic Odyssey. It establishes the patterns every model-consuming system should follow.

## Project Context

The project uses libGDX's `ModelBatch` rendering pipeline with an `Environment` for lighting. Models are stored as `.g3db` (binary) or will eventually use `.gltf` via gdx-gltf. The game follows Ashley ECS — model data lives in components, rendering happens in `GameScreen`.

**Existing pattern** (see `GameScreen.initializePlayerModel()`): models are loaded synchronously with `G3dModelLoader`, stored in a component (`PlayerModelComponent`), and rendered via `ModelBatch`. The project does NOT yet use `AssetManager` for models — that's the next step.

## Model File Formats

### .g3db (current)
Binary format native to libGDX. Load with `G3dModelLoader` + `UBJsonReader`:

```java
G3dModelLoader loader = new G3dModelLoader(new UBJsonReader());
Model model = loader.loadModel(Gdx.files.internal("models/player/player_character.g3db"));
```

### .gltf / .glb (planned)
The design doc specifies gdx-gltf for PBR materials. When gdx-gltf is integrated, load with `SceneAsset` via `AssetManager`. Until then, export glTF as .g3db using fbx-conv or Blender's libGDX exporter.

## Model Registry Pattern

Follow the same data-driven registry pattern used by `ShipWeaponRegistry`, `CombatDataRegistry`, and `VFXRegistry`. Model definitions live in JSON files under `core/src/main/resources/data/models/`.

### JSON Definition Schema

```json
{
  "id": "human_male_base",
  "name": "Human Male Base Model",
  "modelPath": "models/characters/human_male_base.g3db",
  "category": "CHARACTER",
  "scale": 1.0,
  "yOffset": -0.9,
  "animations": {
    "Idle": { "id": "Idle", "loop": true },
    "Walk": { "id": "Walk", "loop": true },
    "Run": { "id": "Run", "loop": true },
    "CrouchIdle": { "id": "CrouchIdle", "loop": true },
    "CrouchWalk": { "id": "CrouchWalk", "loop": true },
    "Fall": { "id": "Fall", "loop": true },
    "Jump": { "id": "Jump", "loop": false }
  },
  "attachmentBones": {
    "rightHand": "Bone_R_Hand",
    "leftHand": "Bone_L_Hand",
    "head": "Bone_Head",
    "back": "Bone_Spine2"
  },
  "collisionShape": "CAPSULE",
  "collisionRadius": 0.3,
  "collisionHeight": 1.8,
  "fallbackShape": "CAPSULE"
}
```

### ModelDefinition Data Class

Place in `core/src/main/java/com/galacticodyssey/data/models/`:

```java
package com.galacticodyssey.data.models;

import java.util.HashMap;
import java.util.Map;

public class ModelDefinition {
    public String id;
    public String name;
    public String modelPath;
    public ModelCategory category;
    public float scale = 1.0f;
    public float yOffset = 0f;
    public Map<String, AnimationDef> animations = new HashMap<>();
    public Map<String, String> attachmentBones = new HashMap<>();
    public String collisionShape;
    public float collisionRadius;
    public float collisionHeight;
    public String fallbackShape;

    public enum ModelCategory {
        CHARACTER, WEAPON, PROP, VEHICLE, ENVIRONMENT
    }

    public static class AnimationDef {
        public String id;
        public boolean loop = true;
    }
}
```

### ModelRegistry

Place in `core/src/main/java/com/galacticodyssey/data/models/`:

```java
package com.galacticodyssey.data.models;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Map;

public class ModelRegistry {
    private final Map<String, ModelDefinition> definitions = new HashMap<>();

    public void loadFromFile(String path) {
        if (Gdx.files == null) return;
        JsonValue root = new JsonReader().parse(Gdx.files.internal(path));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            ModelDefinition def = new ModelDefinition();
            def.id = entry.getString("id");
            def.name = entry.getString("name");
            def.modelPath = entry.getString("modelPath");
            def.category = ModelDefinition.ModelCategory.valueOf(entry.getString("category"));
            def.scale = entry.getFloat("scale", 1.0f);
            def.yOffset = entry.getFloat("yOffset", 0f);

            JsonValue anims = entry.get("animations");
            if (anims != null) {
                for (JsonValue a = anims.child; a != null; a = a.next) {
                    ModelDefinition.AnimationDef ad = new ModelDefinition.AnimationDef();
                    ad.id = a.getString("id");
                    ad.loop = a.getBoolean("loop", true);
                    def.animations.put(a.name, ad);
                }
            }

            JsonValue bones = entry.get("attachmentBones");
            if (bones != null) {
                for (JsonValue b = bones.child; b != null; b = b.next) {
                    def.attachmentBones.put(b.name, b.asString());
                }
            }

            def.collisionShape = entry.getString("collisionShape", "CAPSULE");
            def.collisionRadius = entry.getFloat("collisionRadius", 0.3f);
            def.collisionHeight = entry.getFloat("collisionHeight", 1.8f);
            def.fallbackShape = entry.getString("fallbackShape", "CAPSULE");

            definitions.put(def.id, def);
        }
    }

    public ModelDefinition get(String id) { return definitions.get(id); }

    public boolean has(String id) { return definitions.containsKey(id); }
}
```

### Bootstrap in GameWorld

Register the ModelRegistry alongside existing registries in `GameWorld.initializeSystems()`:

```java
private ModelRegistry modelRegistry;

// In initializeSystems():
modelRegistry = new ModelRegistry();
if (com.badlogic.gdx.Gdx.files != null) {
    modelRegistry.loadFromFile("data/models/characters.json");
    modelRegistry.loadFromFile("data/models/weapons.json");
    modelRegistry.loadFromFile("data/models/props.json");
}
```

## Loading Models

### Synchronous (current approach, fine for few models)

```java
G3dModelLoader loader = new G3dModelLoader(new UBJsonReader());
Model model = loader.loadModel(Gdx.files.internal(definition.modelPath));
ModelInstance instance = new ModelInstance(model);
instance.transform.scl(definition.scale);
```

### Fallback Pattern

Always provide a procedural fallback when the model file doesn't exist. This is critical for development — the game must run without art assets:

```java
private Model loadModelWithFallback(ModelDefinition def) {
    if (Gdx.files.internal(def.modelPath).exists()) {
        G3dModelLoader loader = new G3dModelLoader(new UBJsonReader());
        return loader.loadModel(Gdx.files.internal(def.modelPath));
    }
    ModelBuilder builder = new ModelBuilder();
    int attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
    Material mat = new Material(ColorAttribute.createDiffuse(Color.GRAY));
    switch (def.fallbackShape) {
        case "BOX":
            return builder.createBox(
                def.collisionRadius * 2, def.collisionHeight,
                def.collisionRadius * 2, mat, attrs);
        case "SPHERE":
            return builder.createSphere(
                def.collisionRadius * 2, def.collisionRadius * 2,
                def.collisionRadius * 2, 16, 16, mat, attrs);
        default: // CAPSULE
            return builder.createCapsule(
                def.collisionRadius, def.collisionHeight, 16, mat, attrs);
    }
}
```

### Async Loading (when scaling to many models)

When the number of distinct models grows (many NPCs, weapons, props on screen), switch to `AssetManager`:

```java
// Setup (once, in GameScreen or a dedicated ModelManager)
AssetManager assets = new AssetManager();

// Queue loading
assets.load("models/characters/human_male_base.g3db", Model.class);
assets.load("models/weapons/plasma_rifle.g3db", Model.class);

// Per-frame update (call in render loop during loading screen)
if (!assets.update()) {
    float progress = assets.getProgress(); // 0.0 to 1.0
}

// Retrieve when done
Model model = assets.get("models/characters/human_male_base.g3db", Model.class);
```

## Model Instance Management

A `Model` is the shared template; `ModelInstance` is a per-entity copy with its own transform and materials. Multiple entities can share one `Model` but each needs its own `ModelInstance`:

```java
// One Model loaded once
Model npcModel = loadModelWithFallback(modelRegistry.get("human_male_base"));

// Many instances sharing it
for (Entity npc : npcs) {
    ModelInstance instance = new ModelInstance(npcModel);
    // Each instance gets its own transform, materials, animation controller
}
```

Never dispose a `Model` while any `ModelInstance` still references it.

## Disposal

Every `Model` must be disposed when no longer needed. Track all loaded models and dispose them in `GameScreen.dispose()`:

```java
// In GameScreen fields:
private final Array<Model> loadedModels = new Array<>();

// When loading:
Model m = loadModelWithFallback(def);
loadedModels.add(m);

// In dispose():
for (Model m : loadedModels) {
    m.dispose();
}
loadedModels.clear();
```

If using `AssetManager`, call `assets.dispose()` instead — it handles all loaded assets.

## Directory Structure for Model Assets

```
core/src/main/resources/
  data/models/
    characters.json      <- ModelDefinition entries for player, NPCs
    weapons.json         <- ModelDefinition entries for weapon models
    props.json           <- ModelDefinition entries for props, pickups
  models/
    characters/
      player_character.g3db
      human_male_base.g3db
      alien_grunt.g3db
    weapons/
      plasma_rifle.g3db
      plasma_rifle_fp.g3db    <- first-person view model variant
    props/
      crate_metal.g3db
      terminal_console.g3db
    vehicles/
      rover_light.g3db
```

## Gotchas

- **GL context required for Mesh/Model creation.** Never load models in `GameWorld` constructor or ECS system constructors. Load them in `GameScreen` after GL context is available, or use `AssetManager` which defers GL work to its `update()` call.
- **Model transforms are mutable.** `ModelInstance.transform` is a shared `Matrix4`. If you read a bone transform, copy it — don't hold a reference.
- **Animation IDs are case-sensitive.** The string passed to `AnimationController.animate()` must match the animation name baked into the model file exactly.
- **g3db files bundle textures by reference.** The texture paths inside the g3db must resolve relative to the model file location, or textures will silently fail to load (model renders black).
- **Dispose before removing.** If you remove an entity that owns a model, dispose the Model first if no other instances reference it. Otherwise you leak GPU memory.
