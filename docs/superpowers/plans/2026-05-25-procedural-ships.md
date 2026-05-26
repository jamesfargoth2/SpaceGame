# Procedural Spaceship System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add procedurally generated spaceships that the player can approach, enter, walk around inside, sit in the pilot seat, and fly with 6DOF controls.

**Architecture:** Spline-based hull generation (Bezier spine + superellipse cross-sections) produces ship meshes at runtime. Interior rooms are packed into the hull volume via voxelization. A player state machine manages transitions between on-foot-exterior, on-foot-interior, and piloting modes. Ship flight uses Bullet physics forces with drag-based speed limiting.

**Tech Stack:** Java 21, libGDX 1.13.5, Ashley ECS 1.7.4, gdx-bullet, JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-25-procedural-ships-design.md`

---

## File Structure

### New files — ship package (`core/src/main/java/com/galacticodyssey/ship/`)

| File | Responsibility |
|------|----------------|
| `ShipSizeClass.java` | Enum: SMALL, MEDIUM, LARGE |
| `ShipBlueprint.java` | Seed + size class + parameter ranges (data class) |
| `SpineCurve.java` | Cubic Bezier spine evaluation + Catmull-Rom interpolation |
| `CrossSection.java` | Superellipse cross-section: generates ring of vertices at a given (width, height, exponent) |
| `HullGeometry.java` | Result object: float[] vertices, short[] indices, hardpoint positions, bounding box |
| `ShipHullGenerator.java` | Full pipeline: spine → cross-sections → loft → panel lines → sub-shapes → HullGeometry |
| `ShipInteriorGenerator.java` | Voxelization + room packing + corridor pathfinding + mesh generation |
| `InteriorLayout.java` | Result object: room list, corridor cells, airlock position, pilot seat position, mesh data |
| `RoomType.java` | Enum: COCKPIT, CORRIDOR, ENGINE_ROOM, CARGO_BAY, CREW_QUARTERS, MEDBAY, ARMORY (with color data) |
| `RoomPlacement.java` | Data class: RoomType + grid position + grid dimensions |
| `ShipColorPalette.java` | Generates base/accent/trim colors from a seed |
| `ShipFactory.java` | Orchestrates generators, creates Entity with all components |

### New files — ship components (`core/src/main/java/com/galacticodyssey/ship/components/`)

| File | Responsibility |
|------|----------------|
| `ShipDataComponent.java` | Blueprint ref, size class, seed, stats (mass, thrust, etc.) |
| `ShipMeshComponent.java` | Generated hull Mesh, ModelInstance, Disposable |
| `ShipInteriorComponent.java` | Interior mesh, btDynamicsWorld, room layout, Disposable |
| `ShipFlightComponent.java` | Throttle, thrust/torque values, drag, flight state |
| `PilotSeatComponent.java` | Interior-space trigger position, occupied flag, player ref |
| `ShipEntryPointComponent.java` | Ramp world position, trigger radius, open state |

### New files — ship systems (`core/src/main/java/com/galacticodyssey/ship/systems/`)

| File | Responsibility |
|------|----------------|
| `ShipFlightSystem.java` | Reads input when piloting, applies Bullet forces/torques |
| `ShipInteriorPhysicsSystem.java` | Steps each occupied ship's interior btDynamicsWorld |
| `ShipCameraSystem.java` | Cockpit + chase cam during piloting, toggle with V |
| `ShipRenderSystem.java` | Renders ship hull meshes using the ship shader |

### New files — player additions

| File | Responsibility |
|------|----------------|
| `core/.../player/components/PlayerStateComponent.java` | PlayerMode enum + currentShip + interactionTarget |
| `core/.../player/systems/InteractionSystem.java` | Proximity checks, interaction prompts, E-key transitions |

### New files — events (`core/src/main/java/com/galacticodyssey/core/events/`)

| File | Responsibility |
|------|----------------|
| `ShipEntryAvailableEvent.java` | Fired when player is near a ship ramp |
| `PlayerEnterShipEvent.java` | Fired when player enters a ship |
| `PilotSeatAvailableEvent.java` | Fired when player is near the pilot seat |
| `PlayerStartPilotingEvent.java` | Fired when player starts piloting |
| `PlayerStopPilotingEvent.java` | Fired when player stops piloting |
| `PlayerExitShipEvent.java` | Fired when player exits a ship |
| `InteractionPromptEvent.java` | Fired to show/hide interaction UI prompts |

### New files — data

| File | Responsibility |
|------|----------------|
| `core/src/main/resources/data/ships/ship-classes.json` | Size class parameter ranges + room manifests |
| `core/src/main/resources/data/ships/flight-params.json` | Per-class flight tuning |

### Modified files

| File | Change |
|------|--------|
| `GameWorld.java` | Add InteractionSystem, ShipFlightSystem, ShipInteriorPhysicsSystem, ShipCameraSystem. Expose engine + eventBus getters. |
| `GameScreen.java` | Create ShipFactory, spawn 3 ships, render ship meshes with ship shader, render interiors when inside. |
| `PlayerInputSystem.java` | Add E key (interact), V key (camera toggle), Q/E for roll. Route inputs based on PlayerMode. |
| `PlayerMovementSystem.java` | Skip processing when PlayerMode is PILOTING. Support switching btDynamicsWorld for interior. |
| `CameraSystem.java` | Skip processing when PlayerMode is PILOTING. |
| `DebugHudSystem.java` | Show PlayerMode, ship speed when piloting, interaction prompts. |
| `PlayerInputComponent.java` | Add interactPressed, cameraTogglePressed, rollLeft, rollRight, thrustUp, thrustDown fields. |

### Test files

| File | Tests |
|------|-------|
| `core/src/test/.../ship/SpineCurveTest.java` | Bezier evaluation, endpoint correctness, tangent direction |
| `core/src/test/.../ship/CrossSectionTest.java` | Superellipse vertex ring generation, vertex counts, symmetry |
| `core/src/test/.../ship/ShipHullGeneratorTest.java` | Mesh generation, vertex/index counts, bounding box, normals |
| `core/src/test/.../ship/ShipInteriorGeneratorTest.java` | Voxelization, room packing, corridor connectivity, airlock placement |
| `core/src/test/.../ship/ShipColorPaletteTest.java` | Deterministic seed, valid color ranges |
| `core/src/test/.../ship/ShipFactoryTest.java` | Entity creation, component presence, disposal |
| `core/src/test/.../player/components/PlayerStateComponentTest.java` | Mode transitions |
| `core/src/test/.../ship/systems/ShipFlightSystemTest.java` | Force/torque application, drag behavior |

---

## Task 1: Spine Curve Math

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/SpineCurve.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/SpineCurveTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpineCurveTest {

    @Test
    void evaluateReturnsStartPointAtT0() {
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(0, 0, -3);
        Vector3 p2 = new Vector3(0, 0, -7);
        Vector3 p3 = new Vector3(0, 0, -10);

        SpineCurve curve = new SpineCurve(p0, p1, p2, p3);
        Vector3 result = curve.evaluate(0f);

        assertEquals(0f, result.x, 0.001f);
        assertEquals(0f, result.y, 0.001f);
        assertEquals(0f, result.z, 0.001f);
    }

    @Test
    void evaluateReturnsEndPointAtT1() {
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(0, 1, -3);
        Vector3 p2 = new Vector3(0, 1, -7);
        Vector3 p3 = new Vector3(0, 0, -10);

        SpineCurve curve = new SpineCurve(p0, p1, p2, p3);
        Vector3 result = curve.evaluate(1f);

        assertEquals(0f, result.x, 0.001f);
        assertEquals(0f, result.y, 0.001f);
        assertEquals(-10f, result.z, 0.001f);
    }

    @Test
    void evaluateAtMidpointIsBetweenEndpoints() {
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(0, 0, -3);
        Vector3 p2 = new Vector3(0, 0, -7);
        Vector3 p3 = new Vector3(0, 0, -10);

        SpineCurve curve = new SpineCurve(p0, p1, p2, p3);
        Vector3 result = curve.evaluate(0.5f);

        assertTrue(result.z < 0f && result.z > -10f,
            "Midpoint z should be between 0 and -10, was " + result.z);
    }

    @Test
    void tangentAtStartPointsAlongCurve() {
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(0, 0, -3);
        Vector3 p2 = new Vector3(0, 0, -7);
        Vector3 p3 = new Vector3(0, 0, -10);

        SpineCurve curve = new SpineCurve(p0, p1, p2, p3);
        Vector3 tangent = curve.tangent(0f);

        assertTrue(tangent.z < 0f, "Tangent at t=0 should point in -Z direction");
        assertEquals(0f, tangent.x, 0.001f);
    }

    @Test
    void spineLength() {
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(0, 0, -3);
        Vector3 p2 = new Vector3(0, 0, -7);
        Vector3 p3 = new Vector3(0, 0, -10);

        SpineCurve curve = new SpineCurve(p0, p1, p2, p3);
        float length = curve.approximateLength(32);

        assertEquals(10f, length, 0.5f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.SpineCurveTest" --info`
Expected: compilation failure — `SpineCurve` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.math.Vector3;

public class SpineCurve {

    private final Vector3 p0, p1, p2, p3;

    public SpineCurve(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3) {
        this.p0 = new Vector3(p0);
        this.p1 = new Vector3(p1);
        this.p2 = new Vector3(p2);
        this.p3 = new Vector3(p3);
    }

    public Vector3 evaluate(float t) {
        float u = 1 - t;
        float u2 = u * u;
        float u3 = u2 * u;
        float t2 = t * t;
        float t3 = t2 * t;

        return new Vector3(
            u3 * p0.x + 3 * u2 * t * p1.x + 3 * u * t2 * p2.x + t3 * p3.x,
            u3 * p0.y + 3 * u2 * t * p1.y + 3 * u * t2 * p2.y + t3 * p3.y,
            u3 * p0.z + 3 * u2 * t * p1.z + 3 * u * t2 * p2.z + t3 * p3.z
        );
    }

    public Vector3 tangent(float t) {
        float u = 1 - t;
        float u2 = u * u;
        float t2 = t * t;

        return new Vector3(
            3 * u2 * (p1.x - p0.x) + 6 * u * t * (p2.x - p1.x) + 3 * t2 * (p3.x - p2.x),
            3 * u2 * (p1.y - p0.y) + 6 * u * t * (p2.y - p1.y) + 3 * t2 * (p3.y - p2.y),
            3 * u2 * (p1.z - p0.z) + 6 * u * t * (p2.z - p1.z) + 3 * t2 * (p3.z - p2.z)
        ).nor();
    }

    public float approximateLength(int segments) {
        float length = 0;
        Vector3 prev = evaluate(0);
        for (int i = 1; i <= segments; i++) {
            Vector3 curr = evaluate((float) i / segments);
            length += prev.dst(curr);
            prev = curr;
        }
        return length;
    }

    public Vector3 getP0() { return p0; }
    public Vector3 getP3() { return p3; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.SpineCurveTest" --info`
Expected: all 5 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/SpineCurve.java core/src/test/java/com/galacticodyssey/ship/SpineCurveTest.java
git commit -m "feat(ship): add SpineCurve cubic Bezier evaluation"
```

---

## Task 2: Cross-Section (Superellipse) Vertex Ring

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/CrossSection.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/CrossSectionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrossSectionTest {

    @Test
    void generatesCorrectNumberOfVertices() {
        CrossSection cs = new CrossSection(2f, 1.5f, 2.0f);
        float[][] ring = cs.generateRing(16);
        assertEquals(16, ring.length);
    }

    @Test
    void verticesLieWithinBounds() {
        float halfWidth = 3f;
        float halfHeight = 2f;
        CrossSection cs = new CrossSection(halfWidth, halfHeight, 2.0f);
        float[][] ring = cs.generateRing(32);

        for (float[] vertex : ring) {
            assertTrue(Math.abs(vertex[0]) <= halfWidth + 0.01f,
                "X=" + vertex[0] + " exceeds halfWidth=" + halfWidth);
            assertTrue(Math.abs(vertex[1]) <= halfHeight + 0.01f,
                "Y=" + vertex[1] + " exceeds halfHeight=" + halfHeight);
        }
    }

    @Test
    void ringIsSymmetricAcrossYAxis() {
        CrossSection cs = new CrossSection(2f, 1.5f, 2.5f);
        float[][] ring = cs.generateRing(32);

        // First vertex (angle=0) should be at positive X, near Y=0
        assertEquals(2f, ring[0][0], 0.01f);
        assertEquals(0f, ring[0][1], 0.01f);

        // At quarter point (angle=pi/2) should be near X=0, positive Y
        int quarter = 32 / 4;
        assertEquals(0f, ring[quarter][0], 0.1f);
        assertTrue(ring[quarter][1] > 0);
    }

    @Test
    void exponentAffectsShape() {
        CrossSection round = new CrossSection(2f, 2f, 2.0f);
        CrossSection boxy = new CrossSection(2f, 2f, 4.0f);

        float[][] roundRing = round.generateRing(32);
        float[][] boxyRing = boxy.generateRing(32);

        // At 45 degrees, boxy shape should be further from center than round
        int idx45 = 32 / 8;
        float roundDist = (float) Math.sqrt(roundRing[idx45][0] * roundRing[idx45][0] +
            roundRing[idx45][1] * roundRing[idx45][1]);
        float boxyDist = (float) Math.sqrt(boxyRing[idx45][0] * boxyRing[idx45][0] +
            boxyRing[idx45][1] * boxyRing[idx45][1]);

        assertTrue(boxyDist > roundDist,
            "Boxy shape should extend further at 45 degrees: boxy=" + boxyDist + " round=" + roundDist);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.CrossSectionTest" --info`
Expected: compilation failure — `CrossSection` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.math.MathUtils;

public class CrossSection {

    private final float halfWidth;
    private final float halfHeight;
    private final float exponent;

    public CrossSection(float halfWidth, float halfHeight, float exponent) {
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        this.exponent = exponent;
    }

    public float[][] generateRing(int vertexCount) {
        float[][] ring = new float[vertexCount][2];
        for (int i = 0; i < vertexCount; i++) {
            float angle = MathUtils.PI2 * i / vertexCount;
            float cosA = MathUtils.cos(angle);
            float sinA = MathUtils.sin(angle);

            float signX = Math.signum(cosA);
            float signY = Math.signum(sinA);
            float absCos = Math.abs(cosA);
            float absSin = Math.abs(sinA);

            float exp = 2f / exponent;
            float x = signX * halfWidth * (float) Math.pow(absCos, exp);
            float y = signY * halfHeight * (float) Math.pow(absSin, exp);

            ring[i][0] = x;
            ring[i][1] = y;
        }
        return ring;
    }

    public float getHalfWidth() { return halfWidth; }
    public float getHalfHeight() { return halfHeight; }
    public float getExponent() { return exponent; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.CrossSectionTest" --info`
Expected: all 4 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/CrossSection.java core/src/test/java/com/galacticodyssey/ship/CrossSectionTest.java
git commit -m "feat(ship): add CrossSection superellipse vertex ring generation"
```

---

## Task 3: Ship Size Class + Blueprint Data Types

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/ShipSizeClass.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ShipBlueprint.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/RoomType.java`

- [ ] **Step 1: Create ShipSizeClass enum**

```java
package com.galacticodyssey.ship;

public enum ShipSizeClass {
    SMALL(8f, 12f, 5, 7, 3f, 5f, 2f, 4f, 0, 1, 1, 2),
    MEDIUM(18f, 30f, 8, 12, 6f, 12f, 4f, 8f, 1, 2, 2, 4),
    LARGE(40f, 70f, 12, 18, 15f, 25f, 8f, 15f, 1, 3, 2, 6);

    public final float minSpineLength, maxSpineLength;
    public final int minCrossSections, maxCrossSections;
    public final float minWidth, maxWidth;
    public final float minHeight, maxHeight;
    public final int minWingPairs, maxWingPairs;
    public final int minEnginePods, maxEnginePods;

    ShipSizeClass(float minSpineLength, float maxSpineLength,
                  int minCrossSections, int maxCrossSections,
                  float minWidth, float maxWidth,
                  float minHeight, float maxHeight,
                  int minWingPairs, int maxWingPairs,
                  int minEnginePods, int maxEnginePods) {
        this.minSpineLength = minSpineLength;
        this.maxSpineLength = maxSpineLength;
        this.minCrossSections = minCrossSections;
        this.maxCrossSections = maxCrossSections;
        this.minWidth = minWidth;
        this.maxWidth = maxWidth;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.minWingPairs = minWingPairs;
        this.maxWingPairs = maxWingPairs;
        this.minEnginePods = minEnginePods;
        this.maxEnginePods = maxEnginePods;
    }
}
```

- [ ] **Step 2: Create RoomType enum**

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.graphics.Color;

public enum RoomType {
    COCKPIT(3, 3, 2, 4, 4, 3,
        new Color(0.25f, 0.25f, 0.25f, 1f), new Color(0.3f, 0.4f, 0.7f, 1f)),
    CORRIDOR(1, 1, 2, 1, 1, 3,
        new Color(0.4f, 0.4f, 0.4f, 1f), new Color(0.8f, 0.8f, 0.8f, 1f)),
    ENGINE_ROOM(3, 3, 2, 5, 5, 3,
        new Color(0.2f, 0.2f, 0.22f, 1f), new Color(0.8f, 0.4f, 0.2f, 1f)),
    CARGO_BAY(4, 3, 2, 6, 5, 3,
        new Color(0.35f, 0.28f, 0.18f, 1f), new Color(0.8f, 0.75f, 0.2f, 1f)),
    CREW_QUARTERS(3, 3, 2, 3, 3, 2,
        new Color(0.4f, 0.38f, 0.35f, 1f), new Color(0.85f, 0.85f, 0.8f, 1f)),
    MEDBAY(3, 2, 2, 3, 2, 2,
        new Color(0.8f, 0.8f, 0.8f, 1f), new Color(0.3f, 0.7f, 0.6f, 1f)),
    ARMORY(2, 2, 2, 2, 2, 2,
        new Color(0.25f, 0.25f, 0.25f, 1f), new Color(0.7f, 0.2f, 0.2f, 1f));

    public final int minSizeX, minSizeZ, minSizeY;
    public final int maxSizeX, maxSizeZ, maxSizeY;
    public final Color floorColor;
    public final Color accentColor;

    RoomType(int minSizeX, int minSizeZ, int minSizeY,
             int maxSizeX, int maxSizeZ, int maxSizeY,
             Color floorColor, Color accentColor) {
        this.minSizeX = minSizeX;
        this.minSizeZ = minSizeZ;
        this.minSizeY = minSizeY;
        this.maxSizeX = maxSizeX;
        this.maxSizeZ = maxSizeZ;
        this.maxSizeY = maxSizeY;
        this.floorColor = floorColor;
        this.accentColor = accentColor;
    }
}
```

- [ ] **Step 3: Create ShipBlueprint**

```java
package com.galacticodyssey.ship;

import java.util.Random;

public class ShipBlueprint {

    public final long seed;
    public final ShipSizeClass sizeClass;
    public final float spineLength;
    public final int crossSectionCount;
    public final float maxWidth;
    public final float maxHeight;
    public final int wingPairs;
    public final int enginePodCount;

    public ShipBlueprint(long seed, ShipSizeClass sizeClass) {
        this.seed = seed;
        this.sizeClass = sizeClass;

        Random rng = new Random(seed);
        this.spineLength = lerp(sizeClass.minSpineLength, sizeClass.maxSpineLength, rng.nextFloat());
        this.crossSectionCount = sizeClass.minCrossSections +
            rng.nextInt(sizeClass.maxCrossSections - sizeClass.minCrossSections + 1);
        this.maxWidth = lerp(sizeClass.minWidth, sizeClass.maxWidth, rng.nextFloat());
        this.maxHeight = lerp(sizeClass.minHeight, sizeClass.maxHeight, rng.nextFloat());
        this.wingPairs = sizeClass.minWingPairs +
            rng.nextInt(sizeClass.maxWingPairs - sizeClass.minWingPairs + 1);
        this.enginePodCount = sizeClass.minEnginePods +
            rng.nextInt(sizeClass.maxEnginePods - sizeClass.minEnginePods + 1);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
```

- [ ] **Step 4: Run compilation check**

Run: `.\gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/ShipSizeClass.java core/src/main/java/com/galacticodyssey/ship/ShipBlueprint.java core/src/main/java/com/galacticodyssey/ship/RoomType.java
git commit -m "feat(ship): add ShipSizeClass, RoomType, and ShipBlueprint data types"
```

---

## Task 4: Hull Geometry and ShipHullGenerator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/HullGeometry.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ShipColorPalette.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ShipHullGenerator.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ShipHullGeneratorTest.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ShipColorPaletteTest.java`

- [ ] **Step 1: Create HullGeometry result object**

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

public class HullGeometry {

    public final float[] vertices;
    public final short[] indices;
    public final BoundingBox boundingBox;
    public final Vector3[] hardpoints;
    public final int vertexStride;

    public HullGeometry(float[] vertices, short[] indices, BoundingBox boundingBox,
                        Vector3[] hardpoints, int vertexStride) {
        this.vertices = vertices;
        this.indices = indices;
        this.boundingBox = boundingBox;
        this.hardpoints = hardpoints;
        this.vertexStride = vertexStride;
    }

    public int vertexCount() {
        return vertices.length / vertexStride;
    }

    public int triangleCount() {
        return indices.length / 3;
    }
}
```

- [ ] **Step 2: Create ShipColorPalette**

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.graphics.Color;

import java.util.Random;

public class ShipColorPalette {

    private static final Color[] BASE_COLORS = {
        new Color(0.6f, 0.6f, 0.62f, 1f),
        new Color(0.8f, 0.8f, 0.82f, 1f),
        new Color(0.2f, 0.25f, 0.35f, 1f),
        new Color(0.25f, 0.32f, 0.22f, 1f),
        new Color(0.35f, 0.35f, 0.38f, 1f),
        new Color(0.45f, 0.42f, 0.4f, 1f),
    };

    private static final Color[] ACCENT_COLORS = {
        new Color(0.9f, 0.3f, 0.2f, 1f),
        new Color(0.2f, 0.5f, 0.9f, 1f),
        new Color(0.9f, 0.7f, 0.1f, 1f),
        new Color(0.1f, 0.8f, 0.5f, 1f),
        new Color(0.8f, 0.4f, 0.0f, 1f),
        new Color(0.6f, 0.2f, 0.8f, 1f),
    };

    public final Color baseColor;
    public final Color accentColor;
    public final Color trimColor;
    public final Color engineGlowColor;

    public ShipColorPalette(long seed) {
        Random rng = new Random(seed);
        baseColor = new Color(BASE_COLORS[rng.nextInt(BASE_COLORS.length)]);
        accentColor = new Color(ACCENT_COLORS[rng.nextInt(ACCENT_COLORS.length)]);
        trimColor = new Color(baseColor).lerp(accentColor, 0.3f);

        float glowHue = rng.nextFloat();
        if (glowHue < 0.33f) engineGlowColor = new Color(0.3f, 0.5f, 1f, 1f);
        else if (glowHue < 0.66f) engineGlowColor = new Color(1f, 0.6f, 0.2f, 1f);
        else engineGlowColor = new Color(0.9f, 0.9f, 1f, 1f);
    }
}
```

- [ ] **Step 3: Write failing tests for ShipColorPalette**

```java
package com.galacticodyssey.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipColorPaletteTest {

    @Test
    void sameSeedProducesSameColors() {
        ShipColorPalette a = new ShipColorPalette(42L);
        ShipColorPalette b = new ShipColorPalette(42L);

        assertEquals(a.baseColor, b.baseColor);
        assertEquals(a.accentColor, b.accentColor);
        assertEquals(a.trimColor, b.trimColor);
        assertEquals(a.engineGlowColor, b.engineGlowColor);
    }

    @Test
    void differentSeedsProduceDifferentColors() {
        ShipColorPalette a = new ShipColorPalette(1L);
        ShipColorPalette b = new ShipColorPalette(999L);

        // At least one color should differ (statistically near-certain)
        boolean anyDifferent = !a.baseColor.equals(b.baseColor)
            || !a.accentColor.equals(b.accentColor)
            || !a.engineGlowColor.equals(b.engineGlowColor);
        assertTrue(anyDifferent);
    }

    @Test
    void colorsAreInValidRange() {
        ShipColorPalette p = new ShipColorPalette(42L);
        assertTrue(p.baseColor.r >= 0f && p.baseColor.r <= 1f);
        assertTrue(p.baseColor.g >= 0f && p.baseColor.g <= 1f);
        assertTrue(p.baseColor.b >= 0f && p.baseColor.b <= 1f);
    }
}
```

- [ ] **Step 4: Run color palette tests**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.ShipColorPaletteTest" --info`
Expected: all 3 tests PASS.

- [ ] **Step 5: Write ShipHullGenerator**

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ShipHullGenerator {

    private static final int RING_VERTEX_COUNT = 24;
    private static final int INTERPOLATION_RINGS = 4;
    private static final float PANEL_INSET = 0.015f;
    // vertex stride: px, py, pz, nx, ny, nz, r, g, b, a, emissive = 11
    public static final int VERTEX_STRIDE = 11;

    public HullGeometry generate(ShipBlueprint blueprint) {
        Random rng = new Random(blueprint.seed + 1);
        ShipColorPalette palette = new ShipColorPalette(blueprint.seed);

        SpineCurve spine = generateSpine(blueprint, rng);
        List<CrossSection> sections = generateCrossSections(blueprint, rng);
        List<Float> tValues = generateTValues(sections.size());

        List<float[]> allRings = new ArrayList<>();
        List<Vector3> ringCenters = new ArrayList<>();
        List<Vector3> ringTangents = new ArrayList<>();

        for (int i = 0; i < sections.size() - 1; i++) {
            CrossSection csA = sections.get(i);
            CrossSection csB = sections.get(i + 1);
            float tA = tValues.get(i);
            float tB = tValues.get(i + 1);

            for (int j = 0; j <= INTERPOLATION_RINGS; j++) {
                if (i > 0 && j == 0) continue;
                float frac = (float) j / INTERPOLATION_RINGS;
                float t = MathUtils.lerp(tA, tB, frac);

                float w = MathUtils.lerp(csA.getHalfWidth(), csB.getHalfWidth(), frac);
                float h = MathUtils.lerp(csA.getHalfHeight(), csB.getHalfHeight(), frac);
                float e = MathUtils.lerp(csA.getExponent(), csB.getExponent(), frac);

                CrossSection interpolated = new CrossSection(w, h, e);
                allRings.add(flattenRing(interpolated.generateRing(RING_VERTEX_COUNT)));
                ringCenters.add(spine.evaluate(t));
                ringTangents.add(spine.tangent(t));
            }
        }

        int ringCount = allRings.size();
        int vertsPerRing = RING_VERTEX_COUNT;
        int totalVerts = ringCount * vertsPerRing + 2;
        float[] vertices = new float[totalVerts * VERTEX_STRIDE];
        List<Vector3> hardpointList = new ArrayList<>();

        BoundingBox bbox = new BoundingBox();
        bbox.inf();

        for (int r = 0; r < ringCount; r++) {
            float[] ring = allRings.get(r);
            Vector3 center = ringCenters.get(r);
            Vector3 tangent = ringTangents.get(r);

            Vector3 up = new Vector3(0, 1, 0);
            if (Math.abs(tangent.dot(up)) > 0.99f) up.set(1, 0, 0);
            Vector3 right = new Vector3(tangent).crs(up).nor();
            Vector3 realUp = new Vector3(right).crs(tangent).nor();

            boolean panelInset = (r % 2 == 0);

            for (int v = 0; v < vertsPerRing; v++) {
                float localX = ring[v * 2];
                float localY = ring[v * 2 + 1];

                float insetScale = panelInset && (v % 3 != 0) ? (1f - PANEL_INSET) : 1f;
                localX *= insetScale;
                localY *= insetScale;

                float worldX = center.x + right.x * localX + realUp.x * localY;
                float worldY = center.y + right.y * localX + realUp.y * localY;
                float worldZ = center.z + right.z * localX + realUp.z * localY;

                float nx = right.x * localX + realUp.x * localY;
                float ny = right.y * localX + realUp.y * localY;
                float nz = right.z * localX + realUp.z * localY;
                float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nLen > 0.001f) { nx /= nLen; ny /= nLen; nz /= nLen; }

                int idx = (r * vertsPerRing + v) * VERTEX_STRIDE;
                vertices[idx] = worldX;
                vertices[idx + 1] = worldY;
                vertices[idx + 2] = worldZ;
                vertices[idx + 3] = nx;
                vertices[idx + 4] = ny;
                vertices[idx + 5] = nz;

                float colorBlend = panelInset && (v % 3 != 0) ? 0.15f : 0f;
                vertices[idx + 6] = MathUtils.lerp(palette.baseColor.r, palette.trimColor.r, colorBlend);
                vertices[idx + 7] = MathUtils.lerp(palette.baseColor.g, palette.trimColor.g, colorBlend);
                vertices[idx + 8] = MathUtils.lerp(palette.baseColor.b, palette.trimColor.b, colorBlend);
                vertices[idx + 9] = 1f;
                vertices[idx + 10] = 0f;

                bbox.ext(worldX, worldY, worldZ);
            }
        }

        // nose cap vertex
        Vector3 noseCenter = ringCenters.get(0);
        int noseIdx = ringCount * vertsPerRing * VERTEX_STRIDE;
        vertices[noseIdx] = noseCenter.x;
        vertices[noseIdx + 1] = noseCenter.y;
        vertices[noseIdx + 2] = noseCenter.z;
        Vector3 noseTan = ringTangents.get(0);
        vertices[noseIdx + 3] = -noseTan.x;
        vertices[noseIdx + 4] = -noseTan.y;
        vertices[noseIdx + 5] = -noseTan.z;
        vertices[noseIdx + 6] = palette.baseColor.r;
        vertices[noseIdx + 7] = palette.baseColor.g;
        vertices[noseIdx + 8] = palette.baseColor.b;
        vertices[noseIdx + 9] = 1f;
        vertices[noseIdx + 10] = 0f;

        // tail cap vertex
        Vector3 tailCenter = ringCenters.get(ringCount - 1);
        int tailIdx = (ringCount * vertsPerRing + 1) * VERTEX_STRIDE;
        vertices[tailIdx] = tailCenter.x;
        vertices[tailIdx + 1] = tailCenter.y;
        vertices[tailIdx + 2] = tailCenter.z;
        Vector3 tailTan = ringTangents.get(ringCount - 1);
        vertices[tailIdx + 3] = tailTan.x;
        vertices[tailIdx + 4] = tailTan.y;
        vertices[tailIdx + 5] = tailTan.z;
        vertices[tailIdx + 6] = palette.engineGlowColor.r;
        vertices[tailIdx + 7] = palette.engineGlowColor.g;
        vertices[tailIdx + 8] = palette.engineGlowColor.b;
        vertices[tailIdx + 9] = 1f;
        vertices[tailIdx + 10] = 1f;

        // indices: connect rings into quads, plus nose/tail caps
        int quadRows = ringCount - 1;
        int quadsPerRow = vertsPerRing;
        int hullTriangles = quadRows * quadsPerRow * 2;
        int capTriangles = vertsPerRing * 2;
        short[] indices = new short[(hullTriangles + capTriangles) * 3];
        int ii = 0;

        for (int r = 0; r < ringCount - 1; r++) {
            for (int v = 0; v < vertsPerRing; v++) {
                int v2 = (v + 1) % vertsPerRing;
                short a = (short)(r * vertsPerRing + v);
                short b = (short)(r * vertsPerRing + v2);
                short c = (short)((r + 1) * vertsPerRing + v);
                short d = (short)((r + 1) * vertsPerRing + v2);

                indices[ii++] = a; indices[ii++] = c; indices[ii++] = b;
                indices[ii++] = b; indices[ii++] = c; indices[ii++] = d;
            }
        }

        // nose cap
        short noseVertIdx = (short)(ringCount * vertsPerRing);
        for (int v = 0; v < vertsPerRing; v++) {
            int v2 = (v + 1) % vertsPerRing;
            indices[ii++] = noseVertIdx;
            indices[ii++] = (short)v2;
            indices[ii++] = (short)v;
        }

        // tail cap
        short tailVertIdx = (short)(ringCount * vertsPerRing + 1);
        int lastRingStart = (ringCount - 1) * vertsPerRing;
        for (int v = 0; v < vertsPerRing; v++) {
            int v2 = (v + 1) % vertsPerRing;
            indices[ii++] = tailVertIdx;
            indices[ii++] = (short)(lastRingStart + v);
            indices[ii++] = (short)(lastRingStart + v2);
        }

        // wing hardpoints at mid-spine on the sides
        float midT = 0.4f;
        Vector3 midPoint = spine.evaluate(midT);
        hardpointList.add(new Vector3(midPoint));

        return new HullGeometry(vertices, indices, bbox,
            hardpointList.toArray(new Vector3[0]), VERTEX_STRIDE);
    }

    private SpineCurve generateSpine(ShipBlueprint blueprint, Random rng) {
        float len = blueprint.spineLength;
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p3 = new Vector3(0, 0, -len);

        float ctrlOffset = len * 0.3f;
        float yVariation = len * 0.05f;
        Vector3 p1 = new Vector3(0, rng.nextFloat() * yVariation, -ctrlOffset);
        Vector3 p2 = new Vector3(0, rng.nextFloat() * yVariation, -(len - ctrlOffset));

        return new SpineCurve(p0, p1, p2, p3);
    }

    private List<CrossSection> generateCrossSections(ShipBlueprint blueprint, Random rng) {
        List<CrossSection> sections = new ArrayList<>();
        int count = blueprint.crossSectionCount;

        for (int i = 0; i < count; i++) {
            float frac = (float) i / (count - 1);

            // taper at nose and tail, widest around 30-60%
            float envelope;
            if (frac < 0.15f) {
                envelope = frac / 0.15f * 0.3f;
            } else if (frac < 0.6f) {
                envelope = 0.3f + (frac - 0.15f) / 0.45f * 0.7f;
            } else {
                envelope = 1f - (frac - 0.6f) / 0.4f * 0.4f;
            }

            float w = blueprint.maxWidth * envelope * (0.85f + rng.nextFloat() * 0.15f);
            float h = blueprint.maxHeight * envelope * (0.85f + rng.nextFloat() * 0.15f);
            float exp = 2.0f + rng.nextFloat() * 1.5f;

            w = Math.max(0.1f, w);
            h = Math.max(0.1f, h);

            sections.add(new CrossSection(w, h, exp));
        }
        return sections;
    }

    private List<Float> generateTValues(int count) {
        List<Float> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add((float) i / (count - 1));
        }
        return values;
    }

    private float[] flattenRing(float[][] ring) {
        float[] flat = new float[ring.length * 2];
        for (int i = 0; i < ring.length; i++) {
            flat[i * 2] = ring[i][0];
            flat[i * 2 + 1] = ring[i][1];
        }
        return flat;
    }
}
```

- [ ] **Step 6: Write failing tests for ShipHullGenerator**

```java
package com.galacticodyssey.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipHullGeneratorTest {

    @Test
    void generatesNonEmptyGeometry() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        ShipHullGenerator gen = new ShipHullGenerator();
        HullGeometry geom = gen.generate(bp);

        assertTrue(geom.vertexCount() > 0, "Should generate vertices");
        assertTrue(geom.triangleCount() > 0, "Should generate triangles");
    }

    @Test
    void boundingBoxIsReasonableForSmallShip() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        ShipHullGenerator gen = new ShipHullGenerator();
        HullGeometry geom = gen.generate(bp);

        float width = geom.boundingBox.getWidth();
        float depth = geom.boundingBox.getDepth();

        assertTrue(width > 1f, "Width should be > 1m, was " + width);
        assertTrue(width < 20f, "Width should be < 20m for SMALL, was " + width);
        assertTrue(depth > 5f, "Depth should be > 5m, was " + depth);
        assertTrue(depth < 20f, "Depth should be < 20m for SMALL, was " + depth);
    }

    @Test
    void sameSeedProducesSameGeometry() {
        ShipHullGenerator gen = new ShipHullGenerator();
        HullGeometry a = gen.generate(new ShipBlueprint(42L, ShipSizeClass.MEDIUM));
        HullGeometry b = gen.generate(new ShipBlueprint(42L, ShipSizeClass.MEDIUM));

        assertEquals(a.vertexCount(), b.vertexCount());
        assertEquals(a.triangleCount(), b.triangleCount());
        assertArrayEquals(a.vertices, b.vertices, 0.001f);
    }

    @Test
    void differentSizesProduceDifferentScales() {
        ShipHullGenerator gen = new ShipHullGenerator();
        HullGeometry small = gen.generate(new ShipBlueprint(42L, ShipSizeClass.SMALL));
        HullGeometry large = gen.generate(new ShipBlueprint(42L, ShipSizeClass.LARGE));

        assertTrue(large.boundingBox.getDepth() > small.boundingBox.getDepth(),
            "Large ship should be longer than small ship");
    }

    @Test
    void hasHardpoints() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.MEDIUM);
        ShipHullGenerator gen = new ShipHullGenerator();
        HullGeometry geom = gen.generate(bp);

        assertTrue(geom.hardpoints.length > 0, "Should have at least one hardpoint");
    }

    @Test
    void vertexStrideMatchesFormat() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        ShipHullGenerator gen = new ShipHullGenerator();
        HullGeometry geom = gen.generate(bp);

        assertEquals(ShipHullGenerator.VERTEX_STRIDE, geom.vertexStride);
        assertEquals(0, geom.vertices.length % geom.vertexStride,
            "Vertex array length should be divisible by stride");
    }
}
```

- [ ] **Step 7: Run all hull generator tests**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.*" --info`
Expected: all tests in SpineCurveTest, CrossSectionTest, ShipColorPaletteTest, and ShipHullGeneratorTest PASS.

- [ ] **Step 8: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/HullGeometry.java core/src/main/java/com/galacticodyssey/ship/ShipColorPalette.java core/src/main/java/com/galacticodyssey/ship/ShipHullGenerator.java core/src/test/java/com/galacticodyssey/ship/ShipHullGeneratorTest.java core/src/test/java/com/galacticodyssey/ship/ShipColorPaletteTest.java
git commit -m "feat(ship): add ShipHullGenerator with lofted superellipse hull mesh pipeline"
```

---

## Task 5: Interior Generation (Voxelization + Room Packing + Corridors)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/RoomPlacement.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/InteriorLayout.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ShipInteriorGenerator.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ShipInteriorGeneratorTest.java`

- [ ] **Step 1: Create RoomPlacement data class**

```java
package com.galacticodyssey.ship;

public class RoomPlacement {
    public final RoomType type;
    public final int gridX, gridY, gridZ;
    public final int sizeX, sizeY, sizeZ;

    public RoomPlacement(RoomType type, int gridX, int gridY, int gridZ,
                         int sizeX, int sizeY, int sizeZ) {
        this.type = type;
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridZ = gridZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= gridX && x < gridX + sizeX
            && y >= gridY && y < gridY + sizeY
            && z >= gridZ && z < gridZ + sizeZ;
    }
}
```

- [ ] **Step 2: Create InteriorLayout result object**

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.math.Vector3;

import java.util.List;

public class InteriorLayout {
    public final List<RoomPlacement> rooms;
    public final boolean[][][] corridorCells;
    public final Vector3 airlockPosition;
    public final Vector3 pilotSeatPosition;
    public final float[] floorVertices;
    public final short[] floorIndices;
    public final float[] wallVertices;
    public final short[] wallIndices;
    public final int gridSizeX, gridSizeY, gridSizeZ;

    public InteriorLayout(List<RoomPlacement> rooms, boolean[][][] corridorCells,
                          Vector3 airlockPosition, Vector3 pilotSeatPosition,
                          float[] floorVertices, short[] floorIndices,
                          float[] wallVertices, short[] wallIndices,
                          int gridSizeX, int gridSizeY, int gridSizeZ) {
        this.rooms = rooms;
        this.corridorCells = corridorCells;
        this.airlockPosition = airlockPosition;
        this.pilotSeatPosition = pilotSeatPosition;
        this.floorVertices = floorVertices;
        this.floorIndices = floorIndices;
        this.wallVertices = wallVertices;
        this.wallIndices = wallIndices;
        this.gridSizeX = gridSizeX;
        this.gridSizeY = gridSizeY;
        this.gridSizeZ = gridSizeZ;
    }
}
```

- [ ] **Step 3: Write failing tests**

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipInteriorGeneratorTest {

    private HullGeometry generateHull(ShipSizeClass sizeClass) {
        ShipBlueprint bp = new ShipBlueprint(42L, sizeClass);
        return new ShipHullGenerator().generate(bp);
    }

    @Test
    void smallShipHasCockpitAndCorridor() {
        HullGeometry hull = generateHull(ShipSizeClass.SMALL);
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        ShipInteriorGenerator gen = new ShipInteriorGenerator();
        InteriorLayout layout = gen.generate(bp, hull);

        assertTrue(layout.rooms.stream().anyMatch(r -> r.type == RoomType.COCKPIT),
            "Small ship must have a cockpit");
    }

    @Test
    void mediumShipHasEngineRoom() {
        HullGeometry hull = generateHull(ShipSizeClass.MEDIUM);
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.MEDIUM);
        ShipInteriorGenerator gen = new ShipInteriorGenerator();
        InteriorLayout layout = gen.generate(bp, hull);

        assertTrue(layout.rooms.stream().anyMatch(r -> r.type == RoomType.COCKPIT));
        assertTrue(layout.rooms.stream().anyMatch(r -> r.type == RoomType.ENGINE_ROOM));
    }

    @Test
    void interiorHasAirlockAndPilotSeat() {
        HullGeometry hull = generateHull(ShipSizeClass.SMALL);
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        ShipInteriorGenerator gen = new ShipInteriorGenerator();
        InteriorLayout layout = gen.generate(bp, hull);

        assertNotNull(layout.airlockPosition, "Must have airlock position");
        assertNotNull(layout.pilotSeatPosition, "Must have pilot seat position");
    }

    @Test
    void generatesFloorAndWallMeshData() {
        HullGeometry hull = generateHull(ShipSizeClass.SMALL);
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        ShipInteriorGenerator gen = new ShipInteriorGenerator();
        InteriorLayout layout = gen.generate(bp, hull);

        assertTrue(layout.floorVertices.length > 0, "Must generate floor geometry");
        assertTrue(layout.floorIndices.length > 0, "Must generate floor indices");
        assertTrue(layout.wallVertices.length > 0, "Must generate wall geometry");
        assertTrue(layout.wallIndices.length > 0, "Must generate wall indices");
    }

    @Test
    void sameSeedProducesSameLayout() {
        ShipBlueprint bp = new ShipBlueprint(42L, ShipSizeClass.MEDIUM);
        HullGeometry hull = new ShipHullGenerator().generate(bp);
        ShipInteriorGenerator gen = new ShipInteriorGenerator();
        InteriorLayout a = gen.generate(bp, hull);
        InteriorLayout b = gen.generate(bp, hull);

        assertEquals(a.rooms.size(), b.rooms.size());
        assertEquals(a.pilotSeatPosition, b.pilotSeatPosition);
    }

    @Test
    void largeShipHasMoreRoomsThanSmall() {
        ShipInteriorGenerator gen = new ShipInteriorGenerator();

        ShipBlueprint smallBp = new ShipBlueprint(42L, ShipSizeClass.SMALL);
        InteriorLayout smallLayout = gen.generate(smallBp, new ShipHullGenerator().generate(smallBp));

        ShipBlueprint largeBp = new ShipBlueprint(42L, ShipSizeClass.LARGE);
        InteriorLayout largeLayout = gen.generate(largeBp, new ShipHullGenerator().generate(largeBp));

        assertTrue(largeLayout.rooms.size() > smallLayout.rooms.size(),
            "Large ship should have more rooms than small: large=" +
            largeLayout.rooms.size() + " small=" + smallLayout.rooms.size());
    }
}
```

- [ ] **Step 4: Implement ShipInteriorGenerator**

This is a large class. The core algorithm:
1. Build a 3D boolean grid from the hull bounding box (1m cells)
2. Mark cells as "inside" by testing each cell center against cross-section boundaries
3. Shrink the inside mask by 1 cell for hull walls
4. Place rooms: cockpit at nose-z, engine room at tail-z, others greedy front-to-back
5. Connect room doorways with A* through empty interior cells
6. Generate floor/wall/ceiling quads for each room + corridor cell
7. Locate airlock (bottom center) and pilot seat (cockpit center)

```java
package com.galacticodyssey.ship;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

import java.util.*;

public class ShipInteriorGenerator {

    private static final float CELL_SIZE = 1f;
    // vertex stride: px, py, pz, nx, ny, nz, r, g, b, a = 10
    public static final int VERTEX_STRIDE = 10;

    public InteriorLayout generate(ShipBlueprint blueprint, HullGeometry hull) {
        Random rng = new Random(blueprint.seed + 100);
        BoundingBox bbox = hull.boundingBox;

        Vector3 min = new Vector3();
        Vector3 max = new Vector3();
        bbox.getMin(min);
        bbox.getMax(max);

        int gridX = (int) Math.ceil((max.x - min.x) / CELL_SIZE);
        int gridY = (int) Math.ceil((max.y - min.y) / CELL_SIZE);
        int gridZ = (int) Math.ceil((max.z - min.z) / CELL_SIZE);
        gridX = Math.max(gridX, 1);
        gridY = Math.max(gridY, 1);
        gridZ = Math.max(gridZ, 1);

        boolean[][][] inside = voxelizeHull(hull, min, gridX, gridY, gridZ);
        boolean[][][] packable = shrinkMask(inside, gridX, gridY, gridZ);

        List<RoomType> manifest = getRoomManifest(blueprint.sizeClass, rng);
        List<RoomPlacement> rooms = packRooms(manifest, packable, gridX, gridY, gridZ, rng);

        boolean[][][] corridorCells = new boolean[gridX][gridY][gridZ];
        connectRoomsWithCorridors(rooms, corridorCells, packable, gridX, gridY, gridZ);

        Vector3 airlockPosition = findAirlockPosition(rooms, min);
        Vector3 pilotSeatPosition = findPilotSeatPosition(rooms, min);

        List<float[]> floorVerts = new ArrayList<>();
        List<short[]> floorInds = new ArrayList<>();
        List<float[]> wallVerts = new ArrayList<>();
        List<short[]> wallInds = new ArrayList<>();

        int floorVertOffset = 0;
        int wallVertOffset = 0;

        for (RoomPlacement room : rooms) {
            floorVertOffset = generateRoomFloor(room, min, floorVerts, floorInds, floorVertOffset);
            wallVertOffset = generateRoomWalls(room, min, wallVerts, wallInds, wallVertOffset);
        }

        for (int x = 0; x < gridX; x++) {
            for (int y = 0; y < gridY; y++) {
                for (int z = 0; z < gridZ; z++) {
                    if (corridorCells[x][y][z]) {
                        RoomPlacement corridor = new RoomPlacement(RoomType.CORRIDOR, x, y, z, 1, 1, 1);
                        floorVertOffset = generateRoomFloor(corridor, min, floorVerts, floorInds, floorVertOffset);
                        wallVertOffset = generateRoomWalls(corridor, min, wallVerts, wallInds, wallVertOffset);
                    }
                }
            }
        }

        return new InteriorLayout(
            rooms, corridorCells, airlockPosition, pilotSeatPosition,
            mergeFloats(floorVerts), mergeShorts(floorInds),
            mergeFloats(wallVerts), mergeShorts(wallInds),
            gridX, gridY, gridZ
        );
    }

    private boolean[][][] voxelizeHull(HullGeometry hull, Vector3 min, int gx, int gy, int gz) {
        boolean[][][] inside = new boolean[gx][gy][gz];
        BoundingBox bbox = hull.boundingBox;
        Vector3 bmin = new Vector3();
        Vector3 bmax = new Vector3();
        bbox.getMin(bmin);
        bbox.getMax(bmax);

        float centerX = (bmin.x + bmax.x) / 2f;
        float centerY = (bmin.y + bmax.y) / 2f;
        float halfW = (bmax.x - bmin.x) / 2f;
        float halfH = (bmax.y - bmin.y) / 2f;
        float depth = bmax.z - bmin.z;

        for (int x = 0; x < gx; x++) {
            for (int y = 0; y < gy; y++) {
                for (int z = 0; z < gz; z++) {
                    float wx = min.x + (x + 0.5f) * CELL_SIZE;
                    float wy = min.y + (y + 0.5f) * CELL_SIZE;
                    float wz = min.z + (z + 0.5f) * CELL_SIZE;

                    float zFrac = (wz - bmin.z) / depth;
                    float taper;
                    if (zFrac < 0.15f) taper = zFrac / 0.15f * 0.3f;
                    else if (zFrac < 0.6f) taper = 0.3f + (zFrac - 0.15f) / 0.45f * 0.7f;
                    else taper = 1f - (zFrac - 0.6f) / 0.4f * 0.4f;
                    taper = Math.max(0.05f, taper);

                    float localW = halfW * taper;
                    float localH = halfH * taper;

                    float dx = (wx - centerX) / localW;
                    float dy = (wy - centerY) / localH;

                    inside[x][y][z] = (dx * dx + dy * dy) < 1f;
                }
            }
        }
        return inside;
    }

    private boolean[][][] shrinkMask(boolean[][][] inside, int gx, int gy, int gz) {
        boolean[][][] result = new boolean[gx][gy][gz];
        for (int x = 1; x < gx - 1; x++) {
            for (int y = 1; y < gy - 1; y++) {
                for (int z = 1; z < gz - 1; z++) {
                    result[x][y][z] = inside[x][y][z]
                        && inside[x - 1][y][z] && inside[x + 1][y][z]
                        && inside[x][y - 1][z] && inside[x][y + 1][z]
                        && inside[x][y][z - 1] && inside[x][y][z + 1];
                }
            }
        }
        return result;
    }

    private List<RoomType> getRoomManifest(ShipSizeClass sizeClass, Random rng) {
        List<RoomType> manifest = new ArrayList<>();
        manifest.add(RoomType.COCKPIT);

        if (sizeClass == ShipSizeClass.MEDIUM || sizeClass == ShipSizeClass.LARGE) {
            manifest.add(RoomType.ENGINE_ROOM);
        }
        if (sizeClass == ShipSizeClass.LARGE) {
            manifest.add(RoomType.CARGO_BAY);
        }
        if ((sizeClass == ShipSizeClass.MEDIUM || sizeClass == ShipSizeClass.LARGE) && rng.nextBoolean()) {
            manifest.add(RoomType.CARGO_BAY);
        }
        if ((sizeClass == ShipSizeClass.MEDIUM || sizeClass == ShipSizeClass.LARGE) && rng.nextBoolean()) {
            manifest.add(RoomType.CREW_QUARTERS);
        }
        if (sizeClass == ShipSizeClass.LARGE && rng.nextBoolean()) {
            manifest.add(RoomType.MEDBAY);
        }
        if (sizeClass == ShipSizeClass.LARGE && rng.nextBoolean()) {
            manifest.add(RoomType.ARMORY);
        }
        return manifest;
    }

    private List<RoomPlacement> packRooms(List<RoomType> manifest, boolean[][][] packable,
                                          int gx, int gy, int gz, Random rng) {
        List<RoomPlacement> placements = new ArrayList<>();
        boolean[][][] occupied = new boolean[gx][gy][gz];

        // Sort: cockpit first (place at front=high Z), engine room last (place at back=low Z)
        manifest.sort((a, b) -> {
            if (a == RoomType.COCKPIT) return -1;
            if (b == RoomType.COCKPIT) return 1;
            if (a == RoomType.ENGINE_ROOM) return 1;
            if (b == RoomType.ENGINE_ROOM) return -1;
            return Integer.compare(b.minSizeX * b.minSizeZ, a.minSizeX * a.minSizeZ);
        });

        for (RoomType roomType : manifest) {
            int sx = roomType.minSizeX + rng.nextInt(roomType.maxSizeX - roomType.minSizeX + 1);
            int sy = roomType.minSizeY + rng.nextInt(roomType.maxSizeY - roomType.minSizeY + 1);
            int sz = roomType.minSizeZ + rng.nextInt(roomType.maxSizeZ - roomType.minSizeZ + 1);
            sx = Math.min(sx, gx);
            sy = Math.min(sy, gy);
            sz = Math.min(sz, gz);

            RoomPlacement placement = findPlacement(roomType, sx, sy, sz, packable, occupied, gx, gy, gz);
            if (placement != null) {
                placements.add(placement);
                markOccupied(occupied, placement);
            }
        }
        return placements;
    }

    private RoomPlacement findPlacement(RoomType type, int sx, int sy, int sz,
                                        boolean[][][] packable, boolean[][][] occupied,
                                        int gx, int gy, int gz) {
        // Cockpit: search from high Z (nose)
        // Engine room: search from low Z (tail)
        // Others: search from high Z to low Z
        int zStart, zEnd, zStep;
        if (type == RoomType.ENGINE_ROOM) {
            zStart = 0; zEnd = gz - sz; zStep = 1;
        } else {
            zStart = gz - sz; zEnd = 0; zStep = -1;
        }

        for (int z = zStart; zStep > 0 ? z <= zEnd : z >= zEnd; z += zStep) {
            for (int x = 0; x <= gx - sx; x++) {
                for (int y = 0; y <= gy - sy; y++) {
                    if (canPlace(x, y, z, sx, sy, sz, packable, occupied)) {
                        return new RoomPlacement(type, x, y, z, sx, sy, sz);
                    }
                }
            }
        }
        return null;
    }

    private boolean canPlace(int px, int py, int pz, int sx, int sy, int sz,
                             boolean[][][] packable, boolean[][][] occupied) {
        for (int x = px; x < px + sx; x++) {
            for (int y = py; y < py + sy; y++) {
                for (int z = pz; z < pz + sz; z++) {
                    if (!packable[x][y][z] || occupied[x][y][z]) return false;
                }
            }
        }
        return true;
    }

    private void markOccupied(boolean[][][] occupied, RoomPlacement room) {
        for (int x = room.gridX; x < room.gridX + room.sizeX; x++) {
            for (int y = room.gridY; y < room.gridY + room.sizeY; y++) {
                for (int z = room.gridZ; z < room.gridZ + room.sizeZ; z++) {
                    occupied[x][y][z] = true;
                }
            }
        }
    }

    private void connectRoomsWithCorridors(List<RoomPlacement> rooms, boolean[][][] corridors,
                                           boolean[][][] packable, int gx, int gy, int gz) {
        if (rooms.size() < 2) return;

        for (int i = 0; i < rooms.size() - 1; i++) {
            RoomPlacement from = rooms.get(i);
            RoomPlacement to = rooms.get(i + 1);

            int fx = from.gridX + from.sizeX / 2;
            int fy = from.gridY;
            int fz = from.gridZ + from.sizeZ / 2;
            int tx = to.gridX + to.sizeX / 2;
            int ty = to.gridY;
            int tz = to.gridZ + to.sizeZ / 2;

            // Simple L-shaped corridor: walk in Z then X
            int z = fz;
            int step = tz > fz ? 1 : -1;
            while (z != tz) {
                z += step;
                if (z >= 0 && z < gz && fx >= 0 && fx < gx && fy >= 0 && fy < gy) {
                    if (packable[fx][fy][z] && !isInAnyRoom(fx, fy, z, rooms)) {
                        corridors[fx][fy][z] = true;
                    }
                }
            }
            int x = fx;
            step = tx > fx ? 1 : -1;
            while (x != tx) {
                x += step;
                if (x >= 0 && x < gx && fy >= 0 && fy < gy && tz >= 0 && tz < gz) {
                    if (packable[x][fy][tz] && !isInAnyRoom(x, fy, tz, rooms)) {
                        corridors[x][fy][tz] = true;
                    }
                }
            }
        }
    }

    private boolean isInAnyRoom(int x, int y, int z, List<RoomPlacement> rooms) {
        for (RoomPlacement room : rooms) {
            if (room.contains(x, y, z)) return true;
        }
        return false;
    }

    private Vector3 findAirlockPosition(List<RoomPlacement> rooms, Vector3 gridOrigin) {
        // Place airlock at center of the rooms on the floor
        if (rooms.isEmpty()) return new Vector3(0, 0, 0);

        float avgX = 0, avgZ = 0;
        float minY = Float.MAX_VALUE;
        for (RoomPlacement room : rooms) {
            avgX += (room.gridX + room.sizeX / 2f);
            avgZ += (room.gridZ + room.sizeZ / 2f);
            minY = Math.min(minY, room.gridY);
        }
        avgX /= rooms.size();
        avgZ /= rooms.size();

        return new Vector3(
            gridOrigin.x + avgX * CELL_SIZE,
            gridOrigin.y + minY * CELL_SIZE + 0.5f,
            gridOrigin.z + avgZ * CELL_SIZE
        );
    }

    private Vector3 findPilotSeatPosition(List<RoomPlacement> rooms, Vector3 gridOrigin) {
        for (RoomPlacement room : rooms) {
            if (room.type == RoomType.COCKPIT) {
                return new Vector3(
                    gridOrigin.x + (room.gridX + room.sizeX / 2f) * CELL_SIZE,
                    gridOrigin.y + room.gridY * CELL_SIZE + 0.5f,
                    gridOrigin.z + (room.gridZ + room.sizeZ / 2f) * CELL_SIZE
                );
            }
        }
        return findAirlockPosition(rooms, gridOrigin);
    }

    private int generateRoomFloor(RoomPlacement room, Vector3 gridOrigin,
                                  List<float[]> vertsList, List<short[]> indsList, int vertOffset) {
        float ox = gridOrigin.x + room.gridX * CELL_SIZE;
        float oy = gridOrigin.y + room.gridY * CELL_SIZE;
        float oz = gridOrigin.z + room.gridZ * CELL_SIZE;
        float w = room.sizeX * CELL_SIZE;
        float d = room.sizeZ * CELL_SIZE;

        float r = room.type.floorColor.r;
        float g = room.type.floorColor.g;
        float b = room.type.floorColor.b;

        float[] verts = {
            ox,     oy, oz,     0, 1, 0, r, g, b, 1,
            ox + w, oy, oz,     0, 1, 0, r, g, b, 1,
            ox + w, oy, oz + d, 0, 1, 0, r, g, b, 1,
            ox,     oy, oz + d, 0, 1, 0, r, g, b, 1,
        };
        short base = (short) vertOffset;
        short[] inds = { base, (short)(base+1), (short)(base+2), base, (short)(base+2), (short)(base+3) };

        vertsList.add(verts);
        indsList.add(inds);
        return vertOffset + 4;
    }

    private int generateRoomWalls(RoomPlacement room, Vector3 gridOrigin,
                                  List<float[]> vertsList, List<short[]> indsList, int vertOffset) {
        float ox = gridOrigin.x + room.gridX * CELL_SIZE;
        float oy = gridOrigin.y + room.gridY * CELL_SIZE;
        float oz = gridOrigin.z + room.gridZ * CELL_SIZE;
        float w = room.sizeX * CELL_SIZE;
        float h = room.sizeY * CELL_SIZE;
        float d = room.sizeZ * CELL_SIZE;

        float r = room.type.accentColor.r;
        float g = room.type.accentColor.g;
        float b = room.type.accentColor.b;

        // 4 walls: -X, +X, -Z, +Z
        float[][] walls = {
            { ox, oy, oz, ox, oy + h, oz, ox, oy + h, oz + d, ox, oy, oz + d, -1, 0, 0 },
            { ox + w, oy, oz + d, ox + w, oy + h, oz + d, ox + w, oy + h, oz, ox + w, oy, oz, 1, 0, 0 },
            { ox + w, oy, oz, ox + w, oy + h, oz, ox, oy + h, oz, ox, oy, oz, 0, 0, -1 },
            { ox, oy, oz + d, ox, oy + h, oz + d, ox + w, oy + h, oz + d, ox + w, oy, oz + d, 0, 0, 1 },
        };

        // ceiling
        float[] ceilVerts = {
            ox, oy + h, oz + d,   0, -1, 0, r * 0.7f, g * 0.7f, b * 0.7f, 1,
            ox + w, oy + h, oz + d, 0, -1, 0, r * 0.7f, g * 0.7f, b * 0.7f, 1,
            ox + w, oy + h, oz,     0, -1, 0, r * 0.7f, g * 0.7f, b * 0.7f, 1,
            ox, oy + h, oz,         0, -1, 0, r * 0.7f, g * 0.7f, b * 0.7f, 1,
        };
        short cBase = (short) vertOffset;
        short[] cInds = { cBase, (short)(cBase+1), (short)(cBase+2), cBase, (short)(cBase+2), (short)(cBase+3) };
        vertsList.add(ceilVerts);
        indsList.add(cInds);
        vertOffset += 4;

        for (float[] wall : walls) {
            float nx = wall[12], ny = wall[13], nz = wall[14];
            float[] verts = {
                wall[0], wall[1], wall[2],   nx, ny, nz, r, g, b, 1,
                wall[3], wall[4], wall[5],   nx, ny, nz, r, g, b, 1,
                wall[6], wall[7], wall[8],   nx, ny, nz, r, g, b, 1,
                wall[9], wall[10], wall[11], nx, ny, nz, r, g, b, 1,
            };
            short wBase = (short) vertOffset;
            short[] wInds = { wBase, (short)(wBase+1), (short)(wBase+2), wBase, (short)(wBase+2), (short)(wBase+3) };
            vertsList.add(verts);
            indsList.add(wInds);
            vertOffset += 4;
        }
        return vertOffset;
    }

    private float[] mergeFloats(List<float[]> arrays) {
        int total = 0;
        for (float[] a : arrays) total += a.length;
        float[] result = new float[total];
        int pos = 0;
        for (float[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    private short[] mergeShorts(List<short[]> arrays) {
        int total = 0;
        for (short[] a : arrays) total += a.length;
        short[] result = new short[total];
        int pos = 0;
        for (short[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }
}
```

- [ ] **Step 5: Run interior tests**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.ShipInteriorGeneratorTest" --info`
Expected: all 6 tests PASS.

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/RoomPlacement.java core/src/main/java/com/galacticodyssey/ship/InteriorLayout.java core/src/main/java/com/galacticodyssey/ship/ShipInteriorGenerator.java core/src/test/java/com/galacticodyssey/ship/ShipInteriorGeneratorTest.java
git commit -m "feat(ship): add ShipInteriorGenerator with voxelization and room packing"
```

---

## Task 6: Ship ECS Components

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/components/ShipDataComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/components/ShipMeshComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/components/ShipInteriorComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/components/ShipFlightComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/components/PilotSeatComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/components/ShipEntryPointComponent.java`

- [ ] **Step 1: Create all six components**

```java
// ShipDataComponent.java
package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.ship.ShipBlueprint;
import com.galacticodyssey.ship.ShipSizeClass;

public class ShipDataComponent implements Component {
    public ShipBlueprint blueprint;
    public float mass;
    public float maxThrust;
    public float maxTurnRate;
    public float maxSpeed;
    public float hullHp;
    public float currentHullHp;
}
```

```java
// ShipMeshComponent.java
package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.utils.Disposable;

public class ShipMeshComponent implements Component, Disposable {
    public Mesh hullMesh;
    public int vertexStride;

    @Override
    public void dispose() {
        if (hullMesh != null) {
            hullMesh.dispose();
            hullMesh = null;
        }
    }
}
```

```java
// ShipInteriorComponent.java
package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.ship.InteriorLayout;

public class ShipInteriorComponent implements Component, Disposable {
    public InteriorLayout layout;
    public Mesh floorMesh;
    public Mesh wallMesh;

    public btCollisionConfiguration collisionConfig;
    public btCollisionDispatcher dispatcher;
    public btBroadphaseInterface broadphase;
    public btConstraintSolver solver;
    public btDiscreteDynamicsWorld interiorWorld;
    public btRigidBody interiorStaticBody;
    public btCollisionShape interiorShape;

    public boolean active;

    @Override
    public void dispose() {
        if (interiorStaticBody != null && interiorWorld != null) {
            interiorWorld.removeRigidBody(interiorStaticBody);
        }
        if (interiorStaticBody != null) { interiorStaticBody.dispose(); interiorStaticBody = null; }
        if (interiorShape != null) { interiorShape.dispose(); interiorShape = null; }
        if (interiorWorld != null) { interiorWorld.dispose(); interiorWorld = null; }
        if (solver != null) { solver.dispose(); solver = null; }
        if (broadphase != null) { broadphase.dispose(); broadphase = null; }
        if (dispatcher != null) { dispatcher.dispose(); dispatcher = null; }
        if (collisionConfig != null) { collisionConfig.dispose(); collisionConfig = null; }
        if (floorMesh != null) { floorMesh.dispose(); floorMesh = null; }
        if (wallMesh != null) { wallMesh.dispose(); wallMesh = null; }
    }
}
```

```java
// ShipFlightComponent.java
package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;

public class ShipFlightComponent implements Component {
    public float linearThrust;
    public float strafeThrustFraction;
    public float verticalThrustFraction;
    public float pitchYawTorque;
    public float rollTorque;
    public float linearDrag;
    public float angularDrag;
    public float currentThrottle;
}
```

```java
// PilotSeatComponent.java
package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public class PilotSeatComponent implements Component {
    public final Vector3 interiorPosition = new Vector3();
    public float triggerRadius = 2f;
    public boolean occupied;
    public Entity occupant;
}
```

```java
// ShipEntryPointComponent.java
package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

public class ShipEntryPointComponent implements Component {
    public final Vector3 worldPosition = new Vector3();
    public final Vector3 interiorPosition = new Vector3();
    public float triggerRadius = 3f;
    public boolean rampDeployed = true;
}
```

- [ ] **Step 2: Compile check**

Run: `.\gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/components/
git commit -m "feat(ship): add ship ECS components (data, mesh, interior, flight, seat, entry)"
```

---

## Task 7: Event Classes + PlayerStateComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/events/ShipEntryAvailableEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/PlayerEnterShipEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/PilotSeatAvailableEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/PlayerStartPilotingEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/PlayerStopPilotingEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/PlayerExitShipEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/InteractionPromptEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/player/components/PlayerStateComponent.java`

- [ ] **Step 1: Create all event classes**

```java
// ShipEntryAvailableEvent.java
package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public class ShipEntryAvailableEvent {
    public final Entity ship;
    public final boolean available;
    public ShipEntryAvailableEvent(Entity ship, boolean available) {
        this.ship = ship; this.available = available;
    }
}
```

```java
// PlayerEnterShipEvent.java
package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public class PlayerEnterShipEvent {
    public final Entity player;
    public final Entity ship;
    public PlayerEnterShipEvent(Entity player, Entity ship) {
        this.player = player; this.ship = ship;
    }
}
```

```java
// PilotSeatAvailableEvent.java
package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public class PilotSeatAvailableEvent {
    public final Entity ship;
    public final boolean available;
    public PilotSeatAvailableEvent(Entity ship, boolean available) {
        this.ship = ship; this.available = available;
    }
}
```

```java
// PlayerStartPilotingEvent.java
package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public class PlayerStartPilotingEvent {
    public final Entity player;
    public final Entity ship;
    public PlayerStartPilotingEvent(Entity player, Entity ship) {
        this.player = player; this.ship = ship;
    }
}
```

```java
// PlayerStopPilotingEvent.java
package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public class PlayerStopPilotingEvent {
    public final Entity player;
    public final Entity ship;
    public PlayerStopPilotingEvent(Entity player, Entity ship) {
        this.player = player; this.ship = ship;
    }
}
```

```java
// PlayerExitShipEvent.java
package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public class PlayerExitShipEvent {
    public final Entity player;
    public final Entity ship;
    public PlayerExitShipEvent(Entity player, Entity ship) {
        this.player = player; this.ship = ship;
    }
}
```

```java
// InteractionPromptEvent.java
package com.galacticodyssey.core.events;

public class InteractionPromptEvent {
    public final String promptText;
    public final boolean visible;
    public InteractionPromptEvent(String promptText, boolean visible) {
        this.promptText = promptText; this.visible = visible;
    }
}
```

- [ ] **Step 2: Create PlayerStateComponent**

```java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class PlayerStateComponent implements Component {

    public enum PlayerMode {
        ON_FOOT_EXTERIOR,
        ON_FOOT_INTERIOR,
        PILOTING
    }

    public PlayerMode currentMode = PlayerMode.ON_FOOT_EXTERIOR;
    public Entity currentShip;
    public Entity interactionTarget;
}
```

- [ ] **Step 3: Compile check**

Run: `.\gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/core/events/ core/src/main/java/com/galacticodyssey/player/components/PlayerStateComponent.java
git commit -m "feat(ship): add ship events and PlayerStateComponent with PlayerMode enum"
```

---

## Task 8: ShipFactory (Entity Assembly + Interior Physics World)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ShipFactoryTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.ship;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.BulletPhysicsSystem;
import com.galacticodyssey.ship.components.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipFactoryTest {

    private EventBus eventBus;
    private BulletPhysicsSystem physics;
    private Engine engine;
    private ShipFactory factory;

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        physics = new BulletPhysicsSystem(eventBus);
        physics.initialize();
        engine = new Engine();
        factory = new ShipFactory(engine, physics);
    }

    @AfterEach
    void tearDown() {
        factory.dispose();
        physics.dispose();
    }

    @Test
    void createsEntityWithAllComponents() {
        Entity ship = factory.createShip(42L, ShipSizeClass.SMALL, 0, 5, 0);

        assertNotNull(ship.getComponent(TransformComponent.class));
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class));
        assertNotNull(ship.getComponent(ShipDataComponent.class));
        assertNotNull(ship.getComponent(ShipMeshComponent.class));
        assertNotNull(ship.getComponent(ShipInteriorComponent.class));
        assertNotNull(ship.getComponent(ShipFlightComponent.class));
        assertNotNull(ship.getComponent(PilotSeatComponent.class));
        assertNotNull(ship.getComponent(ShipEntryPointComponent.class));
    }

    @Test
    void shipHasInteriorPhysicsWorld() {
        Entity ship = factory.createShip(42L, ShipSizeClass.SMALL, 0, 5, 0);
        ShipInteriorComponent interior = ship.getComponent(ShipInteriorComponent.class);

        assertNotNull(interior.interiorWorld, "Ship must have interior physics world");
    }

    @Test
    void shipIsAddedToEngine() {
        Entity ship = factory.createShip(42L, ShipSizeClass.SMALL, 0, 5, 0);

        assertTrue(engine.getEntities().size() > 0);
    }

    @Test
    void differentSizesHaveDifferentMass() {
        Entity small = factory.createShip(42L, ShipSizeClass.SMALL, 0, 5, 0);
        Entity large = factory.createShip(43L, ShipSizeClass.LARGE, 50, 5, 50);

        float smallMass = small.getComponent(ShipDataComponent.class).mass;
        float largeMass = large.getComponent(ShipDataComponent.class).mass;

        assertTrue(largeMass > smallMass);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.ShipFactoryTest" --info`
Expected: compilation failure — `ShipFactory` does not exist.

- [ ] **Step 3: Implement ShipFactory**

```java
package com.galacticodyssey.ship;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.BulletPhysicsSystem;
import com.galacticodyssey.ship.components.*;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

public class ShipFactory implements Disposable {

    private final Engine engine;
    private final BulletPhysicsSystem bulletPhysicsSystem;
    private final ShipHullGenerator hullGenerator = new ShipHullGenerator();
    private final ShipInteriorGenerator interiorGenerator = new ShipInteriorGenerator();
    private final Array<Disposable> disposables = new Array<>();

    public ShipFactory(Engine engine, BulletPhysicsSystem bulletPhysicsSystem) {
        this.engine = engine;
        this.bulletPhysicsSystem = bulletPhysicsSystem;
    }

    public Entity createShip(long seed, ShipSizeClass sizeClass, float x, float y, float z) {
        ShipBlueprint blueprint = new ShipBlueprint(seed, sizeClass);
        HullGeometry hull = hullGenerator.generate(blueprint);
        InteriorLayout interior = interiorGenerator.generate(blueprint, hull);

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);

        ShipDataComponent data = createDataComponent(blueprint, seed);
        entity.add(data);

        ShipMeshComponent meshComp = new ShipMeshComponent();
        // Mesh creation requires GL context; store raw geometry for GameScreen to build the Mesh
        meshComp.vertexStride = hull.vertexStride;
        entity.add(meshComp);
        // Store hull geometry on data component for deferred mesh creation
        data.hullGeometry = hull;

        ShipInteriorComponent interiorComp = createInteriorComponent(interior);
        entity.add(interiorComp);

        ShipFlightComponent flight = createFlightComponent(sizeClass, seed);
        entity.add(flight);

        PilotSeatComponent seat = new PilotSeatComponent();
        seat.interiorPosition.set(interior.pilotSeatPosition);
        entity.add(seat);

        ShipEntryPointComponent entry = new ShipEntryPointComponent();
        entry.interiorPosition.set(interior.airlockPosition);
        // World position offset by ship transform
        entry.worldPosition.set(
            x + interior.airlockPosition.x,
            y + interior.airlockPosition.y - 1f,
            z + interior.airlockPosition.z
        );
        entity.add(entry);

        // Exterior physics: convex hull
        PhysicsBodyComponent physics = createExteriorPhysics(hull, data.mass, x, y, z);
        entity.add(physics);
        bulletPhysicsSystem.getDynamicsWorld().addRigidBody(physics.body);
        bulletPhysicsSystem.addManagedBody(physics.body);
        // Start as static (landed)
        physics.body.setMassProps(0, new Vector3(0, 0, 0));
        physics.body.updateInertiaTensor();

        engine.addEntity(entity);

        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(physics.body);
            bulletPhysicsSystem.removeManagedBody(physics.body);
            physics.body.dispose();
            physics.shape.dispose();
            interiorComp.dispose();
            if (meshComp.hullMesh != null) meshComp.dispose();
        });

        return entity;
    }

    private ShipDataComponent createDataComponent(ShipBlueprint blueprint, long seed) {
        ShipDataComponent data = new ShipDataComponent();
        data.blueprint = blueprint;

        Random rng = new Random(seed + 200);
        ShipSizeClass sc = blueprint.sizeClass;
        switch (sc) {
            case SMALL:
                data.mass = 5000 + rng.nextFloat() * 10000;
                data.maxThrust = 50000;
                data.maxTurnRate = 90;
                data.maxSpeed = 150;
                data.hullHp = 200;
                break;
            case MEDIUM:
                data.mass = 30000 + rng.nextFloat() * 50000;
                data.maxThrust = 200000;
                data.maxTurnRate = 45;
                data.maxSpeed = 100;
                data.hullHp = 800;
                break;
            case LARGE:
                data.mass = 150000 + rng.nextFloat() * 350000;
                data.maxThrust = 500000;
                data.maxTurnRate = 20;
                data.maxSpeed = 60;
                data.hullHp = 3000;
                break;
        }
        data.currentHullHp = data.hullHp;
        return data;
    }

    private ShipFlightComponent createFlightComponent(ShipSizeClass sc, long seed) {
        ShipFlightComponent flight = new ShipFlightComponent();
        switch (sc) {
            case SMALL:
                flight.linearThrust = 50000;
                flight.strafeThrustFraction = 0.6f;
                flight.verticalThrustFraction = 0.6f;
                flight.pitchYawTorque = 20000;
                flight.rollTorque = 15000;
                flight.linearDrag = 0.3f;
                flight.angularDrag = 2.0f;
                break;
            case MEDIUM:
                flight.linearThrust = 200000;
                flight.strafeThrustFraction = 0.4f;
                flight.verticalThrustFraction = 0.4f;
                flight.pitchYawTorque = 50000;
                flight.rollTorque = 30000;
                flight.linearDrag = 0.5f;
                flight.angularDrag = 3.0f;
                break;
            case LARGE:
                flight.linearThrust = 500000;
                flight.strafeThrustFraction = 0.25f;
                flight.verticalThrustFraction = 0.25f;
                flight.pitchYawTorque = 100000;
                flight.rollTorque = 60000;
                flight.linearDrag = 0.7f;
                flight.angularDrag = 5.0f;
                break;
        }
        return flight;
    }

    private ShipInteriorComponent createInteriorComponent(InteriorLayout layout) {
        ShipInteriorComponent comp = new ShipInteriorComponent();
        comp.layout = layout;

        comp.collisionConfig = new btDefaultCollisionConfiguration();
        comp.dispatcher = new btCollisionDispatcher(comp.collisionConfig);
        comp.broadphase = new btDbvtBroadphase();
        comp.solver = new btSequentialImpulseConstraintSolver();
        comp.interiorWorld = new btDiscreteDynamicsWorld(
            comp.dispatcher, comp.broadphase, comp.solver, comp.collisionConfig);
        comp.interiorWorld.setGravity(new Vector3(0, -9.81f, 0));

        if (layout.floorVertices.length > 0 && layout.floorIndices.length > 0) {
            btTriangleIndexVertexArray meshData = buildTriMeshData(
                layout.floorVertices, layout.floorIndices,
                ShipInteriorGenerator.VERTEX_STRIDE);
            if (layout.wallVertices.length > 0 && layout.wallIndices.length > 0) {
                appendTriMeshData(meshData, layout.wallVertices, layout.wallIndices,
                    ShipInteriorGenerator.VERTEX_STRIDE);
            }
            comp.interiorShape = new btBvhTriangleMeshShape(meshData, true);
            btRigidBody.btRigidBodyConstructionInfo info =
                new btRigidBody.btRigidBodyConstructionInfo(0, null, comp.interiorShape);
            comp.interiorStaticBody = new btRigidBody(info);
            comp.interiorStaticBody.setFriction(0.9f);
            info.dispose();
            comp.interiorWorld.addRigidBody(comp.interiorStaticBody);
        }

        return comp;
    }

    private btTriangleIndexVertexArray buildTriMeshData(float[] vertices, short[] indices, int stride) {
        int vertCount = vertices.length / stride;
        FloatBuffer vertBuf = com.badlogic.gdx.utils.BufferUtils.newFloatBuffer(vertCount * 3);
        for (int i = 0; i < vertCount; i++) {
            vertBuf.put(vertices[i * stride]);
            vertBuf.put(vertices[i * stride + 1]);
            vertBuf.put(vertices[i * stride + 2]);
        }
        vertBuf.flip();

        ShortBuffer idxBuf = com.badlogic.gdx.utils.BufferUtils.newShortBuffer(indices.length);
        idxBuf.put(indices);
        idxBuf.flip();

        btIndexedMesh indexedMesh = new btIndexedMesh();
        indexedMesh.setTriangleIndexBase(idxBuf);
        indexedMesh.setVertexBase(vertBuf);
        indexedMesh.setNumVertices(vertCount);
        indexedMesh.setVertexStride(3 * 4);
        indexedMesh.setNumTriangles(indices.length / 3);
        indexedMesh.setTriangleIndexStride(3 * 2);

        btTriangleIndexVertexArray triArray = new btTriangleIndexVertexArray();
        triArray.addIndexedMesh(indexedMesh);
        return triArray;
    }

    private void appendTriMeshData(btTriangleIndexVertexArray triArray,
                                    float[] vertices, short[] indices, int stride) {
        int vertCount = vertices.length / stride;
        FloatBuffer vertBuf = com.badlogic.gdx.utils.BufferUtils.newFloatBuffer(vertCount * 3);
        for (int i = 0; i < vertCount; i++) {
            vertBuf.put(vertices[i * stride]);
            vertBuf.put(vertices[i * stride + 1]);
            vertBuf.put(vertices[i * stride + 2]);
        }
        vertBuf.flip();

        ShortBuffer idxBuf = com.badlogic.gdx.utils.BufferUtils.newShortBuffer(indices.length);
        idxBuf.put(indices);
        idxBuf.flip();

        btIndexedMesh indexedMesh = new btIndexedMesh();
        indexedMesh.setTriangleIndexBase(idxBuf);
        indexedMesh.setVertexBase(vertBuf);
        indexedMesh.setNumVertices(vertCount);
        indexedMesh.setVertexStride(3 * 4);
        indexedMesh.setNumTriangles(indices.length / 3);
        indexedMesh.setTriangleIndexStride(3 * 2);

        triArray.addIndexedMesh(indexedMesh);
    }

    private PhysicsBodyComponent createExteriorPhysics(HullGeometry hull, float mass, float x, float y, float z) {
        PhysicsBodyComponent physics = new PhysicsBodyComponent();

        // Build convex hull from a subset of hull vertices
        btConvexHullShape convex = new btConvexHullShape();
        int step = Math.max(1, hull.vertexCount() / 64);
        for (int i = 0; i < hull.vertexCount(); i += step) {
            int idx = i * hull.vertexStride;
            convex.addPoint(new Vector3(hull.vertices[idx], hull.vertices[idx + 1], hull.vertices[idx + 2]), false);
        }
        convex.recalcLocalAabb();

        physics.shape = convex;
        physics.mass = mass;

        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(x, y, z));
        physics.body.setFriction(0.5f);
        info.dispose();

        return physics;
    }

    @Override
    public void dispose() {
        for (int i = disposables.size - 1; i >= 0; i--) {
            disposables.get(i).dispose();
        }
        disposables.clear();
    }
}
```

Note: `ShipDataComponent` needs a `hullGeometry` field added for deferred mesh creation. Update:

```java
// Add to ShipDataComponent.java:
public HullGeometry hullGeometry; // transient, used for deferred Mesh creation in GameScreen
```

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.ShipFactoryTest" --info`
Expected: all 4 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/ShipFactory.java core/src/test/java/com/galacticodyssey/ship/ShipFactoryTest.java core/src/main/java/com/galacticodyssey/ship/components/ShipDataComponent.java
git commit -m "feat(ship): add ShipFactory entity assembly with interior physics world"
```

---

## Task 9: Player Input Expansion + InteractionSystem

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/player/systems/InteractionSystem.java`

- [ ] **Step 1: Expand PlayerInputComponent with new fields**

Add these fields to `PlayerInputComponent.java`:

```java
    public boolean interactPressed;
    public boolean cameraTogglePressed;
    public boolean rollLeft;
    public boolean rollRight;
    public boolean thrustUp;
    public boolean thrustDown;
```

- [ ] **Step 2: Expand PlayerInputSystem to capture new keys**

In `PlayerInputSystem.java`, add to the `inputAdapter.keyDown`:

```java
            if (keycode == Input.Keys.E) {
                interactPressed = true;
                return true;
            }
            if (keycode == Input.Keys.V) {
                cameraTogglePressed = true;
                return true;
            }
```

In `processEntity`, add after the existing input reads:

```java
        input.rollLeft = Gdx.input.isKeyPressed(Input.Keys.Q);
        input.rollRight = Gdx.input.isKeyPressed(Input.Keys.E);
        input.thrustUp = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        input.thrustDown = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT);

        if (interactPressed) {
            input.interactPressed = true;
            interactPressed = false;
        }
        if (cameraTogglePressed) {
            input.cameraTogglePressed = true;
            cameraTogglePressed = false;
        }
```

Add `private boolean interactPressed;` and `private boolean cameraTogglePressed;` as fields.

- [ ] **Step 3: Create InteractionSystem**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.*;

public class InteractionSystem extends EntitySystem {

    private final EventBus eventBus;

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerStateComponent> stateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<ShipEntryPointComponent> entryMapper =
        ComponentMapper.getFor(ShipEntryPointComponent.class);
    private final ComponentMapper<PilotSeatComponent> seatMapper =
        ComponentMapper.getFor(PilotSeatComponent.class);
    private final ComponentMapper<ShipInteriorComponent> interiorMapper =
        ComponentMapper.getFor(ShipInteriorComponent.class);

    private ImmutableArray<Entity> playerEntities;
    private ImmutableArray<Entity> shipEntities;

    private final Vector3 tempVec = new Vector3();

    public InteractionSystem(EventBus eventBus) {
        super(0);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, TransformComponent.class,
            PlayerInputComponent.class, PlayerStateComponent.class).get());
        shipEntities = engine.getEntitiesFor(Family.all(
            ShipEntryPointComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (playerEntities.size() == 0) return;

        Entity player = playerEntities.first();
        TransformComponent playerTransform = transformMapper.get(player);
        PlayerStateComponent state = stateMapper.get(player);
        PlayerInputComponent input = inputMapper.get(player);

        switch (state.currentMode) {
            case ON_FOOT_EXTERIOR:
                checkShipEntry(player, playerTransform, state, input);
                break;
            case ON_FOOT_INTERIOR:
                checkPilotSeat(player, state, input);
                checkShipExit(player, state, input);
                break;
            case PILOTING:
                checkStopPiloting(player, state, input);
                break;
        }

        input.interactPressed = false;
    }

    private void checkShipEntry(Entity player, TransformComponent playerTransform,
                                PlayerStateComponent state, PlayerInputComponent input) {
        Entity nearestShip = null;
        float nearestDist = Float.MAX_VALUE;

        for (int i = 0; i < shipEntities.size(); i++) {
            Entity ship = shipEntities.get(i);
            ShipEntryPointComponent entry = entryMapper.get(ship);
            if (!entry.rampDeployed) continue;

            float dist = tempVec.set(playerTransform.position).dst(entry.worldPosition);
            if (dist < entry.triggerRadius && dist < nearestDist) {
                nearestDist = dist;
                nearestShip = ship;
            }
        }

        if (nearestShip != null) {
            state.interactionTarget = nearestShip;
            eventBus.publish(new InteractionPromptEvent("Press E to enter ship", true));
            if (input.interactPressed) {
                eventBus.publish(new PlayerEnterShipEvent(player, nearestShip));
                state.currentMode = PlayerMode.ON_FOOT_INTERIOR;
                state.currentShip = nearestShip;
                ShipInteriorComponent interior = interiorMapper.get(nearestShip);
                interior.active = true;
            }
        } else {
            if (state.interactionTarget != null) {
                eventBus.publish(new InteractionPromptEvent("", false));
                state.interactionTarget = null;
            }
        }
    }

    private void checkPilotSeat(Entity player, PlayerStateComponent state,
                                PlayerInputComponent input) {
        if (state.currentShip == null) return;

        PilotSeatComponent seat = seatMapper.get(state.currentShip);
        TransformComponent playerTransform = transformMapper.get(player);
        if (seat == null) return;

        float dist = tempVec.set(playerTransform.position).dst(seat.interiorPosition);
        if (dist < seat.triggerRadius) {
            eventBus.publish(new InteractionPromptEvent("Press E to pilot", true));
            if (input.interactPressed) {
                seat.occupied = true;
                seat.occupant = player;
                state.currentMode = PlayerMode.PILOTING;
                eventBus.publish(new PlayerStartPilotingEvent(player, state.currentShip));
            }
        }
    }

    private void checkShipExit(Entity player, PlayerStateComponent state,
                               PlayerInputComponent input) {
        if (state.currentShip == null) return;

        ShipEntryPointComponent entry = entryMapper.get(state.currentShip);
        TransformComponent playerTransform = transformMapper.get(player);
        if (entry == null) return;

        float dist = tempVec.set(playerTransform.position).dst(entry.interiorPosition);
        if (dist < entry.triggerRadius) {
            eventBus.publish(new InteractionPromptEvent("Press E to exit ship", true));
            if (input.interactPressed) {
                ShipInteriorComponent interior = interiorMapper.get(state.currentShip);
                interior.active = false;
                state.currentMode = PlayerMode.ON_FOOT_EXTERIOR;
                state.currentShip = null;
                eventBus.publish(new PlayerExitShipEvent(player, state.currentShip));
            }
        }
    }

    private void checkStopPiloting(Entity player, PlayerStateComponent state,
                                   PlayerInputComponent input) {
        if (input.interactPressed && state.currentShip != null) {
            PilotSeatComponent seat = seatMapper.get(state.currentShip);
            if (seat != null) {
                seat.occupied = false;
                seat.occupant = null;
            }
            state.currentMode = PlayerMode.ON_FOOT_INTERIOR;
            eventBus.publish(new PlayerStopPilotingEvent(player, state.currentShip));
        }
    }
}
```

- [ ] **Step 4: Compile check**

Run: `.\gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java core/src/main/java/com/galacticodyssey/player/systems/InteractionSystem.java
git commit -m "feat(ship): add InteractionSystem and expanded player input for ship interaction"
```

---

## Task 10: ShipFlightSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightSystemTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipFlightSystemTest {

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    @Test
    void forwardThrustIncreasesVelocity() {
        Engine engine = new Engine();
        ShipFlightSystem system = new ShipFlightSystem();
        engine.addSystem(system);

        Entity ship = new Entity();
        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(1, 1, 1));
        float mass = 10000f;
        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().idt());
        physics.mass = mass;
        info.dispose();
        ship.add(physics);

        ShipFlightComponent flight = new ShipFlightComponent();
        flight.linearThrust = 50000;
        flight.strafeThrustFraction = 0.6f;
        flight.verticalThrustFraction = 0.6f;
        flight.pitchYawTorque = 20000;
        flight.rollTorque = 15000;
        flight.linearDrag = 0.3f;
        flight.angularDrag = 2.0f;
        ship.add(flight);

        PlayerInputComponent input = new PlayerInputComponent();
        input.moveForward = 1f;
        ship.add(input);

        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = ship;
        ship.add(state);

        engine.addEntity(ship);
        system.update(1f / 60f);

        Vector3 vel = physics.body.getLinearVelocity();
        float speed = vel.len();
        assertTrue(speed > 0, "Ship should have gained velocity from thrust, speed=" + speed);

        physics.body.dispose();
        physics.shape.dispose();
    }
}
```

- [ ] **Step 2: Implement ShipFlightSystem**

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipFlightComponent;

public class ShipFlightSystem extends IteratingSystem {

    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ShipFlightComponent> flightMapper =
        ComponentMapper.getFor(ShipFlightComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<PlayerStateComponent> stateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);

    private final Vector3 force = new Vector3();
    private final Vector3 torque = new Vector3();
    private final Vector3 localForward = new Vector3();
    private final Vector3 localRight = new Vector3();
    private final Vector3 localUp = new Vector3();
    private final Matrix4 shipTransform = new Matrix4();

    public ShipFlightSystem() {
        super(Family.all(
            PhysicsBodyComponent.class, ShipFlightComponent.class,
            PlayerInputComponent.class, PlayerStateComponent.class).get(), 1);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerStateComponent state = stateMapper.get(entity);
        if (state.currentMode != PlayerMode.PILOTING) return;
        if (state.currentShip != entity) return;

        PhysicsBodyComponent physics = physicsMapper.get(entity);
        ShipFlightComponent flight = flightMapper.get(entity);
        PlayerInputComponent input = inputMapper.get(entity);

        if (physics.body == null) return;

        physics.body.getWorldTransform(shipTransform);

        // Extract local axes from ship rotation
        localForward.set(0, 0, -1).rot(shipTransform).nor();
        localRight.set(1, 0, 0).rot(shipTransform).nor();
        localUp.set(0, 1, 0).rot(shipTransform).nor();

        // Linear forces
        force.setZero();
        force.mulAdd(localForward, input.moveForward * flight.linearThrust);
        force.mulAdd(localRight, input.moveStrafe * flight.linearThrust * flight.strafeThrustFraction);
        if (input.thrustUp) force.mulAdd(localUp, flight.linearThrust * flight.verticalThrustFraction);
        if (input.thrustDown) force.mulAdd(localUp, -flight.linearThrust * flight.verticalThrustFraction);

        physics.body.applyCentralForce(force);

        // Rotational torques
        torque.setZero();
        torque.mulAdd(localRight, input.mouseDeltaY * flight.pitchYawTorque);
        torque.mulAdd(localUp, -input.mouseDeltaX * flight.pitchYawTorque);
        if (input.rollLeft) torque.mulAdd(localForward, flight.rollTorque);
        if (input.rollRight) torque.mulAdd(localForward, -flight.rollTorque);

        physics.body.applyTorque(torque);

        // Set damping
        physics.body.setDamping(flight.linearDrag, flight.angularDrag);

        flight.currentThrottle = input.moveForward;
        physics.body.activate();
    }
}
```

- [ ] **Step 3: Run tests**

Run: `.\gradlew.bat :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightSystemTest" --info`
Expected: PASS.

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightSystemTest.java
git commit -m "feat(ship): add ShipFlightSystem with 6DOF force-based flight"
```

---

## Task 11: ShipInteriorPhysicsSystem + ShipCameraSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/systems/ShipInteriorPhysicsSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/systems/ShipCameraSystem.java`

- [ ] **Step 1: Create ShipInteriorPhysicsSystem**

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.galacticodyssey.ship.components.ShipInteriorComponent;

public class ShipInteriorPhysicsSystem extends IteratingSystem {

    private static final float FIXED_TIMESTEP = 1f / 60f;
    private static final int MAX_SUBSTEPS = 3;

    private final ComponentMapper<ShipInteriorComponent> interiorMapper =
        ComponentMapper.getFor(ShipInteriorComponent.class);

    public ShipInteriorPhysicsSystem() {
        super(Family.all(ShipInteriorComponent.class).get(), 3);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ShipInteriorComponent interior = interiorMapper.get(entity);
        if (!interior.active || interior.interiorWorld == null) return;

        interior.interiorWorld.stepSimulation(deltaTime, MAX_SUBSTEPS, FIXED_TIMESTEP);
    }
}
```

- [ ] **Step 2: Create ShipCameraSystem**

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.ship.components.PilotSeatComponent;
import com.galacticodyssey.ship.components.ShipDataComponent;

public class ShipCameraSystem extends EntitySystem {

    public enum CameraMode { COCKPIT, CHASE }

    private PerspectiveCamera camera;
    private CameraMode cameraMode = CameraMode.COCKPIT;

    private ImmutableArray<Entity> playerEntities;

    private final ComponentMapper<PlayerStateComponent> stateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<ShipDataComponent> dataMapper =
        ComponentMapper.getFor(ShipDataComponent.class);
    private final ComponentMapper<PilotSeatComponent> seatMapper =
        ComponentMapper.getFor(PilotSeatComponent.class);

    private static final float COCKPIT_LAG = 8f;
    private static final float CHASE_POS_LERP = 4f;
    private static final float CHASE_ROT_LERP = 6f;

    private final Vector3 currentChasePos = new Vector3();
    private final Quaternion currentChaseRot = new Quaternion();
    private final Matrix4 tempMat = new Matrix4();
    private final Vector3 tempVec = new Vector3();

    public ShipCameraSystem() {
        super(4);
    }

    public void setCamera(PerspectiveCamera camera) {
        this.camera = camera;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (camera == null || playerEntities.size() == 0) return;

        Entity player = playerEntities.first();
        PlayerStateComponent state = stateMapper.get(player);
        if (state.currentMode != PlayerMode.PILOTING || state.currentShip == null) return;

        PlayerInputComponent input = inputMapper.get(player);
        if (input.cameraTogglePressed) {
            cameraMode = (cameraMode == CameraMode.COCKPIT) ? CameraMode.CHASE : CameraMode.COCKPIT;
            input.cameraTogglePressed = false;
        }

        Entity ship = state.currentShip;
        TransformComponent shipTransform = transformMapper.get(ship);

        switch (cameraMode) {
            case COCKPIT:
                updateCockpitCamera(ship, shipTransform, deltaTime);
                break;
            case CHASE:
                updateChaseCamera(ship, shipTransform, deltaTime);
                break;
        }
    }

    private void updateCockpitCamera(Entity ship, TransformComponent shipTransform, float deltaTime) {
        PilotSeatComponent seat = seatMapper.get(ship);
        if (seat == null) return;

        // Position camera at pilot seat, offset by ship world position
        tempMat.set(
            shipTransform.rotation.x, shipTransform.rotation.y,
            shipTransform.rotation.z, shipTransform.rotation.w,
            shipTransform.position.x, shipTransform.position.y, shipTransform.position.z);

        tempVec.set(seat.interiorPosition).mul(tempMat);
        camera.position.set(tempVec);

        // Direction: ship forward (-Z in local space) with slight lag
        Vector3 shipForward = new Vector3(0, 0, -1).rot(tempMat).nor();
        camera.direction.lerp(shipForward, COCKPIT_LAG * deltaTime).nor();

        camera.up.set(0, 1, 0).rot(tempMat).nor();
        camera.update();
    }

    private void updateChaseCamera(Entity ship, TransformComponent shipTransform, float deltaTime) {
        ShipDataComponent data = dataMapper.get(ship);

        float followDist = 15f;
        if (data != null) {
            switch (data.blueprint.sizeClass) {
                case SMALL: followDist = 15f; break;
                case MEDIUM: followDist = 30f; break;
                case LARGE: followDist = 60f; break;
            }
        }

        tempMat.set(
            shipTransform.rotation.x, shipTransform.rotation.y,
            shipTransform.rotation.z, shipTransform.rotation.w,
            shipTransform.position.x, shipTransform.position.y, shipTransform.position.z);

        // Target position: behind and above the ship
        Vector3 targetPos = new Vector3(0, followDist * 0.3f, followDist).mul(tempMat);
        currentChasePos.lerp(targetPos, CHASE_POS_LERP * deltaTime);
        camera.position.set(currentChasePos);

        // Look at ship
        camera.lookAt(shipTransform.position);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    public CameraMode getCameraMode() { return cameraMode; }
}
```

- [ ] **Step 3: Compile check**

Run: `.\gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/systems/ShipInteriorPhysicsSystem.java core/src/main/java/com/galacticodyssey/ship/systems/ShipCameraSystem.java
git commit -m "feat(ship): add ShipInteriorPhysicsSystem and ShipCameraSystem with cockpit/chase toggle"
```

---

## Task 12: Modify Existing Systems for PlayerMode Awareness

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java`

- [ ] **Step 1: Add PlayerMode check to PlayerMovementSystem**

At the start of `processEntity`, add:

```java
        PlayerStateComponent playerState = entity.getComponent(PlayerStateComponent.class);
        if (playerState != null && playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            return;
        }
```

Add the import: `import com.galacticodyssey.player.components.PlayerStateComponent;`

- [ ] **Step 2: Add PlayerMode check to CameraSystem**

At the start of `processEntity`, add:

```java
        PlayerStateComponent playerState = entity.getComponent(PlayerStateComponent.class);
        if (playerState != null && playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            return;
        }
```

Add the import: `import com.galacticodyssey.player.components.PlayerStateComponent;`

- [ ] **Step 3: Run existing tests to confirm no regressions**

Run: `.\gradlew.bat :core:test --info`
Expected: all existing tests still PASS.

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java
git commit -m "feat(ship): make PlayerMovementSystem and CameraSystem skip processing when piloting"
```

---

## Task 13: Integrate into GameWorld + GameScreen

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Update GameWorld to register new systems and add PlayerStateComponent**

In `GameWorld` constructor, after existing system registrations:

```java
        InteractionSystem interactionSystem = new InteractionSystem(eventBus);
        engine.addSystem(interactionSystem);

        ShipFlightSystem shipFlightSystem = new ShipFlightSystem();
        engine.addSystem(shipFlightSystem);

        ShipInteriorPhysicsSystem interiorPhysicsSystem = new ShipInteriorPhysicsSystem();
        engine.addSystem(interiorPhysicsSystem);

        ShipCameraSystem shipCameraSystem = new ShipCameraSystem();
        engine.addSystem(shipCameraSystem);
```

In `initializeSystems`, add:

```java
        shipCameraSystem.setCamera(camera);
```

In `createPlayerEntity`, add after the other component additions:

```java
        player.add(new PlayerStateComponent());
```

Add getters:

```java
    public Engine getEngine() { return engine; }
    public EventBus getEventBus() { return eventBus; }
    public BulletPhysicsSystem getBulletPhysicsSystem() { return bulletPhysicsSystem; }
```

Add imports for new classes.

- [ ] **Step 2: Update GameScreen to create ships and render them**

In `GameScreen.show()`, after the player entity creation, add:

```java
        // Create ship factory and spawn test ships
        shipFactory = new ShipFactory(gameWorld.getEngine(), gameWorld.getBulletPhysicsSystem());

        float smallX = 10f, smallZ = 10f;
        float smallY = TerrainGenerator.getHeightAt(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, smallX, smallZ) + 2f;
        Entity smallShip = shipFactory.createShip(42L, ShipSizeClass.SMALL, smallX, smallY, smallZ);
        shipEntities.add(smallShip);

        float medX = 40f, medZ = 40f;
        float medY = TerrainGenerator.getHeightAt(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, medX, medZ) + 4f;
        Entity medShip = shipFactory.createShip(123L, ShipSizeClass.MEDIUM, medX, medY, medZ);
        shipEntities.add(medShip);

        float lgX = -60f, lgZ = -60f;
        float lgY = TerrainGenerator.getHeightAt(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, lgX, lgZ) + 6f;
        Entity largeShip = shipFactory.createShip(999L, ShipSizeClass.LARGE, lgX, lgY, lgZ);
        shipEntities.add(largeShip);

        buildShipMeshes();
```

Add fields:

```java
    private ShipFactory shipFactory;
    private final Array<Entity> shipEntities = new Array<>();
    private final Array<Mesh> shipMeshes = new Array<>();
    private ShaderProgram shipShader;
```

Add `buildShipMeshes()` method that creates libGDX `Mesh` objects from the `HullGeometry` stored on each ship's `ShipDataComponent`:

```java
    private void buildShipMeshes() {
        for (int i = 0; i < shipEntities.size; i++) {
            Entity ship = shipEntities.get(i);
            ShipDataComponent data = ship.getComponent(ShipDataComponent.class);
            ShipMeshComponent meshComp = ship.getComponent(ShipMeshComponent.class);
            HullGeometry hull = data.hullGeometry;

            Mesh mesh = new Mesh(true, hull.vertexCount(), hull.indices.length,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_emissive"));

            mesh.setVertices(hull.vertices);
            mesh.setIndices(hull.indices);
            meshComp.hullMesh = mesh;
            shipMeshes.add(mesh);
        }
    }
```

Add ship shader (reuse terrain shader pattern but with emissive support):

```java
    private ShaderProgram getShipShader() {
        if (shipShader != null) return shipShader;

        String vert =
            "attribute vec3 a_position;\n" +
            "attribute vec3 a_normal;\n" +
            "attribute vec4 a_color;\n" +
            "attribute float a_emissive;\n" +
            "uniform mat4 u_projViewTrans;\n" +
            "uniform mat4 u_worldTrans;\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying float v_emissive;\n" +
            "void main() {\n" +
            "    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);\n" +
            "    v_color = a_color;\n" +
            "    v_emissive = a_emissive;\n" +
            "    gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);\n" +
            "}\n";

        String frag =
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying float v_emissive;\n" +
            "uniform vec3 u_lightDir;\n" +
            "uniform vec4 u_ambientColor;\n" +
            "void main() {\n" +
            "    vec3 lightDir = normalize(-u_lightDir);\n" +
            "    float diff = max(dot(v_normal, lightDir), 0.0);\n" +
            "    vec3 lit = v_color.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
            "    vec3 color = mix(lit, v_color.rgb * 2.0, v_emissive);\n" +
            "    gl_FragColor = vec4(color, 1.0);\n" +
            "}\n";

        shipShader = new ShaderProgram(vert, frag);
        if (!shipShader.isCompiled()) {
            Gdx.app.error("ShipShader", shipShader.getLog());
        }
        return shipShader;
    }
```

Add `renderShips()` called from `render()`:

```java
    private void renderShips() {
        ShaderProgram shader = getShipShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);

        for (int i = 0; i < shipEntities.size; i++) {
            Entity ship = shipEntities.get(i);
            TransformComponent t = ship.getComponent(TransformComponent.class);
            ShipMeshComponent meshComp = ship.getComponent(ShipMeshComponent.class);
            if (meshComp.hullMesh == null) continue;

            Matrix4 modelMat = new Matrix4();
            modelMat.set(t.position, t.rotation);
            shader.setUniformMatrix("u_worldTrans", modelMat);

            meshComp.hullMesh.render(shader, GL20.GL_TRIANGLES);
        }
    }
```

In `render()`, after `renderBoxes()`, add:

```java
        renderShips();
```

In `dispose()`, add cleanup:

```java
        if (shipFactory != null) { shipFactory.dispose(); shipFactory = null; }
        for (Mesh m : shipMeshes) { m.dispose(); }
        shipMeshes.clear();
        if (shipShader != null) { shipShader.dispose(); shipShader = null; }
```

- [ ] **Step 3: Compile and run**

Run: `.\gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL.

Run: `.\gradlew.bat :desktop:run`
Expected: Game launches with 3 procedurally generated ships visible on the terrain. Player can walk up to the small ship and see the interaction prompt.

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(ship): integrate ship spawning, rendering, and systems into GameWorld and GameScreen"
```

---

## Task 14: Update DebugHudSystem for Ship State

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/systems/DebugHudSystem.java`

- [ ] **Step 1: Add ship state display to DebugHudSystem**

Add new labels for player mode and ship speed. In `initialize()`, add:

```java
        modeLabel = new Label("Mode: -", style);
        shipSpeedLabel = new Label("Ship: -", style);
        promptLabel = new Label("", style);
        // Add to table before existing labels
        table.add(modeLabel).left().row();
        table.add(shipSpeedLabel).left().row();
        table.add(promptLabel).left().row();
```

In `update()`, read `PlayerStateComponent` and show current mode:

```java
            PlayerStateComponent playerState = player.getComponent(PlayerStateComponent.class);
            if (playerState != null) {
                modeLabel.setText("Mode: " + playerState.currentMode);
                if (playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING
                    && playerState.currentShip != null) {
                    PhysicsBodyComponent shipPhys = playerState.currentShip.getComponent(PhysicsBodyComponent.class);
                    if (shipPhys != null && shipPhys.body != null) {
                        float speed = shipPhys.body.getLinearVelocity().len();
                        shipSpeedLabel.setText(String.format("Ship Speed: %.1f m/s", speed));
                    }
                } else {
                    shipSpeedLabel.setText("Ship: -");
                }
            }
```

Subscribe to `InteractionPromptEvent` via EventBus to update the prompt label.

- [ ] **Step 2: Compile and run**

Run: `.\gradlew.bat :desktop:run`
Expected: Debug HUD shows "Mode: ON_FOOT_EXTERIOR". Walking near a ship shows the interaction prompt. Entering and piloting updates the mode display and ship speed.

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/systems/DebugHudSystem.java
git commit -m "feat(ship): update DebugHudSystem with player mode and ship speed display"
```

---

## Task 15: End-to-End Play Test + Polish

**Files:**
- Possibly modify any file based on testing findings.

- [ ] **Step 1: Launch the game and test the full flow**

Run: `.\gradlew.bat :desktop:run`

Test checklist:
1. Walk towards the small ship — interaction prompt appears at ~3m
2. Press E — player teleports into ship interior
3. Walk around inside — floors, walls, ceiling visible, physics works
4. Walk to cockpit — pilot seat prompt appears at ~2m
5. Press E — camera switches to cockpit view, HUD shows PILOTING mode
6. WASD moves ship, mouse rotates, Q/E rolls
7. Press V — camera toggles to chase view, ship visible
8. Press V again — back to cockpit
9. Press E — exit pilot seat, back to ON_FOOT_INTERIOR
10. Walk to airlock — exit prompt appears
11. Press E — exit ship, back on terrain
12. Visit medium and large ships — they should be visibly different sizes

- [ ] **Step 2: Fix any issues found during testing**

Address bugs, adjust tuning, fix visual glitches.

- [ ] **Step 3: Run full test suite**

Run: `.\gradlew.bat :core:test --info`
Expected: all tests PASS.

- [ ] **Step 4: Final commit**

```
git add -A
git commit -m "feat(ship): complete procedural ship system with enter/exit and 6DOF flight"
```
