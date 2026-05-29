# Creature Generation Core (Cycle A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From a seed, deterministically assemble a modular creature (4 body-plan archetypes) into a GL-free `CreatureSpec`, build it into a renderable static-pose entity, and expose a debug spawn.

**Architecture:** Socket-graph assembly. Parts (`CreaturePartDef`) carry named `Socket`s; a `BodyPlanArchetypeDef` is an explicit attachment tree that places parts at sockets, with mirror symmetry and a `repeat`-chain for spines. A pure-data `CreatureSpec` is produced headless and unit-tested; a thin GL layer (`ProceduralPartProvider`/`CreatureMeshBuilder`) turns it into a libGDX `Model`. Part geometry comes from either a procedural mesher or an authored `.g3db`, behind one interface.

**Tech Stack:** Java 17, libGDX (gdx-math, `MeshBuilder`/`ModelBuilder`, `JsonReader`), Ashley ECS, JUnit 5 + gdx-backend-headless. New package `com.galacticodyssey.fauna`.

---

## File Structure

**New — headless logic (unit-tested):**
- `core/.../galaxy/SeedDeriver.java` *(modify: add `FAUNA_DOMAIN`)*
- `core/.../fauna/part/PartType.java` — enum
- `core/.../fauna/archetype/BodyPlan.java` — enum
- `core/.../fauna/part/Socket.java`
- `core/.../fauna/geometry/PartGeometrySpec.java`
- `core/.../fauna/part/CreaturePartDef.java`
- `core/.../fauna/archetype/AttachmentNode.java`
- `core/.../fauna/archetype/BodyPlanArchetypeDef.java`
- `core/.../data/FaunaDataRegistry.java` — JSON load + validation
- `core/.../fauna/assembly/AssembledNode.java`
- `core/.../fauna/CreatureSpec.java`
- `core/.../fauna/geometry/ProceduralMeshData.java`
- `core/.../fauna/geometry/ProceduralPartMesher.java`
- `core/.../fauna/stats/MassStatModel.java`
- `core/.../fauna/assembly/CreatureAssembler.java`
- `core/.../fauna/CreatureGenerator.java` — seed → CreatureSpec facade
- `core/.../fauna/components/CreatureComponent.java`
- `core/.../fauna/components/CreatureRenderComponent.java`
- `core/.../fauna/CreatureFactory.java` — CreatureSpec → Ashley entity (logical)

**New — GL layer (thin, not CI-tested):**
- `core/.../fauna/geometry/PartGeometryProvider.java` — interface
- `core/.../fauna/geometry/ProceduralPartProvider.java`
- `core/.../fauna/geometry/AuthoredPartProvider.java`
- `core/.../fauna/CreatureMeshBuilder.java`
- `core/.../fauna/FaunaDebugSpawner.java`

**New — content:**
- `core/src/main/resources/data/fauna/parts/default-parts.json`
- `core/src/main/resources/data/fauna/archetypes/default-archetypes.json`

**Modify:**
- `core/.../ui/GameScreen.java` *(add debug keybind near existing F5 handler ~line 404)*

**Tests:** mirror packages under `core/src/test/java/com/galacticodyssey/fauna/...`

---

## Task 1: Add FAUNA seed domain

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java`
- Test: `core/src/test/java/com/galacticodyssey/galaxy/SeedDeriverFaunaTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedDeriverFaunaTest {
    @Test
    void faunaDomainIsStableAndDistinct() {
        long a = SeedDeriver.faunaDomain(12345L);
        long b = SeedDeriver.faunaDomain(12345L);
        assertEquals(a, b, "same input must yield same domain");
        assertNotEquals(SeedDeriver.faunaDomain(1L), SeedDeriver.faunaDomain(2L));
        // distinct from an existing domain for the same parent seed
        assertNotEquals(SeedDeriver.domain(12345L, SeedDeriver.NPC_DOMAIN),
                        SeedDeriver.faunaDomain(12345L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.SeedDeriverFaunaTest"`
Expected: FAIL — `faunaDomain`/`FAUNA_DOMAIN` not defined (compile error).

- [ ] **Step 3: Add the domain constant and helper**

In `SeedDeriver.java`, add a constant alongside the others (after `EROSION_DOMAIN`):

```java
    public static final long FAUNA_DOMAIN          = 0x6A09E667F3BCC909L;
```

And a helper alongside `npcDomain`:

```java
    public static long faunaDomain(long parentSeed) {
        return domain(parentSeed, FAUNA_DOMAIN);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.SeedDeriverFaunaTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java core/src/test/java/com/galacticodyssey/galaxy/SeedDeriverFaunaTest.java
git commit -m "feat(fauna): add FAUNA seed domain to SeedDeriver"
```

---

## Task 2: Core enums (PartType, BodyPlan)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/part/PartType.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/archetype/BodyPlan.java`

No test (pure enums exercised by later tasks).

- [ ] **Step 1: Create `PartType`**

```java
package com.galacticodyssey.fauna.part;

/** Category of a creature body part. Extended in later cycles (wings, fins, antennae). */
public enum PartType {
    TORSO, HEAD, NECK, LIMB_LEG, LIMB_ARM, TAIL
}
```

- [ ] **Step 2: Create `BodyPlan`**

```java
package com.galacticodyssey.fauna.archetype;

/** High-level topology family. Cycle A ships the first four. */
public enum BodyPlan {
    BIPEDAL, QUADRUPED, HEXAPOD, SERPENTINE
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/part/PartType.java core/src/main/java/com/galacticodyssey/fauna/archetype/BodyPlan.java
git commit -m "feat(fauna): add PartType and BodyPlan enums"
```

---

## Task 3: Part data model (Socket, PartGeometrySpec, CreaturePartDef)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/geometry/PartGeometrySpec.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/part/Socket.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/part/CreaturePartDef.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/part/CreaturePartDefTest.java`

- [ ] **Step 1: Create `PartGeometrySpec`**

```java
package com.galacticodyssey.fauna.geometry;

/** How a part's mesh is produced — procedural primitive params, or an authored model reference. */
public final class PartGeometrySpec {
    public enum Kind { PROCEDURAL, AUTHORED }
    public enum Shape { CAPSULE, LOFT, ELLIPSOID_SNOUT, CONE }

    public Kind kind = Kind.PROCEDURAL;

    // Procedural params (kind == PROCEDURAL)
    public Shape shape = Shape.CAPSULE;
    public float length = 1f;     // along local +Z
    public float radius = 0.25f;  // base radius
    public float taper = 1f;      // tip radius / base radius

    // Authored (kind == AUTHORED)
    public String modelRef = null; // path to a .g3db node; null when procedural

    /** Approximate volume in m^3 at unit scale (used for mass). */
    public float approxVolume() {
        switch (shape) {
            case CONE:           return (float) (Math.PI * radius * radius * length / 3.0);
            case ELLIPSOID_SNOUT:return (float) (4.0 / 3.0 * Math.PI * radius * radius * (length * 0.5));
            case LOFT:
            case CAPSULE:
            default:
                float avgR = radius * (1f + taper) * 0.5f;
                return (float) (Math.PI * avgR * avgR * length); // cylinder approx
        }
    }
}
```

- [ ] **Step 2: Create `Socket`**

```java
package com.galacticodyssey.fauna.part;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/** A named attachment point on a part: local transform, accepted child type, symmetry & joint hints. */
public final class Socket {
    public String id;
    public PartType acceptedType;
    public final Vector3 localPosition = new Vector3();
    public final Quaternion localRotation = new Quaternion();
    /** Sockets sharing a non-null group are mirrored as a left/right pair by the assembler. */
    public String mirrorGroup = null;
    /** Joint metadata consumed by Cycle B's rig; ignored in Cycle A. */
    public String jointHint = null;
}
```

- [ ] **Step 3: Create `CreaturePartDef`**

```java
package com.galacticodyssey.fauna.part;

import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.fauna.geometry.PartGeometrySpec;

import java.util.ArrayList;
import java.util.List;

/** A mesh-bearing node in the socket graph: its type, sockets, geometry, scale band, and eligibility. */
public final class CreaturePartDef {
    public String id;
    public PartType partType;
    public final List<Socket> sockets = new ArrayList<>();
    public PartGeometrySpec geometry = new PartGeometrySpec();
    public float minScale = 1f;
    public float maxScale = 1f;
    /** Body plans allowed to use this part. Empty = usable by any plan. */
    public final List<BodyPlan> bodyPlans = new ArrayList<>();

    public Socket findSocket(String socketId) {
        for (Socket s : sockets) if (s.id.equals(socketId)) return s;
        return null;
    }
}
```

- [ ] **Step 4: Write the failing test**

```java
package com.galacticodyssey.fauna.part;

import com.galacticodyssey.fauna.geometry.PartGeometrySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreaturePartDefTest {
    @Test
    void findsSocketByIdAndComputesVolume() {
        CreaturePartDef torso = new CreaturePartDef();
        torso.id = "torso_a";
        torso.partType = PartType.TORSO;
        Socket s = new Socket();
        s.id = "leg_front";
        s.acceptedType = PartType.LIMB_LEG;
        s.localPosition.set(0.3f, 0f, 0.4f);
        torso.sockets.add(s);

        assertSame(s, torso.findSocket("leg_front"));
        assertNull(torso.findSocket("nope"));

        PartGeometrySpec g = torso.geometry;
        g.shape = PartGeometrySpec.Shape.CAPSULE;
        g.length = 2f; g.radius = 0.5f; g.taper = 1f;
        assertTrue(g.approxVolume() > 0f, "volume must be positive");
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.part.CreaturePartDefTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/geometry/PartGeometrySpec.java core/src/main/java/com/galacticodyssey/fauna/part/Socket.java core/src/main/java/com/galacticodyssey/fauna/part/CreaturePartDef.java core/src/test/java/com/galacticodyssey/fauna/part/CreaturePartDefTest.java
git commit -m "feat(fauna): add Socket, PartGeometrySpec, CreaturePartDef data model"
```

---

## Task 4: Archetype data model (AttachmentNode, BodyPlanArchetypeDef)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/archetype/AttachmentNode.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/archetype/BodyPlanArchetypeDef.java`

No standalone test (covered by registry + assembler tasks).

- [ ] **Step 1: Create `AttachmentNode`**

```java
package com.galacticodyssey.fauna.archetype;

import com.galacticodyssey.fauna.part.PartType;

import java.util.ArrayList;
import java.util.List;

/**
 * One node in an archetype's attachment tree. Places a part of {@link #partType} at the parent
 * part's socket {@link #socketId}. The root node has a null socketId (it IS the torso root).
 */
public final class AttachmentNode {
    /** Socket id on the PARENT part to attach to; null/empty only for the root node. */
    public String socketId = null;
    public PartType partType;
    /** If true, also place a mirrored copy of this part (and its subtree) across the YZ plane. */
    public boolean mirror = false;
    /**
     * Chain this many copies of the part end-to-end (>=1). Copy i+1 attaches to copy i's
     * {@link #continuationSocketId}. Used for serpentine spines. Children attach to the LAST copy.
     */
    public int repeat = 1;
    /** Socket id (on this part) used to chain repeats; required when repeat > 1. */
    public String continuationSocketId = null;
    public final List<AttachmentNode> children = new ArrayList<>();
}
```

- [ ] **Step 2: Create `BodyPlanArchetypeDef`**

```java
package com.galacticodyssey.fauna.archetype;

/** Template for one body plan: the attachment tree, size/mass band, stat constants, gait metadata. */
public final class BodyPlanArchetypeDef {
    public String id;
    public BodyPlan bodyPlan;
    public AttachmentNode root;            // root.partType is the torso/root part type

    public float minSize = 1f;             // overall size multiplier band (mouse .. megafauna)
    public float maxSize = 1f;
    public float density = 1000f;          // kg/m^3 for mass calc

    public String gaitClass = "walk";      // metadata for Cycle B

    // Allometric stat constants (tunable). HP = kHp*m^(2/3), speed = kSpeed*m^(-1/4), dmg = kDmg*m^(3/4)
    public float kHp = 12f;
    public float kSpeed = 9f;
    public float kDamage = 4f;
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/archetype/AttachmentNode.java core/src/main/java/com/galacticodyssey/fauna/archetype/BodyPlanArchetypeDef.java
git commit -m "feat(fauna): add AttachmentNode and BodyPlanArchetypeDef"
```

---

## Task 5: FaunaDataRegistry (JSON load + validation)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/data/FaunaDataRegistry.java`
- Test: `core/src/test/java/com/galacticodyssey/data/FaunaDataRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.data;

import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;
import com.galacticodyssey.fauna.part.CreaturePartDef;
import com.galacticodyssey.fauna.part.PartType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FaunaDataRegistryTest {

    private static final String PARTS = "{ \"parts\": [" +
        "{ \"id\":\"torso_quad\", \"partType\":\"TORSO\", \"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
        "  \"sockets\":[ {\"id\":\"leg_f\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,0,0.6],\"mirrorGroup\":\"legs\"}," +
        "                {\"id\":\"head\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0]} ] }," +
        "{ \"id\":\"leg_a\", \"partType\":\"LIMB_LEG\", \"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
        "{ \"id\":\"head_a\", \"partType\":\"HEAD\", \"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
        "] }";

    private static final String ARCHES = "{ \"archetypes\": [" +
        "{ \"id\":\"quad_grazer\", \"bodyPlan\":\"QUADRUPED\", \"minSize\":0.5,\"maxSize\":3,\"density\":900," +
        "  \"root\":{ \"partType\":\"TORSO\", \"children\":[" +
        "     {\"socketId\":\"leg_f\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
        "     {\"socketId\":\"head\",\"partType\":\"HEAD\"} ] } }" +
        "] }";

    @Test
    void loadsPartsAndArchetypes() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson(PARTS);
        reg.loadArchetypesFromJson(ARCHES);

        CreaturePartDef torso = reg.getPart("torso_quad");
        assertNotNull(torso);
        assertEquals(PartType.TORSO, torso.partType);
        assertEquals(2, torso.sockets.size());
        assertEquals("legs", torso.findSocket("leg_f").mirrorGroup);
        assertEquals(0.6f, torso.findSocket("leg_f").localPosition.z, 1e-4);

        BodyPlanArchetypeDef arch = reg.getArchetype("quad_grazer");
        assertNotNull(arch);
        assertEquals(BodyPlan.QUADRUPED, arch.bodyPlan);
        assertEquals(PartType.TORSO, arch.root.partType);
        assertEquals(2, arch.root.children.size());
        assertTrue(arch.root.children.get(0).mirror);
    }

    @Test
    void validationRejectsArchetypeReferencingMissingPartType() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson(PARTS);
        // TAIL has no eligible part in the library
        String bad = "{ \"archetypes\":[ {\"id\":\"x\",\"bodyPlan\":\"QUADRUPED\"," +
            "\"root\":{\"partType\":\"TORSO\",\"children\":[{\"socketId\":\"head\",\"partType\":\"TAIL\"}]}} ] }";
        reg.loadArchetypesFromJson(bad);
        IllegalStateException ex = assertThrows(IllegalStateException.class, reg::validate);
        assertTrue(ex.getMessage().contains("TAIL"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.data.FaunaDataRegistryTest"`
Expected: FAIL — `FaunaDataRegistry` not defined.

- [ ] **Step 3: Implement `FaunaDataRegistry`**

```java
package com.galacticodyssey.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.fauna.archetype.AttachmentNode;
import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;
import com.galacticodyssey.fauna.geometry.PartGeometrySpec;
import com.galacticodyssey.fauna.part.CreaturePartDef;
import com.galacticodyssey.fauna.part.PartType;
import com.galacticodyssey.fauna.part.Socket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads and validates creature part + archetype definitions from JSON. */
public class FaunaDataRegistry {
    private final Map<String, CreaturePartDef> parts = new HashMap<>();
    private final Map<String, BodyPlanArchetypeDef> archetypes = new HashMap<>();

    public void loadParts(String path)      { loadPartsFromJson(Gdx.files.internal(path).readString()); }
    public void loadArchetypes(String path) { loadArchetypesFromJson(Gdx.files.internal(path).readString()); }

    public void loadPartsFromJson(String json) {
        JsonValue arr = new JsonReader().parse(json).get("parts");
        for (JsonValue e = arr.child; e != null; e = e.next) {
            CreaturePartDef p = new CreaturePartDef();
            p.id = e.getString("id");
            p.partType = PartType.valueOf(e.getString("partType"));
            p.minScale = e.getFloat("minScale", 1f);
            p.maxScale = e.getFloat("maxScale", 1f);
            JsonValue plans = e.get("bodyPlans");
            if (plans != null) for (JsonValue pl = plans.child; pl != null; pl = pl.next)
                p.bodyPlans.add(BodyPlan.valueOf(pl.asString()));
            p.geometry = parseGeometry(e.get("geometry"));
            JsonValue socks = e.get("sockets");
            if (socks != null) for (JsonValue s = socks.child; s != null; s = s.next) {
                Socket sock = new Socket();
                sock.id = s.getString("id");
                sock.acceptedType = PartType.valueOf(s.getString("acceptedType"));
                float[] pos = s.get("pos") != null ? s.get("pos").asFloatArray() : new float[]{0,0,0};
                sock.localPosition.set(pos[0], pos[1], pos[2]);
                float[] rot = s.get("rot") != null ? s.get("rot").asFloatArray() : null;
                if (rot != null) sock.localRotation.set(rot[0], rot[1], rot[2], rot[3]);
                sock.mirrorGroup = s.getString("mirrorGroup", null);
                sock.jointHint = s.getString("jointHint", null);
                p.sockets.add(sock);
            }
            parts.put(p.id, p);
        }
    }

    private PartGeometrySpec parseGeometry(JsonValue g) {
        PartGeometrySpec spec = new PartGeometrySpec();
        if (g == null) return spec;
        spec.kind = PartGeometrySpec.Kind.valueOf(g.getString("kind", "PROCEDURAL"));
        if (spec.kind == PartGeometrySpec.Kind.AUTHORED) {
            spec.modelRef = g.getString("modelRef", null);
        } else {
            spec.shape = PartGeometrySpec.Shape.valueOf(g.getString("shape", "CAPSULE"));
            spec.length = g.getFloat("length", 1f);
            spec.radius = g.getFloat("radius", 0.25f);
            spec.taper = g.getFloat("taper", 1f);
        }
        return spec;
    }

    public void loadArchetypesFromJson(String json) {
        JsonValue arr = new JsonReader().parse(json).get("archetypes");
        for (JsonValue e = arr.child; e != null; e = e.next) {
            BodyPlanArchetypeDef a = new BodyPlanArchetypeDef();
            a.id = e.getString("id");
            a.bodyPlan = BodyPlan.valueOf(e.getString("bodyPlan"));
            a.minSize = e.getFloat("minSize", 1f);
            a.maxSize = e.getFloat("maxSize", 1f);
            a.density = e.getFloat("density", 1000f);
            a.gaitClass = e.getString("gaitClass", "walk");
            a.kHp = e.getFloat("kHp", 12f);
            a.kSpeed = e.getFloat("kSpeed", 9f);
            a.kDamage = e.getFloat("kDamage", 4f);
            a.root = parseAttachment(e.get("root"));
            archetypes.put(a.id, a);
        }
    }

    private AttachmentNode parseAttachment(JsonValue n) {
        AttachmentNode node = new AttachmentNode();
        node.socketId = n.getString("socketId", null);
        node.partType = PartType.valueOf(n.getString("partType"));
        node.mirror = n.getBoolean("mirror", false);
        node.repeat = n.getInt("repeat", 1);
        node.continuationSocketId = n.getString("continuationSocketId", null);
        JsonValue kids = n.get("children");
        if (kids != null) for (JsonValue c = kids.child; c != null; c = c.next)
            node.children.add(parseAttachment(c));
        return node;
    }

    /** Throws IllegalStateException if any archetype references a part type with no eligible part. */
    public void validate() {
        Set<PartType> available = new HashSet<>();
        for (CreaturePartDef p : parts.values()) available.add(p.partType);
        for (BodyPlanArchetypeDef a : archetypes.values()) checkNode(a, a.root, available);
    }

    private void checkNode(BodyPlanArchetypeDef a, AttachmentNode node, Set<PartType> available) {
        if (!available.contains(node.partType))
            throw new IllegalStateException("Archetype '" + a.id + "' needs a part of type "
                + node.partType + " but none exists in the library");
        if (node.repeat > 1 && node.continuationSocketId == null)
            throw new IllegalStateException("Archetype '" + a.id + "' node with repeat>1 needs continuationSocketId");
        for (AttachmentNode c : node.children) checkNode(a, c, available);
    }

    /** Parts of a type eligible for a body plan (bodyPlans empty = any), in stable id order. */
    public List<CreaturePartDef> partsFor(PartType type, BodyPlan plan) {
        List<CreaturePartDef> out = new ArrayList<>();
        for (CreaturePartDef p : parts.values())
            if (p.partType == type && (p.bodyPlans.isEmpty() || p.bodyPlans.contains(plan)))
                out.add(p);
        out.sort((x, y) -> x.id.compareTo(y.id)); // determinism: never depend on map order
        return out;
    }

    public CreaturePartDef getPart(String id)            { return parts.get(id); }
    public BodyPlanArchetypeDef getArchetype(String id)  { return archetypes.get(id); }
    public java.util.Collection<BodyPlanArchetypeDef> allArchetypes() { return archetypes.values(); }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.data.FaunaDataRegistryTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/FaunaDataRegistry.java core/src/test/java/com/galacticodyssey/data/FaunaDataRegistryTest.java
git commit -m "feat(fauna): add FaunaDataRegistry with JSON load and validation"
```

---

## Task 6: Assembly output model (AssembledNode, CreatureSpec)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/assembly/AssembledNode.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/CreatureSpec.java`

No standalone test (exercised by the assembler task).

- [ ] **Step 1: Create `AssembledNode`**

```java
package com.galacticodyssey.fauna.assembly;

import com.badlogic.gdx.math.Matrix4;
import com.galacticodyssey.fauna.part.CreaturePartDef;

import java.util.ArrayList;
import java.util.List;

/** A placed part instance in the assembled creature tree. */
public final class AssembledNode {
    public CreaturePartDef part;
    public final Matrix4 localTransform = new Matrix4();  // relative to parent node
    public final Matrix4 worldTransform = new Matrix4();  // relative to creature root
    public float scale = 1f;
    public boolean mirrored = false;
    public final List<AssembledNode> children = new ArrayList<>();
}
```

- [ ] **Step 2: Create `CreatureSpec`**

```java
package com.galacticodyssey.fauna;

import com.badlogic.gdx.math.collision.BoundingBox;
import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.fauna.assembly.AssembledNode;

import java.util.ArrayList;
import java.util.List;

/** Deterministic, GL-free blueprint of a fully assembled creature. */
public final class CreatureSpec {
    public long seed;
    public String archetypeId;
    public BodyPlan bodyPlan;
    public AssembledNode root;
    public final List<AssembledNode> allNodes = new ArrayList<>(); // flattened, root first

    public float sizeMultiplier = 1f;
    public float mass;        // kg
    public float maxHP;
    public float moveSpeed;   // m/s
    public float meleeDamage;
    public long colorSeed;    // drives flat biome tint now; full patterns in Cycle C

    public final BoundingBox bounds = new BoundingBox();

    public int partCount() { return allNodes.size(); }

    public int countOfType(com.galacticodyssey.fauna.part.PartType type) {
        int n = 0;
        for (AssembledNode node : allNodes) if (node.part.partType == type) n++;
        return n;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/assembly/AssembledNode.java core/src/main/java/com/galacticodyssey/fauna/CreatureSpec.java
git commit -m "feat(fauna): add AssembledNode and CreatureSpec output model"
```

---

## Task 7: Procedural part geometry (headless ProceduralMeshData + ProceduralPartMesher)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/geometry/ProceduralMeshData.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/geometry/ProceduralPartMesher.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/geometry/ProceduralPartMesherTest.java`

This keeps all geometry MATH headless-testable; only the GL upload (Task 13) needs a context.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna.geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProceduralPartMesherTest {
    @Test
    void capsuleProducesNonEmptyMeshWithinExpectedBounds() {
        PartGeometrySpec spec = new PartGeometrySpec();
        spec.shape = PartGeometrySpec.Shape.CAPSULE;
        spec.length = 2f; spec.radius = 0.5f; spec.taper = 1f;

        ProceduralMeshData m = new ProceduralPartMesher().build(spec);

        assertTrue(m.positionCount() >= 8, "expected a non-trivial mesh");
        assertEquals(0, m.indices.length % 3, "indices must be whole triangles");
        // length runs along +Z from 0..length; radius bounds X/Y
        assertTrue(m.maxZ() <= 2.001f && m.minZ() >= -0.001f, "Z within [0,length]");
        assertTrue(m.maxAbsXY() <= 0.501f, "XY within radius");
    }

    @Test
    void isDeterministic() {
        PartGeometrySpec spec = new PartGeometrySpec();
        ProceduralMeshData a = new ProceduralPartMesher().build(spec);
        ProceduralMeshData b = new ProceduralPartMesher().build(spec);
        assertArrayEquals(a.positions, b.positions, 1e-6f);
        assertArrayEquals(a.indices, b.indices);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.geometry.ProceduralPartMesherTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Create `ProceduralMeshData`**

```java
package com.galacticodyssey.fauna.geometry;

/** GL-free triangle mesh: flat position array (x,y,z triples) + triangle indices. */
public final class ProceduralMeshData {
    public final float[] positions; // length = vertexCount * 3
    public final short[] indices;

    public ProceduralMeshData(float[] positions, short[] indices) {
        this.positions = positions;
        this.indices = indices;
    }

    public int positionCount() { return positions.length / 3; }

    public float maxZ() { float m = -Float.MAX_VALUE; for (int i = 2; i < positions.length; i += 3) m = Math.max(m, positions[i]); return m; }
    public float minZ() { float m =  Float.MAX_VALUE; for (int i = 2; i < positions.length; i += 3) m = Math.min(m, positions[i]); return m; }
    public float maxAbsXY() {
        float m = 0f;
        for (int i = 0; i < positions.length; i += 3) { m = Math.max(m, Math.abs(positions[i])); m = Math.max(m, Math.abs(positions[i+1])); }
        return m;
    }
}
```

- [ ] **Step 4: Create `ProceduralPartMesher`**

```java
package com.galacticodyssey.fauna.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds simple, deterministic procedural part meshes (math only, no GL). A part runs along its
 * local +Z axis from 0..length; radius is interpolated base->tip by {@code taper}.
 */
public final class ProceduralPartMesher {
    private static final int RADIAL = 8;   // segments around the axis
    private static final int RINGS  = 4;   // rings along the axis

    public ProceduralMeshData build(PartGeometrySpec spec) {
        List<Float> pos = new ArrayList<>();
        List<Short> idx = new ArrayList<>();

        for (int ring = 0; ring <= RINGS; ring++) {
            float t = ring / (float) RINGS;
            float z = t * spec.length;
            float r = spec.radius * (1f + (spec.taper - 1f) * t);
            // ELLIPSOID_SNOUT bulges in the middle then tapers; CONE tapers to a point
            if (spec.shape == PartGeometrySpec.Shape.ELLIPSOID_SNOUT)
                r = spec.radius * (float) Math.sin(Math.PI * Math.max(0.05f, t));
            else if (spec.shape == PartGeometrySpec.Shape.CONE)
                r = spec.radius * (1f - t);
            for (int s = 0; s < RADIAL; s++) {
                double a = 2.0 * Math.PI * s / RADIAL;
                pos.add((float) (Math.cos(a) * r));
                pos.add((float) (Math.sin(a) * r));
                pos.add(z);
            }
        }
        for (int ring = 0; ring < RINGS; ring++) {
            for (int s = 0; s < RADIAL; s++) {
                int s2 = (s + 1) % RADIAL;
                short a = (short) (ring * RADIAL + s);
                short b = (short) (ring * RADIAL + s2);
                short c = (short) ((ring + 1) * RADIAL + s);
                short d = (short) ((ring + 1) * RADIAL + s2);
                idx.add(a); idx.add(c); idx.add(b);
                idx.add(b); idx.add(c); idx.add(d);
            }
        }

        float[] p = new float[pos.size()];
        for (int i = 0; i < p.length; i++) p[i] = pos.get(i);
        short[] ix = new short[idx.size()];
        for (int i = 0; i < ix.length; i++) ix[i] = idx.get(i);
        return new ProceduralMeshData(p, ix);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.geometry.ProceduralPartMesherTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/geometry/ProceduralMeshData.java core/src/main/java/com/galacticodyssey/fauna/geometry/ProceduralPartMesher.java core/src/test/java/com/galacticodyssey/fauna/geometry/ProceduralPartMesherTest.java
git commit -m "feat(fauna): add headless procedural part mesher"
```

---

## Task 8: MassStatModel

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/stats/MassStatModel.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/stats/MassStatModelTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna.stats;

import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MassStatModelTest {
    private BodyPlanArchetypeDef arch() {
        BodyPlanArchetypeDef a = new BodyPlanArchetypeDef();
        a.density = 1000f; a.kHp = 12f; a.kSpeed = 9f; a.kDamage = 4f;
        return a;
    }

    @Test
    void massIsVolumeTimesDensity() {
        MassStatModel m = new MassStatModel();
        assertEquals(2000f, m.mass(2f, 1000f), 1e-3f);
    }

    @Test
    void biggerMassMeansMoreHpMoreDamageLessSpeed() {
        MassStatModel m = new MassStatModel();
        BodyPlanArchetypeDef a = arch();
        float[] small = m.deriveStats(10f, a);   // [hp, speed, dmg]
        float[] big   = m.deriveStats(1000f, a);
        assertTrue(big[0] > small[0], "HP grows with mass");
        assertTrue(big[2] > small[2], "damage grows with mass");
        assertTrue(big[1] < small[1], "speed falls with mass");
    }

    @Test
    void statsAreClampedToSaneRanges() {
        MassStatModel m = new MassStatModel();
        float[] tiny = m.deriveStats(0.0001f, arch());
        assertTrue(tiny[0] >= 1f, "HP floor");
        assertTrue(tiny[1] <= 30f, "speed ceiling");
        float[] huge = m.deriveStats(1e9f, arch());
        assertTrue(huge[1] >= 0.5f, "speed floor");
        assertTrue(huge[0] <= 100000f, "HP ceiling");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.stats.MassStatModelTest"`
Expected: FAIL — `MassStatModel` not defined.

- [ ] **Step 3: Implement `MassStatModel`**

```java
package com.galacticodyssey.fauna.stats;

import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;

/** Derives combat stats from creature mass using allometric scaling. */
public final class MassStatModel {

    public float mass(float volume, float density) { return volume * density; }

    /** Returns {maxHP, moveSpeed, meleeDamage}, clamped to sane ranges. */
    public float[] deriveStats(float mass, BodyPlanArchetypeDef a) {
        float hp    = clamp(a.kHp     * (float) Math.pow(mass,  2.0 / 3.0), 1f, 100000f);
        float speed = clamp(a.kSpeed  * (float) Math.pow(mass, -0.25),      0.5f, 30f);
        float dmg   = clamp(a.kDamage * (float) Math.pow(mass,  0.75),      1f, 10000f);
        return new float[]{hp, speed, dmg};
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.stats.MassStatModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/stats/MassStatModel.java core/src/test/java/com/galacticodyssey/fauna/stats/MassStatModelTest.java
git commit -m "feat(fauna): add allometric MassStatModel"
```

---

## Task 9: CreatureAssembler (socket-graph walker)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/assembly/CreatureAssembler.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/assembly/CreatureAssemblerTest.java`

This is the core. It walks the archetype's attachment tree, seeded-picks part variants, places
them at socket transforms, mirrors mirror-groups, and chains `repeat` segments.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna.assembly;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.part.PartType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureAssemblerTest {

    private FaunaDataRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new FaunaDataRegistry();
        // torso with 2 mirror leg sockets (-> 4 legs) and 1 head socket
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"lf\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,0.6],\"mirrorGroup\":\"front\"}," +
          "               {\"id\":\"lr\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,-0.6],\"mirrorGroup\":\"rear\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0]} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }," +
          "{ \"id\":\"seg\",\"partType\":\"TORSO\",\"bodyPlans\":[\"SERPENTINE\"]," +
          "  \"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.6,\"radius\":0.2}," +
          "  \"sockets\":[ {\"id\":\"next\",\"acceptedType\":\"TORSO\",\"pos\":[0,0,0.6]}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0,0.6]} ] }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"lf\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"lr\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }," +
          "{ \"id\":\"snake\",\"bodyPlan\":\"SERPENTINE\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"root\":{\"partType\":\"TORSO\",\"repeat\":4,\"continuationSocketId\":\"next\"," +
          "    \"children\":[ {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
    }

    @Test
    void quadrupedHasFourLegsAndOneHead() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        assertEquals(4, spec.countOfType(PartType.LIMB_LEG), "2 mirror sockets -> 4 legs");
        assertEquals(1, spec.countOfType(PartType.HEAD));
        assertEquals(1, spec.countOfType(PartType.TORSO));
        assertEquals(6, spec.partCount());
    }

    @Test
    void mirroredLegsAreOnOppositeSides() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        float sumX = 0f;
        int count = 0;
        for (AssembledNode n : spec.allNodes) {
            if (n.part.partType == PartType.LIMB_LEG) {
                sumX += n.worldTransform.getTranslation(new com.badlogic.gdx.math.Vector3()).x;
                count++;
            }
        }
        assertEquals(4, count);
        assertEquals(0f, sumX, 1e-3f, "mirrored legs cancel in X");
    }

    @Test
    void serpentineChainsSegmentsThenHead() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("snake"), 7L);
        assertEquals(4, spec.countOfType(PartType.TORSO), "repeat=4 -> 4 spine segments");
        assertEquals(1, spec.countOfType(PartType.HEAD));
        assertEquals(0, spec.countOfType(PartType.LIMB_LEG));
    }

    @Test
    void assemblyIsDeterministic() {
        CreatureSpec a = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 99L);
        CreatureSpec b = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 99L);
        assertEquals(a.partCount(), b.partCount());
        for (int i = 0; i < a.allNodes.size(); i++) {
            assertEquals(a.allNodes.get(i).part.id, b.allNodes.get(i).part.id);
            assertArrayEquals(a.allNodes.get(i).worldTransform.val,
                              b.allNodes.get(i).worldTransform.val, 1e-6f);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.assembly.CreatureAssemblerTest"`
Expected: FAIL — `CreatureAssembler` not defined.

- [ ] **Step 3: Implement `CreatureAssembler`**

```java
package com.galacticodyssey.fauna.assembly;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.archetype.AttachmentNode;
import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;
import com.galacticodyssey.fauna.part.CreaturePartDef;
import com.galacticodyssey.fauna.part.PartType;
import com.galacticodyssey.fauna.part.Socket;

import java.util.List;
import java.util.Random;

/** Walks an archetype's attachment tree into a deterministic {@link CreatureSpec}. */
public final class CreatureAssembler {

    private final FaunaDataRegistry registry;

    public CreatureAssembler(FaunaDataRegistry registry) { this.registry = registry; }

    public CreatureSpec assemble(BodyPlanArchetypeDef arch, long seed) {
        Random rng = new Random(com.galacticodyssey.galaxy.SeedDeriver.faunaDomain(seed));

        CreatureSpec spec = new CreatureSpec();
        spec.seed = seed;
        spec.archetypeId = arch.id;
        spec.bodyPlan = arch.bodyPlan;
        spec.sizeMultiplier = arch.minSize + rng.nextFloat() * (arch.maxSize - arch.minSize);
        spec.colorSeed = rng.nextLong();

        CreaturePartDef rootPart = pick(arch.root.partType, arch, rng);
        AssembledNode root = newNode(rootPart, new Matrix4(), spec.sizeMultiplier, false, spec);
        spec.root = root;

        attachChildren(arch.root, root, rootPart, arch, rng, spec);

        computeBounds(spec);
        return spec;
    }

    private void attachChildren(AttachmentNode parentTpl, AssembledNode parentNode,
                                CreaturePartDef parentPart, BodyPlanArchetypeDef arch,
                                Random rng, CreatureSpec spec) {
        for (AttachmentNode childTpl : parentTpl.children) {
            placeAttachment(childTpl, parentNode, parentPart, arch, rng, spec);
        }
    }

    private void placeAttachment(AttachmentNode tpl, AssembledNode parentNode,
                                 CreaturePartDef parentPart, BodyPlanArchetypeDef arch,
                                 Random rng, CreatureSpec spec) {
        Socket socket = parentPart.findSocket(tpl.socketId);
        if (socket == null) throw new IllegalStateException(
            "Archetype '" + arch.id + "' references missing socket '" + tpl.socketId
            + "' on part '" + parentPart.id + "'");

        CreaturePartDef chosen = pick(tpl.partType, arch, rng);

        // Primary instance (+ repeat chain). Walk down the continuation socket for repeats.
        AssembledNode anchor = parentNode;
        CreaturePartDef anchorPart = parentPart;
        Socket anchorSocket = socket;
        AssembledNode last = null;
        CreaturePartDef lastPart = null;
        for (int i = 0; i < Math.max(1, tpl.repeat); i++) {
            Matrix4 local = socketMatrix(anchorSocket, false);
            last = newNode(chosen, local, parentNode.scale, false, spec);
            anchor.children.add(last);
            last.worldTransform.set(anchor.worldTransform).mul(last.localTransform);
            lastPart = chosen;
            if (i + 1 < tpl.repeat) {
                anchor = last;
                anchorPart = chosen;
                anchorSocket = chosen.findSocket(tpl.continuationSocketId);
                if (anchorSocket == null) throw new IllegalStateException(
                    "repeat chain on '" + arch.id + "' missing continuation socket '"
                    + tpl.continuationSocketId + "' on part '" + chosen.id + "'");
            }
        }

        // Mirror copy (same chosen variant) across YZ plane, attached to the same parent socket.
        if (tpl.mirror && socket.mirrorGroup != null) {
            Matrix4 mlocal = socketMatrix(socket, true);
            AssembledNode m = newNode(chosen, mlocal, parentNode.scale, true, spec);
            parentNode.children.add(m);
            m.worldTransform.set(parentNode.worldTransform).mul(m.localTransform);
            // children of a mirrored attachment recurse under the mirror instance
            attachChildren(tpl, m, chosen, arch, rng, spec);
        }

        // Children recurse under the last (non-mirrored) instance.
        attachChildren(tpl, last, lastPart, arch, rng, spec);
    }

    private Matrix4 socketMatrix(Socket socket, boolean mirrored) {
        Vector3 p = new Vector3(socket.localPosition);
        if (mirrored) p.x = -p.x;
        Matrix4 m = new Matrix4().set(p, socket.localRotation);
        return m;
    }

    /** Deterministic variant pick: stable id-sorted list, indexed by rng. Throws if none eligible. */
    private CreaturePartDef pick(PartType type, BodyPlanArchetypeDef arch, Random rng) {
        List<CreaturePartDef> options = registry.partsFor(type, arch.bodyPlan);
        if (options.isEmpty()) throw new IllegalStateException(
            "No eligible part of type " + type + " for body plan " + arch.bodyPlan);
        return options.get(rng.nextInt(options.size()));
    }

    private AssembledNode newNode(CreaturePartDef part, Matrix4 local, float scale,
                                  boolean mirrored, CreatureSpec spec) {
        AssembledNode n = new AssembledNode();
        n.part = part;
        n.localTransform.set(local);
        n.worldTransform.set(local);   // root: world == local; overwritten by callers otherwise
        n.scale = scale;
        n.mirrored = mirrored;
        spec.allNodes.add(n);
        return n;
    }

    private void computeBounds(CreatureSpec spec) {
        spec.bounds.inf();
        Vector3 t = new Vector3();
        for (AssembledNode n : spec.allNodes) {
            n.worldTransform.getTranslation(t);
            float r = n.part.geometry.radius * n.scale;
            float len = n.part.geometry.length * n.scale;
            spec.bounds.ext(new Vector3(t).add(r, r, r));
            spec.bounds.ext(new Vector3(t).add(-r, -r, -r));
            spec.bounds.ext(new Vector3(t).add(0, 0, len));
        }
    }
}
```

> **Note on root world transform:** `newNode` sets `worldTransform = localTransform`. For the root the local is identity, so this is correct. Every non-root node has its `worldTransform` overwritten immediately after creation in `placeAttachment` (`last.worldTransform.set(parent.world).mul(local)`), so the temporary assignment is never observed. Mirror and chained nodes likewise.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.assembly.CreatureAssemblerTest"`
Expected: PASS (all four tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/assembly/CreatureAssembler.java core/src/test/java/com/galacticodyssey/fauna/assembly/CreatureAssemblerTest.java
git commit -m "feat(fauna): add socket-graph CreatureAssembler with mirror + repeat chaining"
```

---

## Task 10: CreatureGenerator facade (seed → CreatureSpec, with stats)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/CreatureGenerator.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/CreatureGeneratorTest.java`

Ties together: seeded archetype selection (or explicit id), assembly, volume→mass→stats.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna;

import com.galacticodyssey.data.FaunaDataRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureGeneratorTest {

    private FaunaDataRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"lf\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,0.6],\"mirrorGroup\":\"front\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0]} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"lf\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
    }

    @Test
    void generatesSpecWithPositiveStats() {
        CreatureSpec spec = new CreatureGenerator(reg).generate("quad", 12345L);
        assertEquals("quad", spec.archetypeId);
        assertTrue(spec.mass > 0f);
        assertTrue(spec.maxHP >= 1f);
        assertTrue(spec.moveSpeed > 0f);
        assertTrue(spec.meleeDamage >= 1f);
    }

    @Test
    void sameSeedYieldsIdenticalStatsAndStructure() {
        CreatureGenerator g = new CreatureGenerator(reg);
        CreatureSpec a = g.generate("quad", 555L);
        CreatureSpec b = g.generate("quad", 555L);
        assertEquals(a.partCount(), b.partCount());
        assertEquals(a.mass, b.mass, 1e-4f);
        assertEquals(a.maxHP, b.maxHP, 1e-4f);
        assertEquals(a.colorSeed, b.colorSeed);
    }

    @Test
    void seededArchetypePickIsDeterministic() {
        CreatureGenerator g = new CreatureGenerator(reg);
        assertEquals(g.generate(900L).archetypeId, g.generate(900L).archetypeId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.CreatureGeneratorTest"`
Expected: FAIL — `CreatureGenerator` not defined.

- [ ] **Step 3: Implement `CreatureGenerator`**

```java
package com.galacticodyssey.fauna;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;
import com.galacticodyssey.fauna.assembly.AssembledNode;
import com.galacticodyssey.fauna.assembly.CreatureAssembler;
import com.galacticodyssey.fauna.stats.MassStatModel;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seed → fully-statted {@link CreatureSpec}. The single entry point for creature generation. */
public final class CreatureGenerator {

    private final FaunaDataRegistry registry;
    private final CreatureAssembler assembler;
    private final MassStatModel statModel = new MassStatModel();

    public CreatureGenerator(FaunaDataRegistry registry) {
        this.registry = registry;
        this.assembler = new CreatureAssembler(registry);
    }

    /** Generate using a specific archetype id. */
    public CreatureSpec generate(String archetypeId, long seed) {
        BodyPlanArchetypeDef arch = registry.getArchetype(archetypeId);
        if (arch == null) throw new IllegalArgumentException("Unknown archetype: " + archetypeId);
        return generate(arch, seed);
    }

    /** Generate with a seeded archetype pick from all loaded archetypes (Cycle D adds biome weighting). */
    public CreatureSpec generate(long seed) {
        List<BodyPlanArchetypeDef> all = new ArrayList<>(registry.allArchetypes());
        all.sort((a, b) -> a.id.compareTo(b.id)); // determinism: never depend on map order
        if (all.isEmpty()) throw new IllegalStateException("No archetypes loaded");
        Random pickRng = new Random(SeedDeriver.forId(SeedDeriver.faunaDomain(seed), 0xA5));
        return generate(all.get(pickRng.nextInt(all.size())), seed);
    }

    private CreatureSpec generate(BodyPlanArchetypeDef arch, long seed) {
        CreatureSpec spec = assembler.assemble(arch, seed);
        float volume = 0f;
        for (AssembledNode n : spec.allNodes) {
            float s = n.scale;
            volume += n.part.geometry.approxVolume() * s * s * s;
        }
        spec.mass = statModel.mass(volume, arch.density);
        float[] stats = statModel.deriveStats(spec.mass, arch);
        spec.maxHP = stats[0];
        spec.moveSpeed = stats[1];
        spec.meleeDamage = stats[2];
        return spec;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.CreatureGeneratorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/CreatureGenerator.java core/src/test/java/com/galacticodyssey/fauna/CreatureGeneratorTest.java
git commit -m "feat(fauna): add CreatureGenerator facade (seed -> statted CreatureSpec)"
```

---

## Task 11: Default content JSON + load-real-files test

**Files:**
- Create: `core/src/main/resources/data/fauna/parts/default-parts.json`
- Create: `core/src/main/resources/data/fauna/archetypes/default-archetypes.json`
- Test: `core/src/test/java/com/galacticodyssey/fauna/DefaultFaunaContentTest.java`

Covers all four Cycle-A archetypes (biped/quadruped/hexapod/serpentine). The test reads the
files off the classpath via a headless gdx files API.

- [ ] **Step 1: Create `default-parts.json`**

```json
{ "parts": [
  { "id": "torso_quad", "partType": "TORSO", "bodyPlans": ["QUADRUPED"],
    "geometry": { "shape": "CAPSULE", "length": 2.2, "radius": 0.55 },
    "sockets": [
      { "id": "leg_front", "acceptedType": "LIMB_LEG", "pos": [0.4, -0.2, 0.7], "mirrorGroup": "front", "jointHint": "hip" },
      { "id": "leg_rear",  "acceptedType": "LIMB_LEG", "pos": [0.4, -0.2, -0.7], "mirrorGroup": "rear", "jointHint": "hip" },
      { "id": "neck",      "acceptedType": "HEAD",     "pos": [0, 0.25, 1.15], "jointHint": "neck" },
      { "id": "tail",      "acceptedType": "TAIL",     "pos": [0, 0.1, -1.15], "jointHint": "tail" }
    ] },

  { "id": "torso_hex", "partType": "TORSO", "bodyPlans": ["HEXAPOD"],
    "geometry": { "shape": "CAPSULE", "length": 2.4, "radius": 0.45 },
    "sockets": [
      { "id": "leg_a", "acceptedType": "LIMB_LEG", "pos": [0.4, -0.1, 0.8], "mirrorGroup": "a", "jointHint": "hip" },
      { "id": "leg_b", "acceptedType": "LIMB_LEG", "pos": [0.4, -0.1, 0.0], "mirrorGroup": "b", "jointHint": "hip" },
      { "id": "leg_c", "acceptedType": "LIMB_LEG", "pos": [0.4, -0.1, -0.8], "mirrorGroup": "c", "jointHint": "hip" },
      { "id": "neck",  "acceptedType": "HEAD",     "pos": [0, 0.2, 1.25], "jointHint": "neck" }
    ] },

  { "id": "torso_biped", "partType": "TORSO", "bodyPlans": ["BIPEDAL"],
    "geometry": { "shape": "CAPSULE", "length": 1.4, "radius": 0.4 },
    "sockets": [
      { "id": "leg",  "acceptedType": "LIMB_LEG", "pos": [0.22, -0.7, 0], "mirrorGroup": "legs", "jointHint": "hip" },
      { "id": "arm",  "acceptedType": "LIMB_ARM", "pos": [0.42, 0.5, 0], "mirrorGroup": "arms", "jointHint": "shoulder" },
      { "id": "neck", "acceptedType": "HEAD",     "pos": [0, 0.85, 0.05], "jointHint": "neck" }
    ] },

  { "id": "seg_serpent", "partType": "TORSO", "bodyPlans": ["SERPENTINE"],
    "geometry": { "shape": "CAPSULE", "length": 0.7, "radius": 0.28, "taper": 0.92 },
    "sockets": [
      { "id": "next", "acceptedType": "TORSO", "pos": [0, 0, 0.68], "jointHint": "spine" },
      { "id": "head", "acceptedType": "HEAD",  "pos": [0, 0, 0.68], "jointHint": "neck" }
    ] },

  { "id": "leg_std",  "partType": "LIMB_LEG", "geometry": { "shape": "CAPSULE", "length": 0.9, "radius": 0.13, "taper": 0.7 } },
  { "id": "leg_thin", "partType": "LIMB_LEG", "geometry": { "shape": "CAPSULE", "length": 1.1, "radius": 0.09, "taper": 0.6 } },
  { "id": "arm_std",  "partType": "LIMB_ARM", "geometry": { "shape": "CAPSULE", "length": 0.8, "radius": 0.1, "taper": 0.7 } },
  { "id": "head_std", "partType": "HEAD", "geometry": { "shape": "ELLIPSOID_SNOUT", "length": 0.55, "radius": 0.28 } },
  { "id": "head_long","partType": "HEAD", "geometry": { "shape": "ELLIPSOID_SNOUT", "length": 0.75, "radius": 0.22 } },
  { "id": "tail_std", "partType": "TAIL", "geometry": { "shape": "CONE", "length": 1.0, "radius": 0.2 } }
] }
```

- [ ] **Step 2: Create `default-archetypes.json`**

```json
{ "archetypes": [
  { "id": "grazer_quad", "bodyPlan": "QUADRUPED", "minSize": 0.4, "maxSize": 3.5, "density": 950,
    "gaitClass": "walk", "kHp": 12, "kSpeed": 9, "kDamage": 3,
    "root": { "partType": "TORSO", "children": [
      { "socketId": "leg_front", "partType": "LIMB_LEG", "mirror": true },
      { "socketId": "leg_rear",  "partType": "LIMB_LEG", "mirror": true },
      { "socketId": "neck",      "partType": "HEAD" },
      { "socketId": "tail",      "partType": "TAIL" }
    ] } },

  { "id": "skitterer_hex", "bodyPlan": "HEXAPOD", "minSize": 0.1, "maxSize": 1.2, "density": 700,
    "gaitClass": "skitter", "kHp": 8, "kSpeed": 12, "kDamage": 4,
    "root": { "partType": "TORSO", "children": [
      { "socketId": "leg_a", "partType": "LIMB_LEG", "mirror": true },
      { "socketId": "leg_b", "partType": "LIMB_LEG", "mirror": true },
      { "socketId": "leg_c", "partType": "LIMB_LEG", "mirror": true },
      { "socketId": "neck",  "partType": "HEAD" }
    ] } },

  { "id": "strider_biped", "bodyPlan": "BIPEDAL", "minSize": 0.8, "maxSize": 4.0, "density": 1000,
    "gaitClass": "walk", "kHp": 14, "kSpeed": 8, "kDamage": 5,
    "root": { "partType": "TORSO", "children": [
      { "socketId": "leg",  "partType": "LIMB_LEG", "mirror": true },
      { "socketId": "arm",  "partType": "LIMB_ARM", "mirror": true },
      { "socketId": "neck", "partType": "HEAD" }
    ] } },

  { "id": "crawler_serpent", "bodyPlan": "SERPENTINE", "minSize": 0.5, "maxSize": 6.0, "density": 1050,
    "gaitClass": "slither", "kHp": 10, "kSpeed": 7, "kDamage": 4,
    "root": { "partType": "TORSO", "repeat": 6, "continuationSocketId": "next", "children": [
      { "socketId": "head", "partType": "HEAD" }
    ] } }
] }
```

- [ ] **Step 3: Write the failing test**

```java
package com.galacticodyssey.fauna;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.fauna.part.PartType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

class DefaultFaunaContentTest {

    private FaunaDataRegistry loadDefaults() throws Exception {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        String parts = new String(Files.readAllBytes(
            Paths.get("src/main/resources/data/fauna/parts/default-parts.json")));
        String arches = new String(Files.readAllBytes(
            Paths.get("src/main/resources/data/fauna/archetypes/default-archetypes.json")));
        reg.loadPartsFromJson(parts);
        reg.loadArchetypesFromJson(arches);
        reg.validate();
        return reg;
    }

    @Test
    void defaultContentLoadsAndValidates() throws Exception {
        FaunaDataRegistry reg = loadDefaults();
        assertNotNull(reg.getArchetype("grazer_quad"));
        assertNotNull(reg.getArchetype("skitterer_hex"));
        assertNotNull(reg.getArchetype("strider_biped"));
        assertNotNull(reg.getArchetype("crawler_serpent"));
    }

    @Test
    void eachArchetypeGeneratesExpectedTopology() throws Exception {
        CreatureGenerator g = new CreatureGenerator(loadDefaults());
        assertEquals(4, g.generate("grazer_quad", 1L).countOfType(PartType.LIMB_LEG));
        assertEquals(6, g.generate("skitterer_hex", 1L).countOfType(PartType.LIMB_LEG));
        CreatureSpec biped = g.generate("strider_biped", 1L);
        assertEquals(2, biped.countOfType(PartType.LIMB_LEG));
        assertEquals(2, biped.countOfType(PartType.LIMB_ARM));
        CreatureSpec snake = g.generate("crawler_serpent", 1L);
        assertEquals(6, snake.countOfType(PartType.TORSO));
        assertEquals(BodyPlan.SERPENTINE, snake.bodyPlan);
    }
}
```

> **Note:** the test reads the resource files via a relative path (`src/main/resources/...`).
> Gradle runs `:core:test` with the `core` module dir as the working directory, so this path
> resolves. This avoids needing a GL/`Gdx.files` context in the test.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.DefaultFaunaContentTest"`
Expected: PASS. (If the working-directory assumption fails, switch the test to load via
`com.badlogic.gdx.Gdx.files.classpath("data/fauna/...")` inside a headless app — but try the
relative path first.)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/resources/data/fauna core/src/test/java/com/galacticodyssey/fauna/DefaultFaunaContentTest.java
git commit -m "feat(fauna): add default part/archetype content for 4 archetypes"
```

---

## Task 12: ECS components + CreatureFactory (logical entity)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/components/CreatureComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/components/CreatureRenderComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/CreatureFactory.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/CreatureFactoryTest.java`

The factory builds the logical entity (Transform + Creature + Health) headlessly. The model is
attached later by the GL layer (Task 13) via `CreatureRenderComponent`, mirroring how
`VehicleFactory`/`VehicleRenderComponent` defer the model.

- [ ] **Step 1: Create `CreatureComponent`**

```java
package com.galacticodyssey.fauna.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.fauna.CreatureSpec;

/** Marks an entity as a generated creature and carries its spec + derived stats. */
public class CreatureComponent implements Component {
    public CreatureSpec spec;
    public String archetypeId;
    public float moveSpeed;
    public float meleeDamage;
}
```

- [ ] **Step 2: Create `CreatureRenderComponent`**

```java
package com.galacticodyssey.fauna.components;

import com.badlogic.ashley.core.Component;

/** Render hook-up for a creature. The GL layer fills {@link #model} from the spec. */
public class CreatureRenderComponent implements Component {
    /** com.badlogic.gdx.graphics.g3d.ModelInstance, set by the GL layer; null until built. */
    public Object modelInstance = null;
    /** Flat tint (RGBA packed) derived from colorSeed/biome until Cycle C shaders land. */
    public float tintR = 0.6f, tintG = 0.6f, tintB = 0.6f;
}
```

- [ ] **Step 3: Write the failing test**

```java
package com.galacticodyssey.fauna;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.components.CreatureComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureFactoryTest {

    private CreatureGenerator generator;

    @BeforeEach
    void setUp() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0]} ] }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[{\"socketId\":\"hd\",\"partType\":\"HEAD\"}]} }" +
          "] }");
        reg.validate();
        generator = new CreatureGenerator(reg);
    }

    @Test
    void buildsEntityWithCoreComponents() {
        Engine engine = new Engine();
        CreatureSpec spec = generator.generate("quad", 5L);
        Entity e = new CreatureFactory().create(engine, spec, new Vector3(10, 0, -3));

        assertNotNull(e.getComponent(TransformComponent.class));
        assertEquals(10f, e.getComponent(TransformComponent.class).position.x, 1e-4f);

        CreatureComponent cc = e.getComponent(CreatureComponent.class);
        assertNotNull(cc);
        assertEquals("quad", cc.archetypeId);
        assertSame(spec, cc.spec);

        HealthComponent hp = e.getComponent(HealthComponent.class);
        assertNotNull(hp);
        assertEquals(spec.maxHP, hp.maxHP, 1e-4f);
        assertEquals(spec.maxHP, hp.currentHP, 1e-4f);
        assertTrue(hp.alive);

        assertEquals(1, engine.getEntities().size());
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.CreatureFactoryTest"`
Expected: FAIL — `CreatureFactory` not defined.

- [ ] **Step 5: Implement `CreatureFactory`**

```java
package com.galacticodyssey.fauna;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.fauna.components.CreatureComponent;
import com.galacticodyssey.fauna.components.CreatureRenderComponent;

/** Builds the logical creature entity from a {@link CreatureSpec}. Model attached by GL layer. */
public final class CreatureFactory {

    public Entity create(Engine engine, CreatureSpec spec, Vector3 spawnPos) {
        Entity e = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(spawnPos);
        e.add(transform);

        CreatureComponent cc = new CreatureComponent();
        cc.spec = spec;
        cc.archetypeId = spec.archetypeId;
        cc.moveSpeed = spec.moveSpeed;
        cc.meleeDamage = spec.meleeDamage;
        e.add(cc);

        HealthComponent health = new HealthComponent();
        health.maxHP = spec.maxHP;
        health.currentHP = spec.maxHP;
        health.alive = true;
        e.add(health);

        e.add(new CreatureRenderComponent());

        engine.addEntity(e);
        return e;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.CreatureFactoryTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/components/ core/src/main/java/com/galacticodyssey/fauna/CreatureFactory.java core/src/test/java/com/galacticodyssey/fauna/CreatureFactoryTest.java
git commit -m "feat(fauna): add Creature components and logical CreatureFactory"
```

---

## Task 13: GL geometry layer (providers + CreatureMeshBuilder)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/geometry/PartGeometryProvider.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/geometry/ProceduralPartProvider.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/geometry/AuthoredPartProvider.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/CreatureMeshBuilder.java`

**No CI test** — this is GL-dependent. It is a thin translation over the already-tested
`CreatureSpec` / `ProceduralMeshData`. Verified manually via the debug spawn (Task 14).

- [ ] **Step 1: Create the provider interface**

```java
package com.galacticodyssey.fauna.geometry;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.utils.Disposable;

/** Produces a (single-node) Model for a part's geometry spec. GL-side. */
public interface PartGeometryProvider extends Disposable {
    boolean supports(PartGeometrySpec spec);
    /** Build a Model whose root node mesh is the part, origin at the part's socket origin. */
    Model buildPartModel(PartGeometrySpec spec);
}
```

- [ ] **Step 2: Create `ProceduralPartProvider`**

```java
package com.galacticodyssey.fauna.geometry;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;

/** Uploads {@link ProceduralMeshData} to a libGDX Model. */
public final class ProceduralPartProvider implements PartGeometryProvider {
    private final ProceduralPartMesher mesher = new ProceduralPartMesher();
    private final ModelBuilder modelBuilder = new ModelBuilder();

    @Override public boolean supports(PartGeometrySpec spec) {
        return spec.kind == PartGeometrySpec.Kind.PROCEDURAL;
    }

    @Override public Model buildPartModel(PartGeometrySpec spec) {
        ProceduralMeshData data = mesher.build(spec);
        modelBuilder.begin();
        Material mat = new Material(ColorAttribute.createDiffuse(Color.GRAY));
        MeshPartBuilder mpb = modelBuilder.part("part", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal, mat);
        // positions are (x,y,z) triples; supply flat normals via builder's triangle() with computed normals
        float[] p = data.positions;
        for (int i = 0; i < data.indices.length; i += 3) {
            int a = data.indices[i] & 0xFFFF, b = data.indices[i + 1] & 0xFFFF, c = data.indices[i + 2] & 0xFFFF;
            mpb.triangle(
                vtemp(p, a), vtemp(p, b), vtemp(p, c));
        }
        return modelBuilder.end();
    }

    private static com.badlogic.gdx.math.Vector3 vtemp(float[] p, int idx) {
        return new com.badlogic.gdx.math.Vector3(p[idx * 3], p[idx * 3 + 1], p[idx * 3 + 2]);
    }

    @Override public void dispose() { /* ModelBuilder holds no GL state; built Models owned by caller */ }
}
```

> **Implementation note:** `MeshPartBuilder.triangle(Vector3,Vector3,Vector3)` computes a flat
> normal automatically. If you prefer indexed meshes, use `mpb.vertex(...)` + `mpb.index(...)`
> instead; the triple-vertex form above is simplest and correct for a debug visual.

- [ ] **Step 3: Create `AuthoredPartProvider`**

```java
package com.galacticodyssey.fauna.geometry;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g3d.Model;

/** Resolves an authored .g3db part model by reference. */
public final class AuthoredPartProvider implements PartGeometryProvider {
    private final AssetManager assets;

    public AuthoredPartProvider(AssetManager assets) { this.assets = assets; }

    @Override public boolean supports(PartGeometrySpec spec) {
        return spec.kind == PartGeometrySpec.Kind.AUTHORED && spec.modelRef != null;
    }

    @Override public Model buildPartModel(PartGeometrySpec spec) {
        if (!assets.isLoaded(spec.modelRef)) {
            assets.load(spec.modelRef, Model.class);
            assets.finishLoadingAsset(spec.modelRef);
        }
        return assets.get(spec.modelRef, Model.class);
    }

    @Override public void dispose() { /* models owned by AssetManager */ }
}
```

- [ ] **Step 4: Create `CreatureMeshBuilder`**

```java
package com.galacticodyssey.fauna;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.fauna.assembly.AssembledNode;
import com.galacticodyssey.fauna.geometry.PartGeometryProvider;
import com.galacticodyssey.fauna.geometry.PartGeometrySpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes a {@link CreatureSpec} into renderable {@link ModelInstance}s — one per assembled
 * node, transformed by the node's worldTransform and scale. Thin GL translation layer.
 */
public final class CreatureMeshBuilder implements Disposable {
    private final List<PartGeometryProvider> providers = new ArrayList<>();
    private final Array<Model> ownedModels = new Array<>();

    public CreatureMeshBuilder(PartGeometryProvider... providers) {
        for (PartGeometryProvider p : providers) this.providers.add(p);
    }

    /** Returns one ModelInstance per part, ready to render. */
    public Array<ModelInstance> build(CreatureSpec spec) {
        Array<ModelInstance> out = new Array<>();
        for (AssembledNode node : spec.allNodes) {
            Model model = providerFor(node.part.geometry).buildPartModel(node.part.geometry);
            ownedModels.add(model);
            ModelInstance inst = new ModelInstance(model);
            Matrix4 m = new Matrix4(node.worldTransform);
            m.scl(node.scale);
            inst.transform.set(m);
            out.add(inst);
        }
        return out;
    }

    private PartGeometryProvider providerFor(PartGeometrySpec spec) {
        for (PartGeometryProvider p : providers) if (p.supports(spec)) return p;
        throw new IllegalStateException("No geometry provider supports spec kind " + spec.kind);
    }

    @Override public void dispose() {
        for (Model m : ownedModels) m.dispose();
        ownedModels.clear();
        for (PartGeometryProvider p : providers) p.dispose();
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/geometry/PartGeometryProvider.java core/src/main/java/com/galacticodyssey/fauna/geometry/ProceduralPartProvider.java core/src/main/java/com/galacticodyssey/fauna/geometry/AuthoredPartProvider.java core/src/main/java/com/galacticodyssey/fauna/CreatureMeshBuilder.java
git commit -m "feat(fauna): add GL geometry providers and CreatureMeshBuilder"
```

---

## Task 14: Debug spawn (FaunaDebugSpawner + GameScreen keybind)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/FaunaDebugSpawner.java`
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` (near the F5 handler ~line 404)

**No CI test** — runtime/GL integration, verified manually. Provides a one-call helper and a
dev keybind to spawn a random creature in front of the player.

- [ ] **Step 1: Create `FaunaDebugSpawner`**

```java
package com.galacticodyssey.fauna;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.fauna.components.CreatureRenderComponent;

/**
 * Dev-only helper: generate a random creature and spawn it a few metres in front of a position.
 * Returns the entity; its render ModelInstances are stored on the CreatureRenderComponent for the
 * render system to draw.
 */
public final class FaunaDebugSpawner {
    private final CreatureGenerator generator;
    private final CreatureMeshBuilder meshBuilder;
    private long nextSeed = 1L;

    public FaunaDebugSpawner(CreatureGenerator generator, CreatureMeshBuilder meshBuilder) {
        this.generator = generator;
        this.meshBuilder = meshBuilder;
    }

    /** Spawns in front of {@code origin} along {@code forward} (xz-plane), at origin height. */
    public Entity spawnInFront(Engine engine, Vector3 origin, Vector3 forward, float distance) {
        long seed = nextSeed++;
        CreatureSpec spec = generator.generate(seed);   // seeded archetype pick
        Vector3 flat = new Vector3(forward.x, 0f, forward.z).nor().scl(distance);
        Vector3 pos = new Vector3(origin).add(flat);
        Entity e = new CreatureFactory().create(engine, spec, pos);

        Array<ModelInstance> instances = meshBuilder.build(spec);
        for (ModelInstance inst : instances) inst.transform.translate(pos);
        CreatureRenderComponent render = e.getComponent(CreatureRenderComponent.class);
        render.modelInstance = instances;   // render system iterates and draws
        return e;
    }
}
```

> **Note:** the render system must draw `CreatureRenderComponent.modelInstance` when it is an
> `Array<ModelInstance>`. If the project's render path expects a single ModelInstance per entity,
> wrap the parts under one `ModelInstance` by merging nodes instead — but the Array form keeps
> Cycle A simple and matches the per-part build.

- [ ] **Step 2: Wire the keybind in `GameScreen`**

Read the existing F5 handler around `GameScreen.java:404` and add an F6 case in the same
`keyDown`/`keyUp` `InputProcessor` block, following the surrounding style. Construct the spawner
once where other systems are initialized (where `engine`, the player transform, and the
`AssetManager` are available):

```java
// --- field, alongside other system fields ---
private FaunaDebugSpawner faunaDebugSpawner;

// --- during system/engine setup (where engine + assetManager exist) ---
FaunaDataRegistry faunaRegistry = new FaunaDataRegistry();
faunaRegistry.loadParts("data/fauna/parts/default-parts.json");
faunaRegistry.loadArchetypes("data/fauna/archetypes/default-archetypes.json");
faunaRegistry.validate();
CreatureGenerator creatureGenerator = new CreatureGenerator(faunaRegistry);
CreatureMeshBuilder creatureMeshBuilder = new CreatureMeshBuilder(
    new ProceduralPartProvider(),
    new AuthoredPartProvider(assetManager));
faunaDebugSpawner = new FaunaDebugSpawner(creatureGenerator, creatureMeshBuilder);

// --- in keyDown, next to the existing F5 case (~line 404) ---
if (keycode == Input.Keys.F6) {
    Vector3 origin = playerTransform.position;            // existing player TransformComponent
    Vector3 forward = camera.direction;                   // existing PerspectiveCamera
    faunaDebugSpawner.spawnInFront(engine, origin, forward, 6f);
    return true;
}
```

Add imports at the top of `GameScreen.java`:

```java
import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureGenerator;
import com.galacticodyssey.fauna.CreatureMeshBuilder;
import com.galacticodyssey.fauna.FaunaDebugSpawner;
import com.galacticodyssey.fauna.geometry.ProceduralPartProvider;
import com.galacticodyssey.fauna.geometry.AuthoredPartProvider;
```

> Use the field names that actually exist in `GameScreen` for the engine, player transform,
> camera, and asset manager. If the render loop does not already draw arbitrary
> `ModelInstance`s, add a draw pass that iterates entities with `CreatureRenderComponent` and
> renders each instance with the active `ModelBatch` and environment.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification**

Run the game (see `run-galactic-odyssey` skill / desktop launcher), land on a planet surface,
press **F6**. Expect a grey multi-part creature to appear ~6 m in front of the camera. Press F6
repeatedly — each spawn differs (different archetype/parts/size). Confirm quadruped has 4 legs,
hexapod 6, biped stands with 2 arms, serpent is a chained spine.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/FaunaDebugSpawner.java core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(fauna): add F6 debug spawn for generated creatures"
```

---

## Task 15: Full suite green + spec cross-check

- [ ] **Step 1: Run the whole fauna test set**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.*" --tests "com.galacticodyssey.data.FaunaDataRegistryTest" --tests "com.galacticodyssey.galaxy.SeedDeriverFaunaTest"`
Expected: PASS.

- [ ] **Step 2: Run the full core suite to check for regressions**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL (no pre-existing tests broken).

- [ ] **Step 3: Commit any final fixups**

```bash
git add -A
git commit -m "test(fauna): cycle A generation core suite green"
```

---

## Self-Review

**Spec coverage:**
- Socket-graph assembly → Tasks 3, 4, 9. ✓
- Dual geometry providers (procedural + authored) behind one interface → Tasks 7 (headless math), 13 (`PartGeometryProvider`/`ProceduralPartProvider`/`AuthoredPartProvider`). ✓
- 4 archetypes (biped/quadruped/hexapod/serpentine) → Task 11 content + Task 11 topology test. ✓
- Symmetry mirroring → Task 9 (`mirror`, `mirrorGroup`) + `mirroredLegsAreOnOppositeSides`. ✓
- `repeat`-chain spine for serpentine → Task 9 + Task 11. ✓
- Size scaling + mass-derived stats → Task 8 + Task 10. ✓
- Determinism from seed → Tasks 1, 9 (`assemblyIsDeterministic`), 10 (`sameSeedYieldsIdenticalStatsAndStructure`). ✓
- GL-free testability (rule #5) → all logic tasks headless; GL isolated to Tasks 13/14. ✓
- Data-driven content (rule #2) → Task 5 registry + Task 11 JSON. ✓
- `CreatureSpec` → Task 6; ECS factory + `HealthComponent` feed → Task 12. ✓
- Debug spawn → Task 14. ✓
- `jointHint`/`gaitClass`/`colorSeed` authored-now/used-later hooks → present in Tasks 3/4/6/11. ✓

**Placeholder scan:** No TBD/TODO. GL tasks (13/14) intentionally have no CI test per spec
(GL-dependent); each gives complete code + a compile check + manual verification steps — not
placeholders.

**Type consistency:** `faunaDomain` (T1) used in T9/T10. `partsFor(PartType,BodyPlan)` (T5) used
in T9. `findSocket` (T3) used in T9. `deriveStats`→`{hp,speed,dmg}` (T8) consumed in T10.
`CreatureSpec.allNodes/countOfType/partCount` (T6) used in T9/T10/T11/T12. `AssembledNode.scale`/
`worldTransform` (T6) used in T9/T10/T13. `CreatureGenerator.generate(String,long)` and
`generate(long)` (T10) used in T12/T14. `CreatureFactory.create(Engine,CreatureSpec,Vector3)`
(T12) used in T14. `CreatureMeshBuilder.build(CreatureSpec):Array<ModelInstance>` (T13) used in
T14. `CreatureRenderComponent.modelInstance` (T12) set in T14. Consistent throughout.
