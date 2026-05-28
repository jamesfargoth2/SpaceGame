# Asset Pipeline Phase 1: Streaming Asset Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire in gdx-gltf and build the `GalacticAssetManager` — the foundation that all subsequent asset pipeline phases load through.

**Architecture:** Wrap libGDX's `AssetManager` with category-typed `AssetHandle<T>` objects, a priority-queue `StreamingQueue` that orders loads by `(1/distance) * categoryPriority`, and an Ashley `StreamingSystem` that drives prefetch/unload based on camera distance. The asset manager reads content manifests (JSON) so it knows what assets exist and their file paths for each LOD tier. No synchronous model loads ever occur on the GL thread.

**Tech Stack:** libGDX 1.13.5, Ashley 1.7.4, gdx-gltf 2.2.1 (JitPack), JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-27-asset-animation-pipeline-design.md` — Section 1

**This is Plan 1 of 7.** Subsequent plans cover: Particles (Phase 2), Shader Variants (Phase 3), LOD + Props + Buildings (Phase 4), Blend Tree Animation (Phase 5), Foliage (Phase 6), Occlusion Culling (Phase 7).

---

## File Map

| File | Status | Responsibility |
|---|---|---|
| `build.gradle.kts` | Modify | Add JitPack repository |
| `core/build.gradle.kts` | Modify | Add gdx-gltf dependency |
| `core/src/main/java/com/galacticodyssey/data/AssetCategory.java` | Create | Category enum with priority weights |
| `core/src/main/java/com/galacticodyssey/data/AssetHandle.java` | Create | Ref-counted typed handle |
| `core/src/main/java/com/galacticodyssey/data/StreamingQueue.java` | Create | Priority queue ordered by (1/distance) × categoryPriority |
| `core/src/main/java/com/galacticodyssey/data/AssetManifest.java` | Create | In-memory view of content manifest JSON |
| `core/src/main/java/com/galacticodyssey/data/GalacticAssetManager.java` | Create | Central loader wrapping libGDX AssetManager |
| `core/src/main/java/com/galacticodyssey/data/components/StreamableComponent.java` | Create | Ashley component: assetId + category |
| `core/src/main/java/com/galacticodyssey/data/systems/StreamingSystem.java` | Create | Ashley system driving prefetch/unload |
| `core/src/main/resources/data/assets/asset_budgets.json` | Create | Per-category memory budget config |
| `core/src/main/resources/data/assets/streaming_config.json` | Create | Per-category prefetch/unload radii |
| `core/src/main/resources/data/assets/characters.json` | Create | Character asset manifest (placeholder entries) |
| `core/src/main/resources/data/assets/props.json` | Create | Prop asset manifest (placeholder entries) |
| `core/src/test/java/com/galacticodyssey/data/AssetHandleTest.java` | Create | Ref-count correctness |
| `core/src/test/java/com/galacticodyssey/data/StreamingQueueTest.java` | Create | Priority ordering correctness |
| `core/src/test/java/com/galacticodyssey/data/AssetManifestTest.java` | Create | Manifest JSON parsing |
| `core/src/test/java/com/galacticodyssey/data/StreamingSystemTest.java` | Create | Distance-based enqueue/release logic |

---

## Task 1: Add gdx-gltf to Gradle

**Files:**
- Modify: `build.gradle.kts`
- Modify: `core/build.gradle.kts`

- [ ] **Step 1.1: Add JitPack to root repositories**

Open `build.gradle.kts`. The `allprojects { repositories { ... } }` block currently has `mavenCentral()` and the Sonatype snapshot repo. Add JitPack:

```kotlin
allprojects {
    version = "0.1.0"
    group = "com.galacticodyssey"

    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://jitpack.io")
    }
}
```

- [ ] **Step 1.2: Add gdx-gltf dependency to core**

Open `core/build.gradle.kts`. In the `dependencies { }` block, add after the existing `api(...)` lines:

```kotlin
api("com.github.mgsx-dev.gdx-gltf:gltf:2.2.1")
```

- [ ] **Step 1.3: Verify the build resolves**

```bash
./gradlew :core:dependencies --configuration compileClasspath
```

Expected: output includes `com.github.mgsx-dev.gdx-gltf:gltf:2.2.1` with no resolution errors.

- [ ] **Step 1.4: Commit**

```bash
git add build.gradle.kts core/build.gradle.kts
git commit -m "build: add gdx-gltf 2.2.1 via JitPack"
```

---

## Task 2: AssetCategory

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/data/AssetCategory.java`

- [ ] **Step 2.1: Create AssetCategory enum**

```java
package com.galacticodyssey.data;

public enum AssetCategory {
    CHARACTER(10f),
    PROP_SMALL(6f),
    PROP_LARGE(5f),
    INTERIOR_PROP(8f),
    FOLIAGE(4f),
    BUILDING(3f),
    VFX_MESH(7f),
    TEXTURE_ATLAS(9f);

    /** Higher weight = loaded sooner relative to distance. */
    public final float priorityWeight;

    AssetCategory(float priorityWeight) {
        this.priorityWeight = priorityWeight;
    }
}
```

- [ ] **Step 2.2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/AssetCategory.java
git commit -m "feat(assets): add AssetCategory enum with priority weights"
```

---

## Task 3: AssetHandle — ref-counted typed handle

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/data/AssetHandle.java`
- Create: `core/src/test/java/com/galacticodyssey/data/AssetHandleTest.java`

- [ ] **Step 3.1: Write failing tests**

```java
package com.galacticodyssey.data;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AssetHandleTest {

    @Test
    void newHandleIsNotResident() {
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER, null);
        assertFalse(h.isResident());
        assertNull(h.get());
    }

    @Test
    void setAssetMakesHandleResident() {
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER, null);
        h.setAsset("loaded");
        assertTrue(h.isResident());
        assertEquals("loaded", h.get());
    }

    @Test
    void retainIncreasesRefCount() {
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER, null);
        assertEquals(0, h.getRefCount());
        h.retain();
        assertEquals(1, h.getRefCount());
        h.retain();
        assertEquals(2, h.getRefCount());
    }

    @Test
    void releaseDecrementsRefCount() {
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER, null);
        h.retain();
        h.retain();
        h.release();
        assertEquals(1, h.getRefCount());
    }

    @Test
    void releaseToZeroTriggersCallback() {
        AtomicInteger callCount = new AtomicInteger(0);
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER,
            handle -> callCount.incrementAndGet());
        h.retain();
        h.release();
        assertEquals(1, callCount.get());
    }

    @Test
    void callbackNotFiredAboveZero() {
        AtomicInteger callCount = new AtomicInteger(0);
        AssetHandle<String> h = new AssetHandle<>("id1", AssetCategory.CHARACTER,
            handle -> callCount.incrementAndGet());
        h.retain();
        h.retain();
        h.release(); // still at 1
        assertEquals(0, callCount.get());
    }

    @Test
    void getAssetIdAndCategory() {
        AssetHandle<String> h = new AssetHandle<>("myAsset", AssetCategory.FOLIAGE, null);
        assertEquals("myAsset", h.getAssetId());
        assertEquals(AssetCategory.FOLIAGE, h.getCategory());
    }
}
```

- [ ] **Step 3.2: Run tests, confirm they fail**

```bash
./gradlew :core:test --tests "com.galacticodyssey.data.AssetHandleTest" --info
```

Expected: compilation failure — `AssetHandle` does not exist yet.

- [ ] **Step 3.3: Create AssetHandle**

```java
package com.galacticodyssey.data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AssetHandle<T> {

    private final String assetId;
    private final AssetCategory category;
    private volatile T asset;
    private final AtomicInteger refCount = new AtomicInteger(0);
    private final Consumer<AssetHandle<T>> onZero;

    public AssetHandle(String assetId, AssetCategory category, Consumer<AssetHandle<T>> onZero) {
        this.assetId = assetId;
        this.category = category;
        this.onZero = onZero;
    }

    public AssetHandle<T> retain() {
        refCount.incrementAndGet();
        return this;
    }

    public void release() {
        if (refCount.decrementAndGet() <= 0 && onZero != null) {
            onZero.accept(this);
        }
    }

    /** Called by GalacticAssetManager once the asset finishes loading on the GL thread. */
    public void setAsset(T asset) {
        this.asset = asset;
    }

    public T get() { return asset; }
    public boolean isResident() { return asset != null; }
    public String getAssetId() { return assetId; }
    public AssetCategory getCategory() { return category; }
    public int getRefCount() { return refCount.get(); }
}
```

- [ ] **Step 3.4: Run tests, confirm they pass**

```bash
./gradlew :core:test --tests "com.galacticodyssey.data.AssetHandleTest" --info
```

Expected: 7 tests, all PASS.

- [ ] **Step 3.5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/AssetHandle.java \
        core/src/test/java/com/galacticodyssey/data/AssetHandleTest.java
git commit -m "feat(assets): add AssetHandle with ref-counting"
```

---

## Task 4: StreamingQueue — priority queue

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/data/StreamingQueue.java`
- Create: `core/src/test/java/com/galacticodyssey/data/StreamingQueueTest.java`

- [ ] **Step 4.1: Write failing tests**

```java
package com.galacticodyssey.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreamingQueueTest {

    @Test
    void emptyQueueReturnsTrueForIsEmpty() {
        StreamingQueue q = new StreamingQueue();
        assertTrue(q.isEmpty());
    }

    @Test
    void enqueueAndPollSingleItem() {
        StreamingQueue q = new StreamingQueue();
        q.enqueue("char1", AssetCategory.CHARACTER, 10f);
        assertFalse(q.isEmpty());
        StreamingQueue.StreamRequest r = q.poll();
        assertNotNull(r);
        assertEquals("char1", r.assetId);
        assertEquals(AssetCategory.CHARACTER, r.category);
        assertTrue(q.isEmpty());
    }

    @Test
    void closerItemPolledBeforeFarterSameCategory() {
        StreamingQueue q = new StreamingQueue();
        q.enqueue("far", AssetCategory.CHARACTER, 100f);
        q.enqueue("close", AssetCategory.CHARACTER, 5f);
        StreamingQueue.StreamRequest first = q.poll();
        assertEquals("close", first.assetId);
    }

    @Test
    void highPriorityCategoryBeatsDistanceAdvantage() {
        // CHARACTER (weight 10) at 20m vs FOLIAGE (weight 4) at 20m
        // CHARACTER should come first
        StreamingQueue q = new StreamingQueue();
        q.enqueue("foliage1", AssetCategory.FOLIAGE, 20f);
        q.enqueue("char1", AssetCategory.CHARACTER, 20f);
        StreamingQueue.StreamRequest first = q.poll();
        assertEquals("char1", first.assetId);
    }

    @Test
    void zeroDistanceGetsMaxPriority() {
        StreamingQueue q = new StreamingQueue();
        q.enqueue("far", AssetCategory.BUILDING, 500f);
        q.enqueue("here", AssetCategory.BUILDING, 0f);
        StreamingQueue.StreamRequest first = q.poll();
        assertEquals("here", first.assetId);
    }

    @Test
    void clearEmptiesQueue() {
        StreamingQueue q = new StreamingQueue();
        q.enqueue("a", AssetCategory.PROP_SMALL, 10f);
        q.enqueue("b", AssetCategory.PROP_SMALL, 20f);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
    }

    @Test
    void sizeReflectsEnqueueCount() {
        StreamingQueue q = new StreamingQueue();
        assertEquals(0, q.size());
        q.enqueue("a", AssetCategory.FOLIAGE, 10f);
        assertEquals(1, q.size());
        q.enqueue("b", AssetCategory.FOLIAGE, 20f);
        assertEquals(2, q.size());
        q.poll();
        assertEquals(1, q.size());
    }
}
```

- [ ] **Step 4.2: Run tests, confirm compilation fails**

```bash
./gradlew :core:test --tests "com.galacticodyssey.data.StreamingQueueTest" --info
```

Expected: compilation failure — `StreamingQueue` does not exist yet.

- [ ] **Step 4.3: Create StreamingQueue**

```java
package com.galacticodyssey.data;

import java.util.Comparator;
import java.util.PriorityQueue;

public class StreamingQueue {

    public static final class StreamRequest {
        public final String assetId;
        public final AssetCategory category;
        public final float priority;

        StreamRequest(String assetId, AssetCategory category, float distanceToCamera) {
            this.assetId = assetId;
            this.category = category;
            float inverseDist = distanceToCamera > 0f ? (1.0f / distanceToCamera) : Float.MAX_VALUE;
            this.priority = inverseDist * category.priorityWeight;
        }
    }

    // Max-heap: highest priority dequeued first
    private final PriorityQueue<StreamRequest> queue =
        new PriorityQueue<>(Comparator.comparingDouble((StreamRequest r) -> r.priority).reversed());

    public void enqueue(String assetId, AssetCategory category, float distanceToCamera) {
        queue.offer(new StreamRequest(assetId, category, distanceToCamera));
    }

    public StreamRequest poll() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }
}
```

- [ ] **Step 4.4: Run tests, confirm they pass**

```bash
./gradlew :core:test --tests "com.galacticodyssey.data.StreamingQueueTest" --info
```

Expected: 7 tests, all PASS.

- [ ] **Step 4.5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/StreamingQueue.java \
        core/src/test/java/com/galacticodyssey/data/StreamingQueueTest.java
git commit -m "feat(assets): add StreamingQueue with priority ordering"
```

---

## Task 5: AssetManifest — JSON content registry

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/data/AssetManifest.java`
- Create: `core/src/main/resources/data/assets/characters.json`
- Create: `core/src/main/resources/data/assets/props.json`
- Create: `core/src/test/java/com/galacticodyssey/data/AssetManifestTest.java`

- [ ] **Step 5.1: Create characters.json manifest**

```json
{
  "category": "CHARACTER",
  "assets": [
    {
      "id": "human_player",
      "path": "models/characters/human_player.glb",
      "lod_mid": "models/characters/human_player_lod1.glb",
      "lod_far": "particles/billboards/human_silhouette",
      "memory_tier": 1
    },
    {
      "id": "human_npc_generic",
      "path": "models/characters/human_npc_generic.glb",
      "lod_mid": "models/characters/human_npc_generic_lod1.glb",
      "lod_far": "particles/billboards/human_silhouette",
      "memory_tier": 2
    }
  ]
}
```

- [ ] **Step 5.2: Create props.json manifest**

```json
{
  "category": "INTERIOR_PROP",
  "assets": [
    {
      "id": "chair_crew",
      "path": "models/props/interior/chair_crew.glb",
      "lod_mid": "models/props/interior/chair_crew_lod1.glb",
      "lod_far": null,
      "memory_tier": 3,
      "tags": ["SEATING"]
    },
    {
      "id": "console_nav",
      "path": "models/props/interior/console_nav.glb",
      "lod_mid": "models/props/interior/console_nav_lod1.glb",
      "lod_far": null,
      "memory_tier": 2,
      "tags": ["CONSOLE", "TECH"]
    },
    {
      "id": "desk_standard",
      "path": "models/props/interior/desk_standard.glb",
      "lod_mid": "models/props/interior/desk_standard_lod1.glb",
      "lod_far": null,
      "memory_tier": 3,
      "tags": ["DECORATION"]
    }
  ]
}
```

- [ ] **Step 5.3: Write failing tests for AssetManifest**

```java
package com.galacticodyssey.data;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AssetManifestTest {

    private static JsonValue parseResource(String resourcePath) throws Exception {
        InputStream is = AssetManifestTest.class.getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(is, "Resource not found: " + resourcePath);
        String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        return new JsonReader().parse(json);
    }

    @Test
    void parsesCharacterManifest() throws Exception {
        JsonValue root = parseResource("data/assets/characters.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        assertEquals(AssetCategory.CHARACTER, manifest.getCategory());
        assertFalse(manifest.getEntries().isEmpty());
    }

    @Test
    void entryHasIdAndPath() throws Exception {
        JsonValue root = parseResource("data/assets/characters.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        AssetManifest.Entry first = manifest.getEntries().get(0);
        assertEquals("human_player", first.id());
        assertTrue(first.path().endsWith(".glb"));
    }

    @Test
    void lodMidPathParsed() throws Exception {
        JsonValue root = parseResource("data/assets/characters.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        AssetManifest.Entry first = manifest.getEntries().get(0);
        assertNotNull(first.lodMidPath());
    }

    @Test
    void findByIdReturnsCorrectEntry() throws Exception {
        JsonValue root = parseResource("data/assets/characters.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        AssetManifest.Entry entry = manifest.findById("human_player");
        assertNotNull(entry);
        assertEquals("human_player", entry.id());
    }

    @Test
    void findByIdReturnsNullForUnknownId() throws Exception {
        JsonValue root = parseResource("data/assets/characters.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        assertNull(manifest.findById("does_not_exist"));
    }

    @Test
    void parsesPropsManifestWithTags() throws Exception {
        JsonValue root = parseResource("data/assets/props.json");
        AssetManifest manifest = AssetManifest.fromJson(root);
        AssetManifest.Entry console = manifest.findById("console_nav");
        assertNotNull(console);
        assertTrue(console.tags().contains("CONSOLE"));
        assertTrue(console.tags().contains("TECH"));
    }
}
```

- [ ] **Step 5.4: Run tests, confirm compilation fails**

```bash
./gradlew :core:test --tests "com.galacticodyssey.data.AssetManifestTest" --info
```

Expected: compilation failure — `AssetManifest` does not exist yet.

- [ ] **Step 5.5: Create AssetManifest**

```java
package com.galacticodyssey.data;

import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.List;

public final class AssetManifest {

    public record Entry(
        String id,
        String path,
        String lodMidPath,
        String lodFarSprite,
        int memoryTier,
        List<String> tags
    ) {}

    private final AssetCategory category;
    private final List<Entry> entries;

    private AssetManifest(AssetCategory category, List<Entry> entries) {
        this.category = category;
        this.entries = entries;
    }

    public static AssetManifest fromJson(JsonValue root) {
        AssetCategory category = AssetCategory.valueOf(root.getString("category"));
        List<Entry> entries = new ArrayList<>();
        for (JsonValue assetJson : root.get("assets")) {
            List<String> tags = new ArrayList<>();
            JsonValue tagsJson = assetJson.get("tags");
            if (tagsJson != null) {
                for (JsonValue tag : tagsJson) {
                    tags.add(tag.asString());
                }
            }
            entries.add(new Entry(
                assetJson.getString("id"),
                assetJson.getString("path"),
                assetJson.getString("lod_mid", null),
                assetJson.getString("lod_far", null),
                assetJson.getInt("memory_tier", 3),
                List.copyOf(tags)
            ));
        }
        return new AssetManifest(category, List.copyOf(entries));
    }

    public AssetCategory getCategory() { return category; }
    public List<Entry> getEntries() { return entries; }

    public Entry findById(String id) {
        return entries.stream()
            .filter(e -> e.id().equals(id))
            .findFirst()
            .orElse(null);
    }
}
```

- [ ] **Step 5.6: Run tests, confirm they pass**

```bash
./gradlew :core:test --tests "com.galacticodyssey.data.AssetManifestTest" --info
```

Expected: 6 tests, all PASS.

- [ ] **Step 5.7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/AssetManifest.java \
        core/src/main/resources/data/assets/characters.json \
        core/src/main/resources/data/assets/props.json \
        core/src/test/java/com/galacticodyssey/data/AssetManifestTest.java
git commit -m "feat(assets): add AssetManifest with JSON parsing for characters and props"
```

---

## Task 6: GalacticAssetManager

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/data/GalacticAssetManager.java`
- Create: `core/src/main/resources/data/assets/streaming_config.json`
- Create: `core/src/main/resources/data/assets/asset_budgets.json`

> Note: `GalacticAssetManager` wraps libGDX `AssetManager` which requires a GL context for actual model loading. The testable logic (manifest registration, enqueue, eviction via ref-count) is extracted to pure methods. Integration testing of actual glTF loading is done manually via the running game (see Verification section at the end).

- [ ] **Step 6.1: Create streaming_config.json**

```json
{
  "categories": {
    "CHARACTER":      { "prefetchRadius": 80.0,  "unloadRadius": 120.0 },
    "INTERIOR_PROP":  { "prefetchRadius": 25.0,  "unloadRadius": 35.0  },
    "PROP_SMALL":     { "prefetchRadius": 40.0,  "unloadRadius": 60.0  },
    "PROP_LARGE":     { "prefetchRadius": 60.0,  "unloadRadius": 90.0  },
    "FOLIAGE":        { "prefetchRadius": 150.0, "unloadRadius": 200.0 },
    "BUILDING":       { "prefetchRadius": 300.0, "unloadRadius": 500.0 },
    "VFX_MESH":       { "prefetchRadius": 50.0,  "unloadRadius": 80.0  },
    "TEXTURE_ATLAS":  { "prefetchRadius": 0.0,   "unloadRadius": 9999.0 }
  }
}
```

- [ ] **Step 6.2: Create asset_budgets.json**

> Budget enforcement (evicting LRU assets when a category hits `maxResidentAssets`) is **not implemented in Phase 1** — the file is created now so the format is established, but enforcement logic is added in Phase 4 once there are enough prop assets to make it measurable.

```json
{
  "categories": {
    "CHARACTER":      { "maxResidentAssets": 20  },
    "INTERIOR_PROP":  { "maxResidentAssets": 100 },
    "PROP_SMALL":     { "maxResidentAssets": 50  },
    "PROP_LARGE":     { "maxResidentAssets": 30  },
    "FOLIAGE":        { "maxResidentAssets": 40  },
    "BUILDING":       { "maxResidentAssets": 20  },
    "VFX_MESH":       { "maxResidentAssets": 30  },
    "TEXTURE_ATLAS":  { "maxResidentAssets": 10  }
  }
}
```

- [ ] **Step 6.3: Create GalacticAssetManager**

```java
package com.galacticodyssey.data;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import net.mgsx.gltf.loaders.glb.GLBAssetLoader;
import net.mgsx.gltf.loaders.gltf.GLTFAssetLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class GalacticAssetManager implements Disposable {

    private static final int MAX_LOADS_PER_FRAME = 3;

    public record StreamingRadius(float prefetchRadius, float unloadRadius) {}

    private final AssetManager inner;
    private final StreamingQueue queue;
    private final Map<String, AssetManifest> manifests = new HashMap<>();
    private final Map<String, AssetHandle<SceneAsset>> handles = new HashMap<>();
    private final Map<String, String> assetIdToPath = new HashMap<>();
    private final Map<AssetCategory, StreamingRadius> streamingRadii = new EnumMap<>(AssetCategory.class);

    public GalacticAssetManager() {
        this.inner = new AssetManager();
        this.queue = new StreamingQueue();
        registerLoaders();
    }

    private void registerLoaders() {
        inner.setLoader(SceneAsset.class, ".gltf", new GLTFAssetLoader());
        inner.setLoader(SceneAsset.class, ".glb", new GLBAssetLoader());
    }

    /** Load a category manifest from a JsonValue (already parsed). */
    public void registerManifest(JsonValue root) {
        AssetManifest manifest = AssetManifest.fromJson(root);
        manifests.put(manifest.getCategory().name(), manifest);
        for (AssetManifest.Entry entry : manifest.getEntries()) {
            assetIdToPath.put(entry.id(), entry.path());
        }
    }

    /** Load streaming radii config from a JsonValue. */
    public void loadStreamingConfig(JsonValue root) {
        JsonValue categories = root.get("categories");
        for (AssetCategory cat : AssetCategory.values()) {
            JsonValue cfg = categories.get(cat.name());
            if (cfg != null) {
                streamingRadii.put(cat, new StreamingRadius(
                    cfg.getFloat("prefetchRadius"),
                    cfg.getFloat("unloadRadius")
                ));
            }
        }
    }

    /**
     * Request an asset to be loaded. Safe to call every frame — idempotent if already loading/loaded.
     * Returns the handle (not yet resident until update() completes loading).
     */
    public AssetHandle<SceneAsset> enqueue(String assetId, AssetCategory category, float distanceToCamera) {
        AssetHandle<SceneAsset> handle = handles.computeIfAbsent(assetId,
            id -> new AssetHandle<>(id, category, this::onHandleReleased));
        if (!handle.isResident() && !inner.isLoaded(assetIdToPath.getOrDefault(assetId, ""))) {
            queue.enqueue(assetId, category, distanceToCamera);
        }
        return handle.retain();
    }

    /**
     * Call once per frame on the GL thread. Dispatches pending load requests and
     * promotes any finished loads into their handles.
     */
    public void update() {
        int dispatched = 0;
        while (!queue.isEmpty() && dispatched < MAX_LOADS_PER_FRAME) {
            StreamingQueue.StreamRequest req = queue.poll();
            String path = assetIdToPath.get(req.assetId);
            if (path != null && !inner.isLoaded(path)) {
                inner.load(path, SceneAsset.class);
            }
            dispatched++;
        }
        inner.update();
        promoteLoadedAssets();
    }

    private void promoteLoadedAssets() {
        for (Map.Entry<String, AssetHandle<SceneAsset>> entry : handles.entrySet()) {
            AssetHandle<SceneAsset> handle = entry.getValue();
            if (!handle.isResident()) {
                String path = assetIdToPath.get(entry.getKey());
                if (path != null && inner.isLoaded(path)) {
                    handle.setAsset(inner.get(path, SceneAsset.class));
                }
            }
        }
    }

    private void onHandleReleased(AssetHandle<SceneAsset> handle) {
        String path = assetIdToPath.get(handle.getAssetId());
        if (path != null && inner.isLoaded(path)) {
            inner.unload(path);
        }
        handles.remove(handle.getAssetId());
    }

    public AssetHandle<SceneAsset> getHandle(String assetId) {
        return handles.get(assetId);
    }

    public StreamingRadius getStreamingRadius(AssetCategory category) {
        return streamingRadii.getOrDefault(category, new StreamingRadius(50f, 100f));
    }

    public boolean isLoading() {
        return !inner.isFinished();
    }

    @Override
    public void dispose() {
        inner.dispose();
    }
}
```

- [ ] **Step 6.4: Verify the project compiles**

```bash
./gradlew :core:compileJava
```

Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 6.5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/GalacticAssetManager.java \
        core/src/main/resources/data/assets/streaming_config.json \
        core/src/main/resources/data/assets/asset_budgets.json
git commit -m "feat(assets): add GalacticAssetManager wrapping libGDX AssetManager with gdx-gltf loaders"
```

---

## Task 7: StreamableComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/data/components/StreamableComponent.java`

- [ ] **Step 7.1: Create StreamableComponent**

```java
package com.galacticodyssey.data.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.data.AssetCategory;
import com.galacticodyssey.data.AssetHandle;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

public final class StreamableComponent implements Component {

    /** ID matching an entry in the category's manifest JSON. */
    public String assetId;

    public AssetCategory category;

    /** Non-null once the asset manager has started loading this asset. */
    public AssetHandle<SceneAsset> handle;

    public StreamableComponent() {}

    public StreamableComponent(String assetId, AssetCategory category) {
        this.assetId = assetId;
        this.category = category;
    }
}
```

- [ ] **Step 7.2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/components/StreamableComponent.java
git commit -m "feat(assets): add StreamableComponent for Ashley ECS"
```

---

## Task 8: StreamingSystem — distance-based prefetch/unload

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/data/systems/StreamingSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/data/StreamingSystemTest.java`

- [ ] **Step 8.1: Write failing tests**

The streaming system's core logic (distance comparison against radii) can be tested without GL by using a mock/stub `GalacticAssetManager` subclass.

```java
package com.galacticodyssey.data;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.data.components.StreamableComponent;
import com.galacticodyssey.data.systems.StreamingSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StreamingSystemTest {

    /** Minimal stub that records enqueue calls without needing GL. */
    static class StubAssetManager extends GalacticAssetManager {
        final List<String> enqueuedIds = new ArrayList<>();
        final List<String> releasedIds = new ArrayList<>();

        StubAssetManager() {
            super(/* skip GL init */ true);
        }

        @Override
        public AssetHandle<net.mgsx.gltf.scene3d.scene.SceneAsset> enqueue(
                String assetId, AssetCategory category, float distance) {
            enqueuedIds.add(assetId);
            @SuppressWarnings("unchecked")
            AssetHandle<net.mgsx.gltf.scene3d.scene.SceneAsset> handle =
                new AssetHandle<>(assetId, category, h -> releasedIds.add(h.getAssetId()));
            return handle.retain();
        }

        @Override
        public StreamingRadius getStreamingRadius(AssetCategory category) {
            return new StreamingRadius(50f, 100f);
        }
    }

    private Engine engine;
    private StubAssetManager manager;
    private StreamingSystem system;

    @BeforeEach
    void setUp() {
        manager = new StubAssetManager();
        system = new StreamingSystem(manager);
        engine = new Engine();
        engine.addSystem(system);
    }

    @Test
    void entityWithinPrefetchRadiusGetsEnqueued() {
        Entity entity = new Entity();
        StreamableComponent sc = new StreamableComponent("char1", AssetCategory.CHARACTER);
        entity.add(sc);
        engine.addEntity(entity);

        system.setCameraPosition(new Vector3(0, 0, 0));
        // Place entity at 30m — within CHARACTER prefetch radius of 50m
        entity.add(createTransform(30f, 0f, 0f));
        engine.update(0.016f);

        assertTrue(manager.enqueuedIds.contains("char1"));
    }

    @Test
    void entityBeyondPrefetchRadiusNotEnqueued() {
        Entity entity = new Entity();
        entity.add(new StreamableComponent("char2", AssetCategory.CHARACTER));
        engine.addEntity(entity);

        system.setCameraPosition(new Vector3(0, 0, 0));
        entity.add(createTransform(200f, 0f, 0f)); // beyond 50m prefetch radius
        engine.update(0.016f);

        assertFalse(manager.enqueuedIds.contains("char2"));
    }

    private com.badlogic.ashley.core.Component createTransform(float x, float y, float z) {
        com.galacticodyssey.core.components.TransformComponent tc =
            new com.galacticodyssey.core.components.TransformComponent();
        tc.position.set(x, y, z);
        return tc;
    }
}
```

- [ ] **Step 8.2: Add test-only constructor to GalacticAssetManager**

Open `GalacticAssetManager.java`. Add a protected constructor that skips GL initialisation for testing:

```java
/** Test-only constructor: skips GL-dependent initialisation. */
protected GalacticAssetManager(boolean skipGlInit) {
    this.inner = skipGlInit ? null : new AssetManager();
    this.queue = new StreamingQueue();
    if (!skipGlInit) registerLoaders();
}
```

- [ ] **Step 8.3: Run tests, confirm compilation fails**

```bash
./gradlew :core:test --tests "com.galacticodyssey.data.StreamingSystemTest" --info
```

Expected: compilation failure — `StreamingSystem` does not exist yet.

- [ ] **Step 8.4: Create StreamingSystem**

```java
package com.galacticodyssey.data.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.GalacticAssetManager;
import com.galacticodyssey.data.components.StreamableComponent;

public final class StreamingSystem extends IteratingSystem {

    private static final ComponentMapper<StreamableComponent> STREAM_MAP =
        ComponentMapper.getFor(StreamableComponent.class);
    private static final ComponentMapper<TransformComponent> XFORM_MAP =
        ComponentMapper.getFor(TransformComponent.class);

    private final GalacticAssetManager assetManager;
    private final Vector3 cameraPosition = new Vector3();

    public StreamingSystem(GalacticAssetManager assetManager) {
        super(Family.all(StreamableComponent.class, TransformComponent.class).get());
        this.assetManager = assetManager;
    }

    public void setCameraPosition(Vector3 position) {
        cameraPosition.set(position);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        StreamableComponent sc = STREAM_MAP.get(entity);
        TransformComponent xform = XFORM_MAP.get(entity);

        float distance = cameraPosition.dst(xform.position);
        GalacticAssetManager.StreamingRadius radii = assetManager.getStreamingRadius(sc.category);

        if (distance < radii.prefetchRadius()) {
            if (sc.handle == null) {
                sc.handle = assetManager.enqueue(sc.assetId, sc.category, distance);
            }
        } else if (distance > radii.unloadRadius() && sc.handle != null) {
            sc.handle.release();
            sc.handle = null;
        }
    }
}
```

- [ ] **Step 8.5: Run tests, confirm they pass**

```bash
./gradlew :core:test --tests "com.galacticodyssey.data.StreamingSystemTest" --info
```

Expected: 2 tests, all PASS.

- [ ] **Step 8.6: Run all tests to check for regressions**

```bash
./gradlew :core:test
```

Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 8.7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/systems/StreamingSystem.java \
        core/src/test/java/com/galacticodyssey/data/StreamingSystemTest.java \
        core/src/main/java/com/galacticodyssey/data/GalacticAssetManager.java
git commit -m "feat(assets): add StreamingSystem - distance-based enqueue/release"
```

---

## Task 9: Wire into GameWorld + Verify

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java` (or wherever ECS systems are registered — check the file that calls `engine.addSystem(...)`)

- [ ] **Step 9.1: Find the system registration file**

```bash
grep -rl "addSystem" core/src/main/java/com/galacticodyssey/
```

Identify the file that registers Ashley systems (likely `GameWorld.java` or `GameScreen.java`).

- [ ] **Step 9.2: Instantiate and register GalacticAssetManager and StreamingSystem**

In the identified file, add the following in the constructor or `create()` method alongside other system registrations:

```java
// --- Asset streaming ---
GalacticAssetManager assetManager = new GalacticAssetManager();

// Load manifests
JsonReader jsonReader = new JsonReader();
assetManager.registerManifest(jsonReader.parse(
    Gdx.files.internal("data/assets/characters.json")));
assetManager.registerManifest(jsonReader.parse(
    Gdx.files.internal("data/assets/props.json")));
assetManager.loadStreamingConfig(jsonReader.parse(
    Gdx.files.internal("data/assets/streaming_config.json")));

StreamingSystem streamingSystem = new StreamingSystem(assetManager);
engine.addSystem(streamingSystem);
```

In the game loop `update()` or `render()` method, add before engine update:
```java
assetManager.update(); // drain streaming queue, promote loaded assets
streamingSystem.setCameraPosition(/* camera world position as Vector3 */);
```

In `dispose()`:
```java
assetManager.dispose();
```

- [ ] **Step 9.3: Verify the game still launches**

```bash
./gradlew :desktop:run
```

Expected: game launches without exceptions. No visual changes yet — no glTF models exist in resources. The system runs silently in the background.

- [ ] **Step 9.4: Commit**

```bash
git add -p  # stage only the GameWorld/GameScreen changes
git commit -m "feat(assets): wire GalacticAssetManager and StreamingSystem into game loop"
```

---

## Verification

**End-to-end test (manual, requires an actual .glb file):**

1. Place any valid `.glb` file (e.g. a free CC0 model from KhronosGroup/glTF-Sample-Assets) at `core/src/main/resources/models/characters/human_player.glb`
2. Spawn a test entity with `StreamableComponent("human_player", AssetCategory.CHARACTER)` and `TransformComponent` at `(0, 0, -20)` in the scene
3. Run the game. Walk the player camera to within 80m of the entity's position.
4. Set a breakpoint or add a log in `GalacticAssetManager.promoteLoadedAssets()` — confirm `handle.setAsset(...)` is called without a GL thread hitch.
5. Confirm `handle.isResident()` returns `true` after a few frames.
6. Walk beyond 100m — confirm `handle.release()` is triggered and the asset is eventually unloaded from `inner`.
