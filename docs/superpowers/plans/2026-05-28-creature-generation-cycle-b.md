# Creature Generation Cycle B — Procedural Skeleton & Animation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build runtime skeletons from the socket graph and animate creatures with procedural gait controllers — IK-driven walking, tripod skittering, and sine-wave slithering — with zero authored animation clips.

**Architecture:** `CreatureRigBuilder` walks the `AssembledNode` tree to produce a `CreatureRig` (flat bone array with hierarchy + semantic roles derived from `Socket.jointHint`). `CreatureMeshBuilder` is modified to output a single `Model` with a `Node` hierarchy (one node per bone, each carrying its part's mesh) instead of separate `ModelInstance`s per part. Per-archetype `GaitController` implementations drive bone transforms procedurally each frame. A `CreatureGaitSystem` (Ashley ECS) ticks the active controller on every creature entity and writes updated transforms to the model's nodes.

**Tech Stack:** Java 17, libGDX 1.13 (Model/Node/ModelBuilder, MeshPartBuilder), Ashley ECS, JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-28-creature-generation-bcd-design.md` (Cycle B section)

---

### Task 1: BoneRole enum and Bone data class

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/rig/BoneRole.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/rig/Bone.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/rig/BoneRoleTest.java`

- [ ] **Step 1: Write BoneRole enum**

```java
package com.galacticodyssey.fauna.rig;

public enum BoneRole {
    HIP, SHOULDER, NECK, SPINE, TAIL, STRUCTURAL;

    public static BoneRole fromJointHint(String hint) {
        if (hint == null) return STRUCTURAL;
        switch (hint) {
            case "hip":      return HIP;
            case "shoulder": return SHOULDER;
            case "neck":     return NECK;
            case "spine":    return SPINE;
            case "tail":     return TAIL;
            default:         return STRUCTURAL;
        }
    }
}
```

- [ ] **Step 2: Write the test for BoneRole.fromJointHint**

```java
package com.galacticodyssey.fauna.rig;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoneRoleTest {

    @Test
    void mapsKnownHintsToRoles() {
        assertEquals(BoneRole.HIP, BoneRole.fromJointHint("hip"));
        assertEquals(BoneRole.SHOULDER, BoneRole.fromJointHint("shoulder"));
        assertEquals(BoneRole.NECK, BoneRole.fromJointHint("neck"));
        assertEquals(BoneRole.SPINE, BoneRole.fromJointHint("spine"));
        assertEquals(BoneRole.TAIL, BoneRole.fromJointHint("tail"));
    }

    @Test
    void nullAndUnknownMapToStructural() {
        assertEquals(BoneRole.STRUCTURAL, BoneRole.fromJointHint(null));
        assertEquals(BoneRole.STRUCTURAL, BoneRole.fromJointHint("unknown"));
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.rig.BoneRoleTest" --info`
Expected: 2 tests PASS

- [ ] **Step 4: Write Bone data class**

```java
package com.galacticodyssey.fauna.rig;

import com.badlogic.gdx.math.Matrix4;

public final class Bone {
    public final int index;
    public final int parentIndex;   // -1 for root
    public final BoneRole role;
    public final String name;       // "bone_0", "bone_1", etc.
    public final Matrix4 bindPose = new Matrix4();    // local-space bind transform
    public final Matrix4 currentPose = new Matrix4(); // local-space animated transform (mutated by gait)

    public Bone(int index, int parentIndex, BoneRole role, String name, Matrix4 bindPose) {
        this.index = index;
        this.parentIndex = parentIndex;
        this.role = role;
        this.name = name;
        this.bindPose.set(bindPose);
        this.currentPose.set(bindPose);
    }

    public boolean isRoot() { return parentIndex < 0; }
}
```

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/rig/BoneRole.java \
        core/src/main/java/com/galacticodyssey/fauna/rig/Bone.java \
        core/src/test/java/com/galacticodyssey/fauna/rig/BoneRoleTest.java
git commit -m "feat(fauna): add BoneRole enum and Bone data class for creature rig"
```

---

### Task 2: CreatureRig and CreatureRigBuilder

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/rig/CreatureRig.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/rig/CreatureRigBuilder.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/rig/CreatureRigBuilderTest.java`

- [ ] **Step 1: Write the failing test**

This test re-uses the test data pattern from `CreatureAssemblerTest` — inline JSON for a quadruped (1 torso + 4 legs + 1 head = 6 parts = 6 bones) and a serpentine (4 spine segments + 1 head = 5 bones).

```java
package com.galacticodyssey.fauna.rig;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.assembly.CreatureAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureRigBuilderTest {

    private FaunaDataRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"lf\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,0.6],\"mirrorGroup\":\"front\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"lr\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,-0.6],\"mirrorGroup\":\"rear\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0],\"jointHint\":\"neck\"} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }," +
          "{ \"id\":\"seg\",\"partType\":\"TORSO\",\"bodyPlans\":[\"SERPENTINE\"]," +
          "  \"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.6,\"radius\":0.2}," +
          "  \"sockets\":[ {\"id\":\"next\",\"acceptedType\":\"TORSO\",\"pos\":[0,0,0.6],\"jointHint\":\"spine\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0,0.6],\"jointHint\":\"neck\"} ] }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"gaitClass\":\"walk\"," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"lf\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"lr\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }," +
          "{ \"id\":\"snake\",\"bodyPlan\":\"SERPENTINE\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"gaitClass\":\"slither\"," +
          "  \"root\":{\"partType\":\"TORSO\",\"repeat\":4,\"continuationSocketId\":\"next\"," +
          "    \"children\":[ {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
    }

    @Test
    void quadrupedRigHasCorrectBoneCountAndRoles() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        CreatureRig rig = new CreatureRigBuilder().build(spec);

        assertEquals(6, rig.boneCount(), "1 torso + 4 legs + 1 head");
        assertEquals(0, rig.root().index);
        assertTrue(rig.root().isRoot());

        assertEquals(4, rig.bonesWithRole(BoneRole.HIP).size(), "4 legs have HIP role");
        assertEquals(1, rig.bonesWithRole(BoneRole.NECK).size(), "1 head has NECK role");
    }

    @Test
    void serpentineRigHasSpineBones() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("snake"), 7L);
        CreatureRig rig = new CreatureRigBuilder().build(spec);

        assertEquals(5, rig.boneCount(), "4 spine + 1 head");
        assertEquals(3, rig.bonesWithRole(BoneRole.SPINE).size(),
            "3 spine continuation sockets (first segment is root/STRUCTURAL)");
        assertEquals(1, rig.bonesWithRole(BoneRole.NECK).size());
    }

    @Test
    void boneHierarchyMatchesAssembledNodeTree() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        CreatureRig rig = new CreatureRigBuilder().build(spec);

        // Root bone has no parent
        assertEquals(-1, rig.root().parentIndex);
        // All non-root bones must have a valid parent
        for (int i = 1; i < rig.boneCount(); i++) {
            int parent = rig.getBone(i).parentIndex;
            assertTrue(parent >= 0 && parent < rig.boneCount(),
                "bone " + i + " parent index " + parent + " out of range");
        }
    }

    @Test
    void bindPosesMatchAssembledNodeLocalTransforms() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        CreatureRig rig = new CreatureRigBuilder().build(spec);

        for (int i = 0; i < rig.boneCount(); i++) {
            assertArrayEquals(
                spec.allNodes.get(i).localTransform.val,
                rig.getBone(i).bindPose.val, 1e-5f,
                "bone " + i + " bind pose must match assembled node local transform");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.rig.CreatureRigBuilderTest" --info`
Expected: FAIL — `CreatureRig` and `CreatureRigBuilder` do not exist.

- [ ] **Step 3: Implement CreatureRig**

```java
package com.galacticodyssey.fauna.rig;

import java.util.ArrayList;
import java.util.List;

public final class CreatureRig {
    private final Bone[] bones;

    public CreatureRig(Bone[] bones) {
        this.bones = bones;
    }

    public int boneCount() { return bones.length; }
    public Bone getBone(int index) { return bones[index]; }
    public Bone root() { return bones[0]; }

    public List<Bone> bonesWithRole(BoneRole role) {
        List<Bone> out = new ArrayList<>();
        for (Bone b : bones) if (b.role == role) out.add(b);
        return out;
    }

    public Bone findFirstWithRole(BoneRole role) {
        for (Bone b : bones) if (b.role == role) return b;
        return null;
    }

    public List<Bone> childrenOf(int parentIndex) {
        List<Bone> out = new ArrayList<>();
        for (Bone b : bones) if (b.parentIndex == parentIndex) out.add(b);
        return out;
    }
}
```

- [ ] **Step 4: Implement CreatureRigBuilder**

The builder walks `CreatureSpec.allNodes` (which is already flattened root-first by `CreatureAssembler`). For each `AssembledNode`, it finds the parent index by searching `allNodes` for the node whose `children` list contains it.

```java
package com.galacticodyssey.fauna.rig;

import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.assembly.AssembledNode;
import com.galacticodyssey.fauna.part.Socket;

import java.util.HashMap;
import java.util.Map;

public final class CreatureRigBuilder {

    public CreatureRig build(CreatureSpec spec) {
        Map<AssembledNode, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < spec.allNodes.size(); i++) {
            indexMap.put(spec.allNodes.get(i), i);
        }

        Bone[] bones = new Bone[spec.allNodes.size()];
        for (int i = 0; i < spec.allNodes.size(); i++) {
            AssembledNode node = spec.allNodes.get(i);
            int parentIdx = findParentIndex(node, spec, indexMap);
            BoneRole role = resolveRole(node, parentIdx, spec, indexMap);
            bones[i] = new Bone(i, parentIdx, role, "bone_" + i, node.localTransform);
        }
        return new CreatureRig(bones);
    }

    private int findParentIndex(AssembledNode node, CreatureSpec spec,
                                Map<AssembledNode, Integer> indexMap) {
        if (node == spec.root) return -1;
        for (AssembledNode candidate : spec.allNodes) {
            if (candidate.children.contains(node)) {
                return indexMap.get(candidate);
            }
        }
        return -1;
    }

    private BoneRole resolveRole(AssembledNode node, int parentIdx, CreatureSpec spec,
                                 Map<AssembledNode, Integer> indexMap) {
        if (parentIdx < 0) return BoneRole.STRUCTURAL; // root torso
        AssembledNode parent = spec.allNodes.get(parentIdx);
        // Find the socket on the parent part that accepted this node's part type.
        // The socket's jointHint determines the bone role.
        for (Socket s : parent.part.sockets) {
            if (s.acceptedType == node.part.partType) {
                return BoneRole.fromJointHint(s.jointHint);
            }
        }
        return BoneRole.STRUCTURAL;
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.rig.CreatureRigBuilderTest" --info`
Expected: 4 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/rig/CreatureRig.java \
        core/src/main/java/com/galacticodyssey/fauna/rig/CreatureRigBuilder.java \
        core/src/test/java/com/galacticodyssey/fauna/rig/CreatureRigBuilderTest.java
git commit -m "feat(fauna): CreatureRig + CreatureRigBuilder — socket graph to bone hierarchy"
```

---

### Task 3: TwoBoneIKSolver

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/rig/TwoBoneIKSolver.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/rig/TwoBoneIKSolverTest.java`

- [ ] **Step 1: Write the failing test**

Tests cover: reachable target (known geometry), straight-line target (fully extended), out-of-reach target (clamped to max extension).

```java
package com.galacticodyssey.fauna.rig;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwoBoneIKSolverTest {

    @Test
    void solvesReachableTarget() {
        // Upper bone length 1, lower bone length 1, target at (0, -1.4, 0), pole +Z
        TwoBoneIKSolver.Result r = TwoBoneIKSolver.solve(
            new Vector3(0, 0, 0),       // root position
            new Vector3(0, -1.4f, 0),   // target
            new Vector3(0, 0, 1),       // pole vector (forward knee)
            1f, 1f                       // upper, lower lengths
        );
        assertNotNull(r);
        // The solved chain should place the end effector near the target
        Vector3 elbow = new Vector3(0, 0, 0).add(rotateY(r.upperRotation, new Vector3(0, -1, 0)));
        Vector3 end = new Vector3(elbow).add(rotateY(r.lowerRotation, new Vector3(0, -1, 0)));
        assertEquals(-1.4f, end.y, 0.1f, "end effector y near target");
    }

    @Test
    void fullyExtendedWhenTargetAtMaxReach() {
        TwoBoneIKSolver.Result r = TwoBoneIKSolver.solve(
            new Vector3(0, 0, 0),
            new Vector3(0, -2f, 0),     // exactly upper + lower
            new Vector3(0, 0, 1),
            1f, 1f
        );
        assertNotNull(r);
    }

    @Test
    void clampsWhenTargetBeyondReach() {
        TwoBoneIKSolver.Result r = TwoBoneIKSolver.solve(
            new Vector3(0, 0, 0),
            new Vector3(0, -5f, 0),     // way beyond reach
            new Vector3(0, 0, 1),
            1f, 1f
        );
        assertNotNull(r, "should return a clamped result, not null");
    }

    private Vector3 rotateY(Quaternion q, Vector3 v) {
        return new Vector3(v).mul(q);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.rig.TwoBoneIKSolverTest" --info`
Expected: FAIL — `TwoBoneIKSolver` does not exist.

- [ ] **Step 3: Implement TwoBoneIKSolver**

Analytical 2-bone IK using law of cosines. Inputs: root position, target position, pole vector, upper/lower bone lengths. Outputs: rotation quaternions for upper and lower bones.

```java
package com.galacticodyssey.fauna.rig;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

public final class TwoBoneIKSolver {

    public static final class Result {
        public final Quaternion upperRotation = new Quaternion();
        public final Quaternion lowerRotation = new Quaternion();
    }

    private static final Vector3 tmpDir = new Vector3();
    private static final Vector3 tmpAxis = new Vector3();

    public static Result solve(Vector3 rootPos, Vector3 targetPos, Vector3 poleVector,
                               float upperLen, float lowerLen) {
        Result r = new Result();
        float maxReach = upperLen + lowerLen;

        tmpDir.set(targetPos).sub(rootPos);
        float dist = tmpDir.len();
        if (dist < 1e-5f) {
            return r; // target at root — return identity rotations
        }

        // Clamp to reachable range
        dist = Math.min(dist, maxReach - 0.001f);
        dist = Math.max(dist, Math.abs(upperLen - lowerLen) + 0.001f);
        tmpDir.nor();

        // Law of cosines: angle at the root (between upper bone and direction to target)
        float cosUpper = (upperLen * upperLen + dist * dist - lowerLen * lowerLen)
                         / (2f * upperLen * dist);
        cosUpper = MathUtils.clamp(cosUpper, -1f, 1f);
        float upperAngle = (float) Math.acos(cosUpper);

        // Angle at the elbow/knee (between upper and lower bones, measured from straight)
        float cosElbow = (upperLen * upperLen + lowerLen * lowerLen - dist * dist)
                         / (2f * upperLen * lowerLen);
        cosElbow = MathUtils.clamp(cosElbow, -1f, 1f);
        float elbowAngle = (float) Math.acos(cosElbow);

        // Compute rotation axis from pole vector: perpendicular to both the aim direction and pole
        tmpAxis.set(tmpDir).crs(poleVector).nor();
        if (tmpAxis.len2() < 1e-6f) {
            // pole is parallel to aim — pick an arbitrary perpendicular
            tmpAxis.set(tmpDir).crs(Vector3.Z).nor();
            if (tmpAxis.len2() < 1e-6f) tmpAxis.set(tmpDir).crs(Vector3.X).nor();
        }

        // Upper bone: rotate from -Y (default bone down direction) toward target, then offset by upper angle
        Quaternion aimRot = new Quaternion().setFromCross(Vector3.Y.cpy().scl(-1f), tmpDir);
        Quaternion offsetRot = new Quaternion().setFromAxisRad(tmpAxis, -upperAngle);
        r.upperRotation.set(aimRot).mul(offsetRot);

        // Lower bone: bend at elbow (rotation around the bend axis)
        float bendAngle = MathUtils.PI - elbowAngle;
        r.lowerRotation.setFromAxisRad(tmpAxis, bendAngle);

        return r;
    }

    private TwoBoneIKSolver() {}
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.rig.TwoBoneIKSolverTest" --info`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/rig/TwoBoneIKSolver.java \
        core/src/test/java/com/galacticodyssey/fauna/rig/TwoBoneIKSolverTest.java
git commit -m "feat(fauna): TwoBoneIKSolver — analytical 2-bone IK for creature legs"
```

---

### Task 4: GaitController interface and GaitParams

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/animation/GaitController.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/animation/GaitParams.java`

- [ ] **Step 1: Write GaitParams**

Data class that holds the per-frame inputs a gait controller needs.

```java
package com.galacticodyssey.fauna.animation;

import com.badlogic.gdx.math.Vector3;

public final class GaitParams {
    public float deltaTime;
    public float speed;           // current movement speed (0 = idle)
    public float maxSpeed;        // creature's max move speed
    public float sizeMultiplier;  // from CreatureSpec
    public final Vector3 heading = new Vector3(0, 0, 1);     // creature facing direction (world)
    public final Vector3 position = new Vector3();            // creature world position
    public final Vector3 lookTarget = new Vector3();          // head look-at target (world)
    public boolean hasLookTarget = false;
    public float elapsedTime;     // total animation time (accumulated)
}
```

- [ ] **Step 2: Write GaitController interface**

```java
package com.galacticodyssey.fauna.animation;

import com.galacticodyssey.fauna.rig.CreatureRig;

public interface GaitController {
    void update(CreatureRig rig, GaitParams params);
    void reset(CreatureRig rig);
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/animation/GaitController.java \
        core/src/main/java/com/galacticodyssey/fauna/animation/GaitParams.java
git commit -m "feat(fauna): GaitController interface and GaitParams input data"
```

---

### Task 5: WalkGaitController

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/animation/WalkGaitController.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/animation/WalkGaitControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna.animation;

import com.badlogic.gdx.math.Matrix4;
import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.assembly.CreatureAssembler;
import com.galacticodyssey.fauna.rig.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalkGaitControllerTest {

    private CreatureRig quadRig;

    @BeforeEach
    void setUp() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"lf\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,0.6],\"mirrorGroup\":\"front\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"lr\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,-0.6],\"mirrorGroup\":\"rear\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0],\"jointHint\":\"neck\"} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"gaitClass\":\"walk\"," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"lf\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"lr\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        quadRig = new CreatureRigBuilder().build(spec);
    }

    @Test
    void updateModifiesHipBoneTransforms() {
        WalkGaitController ctrl = new WalkGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 2f;
        params.maxSpeed = 5f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 0.5f;

        // Snapshot bind poses of hip bones
        float[][] before = new float[quadRig.boneCount()][];
        for (int i = 0; i < quadRig.boneCount(); i++) {
            before[i] = quadRig.getBone(i).currentPose.val.clone();
        }

        ctrl.update(quadRig, params);

        // At least one hip bone should have a modified currentPose
        boolean anyChanged = false;
        for (Bone b : quadRig.bonesWithRole(BoneRole.HIP)) {
            for (int j = 0; j < 16; j++) {
                if (Math.abs(b.currentPose.val[j] - before[b.index][j]) > 1e-6f) {
                    anyChanged = true;
                    break;
                }
            }
            if (anyChanged) break;
        }
        assertTrue(anyChanged, "walk gait must modify at least one hip bone");
    }

    @Test
    void idleDoesNotModifyLegsAggressively() {
        WalkGaitController ctrl = new WalkGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 0f;   // idle
        params.maxSpeed = 5f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 0f;

        ctrl.update(quadRig, params);

        // At idle, hip bones should remain close to bind pose (only breathing/sway)
        for (Bone b : quadRig.bonesWithRole(BoneRole.HIP)) {
            float delta = 0f;
            for (int j = 0; j < 16; j++) {
                delta += Math.abs(b.currentPose.val[j] - b.bindPose.val[j]);
            }
            assertTrue(delta < 2f, "idle should produce only subtle hip variation, got delta=" + delta);
        }
    }

    @Test
    void resetRestoresBindPoses() {
        WalkGaitController ctrl = new WalkGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 0.5f;
        params.speed = 3f;
        params.maxSpeed = 5f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 2f;
        ctrl.update(quadRig, params);

        ctrl.reset(quadRig);

        for (int i = 0; i < quadRig.boneCount(); i++) {
            Bone b = quadRig.getBone(i);
            assertArrayEquals(b.bindPose.val, b.currentPose.val, 1e-6f,
                "reset should restore bind pose for bone " + i);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.animation.WalkGaitControllerTest" --info`
Expected: FAIL — `WalkGaitController` does not exist.

- [ ] **Step 3: Implement WalkGaitController**

```java
package com.galacticodyssey.fauna.animation;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.fauna.rig.Bone;
import com.galacticodyssey.fauna.rig.BoneRole;
import com.galacticodyssey.fauna.rig.CreatureRig;

import java.util.List;

public final class WalkGaitController implements GaitController {

    private static final float STRIDE_FREQ_BASE = 2.0f;   // Hz at max speed, unit scale
    private static final float STRIDE_AMP_BASE = 0.15f;   // radians swing amplitude
    private static final float BOB_AMP_BASE = 0.03f;      // metres vertical bob
    private static final float IDLE_BREATHE_FREQ = 0.5f;   // Hz
    private static final float IDLE_BREATHE_AMP = 0.005f;  // metres
    private static final float IDLE_SWAY_FREQ = 0.15f;
    private static final float IDLE_SWAY_AMP = 0.003f;

    @Override
    public void update(CreatureRig rig, GaitParams params) {
        float speedFrac = params.maxSpeed > 0 ? params.speed / params.maxSpeed : 0f;
        float freq = STRIDE_FREQ_BASE / Math.max(0.5f, params.sizeMultiplier);
        float t = params.elapsedTime;

        // Leg cycling
        List<Bone> hips = rig.bonesWithRole(BoneRole.HIP);
        int legCount = hips.size();
        for (int i = 0; i < legCount; i++) {
            Bone hip = hips.get(i);
            float phase = (float) i / Math.max(1, legCount) * MathUtils.PI2;
            float swing = MathUtils.sin(t * freq * MathUtils.PI2 + phase) * STRIDE_AMP_BASE * speedFrac;
            hip.currentPose.set(hip.bindPose);
            hip.currentPose.rotate(Vector3.X, swing * MathUtils.radiansToDegrees);
        }

        // Body bob on root
        Bone root = rig.root();
        float bob = MathUtils.sin(t * freq * MathUtils.PI2 * 2f) * BOB_AMP_BASE * speedFrac;
        root.currentPose.set(root.bindPose);
        root.currentPose.translate(0, bob, 0);

        // Idle breathing + sway when stationary
        if (speedFrac < 0.01f) {
            float breathe = MathUtils.sin(t * IDLE_BREATHE_FREQ * MathUtils.PI2) * IDLE_BREATHE_AMP;
            root.currentPose.translate(0, breathe, 0);
            float sway = MathUtils.sin(t * IDLE_SWAY_FREQ * MathUtils.PI2) * IDLE_SWAY_AMP;
            root.currentPose.translate(sway, 0, 0);
        }

        // Head look-at (damped)
        Bone neck = rig.findFirstWithRole(BoneRole.NECK);
        if (neck != null && params.hasLookTarget) {
            Vector3 toTarget = new Vector3(params.lookTarget).sub(params.position).nor();
            float yaw = MathUtils.atan2(toTarget.x, toTarget.z) * MathUtils.radiansToDegrees;
            float maxSlew = 5f; // degrees per frame
            yaw = MathUtils.clamp(yaw, -maxSlew, maxSlew);
            neck.currentPose.set(neck.bindPose);
            neck.currentPose.rotate(Vector3.Y, yaw * 0.3f); // damped
        }

        // Tail follow-through
        Bone tail = rig.findFirstWithRole(BoneRole.TAIL);
        if (tail != null) {
            float tailLag = MathUtils.sin(t * freq * MathUtils.PI2 - MathUtils.PI * 0.5f)
                            * STRIDE_AMP_BASE * 0.5f * speedFrac;
            tail.currentPose.set(tail.bindPose);
            tail.currentPose.rotate(Vector3.Y, tailLag * MathUtils.radiansToDegrees);
        }
    }

    @Override
    public void reset(CreatureRig rig) {
        for (int i = 0; i < rig.boneCount(); i++) {
            rig.getBone(i).currentPose.set(rig.getBone(i).bindPose);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.animation.WalkGaitControllerTest" --info`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/animation/WalkGaitController.java \
        core/src/test/java/com/galacticodyssey/fauna/animation/WalkGaitControllerTest.java
git commit -m "feat(fauna): WalkGaitController — sine-wave leg cycling, body bob, head look-at"
```

---

### Task 6: SkitterGaitController

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/animation/SkitterGaitController.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/animation/SkitterGaitControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna.animation;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.assembly.CreatureAssembler;
import com.galacticodyssey.fauna.rig.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkitterGaitControllerTest {

    private CreatureRig hexRig;

    @BeforeEach
    void setUp() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso_hex\",\"partType\":\"TORSO\",\"bodyPlans\":[\"HEXAPOD\"]," +
          "  \"geometry\":{\"shape\":\"CAPSULE\",\"length\":2.4,\"radius\":0.45}," +
          "  \"sockets\":[ {\"id\":\"la\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.4,-0.1,0.8],\"mirrorGroup\":\"a\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"lb\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.4,-0.1,0.0],\"mirrorGroup\":\"b\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"lc\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.4,-0.1,-0.8],\"mirrorGroup\":\"c\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.25],\"jointHint\":\"neck\"} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"hex\",\"bodyPlan\":\"HEXAPOD\",\"minSize\":1,\"maxSize\":1,\"density\":700," +
          "  \"gaitClass\":\"skitter\"," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"la\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"lb\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"lc\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("hex"), 42L);
        hexRig = new CreatureRigBuilder().build(spec);
    }

    @Test
    void hexapodHasSixHipBones() {
        assertEquals(6, hexRig.bonesWithRole(BoneRole.HIP).size(), "3 mirror pairs = 6 legs");
    }

    @Test
    void updateModifiesLegBones() {
        SkitterGaitController ctrl = new SkitterGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 3f;
        params.maxSpeed = 5f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 0.5f;

        float[][] before = new float[hexRig.boneCount()][];
        for (int i = 0; i < hexRig.boneCount(); i++) {
            before[i] = hexRig.getBone(i).currentPose.val.clone();
        }

        ctrl.update(hexRig, params);

        boolean anyChanged = false;
        for (Bone b : hexRig.bonesWithRole(BoneRole.HIP)) {
            for (int j = 0; j < 16; j++) {
                if (Math.abs(b.currentPose.val[j] - before[b.index][j]) > 1e-6f) {
                    anyChanged = true;
                    break;
                }
            }
            if (anyChanged) break;
        }
        assertTrue(anyChanged, "skitter gait must modify leg bones");
    }

    @Test
    void alternatingTripodGroupsHaveOppositePhase() {
        SkitterGaitController ctrl = new SkitterGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 3f;
        params.maxSpeed = 5f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 0.25f; // quarter cycle

        ctrl.update(hexRig, params);

        // With 6 legs in alternating tripod, legs at even indices and odd indices
        // should have different phase offsets (their poses should differ)
        java.util.List<Bone> hips = hexRig.bonesWithRole(BoneRole.HIP);
        assertTrue(hips.size() == 6);
        // Group A: indices 0,2,4; Group B: indices 1,3,5
        // They shouldn't all be identical
        boolean groupsDiffer = false;
        for (int j = 0; j < 16; j++) {
            if (Math.abs(hips.get(0).currentPose.val[j] - hips.get(1).currentPose.val[j]) > 1e-6f) {
                groupsDiffer = true;
                break;
            }
        }
        assertTrue(groupsDiffer, "alternating tripod groups should have different phases");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.animation.SkitterGaitControllerTest" --info`
Expected: FAIL — `SkitterGaitController` does not exist.

- [ ] **Step 3: Implement SkitterGaitController**

```java
package com.galacticodyssey.fauna.animation;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.fauna.rig.Bone;
import com.galacticodyssey.fauna.rig.BoneRole;
import com.galacticodyssey.fauna.rig.CreatureRig;

import java.util.List;

public final class SkitterGaitController implements GaitController {

    private static final float FREQ_BASE = 4.0f;      // faster than walk
    private static final float AMP_BASE = 0.10f;       // radians — smaller amplitude
    private static final float IDLE_BREATHE_AMP = 0.003f;

    @Override
    public void update(CreatureRig rig, GaitParams params) {
        float speedFrac = params.maxSpeed > 0 ? params.speed / params.maxSpeed : 0f;
        float freq = FREQ_BASE / Math.max(0.3f, params.sizeMultiplier);
        float t = params.elapsedTime;

        List<Bone> hips = rig.bonesWithRole(BoneRole.HIP);
        for (int i = 0; i < hips.size(); i++) {
            Bone hip = hips.get(i);
            // Alternating tripod: even-indexed legs in group A (phase 0), odd in group B (phase PI)
            float phase = (i % 2 == 0) ? 0f : MathUtils.PI;
            float swing = MathUtils.sin(t * freq * MathUtils.PI2 + phase) * AMP_BASE * speedFrac;
            hip.currentPose.set(hip.bindPose);
            hip.currentPose.rotate(Vector3.X, swing * MathUtils.radiansToDegrees);
        }

        // Minimal body motion — hexapods stay level
        Bone root = rig.root();
        root.currentPose.set(root.bindPose);
        if (speedFrac < 0.01f) {
            float breathe = MathUtils.sin(t * 0.5f * MathUtils.PI2) * IDLE_BREATHE_AMP;
            root.currentPose.translate(0, breathe, 0);
        }

        // Head look-at
        Bone neck = rig.findFirstWithRole(BoneRole.NECK);
        if (neck != null) {
            neck.currentPose.set(neck.bindPose);
            if (params.hasLookTarget) {
                Vector3 toTarget = new Vector3(params.lookTarget).sub(params.position).nor();
                float yaw = MathUtils.atan2(toTarget.x, toTarget.z) * MathUtils.radiansToDegrees;
                yaw = MathUtils.clamp(yaw, -5f, 5f);
                neck.currentPose.rotate(Vector3.Y, yaw * 0.3f);
            }
        }
    }

    @Override
    public void reset(CreatureRig rig) {
        for (int i = 0; i < rig.boneCount(); i++) {
            rig.getBone(i).currentPose.set(rig.getBone(i).bindPose);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.animation.SkitterGaitControllerTest" --info`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/animation/SkitterGaitController.java \
        core/src/test/java/com/galacticodyssey/fauna/animation/SkitterGaitControllerTest.java
git commit -m "feat(fauna): SkitterGaitController — alternating tripod gait for hexapods"
```

---

### Task 7: SlitherGaitController

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/animation/SlitherGaitController.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/animation/SlitherGaitControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna.animation;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.assembly.CreatureAssembler;
import com.galacticodyssey.fauna.rig.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlitherGaitControllerTest {

    private CreatureRig snakeRig;

    @BeforeEach
    void setUp() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"seg\",\"partType\":\"TORSO\",\"bodyPlans\":[\"SERPENTINE\"]," +
          "  \"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.6,\"radius\":0.2}," +
          "  \"sockets\":[ {\"id\":\"next\",\"acceptedType\":\"TORSO\",\"pos\":[0,0,0.6],\"jointHint\":\"spine\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0,0.6],\"jointHint\":\"neck\"} ] }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"snake\",\"bodyPlan\":\"SERPENTINE\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"gaitClass\":\"slither\"," +
          "  \"root\":{\"partType\":\"TORSO\",\"repeat\":4,\"continuationSocketId\":\"next\"," +
          "    \"children\":[ {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("snake"), 7L);
        snakeRig = new CreatureRigBuilder().build(spec);
    }

    @Test
    void spineBonesPresentInRig() {
        assertTrue(snakeRig.bonesWithRole(BoneRole.SPINE).size() >= 2,
            "serpentine should have multiple spine bones");
    }

    @Test
    void updateModifiesSpineBones() {
        SlitherGaitController ctrl = new SlitherGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 2f;
        params.maxSpeed = 4f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 1.0f;

        float[][] before = new float[snakeRig.boneCount()][];
        for (int i = 0; i < snakeRig.boneCount(); i++) {
            before[i] = snakeRig.getBone(i).currentPose.val.clone();
        }

        ctrl.update(snakeRig, params);

        boolean anySpineChanged = false;
        for (Bone b : snakeRig.bonesWithRole(BoneRole.SPINE)) {
            for (int j = 0; j < 16; j++) {
                if (Math.abs(b.currentPose.val[j] - before[b.index][j]) > 1e-6f) {
                    anySpineChanged = true;
                    break;
                }
            }
            if (anySpineChanged) break;
        }
        assertTrue(anySpineChanged, "slither must modify spine bones");
    }

    @Test
    void spineBoneHavePhaseOffsetRotations() {
        SlitherGaitController ctrl = new SlitherGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 2f;
        params.maxSpeed = 4f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 0.5f;

        ctrl.update(snakeRig, params);

        java.util.List<Bone> spines = snakeRig.bonesWithRole(BoneRole.SPINE);
        if (spines.size() >= 2) {
            // Adjacent spine bones should have different Y rotations (phase offset)
            boolean differ = false;
            for (int j = 0; j < 16; j++) {
                if (Math.abs(spines.get(0).currentPose.val[j] - spines.get(1).currentPose.val[j]) > 1e-6f) {
                    differ = true;
                    break;
                }
            }
            assertTrue(differ, "adjacent spine bones should have different phase-offset rotations");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.animation.SlitherGaitControllerTest" --info`
Expected: FAIL — `SlitherGaitController` does not exist.

- [ ] **Step 3: Implement SlitherGaitController**

```java
package com.galacticodyssey.fauna.animation;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.fauna.rig.Bone;
import com.galacticodyssey.fauna.rig.BoneRole;
import com.galacticodyssey.fauna.rig.CreatureRig;

import java.util.List;

public final class SlitherGaitController implements GaitController {

    private static final float WAVE_FREQ = 1.5f;           // Hz
    private static final float WAVE_AMP_BASE = 15f;        // degrees lateral swing
    private static final float PHASE_PER_BONE = 0.8f;      // radians phase offset between bones

    @Override
    public void update(CreatureRig rig, GaitParams params) {
        float speedFrac = params.maxSpeed > 0 ? params.speed / params.maxSpeed : 0f;
        float t = params.elapsedTime;
        float amp = WAVE_AMP_BASE * Math.max(0.15f, speedFrac); // subtle even when idle

        // Root bone: slight lateral wave at phase 0
        Bone root = rig.root();
        float rootAngle = MathUtils.sin(t * WAVE_FREQ * MathUtils.PI2) * amp * 0.5f;
        root.currentPose.set(root.bindPose);
        root.currentPose.rotate(Vector3.Y, rootAngle);

        // Spine bones: propagating lateral sine wave with phase offset per bone
        List<Bone> spines = rig.bonesWithRole(BoneRole.SPINE);
        for (int i = 0; i < spines.size(); i++) {
            Bone spine = spines.get(i);
            float phase = (i + 1) * PHASE_PER_BONE; // +1 because root is bone 0
            float angle = MathUtils.sin(t * WAVE_FREQ * MathUtils.PI2 - phase) * amp;
            spine.currentPose.set(spine.bindPose);
            spine.currentPose.rotate(Vector3.Y, angle);
        }

        // Head: counter-rotate slightly to keep head more stable
        Bone neck = rig.findFirstWithRole(BoneRole.NECK);
        if (neck != null) {
            float headPhase = (spines.size() + 1) * PHASE_PER_BONE;
            float headAngle = MathUtils.sin(t * WAVE_FREQ * MathUtils.PI2 - headPhase) * amp * 0.3f;
            neck.currentPose.set(neck.bindPose);
            neck.currentPose.rotate(Vector3.Y, -headAngle); // counter-rotate
        }
    }

    @Override
    public void reset(CreatureRig rig) {
        for (int i = 0; i < rig.boneCount(); i++) {
            rig.getBone(i).currentPose.set(rig.getBone(i).bindPose);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.animation.SlitherGaitControllerTest" --info`
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/animation/SlitherGaitController.java \
        core/src/test/java/com/galacticodyssey/fauna/animation/SlitherGaitControllerTest.java
git commit -m "feat(fauna): SlitherGaitController — lateral sine wave for serpentines"
```

---

### Task 8: CreatureAnimationComponent and GaitControllerFactory

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/components/CreatureAnimationComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/animation/GaitControllerFactory.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/animation/GaitControllerFactoryTest.java`

- [ ] **Step 1: Write CreatureAnimationComponent**

```java
package com.galacticodyssey.fauna.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.fauna.animation.GaitController;
import com.galacticodyssey.fauna.animation.GaitParams;
import com.galacticodyssey.fauna.rig.CreatureRig;

public class CreatureAnimationComponent implements Component {
    public CreatureRig rig;
    public GaitController gaitController;
    public final GaitParams params = new GaitParams();
}
```

- [ ] **Step 2: Write the failing test for GaitControllerFactory**

```java
package com.galacticodyssey.fauna.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GaitControllerFactoryTest {

    @Test
    void walkGaitClassReturnsWalkController() {
        GaitController ctrl = GaitControllerFactory.create("walk");
        assertInstanceOf(WalkGaitController.class, ctrl);
    }

    @Test
    void skitterGaitClassReturnsSkitterController() {
        GaitController ctrl = GaitControllerFactory.create("skitter");
        assertInstanceOf(SkitterGaitController.class, ctrl);
    }

    @Test
    void slitherGaitClassReturnsSlitherController() {
        GaitController ctrl = GaitControllerFactory.create("slither");
        assertInstanceOf(SlitherGaitController.class, ctrl);
    }

    @Test
    void unknownGaitClassDefaultsToWalk() {
        GaitController ctrl = GaitControllerFactory.create("gallop");
        assertInstanceOf(WalkGaitController.class, ctrl);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.animation.GaitControllerFactoryTest" --info`
Expected: FAIL — `GaitControllerFactory` does not exist.

- [ ] **Step 4: Implement GaitControllerFactory**

```java
package com.galacticodyssey.fauna.animation;

public final class GaitControllerFactory {

    public static GaitController create(String gaitClass) {
        if (gaitClass == null) return new WalkGaitController();
        switch (gaitClass) {
            case "skitter": return new SkitterGaitController();
            case "slither": return new SlitherGaitController();
            case "walk":
            default:        return new WalkGaitController();
        }
    }

    private GaitControllerFactory() {}
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.animation.GaitControllerFactoryTest" --info`
Expected: 4 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/components/CreatureAnimationComponent.java \
        core/src/main/java/com/galacticodyssey/fauna/animation/GaitControllerFactory.java \
        core/src/test/java/com/galacticodyssey/fauna/animation/GaitControllerFactoryTest.java
git commit -m "feat(fauna): CreatureAnimationComponent + GaitControllerFactory"
```

---

### Task 9: Rebuild CreatureMeshBuilder for node hierarchy

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/fauna/CreatureMeshBuilder.java`
- Modify: `core/src/main/java/com/galacticodyssey/fauna/components/CreatureRenderComponent.java`

The current `CreatureMeshBuilder.build()` returns `Array<ModelInstance>` (one per part). We add a new method `buildSkinned()` that returns a single `ModelInstance` with a `Node` hierarchy matching the `CreatureRig` bone tree. Each node carries its part's mesh as a `NodePart`. The old `build()` method is kept for backward compatibility during the transition.

- [ ] **Step 1: Add `buildSkinned` method to CreatureMeshBuilder**

Add a new method below the existing `build()`. It uses `ModelBuilder` to create all nodes flat, then rearranges them into a parent-child hierarchy, and assigns local transforms from bone bind poses.

```java
// Add these imports at the top of CreatureMeshBuilder.java:
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.galacticodyssey.fauna.rig.CreatureRig;
import java.util.HashMap;
import java.util.Map;

// Add this new method after the existing build() method:

/** Returns a single ModelInstance with a node hierarchy driven by the rig. */
public ModelInstance buildSkinned(CreatureSpec spec, CreatureRig rig) {
    com.badlogic.gdx.graphics.g3d.utils.ModelBuilder mb = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
    mb.begin();

    Map<Integer, Node> nodeMap = new HashMap<>();
    for (int i = 0; i < spec.allNodes.size(); i++) {
        AssembledNode an = spec.allNodes.get(i);
        PartGeometryProvider prov = providerFor(an.part.geometry);

        Node node = mb.node();
        node.id = rig.getBone(i).name;
        // Mesh for this part
        com.badlogic.gdx.graphics.Color color = com.badlogic.gdx.graphics.Color.GRAY;
        com.badlogic.gdx.graphics.g3d.Material mat = new com.badlogic.gdx.graphics.g3d.Material(
            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color));
        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder mpb = mb.part(
            "mesh_" + i, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal, mat);

        // Build geometry using the mesher (same as ProceduralPartProvider but inline)
        com.galacticodyssey.fauna.geometry.ProceduralMeshData data =
            new com.galacticodyssey.fauna.geometry.ProceduralPartMesher().build(an.part.geometry);
        float[] p = data.positions;
        for (int t = 0; t < data.indices.length; t += 3) {
            int a = data.indices[t] & 0xFFFF;
            int b = data.indices[t + 1] & 0xFFFF;
            int c = data.indices[t + 2] & 0xFFFF;
            mpb.triangle(
                new com.badlogic.gdx.math.Vector3(p[a*3], p[a*3+1], p[a*3+2]),
                new com.badlogic.gdx.math.Vector3(p[b*3], p[b*3+1], p[b*3+2]),
                new com.badlogic.gdx.math.Vector3(p[c*3], p[c*3+1], p[c*3+2]));
        }

        nodeMap.put(i, node);
    }

    Model model = mb.end();
    ownedModels.add(model);

    // Rearrange into bone hierarchy: remove children from top-level, add as children of parent
    for (int i = 0; i < rig.boneCount(); i++) {
        Node node = nodeMap.get(i);
        com.galacticodyssey.fauna.rig.Bone bone = rig.getBone(i);
        // Set local transform from bind pose
        node.localTransform.set(bone.bindPose);
        // Scale the mesh node
        node.localTransform.scl(spec.allNodes.get(i).scale);

        if (bone.parentIndex >= 0) {
            Node parent = nodeMap.get(bone.parentIndex);
            model.nodes.removeValue(node, true);
            parent.addChild(node);
        }
    }

    model.calculateTransforms();
    return new ModelInstance(model);
}
```

- [ ] **Step 2: Update CreatureRenderComponent**

Change the `modelInstance` field from `Object` to `ModelInstance` and remove the flat tint fields (to be replaced by `CreatureSkinSpec` in Cycle C). Keep tint fields for now but add the typed field.

```java
// In CreatureRenderComponent.java, add a typed field:
import com.badlogic.gdx.graphics.g3d.ModelInstance;

public class CreatureRenderComponent implements Component {
    /** Legacy: Array<ModelInstance> from Cycle A build path. */
    public Object modelInstance = null;
    /** Skinned: single ModelInstance with bone node hierarchy. Set by Cycle B+ path. */
    public ModelInstance skinnedInstance = null;
    public float tintR = 0.6f, tintG = 0.6f, tintB = 0.6f;
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/CreatureMeshBuilder.java \
        core/src/main/java/com/galacticodyssey/fauna/components/CreatureRenderComponent.java
git commit -m "feat(fauna): CreatureMeshBuilder.buildSkinned — single Model with bone node hierarchy"
```

---

### Task 10: CreatureGaitSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/animation/CreatureGaitSystem.java`

This Ashley system iterates all entities with `CreatureAnimationComponent` + `TransformComponent`, ticks each creature's gait controller to update bone poses, then writes those poses to the `skinnedInstance`'s node tree.

- [ ] **Step 1: Implement CreatureGaitSystem**

```java
package com.galacticodyssey.fauna.animation;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.fauna.components.CreatureAnimationComponent;
import com.galacticodyssey.fauna.components.CreatureComponent;
import com.galacticodyssey.fauna.components.CreatureRenderComponent;
import com.galacticodyssey.fauna.rig.Bone;
import com.galacticodyssey.fauna.rig.CreatureRig;

public class CreatureGaitSystem extends IteratingSystem {

    private final ComponentMapper<CreatureAnimationComponent> animMapper =
        ComponentMapper.getFor(CreatureAnimationComponent.class);
    private final ComponentMapper<TransformComponent> txMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<CreatureComponent> creatureMapper =
        ComponentMapper.getFor(CreatureComponent.class);
    private final ComponentMapper<CreatureRenderComponent> renderMapper =
        ComponentMapper.getFor(CreatureRenderComponent.class);

    public CreatureGaitSystem(int priority) {
        super(Family.all(CreatureAnimationComponent.class, TransformComponent.class,
                         CreatureComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CreatureAnimationComponent anim = animMapper.get(entity);
        TransformComponent tx = txMapper.get(entity);
        CreatureComponent creature = creatureMapper.get(entity);

        if (anim.rig == null || anim.gaitController == null) return;

        // Update gait params
        anim.params.deltaTime = deltaTime;
        anim.params.elapsedTime += deltaTime;
        anim.params.position.set(tx.position);
        anim.params.sizeMultiplier = creature.spec.sizeMultiplier;
        // speed is set externally by movement/behavior systems; default to 0 (idle)

        // Tick gait controller — updates bone currentPoses
        anim.gaitController.update(anim.rig, anim.params);

        // Write bone poses to model nodes
        CreatureRenderComponent render = renderMapper.get(entity);
        if (render != null && render.skinnedInstance != null) {
            applyRigToModel(anim.rig, render.skinnedInstance);
        }
    }

    private void applyRigToModel(CreatureRig rig, ModelInstance instance) {
        for (int i = 0; i < rig.boneCount(); i++) {
            Bone bone = rig.getBone(i);
            Node node = instance.getNode(bone.name);
            if (node != null) {
                node.localTransform.set(bone.currentPose);
                // Re-apply scale (gait controllers write to currentPose which is local transform only)
                // Scale was baked into the node during buildSkinned, so we preserve it
            }
        }
        instance.calculateTransforms();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/animation/CreatureGaitSystem.java
git commit -m "feat(fauna): CreatureGaitSystem — ticks gait controllers, writes bone poses to model nodes"
```

---

### Task 11: Update CreatureFactory and FaunaDebugSpawner

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/fauna/CreatureFactory.java`
- Modify: `core/src/main/java/com/galacticodyssey/fauna/FaunaDebugSpawner.java`

- [ ] **Step 1: Update CreatureFactory to attach CreatureAnimationComponent**

```java
// Add imports at top of CreatureFactory.java:
import com.galacticodyssey.fauna.animation.GaitControllerFactory;
import com.galacticodyssey.fauna.components.CreatureAnimationComponent;
import com.galacticodyssey.fauna.rig.CreatureRig;
import com.galacticodyssey.fauna.rig.CreatureRigBuilder;

// Replace the create() method body:
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

    // Cycle B: build rig and attach animation component
    CreatureRig rig = new CreatureRigBuilder().build(spec);
    CreatureAnimationComponent anim = new CreatureAnimationComponent();
    anim.rig = rig;
    // Resolve gait class from the archetype def — stored in spec as bodyPlan,
    // but gaitClass comes from the archetype. We need it on the spec or passed in.
    // For now, use a lookup via bodyPlan as a reasonable default.
    anim.gaitController = GaitControllerFactory.create(resolveGaitClass(spec));
    anim.params.sizeMultiplier = spec.sizeMultiplier;
    e.add(anim);

    engine.addEntity(e);
    return e;
}

private String resolveGaitClass(CreatureSpec spec) {
    switch (spec.bodyPlan) {
        case HEXAPOD:    return "skitter";
        case SERPENTINE: return "slither";
        default:         return "walk";
    }
}
```

- [ ] **Step 2: Update FaunaDebugSpawner to use buildSkinned**

Replace the `spawnInFront` method to use the new skinned build path:

```java
// Add import at top of FaunaDebugSpawner.java:
import com.galacticodyssey.fauna.components.CreatureAnimationComponent;
import com.galacticodyssey.fauna.rig.CreatureRig;
import com.galacticodyssey.fauna.rig.CreatureRigBuilder;

// Replace spawnInFront method:
public Entity spawnInFront(Engine engine, Vector3 origin, Vector3 forward, float distance) {
    long seed = nextSeed++;
    CreatureSpec spec = generator.generate(seed);
    Vector3 flat = new Vector3(forward.x, 0f, forward.z).nor().scl(distance);
    Vector3 pos = new Vector3(origin).add(flat);
    Entity e = new CreatureFactory().create(engine, spec, pos);

    // Build skinned model with bone hierarchy
    CreatureAnimationComponent anim = e.getComponent(CreatureAnimationComponent.class);
    com.badlogic.gdx.graphics.g3d.ModelInstance instance = meshBuilder.buildSkinned(spec, anim.rig);
    instance.transform.translate(pos);
    CreatureRenderComponent render = e.getComponent(CreatureRenderComponent.class);
    render.skinnedInstance = instance;
    return e;
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/CreatureFactory.java \
        core/src/main/java/com/galacticodyssey/fauna/FaunaDebugSpawner.java
git commit -m "feat(fauna): wire CreatureFactory + FaunaDebugSpawner to use rig and skinned model"
```

---

### Task 12: Update GameScreen rendering and register CreatureGaitSystem

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` (renderCreatures method, ~line 1331)
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java` (system registration)

- [ ] **Step 1: Update renderCreatures in GameScreen**

The render loop now checks for the new `skinnedInstance` field first, falling back to the legacy `modelInstance` array for backward compat.

Find the `renderCreatures()` method (around line 1331) and replace its body:

```java
private void renderCreatures() {
    var creatures = gameWorld.getEngine().getEntitiesFor(
        Family.all(CreatureRenderComponent.class).get());
    creatureRenderQueue.clear();
    for (int i = 0; i < creatures.size(); i++) {
        CreatureRenderComponent render = creatures.get(i).getComponent(CreatureRenderComponent.class);
        if (render.skinnedInstance != null) {
            creatureRenderQueue.add(render.skinnedInstance);
        } else if (render.modelInstance instanceof Array) {
            creatureRenderQueue.addAll((Array<ModelInstance>) render.modelInstance);
        }
    }
    gbufferBatch.begin(camera);
    for (int i = 0; i < creatureRenderQueue.size; i++) {
        gbufferBatch.render(creatureRenderQueue.get(i));
    }
    gbufferBatch.end();
}
```

- [ ] **Step 2: Register CreatureGaitSystem in GameWorld**

Find the system registration section in `GameWorld.java`. Add the creature gait system after physics systems but before rendering. Look for the comment or region where creature-adjacent systems are (or near `PlayerAnimationSystem`). Add:

```java
engine.addSystem(new com.galacticodyssey.fauna.animation.CreatureGaitSystem(45));
```

Use priority 45 — after physics (priorities ~10–30) but before rendering systems (priorities ~80+). This matches the spec requirement: "priority between physics and rendering."

- [ ] **Step 3: Run all fauna tests to verify nothing is broken**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.*" --info`
Expected: All existing + new tests PASS (BoneRoleTest, CreatureRigBuilderTest, TwoBoneIKSolverTest, WalkGaitControllerTest, SkitterGaitControllerTest, SlitherGaitControllerTest, GaitControllerFactoryTest, plus existing CreatureGeneratorTest, CreatureAssemblerTest, etc.)

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java \
        core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(fauna): register CreatureGaitSystem, update render loop for skinned creatures"
```

---

### Task 13: Store gaitClass on CreatureSpec for proper resolution

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/fauna/CreatureSpec.java`
- Modify: `core/src/main/java/com/galacticodyssey/fauna/assembly/CreatureAssembler.java`
- Modify: `core/src/main/java/com/galacticodyssey/fauna/CreatureFactory.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/CreatureGeneratorTest.java`

Currently `gaitClass` lives only on `BodyPlanArchetypeDef` and is lost after assembly. The factory needs it to pick the right gait controller. The cleanest fix is to copy it onto `CreatureSpec` during assembly.

- [ ] **Step 1: Add gaitClass field to CreatureSpec**

```java
// In CreatureSpec.java, add after the bodyPlan field (line 14):
public String gaitClass = "walk";
```

- [ ] **Step 2: Set gaitClass in CreatureAssembler**

```java
// In CreatureAssembler.assemble(), after line 30 (spec.bodyPlan = arch.bodyPlan):
spec.gaitClass = arch.gaitClass;
```

- [ ] **Step 3: Update CreatureFactory to use spec.gaitClass**

Replace the `resolveGaitClass` method and its call:

```java
// In CreatureFactory.create(), replace the gaitController line:
anim.gaitController = GaitControllerFactory.create(spec.gaitClass);

// Remove the resolveGaitClass method entirely.
```

- [ ] **Step 4: Add test for gaitClass propagation**

Append to `CreatureGeneratorTest.java`:

```java
@Test
void gaitClassPropagatedToSpec() {
    CreatureSpec spec = new CreatureGenerator(reg).generate("quad", 42L);
    assertNotNull(spec.gaitClass);
    assertFalse(spec.gaitClass.isEmpty());
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.*" --info`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/CreatureSpec.java \
        core/src/main/java/com/galacticodyssey/fauna/assembly/CreatureAssembler.java \
        core/src/main/java/com/galacticodyssey/fauna/CreatureFactory.java \
        core/src/test/java/com/galacticodyssey/fauna/CreatureGeneratorTest.java
git commit -m "feat(fauna): propagate gaitClass from archetype through CreatureSpec to factory"
```
