# Asset Pipeline Phase 2: Particle Effects Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix invisible particles by loading VFX definitions from JSON, adding a programmatic placeholder texture atlas, upgrading billboard particles with depth sorting, and introducing a mesh particle emitter type.

**Architecture:** `VFXLoader` reads the existing JSON files into `VFXRegistry`/`VFXEventBindings` at startup (currently empty). `ParticleAtlasManager` generates a placeholder `TextureAtlas` via `PixmapPacker` on the GL thread — eight named regions (smoke, flame, glow, etc.) as soft white circles. `ParticleSpawnSystem` looks up the atlas region from `def.sprite` and stores it on each spawned `Particle`. `ParticleRenderSystem` sorts billboard particles back-to-front each frame before flushing `DecalBatch`, and gains a second render path for `MeshParticle` objects rendered via `ModelBatch`.

**Tech Stack:** libGDX 1.13.5, Ashley 1.7.4, libGDX `PixmapPacker`, `DecalBatch`, `ModelBatch`, JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-27-asset-animation-pipeline-design.md` — Section 4

**This is Plan 2 of 7.** Subsequent plans: Shader Variants (Phase 3), LOD + Props + Buildings (Phase 4), Blend Tree Animation (Phase 5), Foliage (Phase 6), Occlusion Culling (Phase 7).

---

## Context for the implementer

### What exists (don't recreate these)

| Class | Package | What it does |
|---|---|---|
| `Particle` | `com.galacticodyssey.vfx` | Pool-managed particle: position/velocity/acceleration/life/color/textureRegion/flags |
| `VFXEnums` | `com.galacticodyssey.vfx` | `BlendMode` (NORMAL, ADDITIVE), `EmitterState`, bit flags (FLAG_ADDITIVE_BLEND, FLAG_FACE_CAMERA) |
| `ParticleEffectDefinition` | `com.galacticodyssey.vfx.data` | POJO describing one particle effect; fields: id, maxParticles, emitRate, burstCount, lifetimeMin/Max, speedMin/Max, spread, sizeMin/Max, sizeEnd, color/colorEnd (hex strings), texture (file path), blendMode, gravity, duration |
| `VFXRegistry` | `com.galacticodyssey.vfx.data` | `register(def)` / `getEffect(id)` map |
| `VFXEventBindings` | `com.galacticodyssey.vfx.data` | `bind(eventType, variant, effectId)` / `resolve(eventType, variant)` / `loadFromMap(Map<String,String>)` |
| `ParticlePoolComponent` | `com.galacticodyssey.vfx.components` | Has `List<Particle> active` and `obtain()` / `free()` |
| `ParticleEmitterComponent` | `com.galacticodyssey.vfx.components` | Ashley component holding active emitters on an entity |
| `ParticleSpawnSystem` | `com.galacticodyssey.vfx.systems` | Subscribes to game events, calls `spawnBurst(def, origin)` — currently does NOT set `textureRegion` on particles |
| `ParticleUpdateSystem` | `com.galacticodyssey.vfx.systems` | Updates velocity/position/lifetime each frame |
| `ParticleRenderSystem` | `com.galacticodyssey.vfx.systems` | Renders via `DecalBatch`; `initialize(TextureRegion defaultTexture)` creates the batch (currently never called — particles are invisible) |
| `ActiveEmitter` | `com.galacticodyssey.vfx` | Per-entity emitter state (not changed in Phase 2) |

### What is currently broken

1. `VFXRegistry` is empty at runtime — no code loads the JSON effect definitions from `data/vfx/`
2. `VFXEventBindings` is empty — `vfx_event_bindings.json` is never loaded
3. `ParticleRenderSystem.initialize()` is never called → `decalBatch` is null → `render()` returns immediately
4. `ParticleSpawnSystem.spawnBurst()` never sets `p.textureRegion` → always null → falls back to `defaultTexture` → which is also null
5. Three effect IDs referenced in `vfx_event_bindings.json` have no JSON files: `muzzle_flash_energy`, `muzzle_flash_plasma`, `impact_explosion`

### Existing VFX JSON files (already in `core/src/main/resources/data/vfx/`)

```
engine_exhaust.json       — continuous exhaust, texture: particles/smoke.png
impact_sparks.json        — burst sparks, texture: particles/spark.png
muzzle_flash_ballistic.json — burst flash, texture: particles/spark.png
shield_ripple.json        — burst shield hit, texture: particles/hex.png
vfx_event_bindings.json   — event→effectId map
screen_shake_config.json  — not a particle effect, don't touch
```

### Pre-existing unstaged changes — DO NOT COMMIT

These files have in-progress changes. Never stage them unless a task explicitly says to:
- `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`
- `core/src/main/java/com/galacticodyssey/ui/AtmosphericSkyRenderer.java`
- `core/src/main/java/com/galacticodyssey/planet/ScatteringParams.java`
- `core/src/main/java/com/galacticodyssey/core/GameWorld.java` (unless a task says to touch it)
- `core/src/main/java/com/galacticodyssey/vfx/systems/ParticleRenderSystem.java` (until Task 6)

### Build command (JAVA_HOME must be set)

You cannot run Gradle — the controller will run tests. Report STATUS: DONE after committing.

---

## File Map

| File | Status | Responsibility |
|---|---|---|
| `core/.../vfx/data/VFXLoader.java` | Create | JSON → ParticleEffectDefinition + VFXEventBindings parsing |
| `core/.../vfx/data/ParticleEffectDefinition.java` | Modify | Add type, sprite, mesh, bounce, emitOnce fields |
| `core/src/main/resources/data/vfx/engine_exhaust.json` | Modify | Add type/sprite fields |
| `core/src/main/resources/data/vfx/impact_sparks.json` | Modify | Add type/sprite fields |
| `core/src/main/resources/data/vfx/muzzle_flash_ballistic.json` | Modify | Add type/sprite fields |
| `core/src/main/resources/data/vfx/shield_ripple.json` | Modify | Add type/sprite fields |
| `core/src/main/resources/data/vfx/muzzle_flash_energy.json` | Create | Missing effect definition |
| `core/src/main/resources/data/vfx/muzzle_flash_plasma.json` | Create | Missing effect definition |
| `core/src/main/resources/data/vfx/impact_explosion.json` | Create | Missing effect definition |
| `core/.../vfx/ParticleAtlasManager.java` | Create | Programmatic TextureAtlas with 8 regions (GL-thread only) |
| `core/.../vfx/MeshParticle.java` | Create | Mesh particle data class + per-frame physics |
| `core/.../vfx/MeshParticlePool.java` | Create | Pool + active list for mesh particles |
| `core/.../vfx/systems/ParticleSpawnSystem.java` | Modify | Add setAtlasManager(); set textureRegion at spawn |
| `core/.../vfx/systems/ParticleRenderSystem.java` | Modify | Depth sort + mesh particle render path |
| `core/.../core/GameWorld.java` | Modify | Call VFXLoader.loadAll(); wire ParticleAtlasManager in initializeSystems() |
| `core/src/test/java/.../vfx/VFXLoaderTest.java` | Create | 6 tests for JSON parsing |
| `core/src/test/java/.../vfx/MeshParticleTest.java` | Create | 4 tests for physics simulation |

---

## Task 1: VFXLoader — JSON parsing for effects and bindings

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/vfx/data/VFXLoader.java`
- Create: `core/src/test/java/com/galacticodyssey/vfx/VFXLoaderTest.java`

> `VFXLoader` has two package-private static methods that are fully testable without GL, plus a `loadAll()` that reads from `Gdx.files`.

- [ ] **Step 1.1: Write failing tests**

Create `core/src/test/java/com/galacticodyssey/vfx/VFXLoaderTest.java`:

```java
package com.galacticodyssey.vfx;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.vfx.VFXEnums.BlendMode;
import com.galacticodyssey.vfx.data.ParticleEffectDefinition;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXLoader;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class VFXLoaderTest {

    private static JsonValue json(String text) {
        return new JsonReader().parse(text);
    }

    @Test
    void parsesIdAndType() {
        ParticleEffectDefinition def = VFXLoader.parseEffect(
            json("{\"id\":\"smoke_puff\",\"type\":\"BILLBOARD\",\"sprite\":\"smoke\"}"));
        assertEquals("smoke_puff", def.id);
        assertEquals("BILLBOARD", def.type);
        assertEquals("smoke", def.sprite);
    }

    @Test
    void defaultsAppliedWhenFieldsMissing() {
        ParticleEffectDefinition def = VFXLoader.parseEffect(json("{\"id\":\"minimal\"}"));
        assertEquals("BILLBOARD", def.type);
        assertEquals("smoke", def.sprite);
        assertEquals(16, def.maxParticles);
        assertEquals(BlendMode.ADDITIVE, def.blendMode);
    }

    @Test
    void parsesMeshType() {
        ParticleEffectDefinition def = VFXLoader.parseEffect(
            json("{\"id\":\"sparks\",\"type\":\"MESH\",\"mesh\":\"spark_line\",\"bounce\":0.5}"));
        assertEquals("MESH", def.type);
        assertEquals("spark_line", def.mesh);
        assertEquals(0.5f, def.bounce, 0.001f);
    }

    @Test
    void parsesBlendModeNormal() {
        ParticleEffectDefinition def = VFXLoader.parseEffect(
            json("{\"id\":\"x\",\"blendMode\":\"NORMAL\"}"));
        assertEquals(BlendMode.NORMAL, def.blendMode);
    }

    @Test
    void parseEventBindings() {
        VFXEventBindings bindings = new VFXEventBindings();
        VFXLoader.parseBindings(bindings,
            json("{\"WeaponFiredEvent\":\"muzzle_flash_ballistic\",\"HitscanHitEvent:ENERGY\":\"impact_energy\"}"));
        assertEquals("muzzle_flash_ballistic", bindings.resolve("WeaponFiredEvent", null));
        assertEquals("impact_energy", bindings.resolve("HitscanHitEvent", "ENERGY"));
    }

    @Test
    void parsesImpactSparksResource() throws Exception {
        InputStream is = VFXLoaderTest.class.getClassLoader()
            .getResourceAsStream("data/vfx/impact_sparks.json");
        assertNotNull(is, "data/vfx/impact_sparks.json not found on classpath");
        String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        ParticleEffectDefinition def = VFXLoader.parseEffect(new JsonReader().parse(text));
        assertEquals("impact_sparks", def.id);
        assertTrue(def.burstCount > 0);
        assertEquals("flash", def.sprite);
    }
}
```

- [ ] **Step 1.2: Run tests, confirm compilation fails**

The controller will run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.vfx.VFXLoaderTest"`

Expected: compilation failure — `VFXLoader` does not exist yet.

- [ ] **Step 1.3: Create VFXLoader.java**

Create `core/src/main/java/com/galacticodyssey/vfx/data/VFXLoader.java`:

```java
package com.galacticodyssey.vfx.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.vfx.VFXEnums.BlendMode;

public final class VFXLoader {

    private static final String[] EFFECT_IDS = {
        "engine_exhaust", "impact_sparks", "muzzle_flash_ballistic", "shield_ripple",
        "muzzle_flash_energy", "muzzle_flash_plasma", "impact_explosion"
    };

    /** Call once after Gdx.files is available (GL thread). */
    public static void loadAll(VFXRegistry registry, VFXEventBindings bindings) {
        JsonReader reader = new JsonReader();
        for (String id : EFFECT_IDS) {
            String path = "data/vfx/" + id + ".json";
            try {
                JsonValue json = reader.parse(Gdx.files.internal(path));
                registry.register(parseEffect(json));
            } catch (Exception e) {
                Gdx.app.error("VFXLoader", "Skipping missing effect: " + path);
            }
        }
        try {
            JsonValue bindingsJson = reader.parse(Gdx.files.internal("data/vfx/vfx_event_bindings.json"));
            parseBindings(bindings, bindingsJson);
        } catch (Exception e) {
            Gdx.app.error("VFXLoader", "Failed to load vfx_event_bindings.json");
        }
    }

    static ParticleEffectDefinition parseEffect(JsonValue json) {
        ParticleEffectDefinition def = new ParticleEffectDefinition();
        def.id = json.getString("id");
        def.type = json.getString("type", "BILLBOARD");
        def.sprite = json.getString("sprite", "smoke");
        def.mesh = json.getString("mesh", "");
        def.maxParticles = json.getInt("maxParticles", 16);
        def.emitRate = json.getFloat("emitRate", 0f);
        def.burstCount = json.getInt("burstCount", 0);
        def.emitOnce = json.getBoolean("emitOnce", false);
        def.lifetimeMin = json.getFloat("lifetimeMin", 0.5f);
        def.lifetimeMax = json.getFloat("lifetimeMax", 1.0f);
        def.speedMin = json.getFloat("speedMin", 1f);
        def.speedMax = json.getFloat("speedMax", 5f);
        def.spread = json.getFloat("spread", 30f);
        def.sizeMin = json.getFloat("sizeMin", 0.1f);
        def.sizeMax = json.getFloat("sizeMax", 0.3f);
        def.sizeEnd = json.getFloat("sizeEnd", 0f);
        def.color = json.getString("color", "#FFFFFF");
        def.colorEnd = json.getString("colorEnd", "#FFFFFF");
        def.texture = json.getString("texture", "particles/default.png");
        def.blendMode = BlendMode.valueOf(json.getString("blendMode", "ADDITIVE"));
        def.gravity = json.getFloat("gravity", 0f);
        def.bounce = json.getFloat("bounce", 0.3f);
        def.duration = json.getFloat("duration", -1f);
        return def;
    }

    static void parseBindings(VFXEventBindings bindings, JsonValue json) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (JsonValue entry = json.child; entry != null; entry = entry.next) {
            map.put(entry.name, entry.asString());
        }
        bindings.loadFromMap(map);
    }

    private VFXLoader() {}
}
```

- [ ] **Step 1.4: Run tests, confirm they pass**

Expected: 6 tests, all PASS.  
(Note: `parsesImpactSparksResource` will fail until Task 2 adds `"sprite": "flash"` to impact_sparks.json. That's expected and acceptable — fix it in Task 2.)

- [ ] **Step 1.5: Commit**

```
git add core/src/main/java/com/galacticodyssey/vfx/data/VFXLoader.java
git add core/src/test/java/com/galacticodyssey/vfx/VFXLoaderTest.java
git commit -m "feat(vfx): add VFXLoader - JSON parsing for effect definitions and event bindings"
```

---

## Task 2: Upgrade ParticleEffectDefinition + update all VFX JSON files

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/vfx/data/ParticleEffectDefinition.java`
- Modify: `core/src/main/resources/data/vfx/engine_exhaust.json`
- Modify: `core/src/main/resources/data/vfx/impact_sparks.json`
- Modify: `core/src/main/resources/data/vfx/muzzle_flash_ballistic.json`
- Modify: `core/src/main/resources/data/vfx/shield_ripple.json`
- Create: `core/src/main/resources/data/vfx/muzzle_flash_energy.json`
- Create: `core/src/main/resources/data/vfx/muzzle_flash_plasma.json`
- Create: `core/src/main/resources/data/vfx/impact_explosion.json`

- [ ] **Step 2.1: Update ParticleEffectDefinition.java**

Replace the entire file:

```java
package com.galacticodyssey.vfx.data;

import com.galacticodyssey.vfx.VFXEnums.BlendMode;

public class ParticleEffectDefinition {
    public String id;
    /** "BILLBOARD" (default) or "MESH" */
    public String type = "BILLBOARD";
    /** Atlas region name for BILLBOARD type (e.g. "smoke", "flash"). */
    public String sprite = "smoke";
    /** Mesh asset ID for MESH type (e.g. "spark_line"). */
    public String mesh = "";
    public int maxParticles = 16;
    public float emitRate;
    public int burstCount;
    /** If true, emit all particles once on spawn and stop (MESH type). */
    public boolean emitOnce = false;
    public float lifetimeMin = 0.5f, lifetimeMax = 1.0f;
    public float speedMin = 1f, speedMax = 5f;
    public float spread = 30f;
    public float sizeMin = 0.1f, sizeMax = 0.3f;
    public float sizeEnd;
    public String color = "#FFFFFF";
    public String colorEnd = "#FFFFFF";
    /** Legacy file path — kept for backward compatibility; prefer sprite. */
    public String texture = "particles/default.png";
    public BlendMode blendMode = BlendMode.ADDITIVE;
    public float gravity;
    /** Elasticity coefficient for MESH particles bouncing off the floor (0=dead stop, 1=elastic). */
    public float bounce = 0.3f;
    public float duration = -1f;
}
```

- [ ] **Step 2.2: Update engine_exhaust.json**

```json
{
  "id": "engine_exhaust",
  "type": "BILLBOARD",
  "sprite": "smoke",
  "maxParticles": 30,
  "emitRate": 60,
  "burstCount": 0,
  "lifetimeMin": 0.3,
  "lifetimeMax": 0.8,
  "speedMin": 5.0,
  "speedMax": 15.0,
  "spread": 8.0,
  "sizeMin": 0.3,
  "sizeMax": 0.5,
  "sizeEnd": 1.0,
  "color": "#4FC3F7",
  "colorEnd": "#4FC3F700",
  "blendMode": "ADDITIVE",
  "gravity": 0.0,
  "duration": -1
}
```

- [ ] **Step 2.3: Update impact_sparks.json**

```json
{
  "id": "impact_sparks",
  "type": "BILLBOARD",
  "sprite": "flash",
  "maxParticles": 8,
  "emitRate": 0,
  "burstCount": 8,
  "lifetimeMin": 0.1,
  "lifetimeMax": 0.3,
  "speedMin": 3.0,
  "speedMax": 12.0,
  "spread": 60.0,
  "sizeMin": 0.05,
  "sizeMax": 0.15,
  "sizeEnd": 0.0,
  "color": "#FFD700",
  "colorEnd": "#8B4513",
  "blendMode": "ADDITIVE",
  "gravity": -9.8
}
```

- [ ] **Step 2.4: Update muzzle_flash_ballistic.json**

```json
{
  "id": "muzzle_flash_ballistic",
  "type": "BILLBOARD",
  "sprite": "flash",
  "maxParticles": 12,
  "emitRate": 0,
  "burstCount": 12,
  "lifetimeMin": 0.05,
  "lifetimeMax": 0.12,
  "speedMin": 2.0,
  "speedMax": 8.0,
  "spread": 25.0,
  "sizeMin": 0.1,
  "sizeMax": 0.3,
  "sizeEnd": 0.0,
  "color": "#FFA500",
  "colorEnd": "#FF4500",
  "blendMode": "ADDITIVE",
  "gravity": 0.0
}
```

- [ ] **Step 2.5: Update shield_ripple.json**

```json
{
  "id": "shield_ripple",
  "type": "BILLBOARD",
  "sprite": "glow",
  "maxParticles": 20,
  "emitRate": 0,
  "burstCount": 20,
  "lifetimeMin": 0.2,
  "lifetimeMax": 0.5,
  "speedMin": 0.5,
  "speedMax": 2.0,
  "spread": 180.0,
  "sizeMin": 0.2,
  "sizeMax": 0.6,
  "sizeEnd": 0.8,
  "color": "#00BFFF",
  "colorEnd": "#00BFFF00",
  "blendMode": "ADDITIVE",
  "gravity": 0.0
}
```

- [ ] **Step 2.6: Create muzzle_flash_energy.json**

```json
{
  "id": "muzzle_flash_energy",
  "type": "BILLBOARD",
  "sprite": "glow",
  "maxParticles": 10,
  "emitRate": 0,
  "burstCount": 10,
  "lifetimeMin": 0.04,
  "lifetimeMax": 0.1,
  "speedMin": 1.0,
  "speedMax": 6.0,
  "spread": 20.0,
  "sizeMin": 0.15,
  "sizeMax": 0.4,
  "sizeEnd": 0.0,
  "color": "#00FFFF",
  "colorEnd": "#00CCFF00",
  "blendMode": "ADDITIVE",
  "gravity": 0.0
}
```

- [ ] **Step 2.7: Create muzzle_flash_plasma.json**

```json
{
  "id": "muzzle_flash_plasma",
  "type": "BILLBOARD",
  "sprite": "glow",
  "maxParticles": 15,
  "emitRate": 0,
  "burstCount": 15,
  "lifetimeMin": 0.06,
  "lifetimeMax": 0.15,
  "speedMin": 2.0,
  "speedMax": 10.0,
  "spread": 30.0,
  "sizeMin": 0.2,
  "sizeMax": 0.5,
  "sizeEnd": 0.0,
  "color": "#AA00FF",
  "colorEnd": "#6600CC00",
  "blendMode": "ADDITIVE",
  "gravity": 0.0
}
```

- [ ] **Step 2.8: Create impact_explosion.json**

```json
{
  "id": "impact_explosion",
  "type": "BILLBOARD",
  "sprite": "shockwave",
  "maxParticles": 30,
  "emitRate": 0,
  "burstCount": 30,
  "lifetimeMin": 0.3,
  "lifetimeMax": 0.8,
  "speedMin": 5.0,
  "speedMax": 20.0,
  "spread": 180.0,
  "sizeMin": 0.3,
  "sizeMax": 1.0,
  "sizeEnd": 0.0,
  "color": "#FF6600",
  "colorEnd": "#33000000",
  "blendMode": "ADDITIVE",
  "gravity": -2.0
}
```

- [ ] **Step 2.9: Commit**

```
git add core/src/main/java/com/galacticodyssey/vfx/data/ParticleEffectDefinition.java
git add core/src/main/resources/data/vfx/engine_exhaust.json
git add core/src/main/resources/data/vfx/impact_sparks.json
git add core/src/main/resources/data/vfx/muzzle_flash_ballistic.json
git add core/src/main/resources/data/vfx/shield_ripple.json
git add core/src/main/resources/data/vfx/muzzle_flash_energy.json
git add core/src/main/resources/data/vfx/muzzle_flash_plasma.json
git add core/src/main/resources/data/vfx/impact_explosion.json
git commit -m "feat(vfx): add type/sprite fields to ParticleEffectDefinition; update all effect JSON files"
```

---

## Task 3: ParticleAtlasManager — programmatic placeholder atlas

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/vfx/ParticleAtlasManager.java`

> This class generates a `TextureAtlas` on the GL thread using `PixmapPacker`. Each of the 8 named regions is a soft white circle (white center → transparent at edges). No unit tests — GL-dependent. The controller will verify compilation.

- [ ] **Step 3.1: Create ParticleAtlasManager.java**

```java
package com.galacticodyssey.vfx;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

public final class ParticleAtlasManager implements Disposable {

    public static final String[] REGIONS = {
        "smoke", "flame", "glow", "flash", "shockwave", "dust", "droplet", "debris_soft"
    };

    private TextureAtlas atlas;

    /** Call once on the GL thread after the OpenGL context is ready. */
    public void generate() {
        PixmapPacker packer = new PixmapPacker(512, 512, Pixmap.Format.RGBA8888, 2, false);
        for (String name : REGIONS) {
            Pixmap pm = makeCircle(64, 64);
            packer.pack(name, pm);
            pm.dispose();
        }
        atlas = packer.generateTextureAtlas(TextureFilter.Linear, TextureFilter.Linear, false);
        packer.dispose();
    }

    public TextureRegion getRegion(String name) {
        return atlas != null ? atlas.findRegion(name) : null;
    }

    private static Pixmap makeCircle(int w, int h) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float cx = w * 0.5f, cy = h * 0.5f;
        float r = Math.min(cx, cy) - 1f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dx = x - cx, dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float alpha = Math.max(0f, 1f - dist / r);
                int a = (int) (alpha * 255f);
                pm.drawPixel(x, y, (255 << 24) | (255 << 16) | (255 << 8) | a);
            }
        }
        return pm;
    }

    @Override
    public void dispose() {
        if (atlas != null) {
            atlas.dispose();
            atlas = null;
        }
    }
}
```

- [ ] **Step 3.2: Verify compiles**

The controller will run `.\gradlew.bat :core:compileJava`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 3.3: Commit**

```
git add core/src/main/java/com/galacticodyssey/vfx/ParticleAtlasManager.java
git commit -m "feat(vfx): add ParticleAtlasManager - programmatic placeholder texture atlas (8 regions)"
```

---

## Task 4: Update ParticleSpawnSystem to use atlas regions

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/vfx/systems/ParticleSpawnSystem.java`

> Add `setAtlasManager(ParticleAtlasManager)` and use it in `spawnBurst()` to set `p.textureRegion` from the atlas.

- [ ] **Step 4.1: Update ParticleSpawnSystem.java**

Replace the file:

```java
package com.galacticodyssey.vfx.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.events.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;
import com.galacticodyssey.vfx.Particle;
import com.galacticodyssey.vfx.ParticleAtlasManager;
import com.galacticodyssey.vfx.VFXEnums;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.data.ParticleEffectDefinition;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;

import java.util.ArrayList;
import java.util.List;

public class ParticleSpawnSystem extends EntitySystem {
    private static final int PRIORITY = 12;
    private static final Vector3 ZERO = new Vector3();
    private final VFXRegistry registry;
    private final VFXEventBindings bindings;
    private final ParticlePoolComponent pool;
    private ParticleAtlasManager atlasManager;

    private final List<SpawnRequest> pendingSpawns = new ArrayList<>();

    public ParticleSpawnSystem(EventBus eventBus, VFXRegistry registry,
                               VFXEventBindings bindings, ParticlePoolComponent pool) {
        super(PRIORITY);
        this.registry = registry;
        this.bindings = bindings;
        this.pool = pool;

        eventBus.subscribe(HitscanHitEvent.class, e ->
            queueSpawn("HitscanHitEvent", e.damageType.name(), e.hitPoint));
        eventBus.subscribe(ProjectileHitEvent.class, e ->
            queueSpawn("ProjectileHitEvent", e.damageType.name(), e.hitPoint));
        eventBus.subscribe(WeaponFiredEvent.class, e ->
            queueSpawn("WeaponFiredEvent", null, e.aimDirection));
        eventBus.subscribe(ShieldAbsorbEvent.class, e ->
            queueSpawn("ShieldAbsorbEvent", null, ZERO));
        eventBus.subscribe(EntityKilledEvent.class, e ->
            queueSpawn("EntityKilledEvent", null, ZERO));
        eventBus.subscribe(ShipWeaponFiredEvent.class, e ->
            queueSpawn("ShipWeaponFiredEvent", null, e.origin));
    }

    public void setAtlasManager(ParticleAtlasManager atlasManager) {
        this.atlasManager = atlasManager;
    }

    private void queueSpawn(String eventType, String variant, Vector3 position) {
        pendingSpawns.add(new SpawnRequest(eventType, variant, new Vector3(position)));
    }

    @Override
    public void update(float deltaTime) {
        for (SpawnRequest req : pendingSpawns) {
            String effectId = bindings.resolve(req.eventType, req.variant);
            if (effectId == null) continue;
            ParticleEffectDefinition def = registry.getEffect(effectId);
            if (def == null) continue;
            if ("MESH".equals(def.type)) continue; // mesh particles handled separately
            spawnBurst(def, req.position);
        }
        pendingSpawns.clear();
    }

    void spawnBurst(ParticleEffectDefinition def, Vector3 origin) {
        int count = def.burstCount > 0 ? def.burstCount : 1;
        Color startColor = Color.valueOf(def.color);
        Color endColor = Color.valueOf(def.colorEnd);

        for (int i = 0; i < count; i++) {
            Particle p = pool.obtain();
            p.position.set(origin);
            float speed = MathUtils.random(def.speedMin, def.speedMax);
            float spreadRad = def.spread * MathUtils.degreesToRadians;
            float theta = MathUtils.random(0f, MathUtils.PI2);
            float phi = MathUtils.random(0f, spreadRad);
            p.velocity.set(
                speed * MathUtils.sin(phi) * MathUtils.cos(theta),
                speed * MathUtils.cos(phi),
                speed * MathUtils.sin(phi) * MathUtils.sin(theta)
            );
            p.acceleration.set(0, def.gravity, 0);
            p.life = MathUtils.random(def.lifetimeMin, def.lifetimeMax);
            p.maxLife = p.life;
            p.size = MathUtils.random(def.sizeMin, def.sizeMax);
            p.sizeEnd = def.sizeEnd;
            p.color.set(startColor);
            p.colorEnd.set(endColor);
            p.flags = def.blendMode == VFXEnums.BlendMode.ADDITIVE
                ? VFXEnums.FLAG_ADDITIVE_BLEND | VFXEnums.FLAG_FACE_CAMERA
                : VFXEnums.FLAG_FACE_CAMERA;
            if (atlasManager != null) {
                p.textureRegion = atlasManager.getRegion(def.sprite);
            }
        }
    }

    private static class SpawnRequest {
        final String eventType;
        final String variant;
        final Vector3 position;

        SpawnRequest(String eventType, String variant, Vector3 position) {
            this.eventType = eventType;
            this.variant = variant;
            this.position = position;
        }
    }
}
```

- [ ] **Step 4.2: Commit**

```
git add core/src/main/java/com/galacticodyssey/vfx/systems/ParticleSpawnSystem.java
git commit -m "feat(vfx): ParticleSpawnSystem sets textureRegion from atlas on spawn; skips MESH type"
```

---

## Task 5: MeshParticle + MeshParticlePool

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/vfx/MeshParticle.java`
- Create: `core/src/main/java/com/galacticodyssey/vfx/MeshParticlePool.java`
- Create: `core/src/test/java/com/galacticodyssey/vfx/MeshParticleTest.java`

- [ ] **Step 5.1: Write failing tests**

Create `core/src/test/java/com/galacticodyssey/vfx/MeshParticleTest.java`:

```java
package com.galacticodyssey.vfx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshParticleTest {

    @Test
    void gravityAccumulatesVelocity() {
        MeshParticle mp = new MeshParticle();
        mp.position.set(0, 100f, 0); // high up — won't hit floor
        mp.velocity.set(0, 0, 0);
        mp.gravity = -10f;
        mp.bounce = 0f;
        mp.life = 1f;
        mp.update(1f);
        assertEquals(-10f, mp.velocity.y, 0.001f);
    }

    @Test
    void positionAdvancesWithVelocity() {
        MeshParticle mp = new MeshParticle();
        mp.position.set(0, 10f, 0);
        mp.velocity.set(2f, 0f, 3f);
        mp.gravity = 0f;
        mp.bounce = 0f;
        mp.life = 1f;
        mp.update(0.5f);
        assertEquals(1f, mp.position.x, 0.001f);
        assertEquals(10f, mp.position.y, 0.001f);
        assertEquals(1.5f, mp.position.z, 0.001f);
    }

    @Test
    void bounceInvertsYVelocityAtFloor() {
        MeshParticle mp = new MeshParticle();
        mp.position.set(0, 0.01f, 0); // just above floor
        mp.velocity.set(0, -5f, 0);
        mp.gravity = 0f;
        mp.bounce = 0.6f;
        mp.life = 1f;
        mp.update(0.1f);
        assertTrue(mp.velocity.y > 0, "Expected upward bounce velocity");
        assertTrue(mp.position.y >= 0f, "Expected particle at or above floor");
    }

    @Test
    void resetClearsAllFields() {
        MeshParticle mp = new MeshParticle();
        mp.position.set(1, 2, 3);
        mp.velocity.set(4, 5, 6);
        mp.life = 0.5f;
        mp.meshId = "spark_line";
        mp.reset();
        assertEquals(0f, mp.position.x);
        assertEquals(0f, mp.position.y);
        assertEquals(0f, mp.position.z);
        assertEquals(0f, mp.life);
        assertNull(mp.meshId);
    }
}
```

- [ ] **Step 5.2: Run tests, confirm compilation fails**

Expected: compilation failure — `MeshParticle` does not exist yet.

- [ ] **Step 5.3: Create MeshParticle.java**

```java
package com.galacticodyssey.vfx;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool.Poolable;

public final class MeshParticle implements Poolable {

    public final Vector3 position = new Vector3();
    public final Vector3 velocity = new Vector3();
    public float gravity = -9.8f;
    public float bounce = 0.3f;
    public float life;
    public float maxLife;
    public String meshId;

    /** Advances physics by deltaTime. Applies gravity, moves position, bounces off y=0. */
    public void update(float delta) {
        velocity.y += gravity * delta;
        position.mulAdd(velocity, delta);
        if (position.y < 0f) {
            position.y = 0f;
            velocity.y = -velocity.y * bounce;
        }
        life -= delta;
    }

    @Override
    public void reset() {
        position.setZero();
        velocity.setZero();
        gravity = -9.8f;
        bounce = 0.3f;
        life = 0f;
        maxLife = 0f;
        meshId = null;
    }
}
```

- [ ] **Step 5.4: Create MeshParticlePool.java**

```java
package com.galacticodyssey.vfx;

import com.badlogic.gdx.utils.Pool;
import java.util.ArrayList;
import java.util.List;

public final class MeshParticlePool {

    private static final int MAX = 512;

    private final Pool<MeshParticle> pool = new Pool<MeshParticle>(64, MAX) {
        @Override
        protected MeshParticle newObject() {
            return new MeshParticle();
        }
    };

    public final List<MeshParticle> active = new ArrayList<>(MAX);

    public MeshParticle obtain() {
        if (active.size() >= MAX) {
            MeshParticle oldest = active.remove(0);
            pool.free(oldest);
        }
        MeshParticle mp = pool.obtain();
        active.add(mp);
        return mp;
    }

    public void free(MeshParticle mp) {
        active.remove(mp);
        pool.free(mp);
    }

    public void freeAll() {
        for (MeshParticle mp : active) pool.free(mp);
        active.clear();
    }
}
```

- [ ] **Step 5.5: Run tests, confirm they pass**

Expected: 4 tests, all PASS.

- [ ] **Step 5.6: Commit**

```
git add core/src/main/java/com/galacticodyssey/vfx/MeshParticle.java
git add core/src/main/java/com/galacticodyssey/vfx/MeshParticlePool.java
git add core/src/test/java/com/galacticodyssey/vfx/MeshParticleTest.java
git commit -m "feat(vfx): add MeshParticle with gravity/bounce physics and MeshParticlePool"
```

---

## Task 6: Upgrade ParticleRenderSystem — depth sort + mesh particle path

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/vfx/systems/ParticleRenderSystem.java`

> No unit tests — rendering is GL-dependent. The controller verifies compilation.

Key changes:
1. **Depth sort:** Sort `pool.active` back-to-front by `camera.position.dst2(particle.position)` before submitting to `DecalBatch`. Back-to-front means far particles come first in the list (rendered first, overwritten by closer ones). Use `Comparator.comparingDouble` with reversed order.
2. **Mesh particle path:** Accept `MeshParticlePool meshPool` and a fallback `ModelInstance` (tiny box). If `meshPool` is set and non-empty, render each `MeshParticle` via `ModelBatch`.
3. **Atlas manager setter:** `setAtlasManager(ParticleAtlasManager)` replaces the old `initialize(TextureRegion)` signature. Keep `initialize(TextureRegion)` for backward compatibility — it just sets `defaultTexture` and creates the `DecalBatch`.

- [ ] **Step 6.1: Update ParticleRenderSystem.java**

```java
package com.galacticodyssey.vfx.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.vfx.MeshParticle;
import com.galacticodyssey.vfx.MeshParticlePool;
import com.galacticodyssey.vfx.Particle;
import com.galacticodyssey.vfx.ParticleAtlasManager;
import com.galacticodyssey.vfx.VFXEnums;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import java.util.Comparator;

public class ParticleRenderSystem extends EntitySystem implements Disposable {

    private static final int PRIORITY = 20;

    private final ParticlePoolComponent pool;
    private final Camera camera;
    private DecalBatch decalBatch;
    private TextureRegion defaultTexture;
    private ParticleAtlasManager atlasManager;
    private MeshParticlePool meshPool;
    private ModelBatch modelBatch;
    private ModelInstance fallbackMeshInstance;

    // Reusable comparator — sorts far particles first (back-to-front = far drawn first)
    private final Comparator<Particle> backToFront = (a, b) -> {
        float da = camera.position.dst2(a.position.x, a.position.y, a.position.z);
        float db = camera.position.dst2(b.position.x, b.position.y, b.position.z);
        return Float.compare(db, da); // descending distance
    };

    public ParticleRenderSystem(ParticlePoolComponent pool, Camera camera) {
        super(PRIORITY);
        this.pool = pool;
        this.camera = camera;
    }

    public void initialize(TextureRegion defaultTexture) {
        this.defaultTexture = defaultTexture;
        if (decalBatch == null) {
            decalBatch = new DecalBatch(ParticlePoolComponent.MAX_PARTICLES,
                new CameraGroupStrategy(camera));
        }
    }

    public void setAtlasManager(ParticleAtlasManager atlasManager) {
        this.atlasManager = atlasManager;
        if (atlasManager != null) {
            initialize(atlasManager.getRegion("smoke"));
        }
    }

    public void setMeshParticlePool(MeshParticlePool meshPool, ModelBatch modelBatch,
                                     ModelInstance fallbackMeshInstance) {
        this.meshPool = meshPool;
        this.modelBatch = modelBatch;
        this.fallbackMeshInstance = fallbackMeshInstance;
    }

    @Override
    public void update(float deltaTime) {
        // Rendering is done in render(), called explicitly by GameScreen after world rendering.
    }

    public void render() {
        renderBillboards();
        renderMeshParticles();
    }

    private void renderBillboards() {
        if (decalBatch == null || pool.active.isEmpty()) return;

        // Back-to-front sort for correct alpha blending
        pool.active.sort(backToFront);

        for (Particle p : pool.active) {
            TextureRegion tex = p.textureRegion != null ? p.textureRegion : defaultTexture;
            if (tex == null) continue;

            Decal decal = Decal.newDecal(tex, true);
            float size = p.getCurrentSize();
            decal.setDimensions(size, size);
            decal.setPosition(p.position.x, p.position.y, p.position.z);
            decal.setRotation(camera.direction.cpy().scl(-1), camera.up);

            Color c = p.getCurrentColor();
            decal.setColor(c.r, c.g, c.b, c.a);

            if ((p.flags & VFXEnums.FLAG_ADDITIVE_BLEND) != 0) {
                decal.setBlending(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            }

            decalBatch.add(decal);
        }

        decalBatch.flush();
    }

    private void renderMeshParticles() {
        if (meshPool == null || meshPool.active.isEmpty() || modelBatch == null) return;
        if (fallbackMeshInstance == null) return;

        modelBatch.begin(camera);
        for (MeshParticle mp : meshPool.active) {
            fallbackMeshInstance.transform.setToTranslation(mp.position);
            modelBatch.render(fallbackMeshInstance);
        }
        modelBatch.end();
    }

    @Override
    public void dispose() {
        if (decalBatch != null) decalBatch.dispose();
    }
}
```

- [ ] **Step 6.2: Verify compiles**

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.3: Commit**

```
git add core/src/main/java/com/galacticodyssey/vfx/systems/ParticleRenderSystem.java
git commit -m "feat(vfx): ParticleRenderSystem - depth sort billboard particles; add mesh particle render path"
```

---

## Task 7: Wire VFXLoader and ParticleAtlasManager into GameWorld

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

> Call `VFXLoader.loadAll()` in the constructor (when `Gdx.files` is available) and wire `ParticleAtlasManager` in `initializeSystems()`.

**CRITICAL:** `GameWorld.java` already has pre-existing unstaged changes (water/mission system additions from earlier sessions). When you stage this file, ALL current changes will be committed together. That is intentional — include them. Use `git add core/src/main/java/com/galacticodyssey/core/GameWorld.java` (not `-p`).

- [ ] **Step 7.1: Add VFXLoader import to GameWorld.java**

Find the import block and add:

```java
import com.galacticodyssey.vfx.data.VFXLoader;
import com.galacticodyssey.vfx.ParticleAtlasManager;
import com.galacticodyssey.vfx.MeshParticlePool;
```

- [ ] **Step 7.2: Add fields to GameWorld**

After the `private ParticleRenderSystem particleRenderSystem;` field declaration, add:

```java
private ParticleAtlasManager particleAtlasManager;
private MeshParticlePool meshParticlePool;
```

- [ ] **Step 7.3: Call VFXLoader.loadAll() in GameWorld constructor**

Find the existing block in the constructor that creates `vfxRegistry` and `vfxBindings`:

```java
vfxRegistry = new VFXRegistry();
VFXEventBindings vfxBindings = new VFXEventBindings();
```

Change to:

```java
vfxRegistry = new VFXRegistry();
VFXEventBindings vfxBindings = new VFXEventBindings();
if (com.badlogic.gdx.Gdx.files != null) {
    VFXLoader.loadAll(vfxRegistry, vfxBindings);
}
```

- [ ] **Step 7.4: Wire ParticleAtlasManager in initializeSystems()**

In `initializeSystems(PerspectiveCamera camera)`, after `particleRenderSystem = new ParticleRenderSystem(particlePool, camera);`, add:

```java
particleAtlasManager = new ParticleAtlasManager();
particleAtlasManager.generate();
meshParticlePool = new MeshParticlePool();
particleSpawnSystem.setAtlasManager(particleAtlasManager);
particleRenderSystem.setAtlasManager(particleAtlasManager);
// Fallback mesh: a tiny ModelBuilder box for MESH-type particles
com.badlogic.gdx.graphics.g3d.utils.ModelBuilder mb = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
com.badlogic.gdx.graphics.g3d.Model fallbackModel = mb.createBox(0.05f, 0.05f, 0.05f,
    new com.badlogic.gdx.graphics.g3d.Material(), 
    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
com.badlogic.gdx.graphics.g3d.ModelInstance fallbackMesh = new com.badlogic.gdx.graphics.g3d.ModelInstance(fallbackModel);
ModelBatch meshModelBatch = new ModelBatch();
particleRenderSystem.setMeshParticlePool(meshParticlePool, meshModelBatch, fallbackMesh);
```

> Note: `ModelBatch` import is `com.badlogic.gdx.graphics.g3d.ModelBatch`. Add the import if it's not already in GameWorld.java.

- [ ] **Step 7.5: Add dispose() calls for new resources**

In `GameWorld.dispose()`, before `bulletPhysicsSystem.dispose();`:

```java
if (particleAtlasManager != null) particleAtlasManager.dispose();
```

- [ ] **Step 7.6: Verify compiles**

The controller will run `.\gradlew.bat :core:compileJava`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.7: Run all VFX tests to confirm no regressions**

The controller will run `.\gradlew.bat :core:test --tests "com.galacticodyssey.vfx.*"`. Expected: all PASS.

- [ ] **Step 7.8: Commit**

```
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(vfx): wire VFXLoader, ParticleAtlasManager, and MeshParticlePool into GameWorld"
```

---

## Verification

**Manual end-to-end smoke test (requires running the game):**

1. Launch the game: `.\gradlew.bat :desktop:run`
2. Fire a weapon — confirm a brief flash appears at the muzzle (previously invisible)
3. Walk into a melee hit or hitscan hit — confirm spark particles appear
4. Check console — no `NullPointerException` from `ParticleRenderSystem.render()`
5. Check console — no "Skipping missing effect" errors from `VFXLoader` (all 7 JSON files should load)

**Expected visual result:** Particles are visible but use placeholder white circle sprites (not finished art). The placeholder is the intended outcome for Phase 2 — real art replaces the generated circles in a future art pass.
