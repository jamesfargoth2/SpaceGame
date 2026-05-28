# Ship Builder & Interior Customization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a phased ship builder (hull sculpt → room layout → module fit) at shipyard stations, with in-flight planning mode.

**Architecture:** A `ShipDesign` data object holds mutable hull params, room placements, and module assignments. The builder runs in a dedicated `DrydockScreen` with three sequential phases, each with its own camera and input handler. On commit, `ShipDesign.toBlueprint()` feeds into the existing `ShipFactory` pipeline. A lightweight `PlanningOverlay` provides in-flight modification queuing.

**Tech Stack:** Java 17, libGDX 1.13+, Ashley ECS, Bullet physics (gdx-bullet), Scene2D.UI, JUnit 5

**Spec:** [docs/superpowers/specs/2026-05-28-ship-builder-interior-customization-design.md](../specs/2026-05-28-ship-builder-interior-customization-design.md)

---

## File Map

### New Files — Data Model (`core/src/main/java/com/galacticodyssey/shipbuilder/`)

| File | Responsibility |
|------|---------------|
| `CrossSectionDef.java` | Immutable cross-section definition (t, width, height, exponent) |
| `AppendageDef.java` | Immutable appendage definition (type, spineT, side, scale) |
| `HullDesign.java` | Mutable hull: spine points, cross-sections, appendages |
| `RoomDesign.java` | Single room placement (type, grid position, size) |
| `ModuleAssignment.java` | Module → hardpoint binding (moduleId, blueprintId) |
| `DesignMetadata.java` | Computed aggregates: cost, mass, power draw |
| `ShipDesign.java` | Central mutable design: hull + rooms + modules + metadata |
| `ShipDesignValidator.java` | Hard constraint validation (hull fit, overlap, connectivity, budget) |
| `AdjacencyBonusCalculator.java` | Layout bonus computation (adjacency + positional) |
| `ShipStatsCalculator.java` | Aggregate ship stat computation |
| `BuildCostCalculator.java` | Credit cost computation from design |

### New Files — Blueprint & Economy (`core/src/main/java/com/galacticodyssey/shipbuilder/`)

| File | Responsibility |
|------|---------------|
| `BlueprintData.java` | Single blueprint definition (id, type, unlocks, rarity, price) |
| `BlueprintRegistry.java` | Loads blueprint definitions, tracks unlock state |

### New Files — Builder Core (`core/src/main/java/com/galacticodyssey/shipbuilder/`)

| File | Responsibility |
|------|---------------|
| `BuilderPhase.java` | Enum: HULL_SCULPT, ROOM_LAYOUT, MODULE_FIT |
| `BuilderPhaseController.java` | Phase state machine, transitions, validation gates |
| `DrydockScreen.java` | Main builder screen (extends Screen) |
| `DrydockScene.java` | 3D scene: drydock environment, ship preview mesh |

### New Files — Phase 1 (`core/src/main/java/com/galacticodyssey/shipbuilder/phase1/`)

| File | Responsibility |
|------|---------------|
| `HullSculptInputProcessor.java` | Orbital camera + gizmo click/drag |
| `HullSculptHUD.java` | Stats sidebar, phase toolbar |
| `ControlPointGizmo.java` | 3D translation gizmo rendering + picking |
| `CrossSectionEditor.java` | 2D Scene2D overlay for cross-section editing |
| `AppendageCatalog.java` | Appendage type browser (Scene2D panel) |

### New Files — Phase 2 (`core/src/main/java/com/galacticodyssey/shipbuilder/phase2/`)

| File | Responsibility |
|------|---------------|
| `RoomLayoutInputProcessor.java` | First-person movement + ghost placement |
| `RoomLayoutHUD.java` | Room palette, budget sidebar |
| `GhostVolume.java` | Translucent room preview following player gaze |
| `RoomGridRenderer.java` | Grid overlay on interior voxel surfaces |
| `CorridorPreview.java` | A* corridor preview rendering |

### New Files — Phase 3 (`core/src/main/java/com/galacticodyssey/shipbuilder/phase3/`)

| File | Responsibility |
|------|---------------|
| `ModuleFitInputProcessor.java` | Hardpoint click + interior/exterior toggle |
| `ModuleFitHUD.java` | Performance panel |
| `ModuleBrowserPanel.java` | Scene2D: inventory/shop/blueprints tabs |
| `HardpointMarkerRenderer.java` | Glowing hardpoint indicators on hull |

### New Files — In-Flight Planning (`core/src/main/java/com/galacticodyssey/shipbuilder/planning/`)

| File | Responsibility |
|------|---------------|
| `BuildAction.java` | Single queued modification (type, params, cost) |
| `BuildOrder.java` | Ordered list of BuildActions with validation |
| `PlanningOverlay.java` | Scene2D overlay for in-flight planning |
| `ShipSchematicRenderer.java` | 2D top-down ship schematic rendering |
| `BuildQueuePanel.java` | Build order list with costs |

### New Files — Events (`core/src/main/java/com/galacticodyssey/shipbuilder/events/`)

| File | Responsibility |
|------|---------------|
| `EnterDrydockEvent.java` | Player enters builder mode |
| `ExitDrydockEvent.java` | Player leaves builder (commit or cancel) |
| `BuildPhaseChangedEvent.java` | Phase transition |
| `ShipDesignCommittedEvent.java` | Build committed — carries final ShipDesign |
| `BuildOrderQueuedEvent.java` | In-flight: action added to queue |
| `BuildOrderAppliedEvent.java` | Docking: queued build order applied |

### New Data Files (`core/src/main/resources/data/shipbuilder/`)

| File | Responsibility |
|------|---------------|
| `blueprints.json` | Blueprint definitions (rooms, modules, appendages) |
| `build_costs.json` | Room costs, hull cost multipliers, fees |
| `adjacency_bonuses.json` | Room adjacency/positional bonus definitions |
| `module_catalog.json` | Installable modules with stats |
| `starting_blueprints.json` | Default unlocked blueprint IDs |

### Modified Files

| File | Change |
|------|--------|
| `ship/ShipBlueprint.java` | Add constructor overload accepting explicit hull params from ShipDesign |
| `ship/ShipFactory.java` | Add `createShipFromDesign(ShipDesign)` method |
| `data/GameSession.java` | Add `ShipDesign` and `BuildOrder` fields for persistence |

### New Test Files (`core/src/test/java/com/galacticodyssey/shipbuilder/`)

| File | Tests |
|------|-------|
| `CrossSectionDefTest.java` | Serialization, value range clamping |
| `HullDesignTest.java` | Spine point manipulation, cross-section CRUD |
| `RoomDesignTest.java` | Volume computation, grid bounds |
| `ShipDesignTest.java` | toBlueprint(), JSON round-trip |
| `ShipDesignValidatorTest.java` | All hard constraints |
| `AdjacencyBonusCalculatorTest.java` | Adjacency + positional bonus computation |
| `BuildCostCalculatorTest.java` | Room costs, hull costs, refunds |
| `BlueprintRegistryTest.java` | Load, unlock, query |
| `BuilderPhaseControllerTest.java` | Phase transitions, back navigation, validation gates |
| `BuildOrderTest.java` | Action queuing, validation, cost totals |

---

## Task 1: Core Data Model — Hull & Room Definitions

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/CrossSectionDef.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/AppendageDef.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/HullDesign.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/RoomDesign.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/ModuleAssignment.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/DesignMetadata.java`
- Test: `core/src/test/java/com/galacticodyssey/shipbuilder/HullDesignTest.java`
- Test: `core/src/test/java/com/galacticodyssey/shipbuilder/RoomDesignTest.java`

- [ ] **Step 1: Write CrossSectionDef and AppendageDef**

```java
// CrossSectionDef.java
package com.galacticodyssey.shipbuilder;

public final class CrossSectionDef {
    public float t;
    public float width;
    public float height;
    public float exponent;

    public CrossSectionDef() {}

    public CrossSectionDef(float t, float width, float height, float exponent) {
        this.t = t;
        this.width = width;
        this.height = height;
        this.exponent = exponent;
    }

    public CrossSectionDef copy() {
        return new CrossSectionDef(t, width, height, exponent);
    }
}
```

```java
// AppendageDef.java
package com.galacticodyssey.shipbuilder;

public final class AppendageDef {

    public enum AppendageType {
        SWEPT_WING, DELTA_WING, STRAIGHT_WING,
        ENGINE_POD, DORSAL_FIN, VENTRAL_FIN
    }

    public enum Side { LEFT, RIGHT, BOTH, CENTER }

    public AppendageType type;
    public float spineT;
    public Side side;
    public float scale;

    public AppendageDef() {}

    public AppendageDef(AppendageType type, float spineT, Side side, float scale) {
        this.type = type;
        this.spineT = spineT;
        this.side = side;
        this.scale = scale;
    }

    public AppendageDef copy() {
        return new AppendageDef(type, spineT, side, scale);
    }
}
```

- [ ] **Step 2: Write HullDesign**

```java
// HullDesign.java
package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public class HullDesign {
    public final List<Vector3> spinePoints = new ArrayList<>();
    public final List<CrossSectionDef> crossSections = new ArrayList<>();
    public final List<AppendageDef> appendages = new ArrayList<>();

    public void addSpinePoint(int index, Vector3 point) {
        spinePoints.add(index, new Vector3(point));
    }

    public void removeSpinePoint(int index) {
        if (spinePoints.size() <= 3) throw new IllegalStateException("Minimum 3 spine points required");
        spinePoints.remove(index);
    }

    public void moveSpinePoint(int index, Vector3 newPosition) {
        spinePoints.get(index).set(newPosition);
    }

    public void addCrossSection(CrossSectionDef def) {
        crossSections.add(def);
        crossSections.sort((a, b) -> Float.compare(a.t, b.t));
    }

    public void removeCrossSection(int index) {
        if (crossSections.size() <= 2) throw new IllegalStateException("Minimum 2 cross-sections required");
        crossSections.remove(index);
    }

    public void addAppendage(AppendageDef def) {
        appendages.add(def);
    }

    public void removeAppendage(int index) {
        appendages.remove(index);
    }

    public float estimateSpineLength() {
        float length = 0;
        for (int i = 1; i < spinePoints.size(); i++) {
            length += spinePoints.get(i).dst(spinePoints.get(i - 1));
        }
        return length;
    }

    public float estimateMaxWidth() {
        float max = 0;
        for (CrossSectionDef cs : crossSections) {
            max = Math.max(max, cs.width);
        }
        return max;
    }

    public float estimateMaxHeight() {
        float max = 0;
        for (CrossSectionDef cs : crossSections) {
            max = Math.max(max, cs.height);
        }
        return max;
    }

    public int countWingPairs() {
        int count = 0;
        for (AppendageDef a : appendages) {
            if (a.type == AppendageDef.AppendageType.SWEPT_WING
                || a.type == AppendageDef.AppendageType.DELTA_WING
                || a.type == AppendageDef.AppendageType.STRAIGHT_WING) {
                count++;
            }
        }
        return count;
    }

    public int countEnginePods() {
        int count = 0;
        for (AppendageDef a : appendages) {
            if (a.type == AppendageDef.AppendageType.ENGINE_POD) count++;
        }
        return count;
    }

    public HullDesign copy() {
        HullDesign copy = new HullDesign();
        for (Vector3 p : spinePoints) copy.spinePoints.add(new Vector3(p));
        for (CrossSectionDef cs : crossSections) copy.crossSections.add(cs.copy());
        for (AppendageDef a : appendages) copy.appendages.add(a.copy());
        return copy;
    }
}
```

- [ ] **Step 3: Write HullDesignTest**

```java
// HullDesignTest.java
package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HullDesignTest {
    private HullDesign hull;

    @BeforeEach
    void setUp() {
        hull = new HullDesign();
        hull.spinePoints.add(new Vector3(0, 0, 0));
        hull.spinePoints.add(new Vector3(0, 0, 5));
        hull.spinePoints.add(new Vector3(0, 0, 10));
        hull.addCrossSection(new CrossSectionDef(0f, 1f, 1f, 2f));
        hull.addCrossSection(new CrossSectionDef(0.5f, 3f, 2f, 2.5f));
        hull.addCrossSection(new CrossSectionDef(1f, 1.5f, 1f, 2f));
    }

    @Test
    void estimateSpineLength_returnsChordSum() {
        assertEquals(10f, hull.estimateSpineLength(), 0.01f);
    }

    @Test
    void estimateMaxWidth_returnsLargestCrossSection() {
        assertEquals(3f, hull.estimateMaxWidth(), 0.01f);
    }

    @Test
    void addSpinePoint_insertsAtIndex() {
        hull.addSpinePoint(1, new Vector3(0, 1, 2.5f));
        assertEquals(4, hull.spinePoints.size());
        assertEquals(2.5f, hull.spinePoints.get(1).z, 0.01f);
    }

    @Test
    void removeSpinePoint_throwsAtMinimum() {
        hull.removeSpinePoint(1);
        assertThrows(IllegalStateException.class, () -> hull.removeSpinePoint(0));
    }

    @Test
    void addCrossSection_maintainsSortByT() {
        hull.addCrossSection(new CrossSectionDef(0.25f, 2f, 1.5f, 2f));
        assertEquals(0f, hull.crossSections.get(0).t, 0.001f);
        assertEquals(0.25f, hull.crossSections.get(1).t, 0.001f);
        assertEquals(0.5f, hull.crossSections.get(2).t, 0.001f);
    }

    @Test
    void removeCrossSection_throwsAtMinimum() {
        hull.removeCrossSection(0);
        assertThrows(IllegalStateException.class, () -> hull.removeCrossSection(0));
    }

    @Test
    void countWingPairs_countsWingTypes() {
        hull.addAppendage(new AppendageDef(AppendageDef.AppendageType.SWEPT_WING, 0.4f, AppendageDef.Side.BOTH, 1f));
        hull.addAppendage(new AppendageDef(AppendageDef.AppendageType.ENGINE_POD, 0.8f, AppendageDef.Side.BOTH, 1f));
        assertEquals(1, hull.countWingPairs());
        assertEquals(1, hull.countEnginePods());
    }

    @Test
    void copy_isDeepCopy() {
        HullDesign copy = hull.copy();
        copy.spinePoints.get(0).set(99, 99, 99);
        assertEquals(0, hull.spinePoints.get(0).x, 0.01f);
    }
}
```

- [ ] **Step 4: Run HullDesignTest to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.shipbuilder.HullDesignTest" --info`
Expected: All 7 tests PASS

- [ ] **Step 5: Write RoomDesign and ModuleAssignment**

```java
// RoomDesign.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;

public final class RoomDesign {
    public RoomType type;
    public int gridX, gridY, gridZ;
    public int sizeX, sizeY, sizeZ;

    public RoomDesign() {}

    public RoomDesign(RoomType type, int gridX, int gridY, int gridZ, int sizeX, int sizeY, int sizeZ) {
        this.type = type;
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridZ = gridZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public int volume() {
        return sizeX * sizeY * sizeZ;
    }

    public boolean overlaps(RoomDesign other) {
        return gridX < other.gridX + other.sizeX && gridX + sizeX > other.gridX
            && gridY < other.gridY + other.sizeY && gridY + sizeY > other.gridY
            && gridZ < other.gridZ + other.sizeZ && gridZ + sizeZ > other.gridZ;
    }

    public boolean containsCell(int x, int y, int z) {
        return x >= gridX && x < gridX + sizeX
            && y >= gridY && y < gridY + sizeY
            && z >= gridZ && z < gridZ + sizeZ;
    }

    public RoomDesign copy() {
        return new RoomDesign(type, gridX, gridY, gridZ, sizeX, sizeY, sizeZ);
    }
}
```

```java
// ModuleAssignment.java
package com.galacticodyssey.shipbuilder;

public final class ModuleAssignment {
    public String moduleId;
    public String blueprintId;

    public ModuleAssignment() {}

    public ModuleAssignment(String moduleId, String blueprintId) {
        this.moduleId = moduleId;
        this.blueprintId = blueprintId;
    }
}
```

```java
// DesignMetadata.java
package com.galacticodyssey.shipbuilder;

public class DesignMetadata {
    public long createdAt;
    public long lastModified;
    public int buildCost;
    public float totalMass;
    public float totalPowerDraw;

    public DesignMetadata() {
        this.createdAt = System.currentTimeMillis();
        this.lastModified = this.createdAt;
    }
}
```

- [ ] **Step 6: Write RoomDesignTest**

```java
// RoomDesignTest.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomDesignTest {

    @Test
    void volume_computesCorrectly() {
        RoomDesign room = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        assertEquals(36, room.volume());
    }

    @Test
    void overlaps_detectsOverlap() {
        RoomDesign a = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        RoomDesign b = new RoomDesign(RoomType.MEDBAY, 3, 0, 0, 3, 3, 3);
        assertTrue(a.overlaps(b));
    }

    @Test
    void overlaps_returnsFalseForAdjacentRooms() {
        RoomDesign a = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        RoomDesign b = new RoomDesign(RoomType.MEDBAY, 4, 0, 0, 3, 3, 3);
        assertFalse(a.overlaps(b));
    }

    @Test
    void containsCell_checksBounds() {
        RoomDesign room = new RoomDesign(RoomType.CARGO_BAY, 5, 0, 2, 4, 3, 3);
        assertTrue(room.containsCell(5, 0, 2));
        assertTrue(room.containsCell(8, 2, 4));
        assertFalse(room.containsCell(9, 0, 2));
        assertFalse(room.containsCell(4, 0, 2));
    }

    @Test
    void copy_isIndependent() {
        RoomDesign original = new RoomDesign(RoomType.ARMORY, 1, 2, 3, 3, 3, 3);
        RoomDesign copy = original.copy();
        copy.gridX = 99;
        assertEquals(1, original.gridX);
    }
}
```

- [ ] **Step 7: Run RoomDesignTest**

Run: `./gradlew :core:test --tests "com.galacticodyssey.shipbuilder.RoomDesignTest" --info`
Expected: All 5 tests PASS

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/CrossSectionDef.java \
        core/src/main/java/com/galacticodyssey/shipbuilder/AppendageDef.java \
        core/src/main/java/com/galacticodyssey/shipbuilder/HullDesign.java \
        core/src/main/java/com/galacticodyssey/shipbuilder/RoomDesign.java \
        core/src/main/java/com/galacticodyssey/shipbuilder/ModuleAssignment.java \
        core/src/main/java/com/galacticodyssey/shipbuilder/DesignMetadata.java \
        core/src/test/java/com/galacticodyssey/shipbuilder/HullDesignTest.java \
        core/src/test/java/com/galacticodyssey/shipbuilder/RoomDesignTest.java
git commit -m "feat(shipbuilder): add core data model classes"
```

---

## Task 2: ShipDesign — Central Design Object

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/ShipDesign.java`
- Modify: `core/src/main/java/com/galacticodyssey/ship/ShipBlueprint.java`
- Test: `core/src/test/java/com/galacticodyssey/shipbuilder/ShipDesignTest.java`

- [ ] **Step 1: Write ShipDesign**

```java
// ShipDesign.java
package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.ShipBlueprint;
import com.galacticodyssey.ship.ShipSizeClass;

import java.util.*;

public class ShipDesign {
    public String designId;
    public String name;
    public ShipSizeClass sizeClass;
    public final HullDesign hull = new HullDesign();
    public final List<RoomDesign> rooms = new ArrayList<>();
    public final Map<String, ModuleAssignment> modules = new LinkedHashMap<>();
    public final DesignMetadata metadata = new DesignMetadata();

    public ShipDesign() {
        this.designId = UUID.randomUUID().toString();
        this.name = "Untitled Ship";
        this.sizeClass = ShipSizeClass.SMALL;
    }

    public ShipDesign(ShipSizeClass sizeClass) {
        this();
        this.sizeClass = sizeClass;
    }

    public static ShipDesign fromSeed(long seed, ShipSizeClass sizeClass) {
        ShipBlueprint bp = new ShipBlueprint(seed, sizeClass);
        ShipDesign design = new ShipDesign(sizeClass);
        design.hull.spinePoints.add(new Vector3(0, 0, 0));
        design.hull.spinePoints.add(new Vector3(0, bp.maxHeight * 0.15f, bp.spineLength * 0.33f));
        design.hull.spinePoints.add(new Vector3(0, bp.maxHeight * 0.05f, bp.spineLength * 0.66f));
        design.hull.spinePoints.add(new Vector3(0, 0, bp.spineLength));

        int csCount = bp.crossSectionCount;
        for (int i = 0; i < csCount; i++) {
            float t = (float) i / (csCount - 1);
            float envelope = computeEnvelope(t);
            design.hull.addCrossSection(new CrossSectionDef(
                t, bp.maxWidth * envelope, bp.maxHeight * envelope, 2.5f
            ));
        }

        for (int w = 0; w < bp.wingPairs; w++) {
            float spineT = 0.35f + w * 0.08f;
            design.hull.addAppendage(new AppendageDef(
                AppendageDef.AppendageType.SWEPT_WING, spineT, AppendageDef.Side.BOTH, 1f
            ));
        }
        for (int e = 0; e < bp.enginePodCount; e++) {
            float spineT = 0.8f + e * 0.05f;
            design.hull.addAppendage(new AppendageDef(
                AppendageDef.AppendageType.ENGINE_POD, spineT, AppendageDef.Side.BOTH, 1f
            ));
        }

        return design;
    }

    private static float computeEnvelope(float t) {
        if (t < 0.15f) return t / 0.15f;
        if (t < 0.65f) return 1f;
        return 1f - ((t - 0.65f) / 0.35f) * 0.6f;
    }

    public ShipBlueprint toBlueprint() {
        return new ShipBlueprint(
            sizeClass,
            hull.estimateSpineLength(),
            hull.crossSections.size(),
            hull.estimateMaxWidth(),
            hull.estimateMaxHeight(),
            hull.countWingPairs(),
            hull.countEnginePods()
        );
    }

    public void addRoom(RoomDesign room) {
        rooms.add(room);
        metadata.lastModified = System.currentTimeMillis();
    }

    public void removeRoom(int index) {
        rooms.remove(index);
        metadata.lastModified = System.currentTimeMillis();
    }

    public void setModule(String hardpointId, ModuleAssignment assignment) {
        modules.put(hardpointId, assignment);
        metadata.lastModified = System.currentTimeMillis();
    }

    public void clearModule(String hardpointId) {
        modules.remove(hardpointId);
        metadata.lastModified = System.currentTimeMillis();
    }

    public int totalRoomVolume() {
        int total = 0;
        for (RoomDesign r : rooms) total += r.volume();
        return total;
    }
}
```

- [ ] **Step 2: Add explicit-params constructor to ShipBlueprint**

Add a second constructor to the existing `ShipBlueprint.java` that accepts explicit hull parameters instead of deriving them from a seed:

```java
// Add to ShipBlueprint.java — new constructor after existing one
public ShipBlueprint(ShipSizeClass sizeClass, float spineLength, int crossSectionCount,
                     float maxWidth, float maxHeight, int wingPairs, int enginePodCount) {
    this.seed = 0;
    this.sizeClass = sizeClass;
    this.spineLength = spineLength;
    this.crossSectionCount = crossSectionCount;
    this.maxWidth = maxWidth;
    this.maxHeight = maxHeight;
    this.wingPairs = wingPairs;
    this.enginePodCount = enginePodCount;
}
```

- [ ] **Step 3: Write ShipDesignTest**

```java
// ShipDesignTest.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipBlueprint;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipDesignTest {

    @Test
    void fromSeed_populatesHullFromBlueprint() {
        ShipDesign design = ShipDesign.fromSeed(42L, ShipSizeClass.MEDIUM);
        assertEquals(ShipSizeClass.MEDIUM, design.sizeClass);
        assertEquals(4, design.hull.spinePoints.size());
        assertTrue(design.hull.crossSections.size() >= 8);
        assertTrue(design.hull.estimateSpineLength() > 0);
    }

    @Test
    void toBlueprint_producesValidBlueprint() {
        ShipDesign design = ShipDesign.fromSeed(42L, ShipSizeClass.SMALL);
        ShipBlueprint bp = design.toBlueprint();
        assertEquals(ShipSizeClass.SMALL, bp.sizeClass);
        assertTrue(bp.spineLength > 0);
        assertTrue(bp.crossSectionCount >= 2);
    }

    @Test
    void addRoom_incrementsRoomList() {
        ShipDesign design = new ShipDesign(ShipSizeClass.SMALL);
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        assertEquals(1, design.rooms.size());
    }

    @Test
    void totalRoomVolume_sumsAllRooms() {
        ShipDesign design = new ShipDesign(ShipSizeClass.SMALL);
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 5, 0, 0, 3, 3, 3));
        assertEquals(36 + 27, design.totalRoomVolume());
    }

    @Test
    void setModule_storesAssignment() {
        ShipDesign design = new ShipDesign(ShipSizeClass.SMALL);
        design.setModule("WPN-1", new ModuleAssignment("laser_mk1", "bp_laser_mk1"));
        assertNotNull(design.modules.get("WPN-1"));
        assertEquals("laser_mk1", design.modules.get("WPN-1").moduleId);
    }

    @Test
    void clearModule_removesAssignment() {
        ShipDesign design = new ShipDesign(ShipSizeClass.SMALL);
        design.setModule("WPN-1", new ModuleAssignment("laser_mk1", "bp_laser_mk1"));
        design.clearModule("WPN-1");
        assertNull(design.modules.get("WPN-1"));
    }
}
```

- [ ] **Step 4: Run ShipDesignTest**

Run: `./gradlew :core:test --tests "com.galacticodyssey.shipbuilder.ShipDesignTest" --info`
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/ShipDesign.java \
        core/src/main/java/com/galacticodyssey/ship/ShipBlueprint.java \
        core/src/test/java/com/galacticodyssey/shipbuilder/ShipDesignTest.java
git commit -m "feat(shipbuilder): add ShipDesign and extend ShipBlueprint with explicit params"
```

---

## Task 3: Blueprint System & Data Files

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/BlueprintData.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/BlueprintRegistry.java`
- Create: `core/src/main/resources/data/shipbuilder/blueprints.json`
- Create: `core/src/main/resources/data/shipbuilder/starting_blueprints.json`
- Test: `core/src/test/java/com/galacticodyssey/shipbuilder/BlueprintRegistryTest.java`

- [ ] **Step 1: Write BlueprintData**

```java
// BlueprintData.java
package com.galacticodyssey.shipbuilder;

public class BlueprintData {
    public enum BlueprintType { ROOM, MODULE, APPENDAGE }
    public enum Rarity { COMMON, UNCOMMON, RARE, EPIC }

    public String blueprintId;
    public BlueprintType type;
    public String unlocks;
    public Rarity rarity;
    public int shopPrice;
    public String description;

    public BlueprintData() {}
}
```

- [ ] **Step 2: Write blueprints.json and starting_blueprints.json**

```json
// blueprints.json
[
  { "blueprintId": "bp_cockpit", "type": "ROOM", "unlocks": "COCKPIT", "rarity": "COMMON", "shopPrice": 0, "description": "Standard cockpit" },
  { "blueprintId": "bp_engine_room", "type": "ROOM", "unlocks": "ENGINE_ROOM", "rarity": "COMMON", "shopPrice": 0, "description": "Engine room" },
  { "blueprintId": "bp_crew_quarters", "type": "ROOM", "unlocks": "CREW_QUARTERS", "rarity": "COMMON", "shopPrice": 0, "description": "Crew quarters" },
  { "blueprintId": "bp_cargo_bay", "type": "ROOM", "unlocks": "CARGO_BAY", "rarity": "COMMON", "shopPrice": 0, "description": "Cargo bay" },
  { "blueprintId": "bp_medbay", "type": "ROOM", "unlocks": "MEDBAY", "rarity": "UNCOMMON", "shopPrice": 15000, "description": "Medical bay" },
  { "blueprintId": "bp_armory", "type": "ROOM", "unlocks": "ARMORY", "rarity": "UNCOMMON", "shopPrice": 18000, "description": "Armory" },
  { "blueprintId": "bp_brig", "type": "ROOM", "unlocks": "BRIG", "rarity": "RARE", "shopPrice": 25000, "description": "Brig holding cells" },
  { "blueprintId": "bp_laser_mk1", "type": "MODULE", "unlocks": "laser_mk1", "rarity": "COMMON", "shopPrice": 0, "description": "Basic laser cannon" },
  { "blueprintId": "bp_laser_mk2", "type": "MODULE", "unlocks": "laser_mk2", "rarity": "UNCOMMON", "shopPrice": 12000, "description": "Improved laser cannon" },
  { "blueprintId": "bp_missile_s", "type": "MODULE", "unlocks": "missile_rack_s", "rarity": "UNCOMMON", "shopPrice": 20000, "description": "Small missile rack" },
  { "blueprintId": "bp_thruster_basic", "type": "MODULE", "unlocks": "thruster_basic", "rarity": "COMMON", "shopPrice": 0, "description": "Standard thruster" },
  { "blueprintId": "bp_fusion_m", "type": "MODULE", "unlocks": "fusion_drive_m", "rarity": "RARE", "shopPrice": 35000, "description": "Medium fusion drive" },
  { "blueprintId": "bp_shield_1", "type": "MODULE", "unlocks": "shield_gen_mk1", "rarity": "UNCOMMON", "shopPrice": 22000, "description": "Basic shield generator" },
  { "blueprintId": "bp_scanner_1", "type": "MODULE", "unlocks": "scanner_mk1", "rarity": "COMMON", "shopPrice": 5000, "description": "Basic sensor array" },
  { "blueprintId": "bp_swept_wing", "type": "APPENDAGE", "unlocks": "SWEPT_WING", "rarity": "COMMON", "shopPrice": 0, "description": "Swept wing" },
  { "blueprintId": "bp_delta_wing", "type": "APPENDAGE", "unlocks": "DELTA_WING", "rarity": "UNCOMMON", "shopPrice": 10000, "description": "Delta wing" },
  { "blueprintId": "bp_engine_pod", "type": "APPENDAGE", "unlocks": "ENGINE_POD", "rarity": "COMMON", "shopPrice": 0, "description": "Engine nacelle pod" },
  { "blueprintId": "bp_dorsal_fin", "type": "APPENDAGE", "unlocks": "DORSAL_FIN", "rarity": "COMMON", "shopPrice": 3000, "description": "Dorsal stabilizer" }
]
```

```json
// starting_blueprints.json
[
  "bp_cockpit", "bp_engine_room", "bp_crew_quarters", "bp_cargo_bay",
  "bp_laser_mk1", "bp_thruster_basic", "bp_swept_wing", "bp_engine_pod"
]
```

- [ ] **Step 3: Write BlueprintRegistry**

```java
// BlueprintRegistry.java
package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.*;

public class BlueprintRegistry {
    private final Map<String, BlueprintData> allBlueprints = new LinkedHashMap<>();
    private final Set<String> unlockedIds = new LinkedHashSet<>();

    public void loadFromFiles(String blueprintsPath, String startingPath) {
        Json json = new Json();
        JsonReader reader = new JsonReader();

        JsonValue bpArray = reader.parse(Gdx.files.internal(blueprintsPath));
        for (JsonValue entry = bpArray.child; entry != null; entry = entry.next) {
            BlueprintData bp = json.readValue(BlueprintData.class, entry);
            allBlueprints.put(bp.blueprintId, bp);
        }

        JsonValue startArray = reader.parse(Gdx.files.internal(startingPath));
        for (JsonValue id = startArray.child; id != null; id = id.next) {
            unlockedIds.add(id.asString());
        }
    }

    public void loadFromData(List<BlueprintData> blueprints, List<String> startingIds) {
        for (BlueprintData bp : blueprints) allBlueprints.put(bp.blueprintId, bp);
        unlockedIds.addAll(startingIds);
    }

    public BlueprintData get(String blueprintId) {
        return allBlueprints.get(blueprintId);
    }

    public boolean isUnlocked(String blueprintId) {
        return unlockedIds.contains(blueprintId);
    }

    public void unlock(String blueprintId) {
        unlockedIds.add(blueprintId);
    }

    public List<BlueprintData> getByType(BlueprintData.BlueprintType type) {
        List<BlueprintData> result = new ArrayList<>();
        for (BlueprintData bp : allBlueprints.values()) {
            if (bp.type == type) result.add(bp);
        }
        return result;
    }

    public List<BlueprintData> getUnlockedByType(BlueprintData.BlueprintType type) {
        List<BlueprintData> result = new ArrayList<>();
        for (BlueprintData bp : allBlueprints.values()) {
            if (bp.type == type && unlockedIds.contains(bp.blueprintId)) result.add(bp);
        }
        return result;
    }

    public boolean isRoomUnlocked(String roomTypeName) {
        for (BlueprintData bp : allBlueprints.values()) {
            if (bp.type == BlueprintData.BlueprintType.ROOM
                && bp.unlocks.equals(roomTypeName)
                && unlockedIds.contains(bp.blueprintId)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getUnlockedIds() {
        return Collections.unmodifiableSet(unlockedIds);
    }

    public void setUnlockedIds(Collection<String> ids) {
        unlockedIds.clear();
        unlockedIds.addAll(ids);
    }
}
```

- [ ] **Step 4: Write BlueprintRegistryTest**

```java
// BlueprintRegistryTest.java
package com.galacticodyssey.shipbuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintRegistryTest {
    private BlueprintRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BlueprintRegistry();
        List<BlueprintData> blueprints = new ArrayList<>();

        BlueprintData cockpit = new BlueprintData();
        cockpit.blueprintId = "bp_cockpit";
        cockpit.type = BlueprintData.BlueprintType.ROOM;
        cockpit.unlocks = "COCKPIT";
        cockpit.rarity = BlueprintData.Rarity.COMMON;
        cockpit.shopPrice = 0;
        blueprints.add(cockpit);

        BlueprintData medbay = new BlueprintData();
        medbay.blueprintId = "bp_medbay";
        medbay.type = BlueprintData.BlueprintType.ROOM;
        medbay.unlocks = "MEDBAY";
        medbay.rarity = BlueprintData.Rarity.UNCOMMON;
        medbay.shopPrice = 15000;
        blueprints.add(medbay);

        BlueprintData laser = new BlueprintData();
        laser.blueprintId = "bp_laser_mk1";
        laser.type = BlueprintData.BlueprintType.MODULE;
        laser.unlocks = "laser_mk1";
        laser.rarity = BlueprintData.Rarity.COMMON;
        laser.shopPrice = 0;
        blueprints.add(laser);

        registry.loadFromData(blueprints, Arrays.asList("bp_cockpit", "bp_laser_mk1"));
    }

    @Test
    void isUnlocked_returnsTrueForStartingBlueprints() {
        assertTrue(registry.isUnlocked("bp_cockpit"));
        assertTrue(registry.isUnlocked("bp_laser_mk1"));
    }

    @Test
    void isUnlocked_returnsFalseForLockedBlueprints() {
        assertFalse(registry.isUnlocked("bp_medbay"));
    }

    @Test
    void unlock_makesBluprintAvailable() {
        registry.unlock("bp_medbay");
        assertTrue(registry.isUnlocked("bp_medbay"));
    }

    @Test
    void getByType_filtersCorrectly() {
        List<BlueprintData> rooms = registry.getByType(BlueprintData.BlueprintType.ROOM);
        assertEquals(2, rooms.size());
        List<BlueprintData> modules = registry.getByType(BlueprintData.BlueprintType.MODULE);
        assertEquals(1, modules.size());
    }

    @Test
    void getUnlockedByType_filtersLockedOut() {
        List<BlueprintData> unlockedRooms = registry.getUnlockedByType(BlueprintData.BlueprintType.ROOM);
        assertEquals(1, unlockedRooms.size());
        assertEquals("bp_cockpit", unlockedRooms.get(0).blueprintId);
    }

    @Test
    void isRoomUnlocked_checksByRoomTypeName() {
        assertTrue(registry.isRoomUnlocked("COCKPIT"));
        assertFalse(registry.isRoomUnlocked("MEDBAY"));
    }
}
```

- [ ] **Step 5: Run BlueprintRegistryTest**

Run: `./gradlew :core:test --tests "com.galacticodyssey.shipbuilder.BlueprintRegistryTest" --info`
Expected: All 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/BlueprintData.java \
        core/src/main/java/com/galacticodyssey/shipbuilder/BlueprintRegistry.java \
        core/src/main/resources/data/shipbuilder/blueprints.json \
        core/src/main/resources/data/shipbuilder/starting_blueprints.json \
        core/src/test/java/com/galacticodyssey/shipbuilder/BlueprintRegistryTest.java
git commit -m "feat(shipbuilder): add blueprint system with data files and registry"
```

---

## Task 4: Build Cost Calculator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/BuildCostCalculator.java`
- Create: `core/src/main/resources/data/shipbuilder/build_costs.json`
- Test: `core/src/test/java/com/galacticodyssey/shipbuilder/BuildCostCalculatorTest.java`

- [ ] **Step 1: Write build_costs.json**

```json
{
  "roomCosts": {
    "COCKPIT":       { "base": 5000,  "perM3": 200 },
    "ENGINE_ROOM":   { "base": 8000,  "perM3": 300 },
    "CREW_QUARTERS": { "base": 3000,  "perM3": 150 },
    "MEDBAY":        { "base": 6000,  "perM3": 250 },
    "ARMORY":        { "base": 7000,  "perM3": 280 },
    "CARGO_BAY":     { "base": 2000,  "perM3": 100 },
    "BRIG":          { "base": 4000,  "perM3": 200 }
  },
  "hullCostPerMeter": {
    "SMALL": 1000,
    "MEDIUM": 2500,
    "LARGE": 5000
  },
  "installFee": 500,
  "refundRate": 0.7
}
```

- [ ] **Step 2: Write BuildCostCalculator**

```java
// BuildCostCalculator.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;

import java.util.EnumMap;
import java.util.Map;

public class BuildCostCalculator {
    private final Map<RoomType, RoomCost> roomCosts = new EnumMap<>(RoomType.class);
    private final Map<ShipSizeClass, Integer> hullCostPerMeter = new EnumMap<>(ShipSizeClass.class);
    private int installFee = 500;
    private float refundRate = 0.7f;

    public static class RoomCost {
        public final int base;
        public final int perM3;
        public RoomCost(int base, int perM3) { this.base = base; this.perM3 = perM3; }
    }

    public BuildCostCalculator() {
        roomCosts.put(RoomType.COCKPIT, new RoomCost(5000, 200));
        roomCosts.put(RoomType.ENGINE_ROOM, new RoomCost(8000, 300));
        roomCosts.put(RoomType.CREW_QUARTERS, new RoomCost(3000, 150));
        roomCosts.put(RoomType.MEDBAY, new RoomCost(6000, 250));
        roomCosts.put(RoomType.ARMORY, new RoomCost(7000, 280));
        roomCosts.put(RoomType.CARGO_BAY, new RoomCost(2000, 100));
        roomCosts.put(RoomType.BRIG, new RoomCost(4000, 200));

        hullCostPerMeter.put(ShipSizeClass.SMALL, 1000);
        hullCostPerMeter.put(ShipSizeClass.MEDIUM, 2500);
        hullCostPerMeter.put(ShipSizeClass.LARGE, 5000);
    }

    public int roomConstructionCost(RoomDesign room) {
        RoomCost cost = roomCosts.get(room.type);
        if (cost == null) return 0;
        return cost.base + cost.perM3 * room.volume();
    }

    public int roomRefund(RoomDesign room) {
        return (int) (roomConstructionCost(room) * refundRate);
    }

    public int hullCost(ShipSizeClass sizeClass, float spineLength) {
        Integer costPerMeter = hullCostPerMeter.get(sizeClass);
        if (costPerMeter == null) return 0;
        return (int) (costPerMeter * spineLength);
    }

    public int moduleInstallFee() {
        return installFee;
    }

    public int totalDesignCost(ShipDesign design) {
        int total = hullCost(design.sizeClass, design.hull.estimateSpineLength());
        for (RoomDesign room : design.rooms) {
            total += roomConstructionCost(room);
        }
        total += design.modules.size() * installFee;
        return total;
    }
}
```

- [ ] **Step 3: Write BuildCostCalculatorTest**

```java
// BuildCostCalculatorTest.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildCostCalculatorTest {
    private BuildCostCalculator calc;

    @BeforeEach
    void setUp() {
        calc = new BuildCostCalculator();
    }

    @Test
    void roomConstructionCost_baseAndVolumeScaling() {
        RoomDesign cockpit = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        // base=5000 + perM3=200 * volume=36 = 5000 + 7200 = 12200
        assertEquals(12200, calc.roomConstructionCost(cockpit));
    }

    @Test
    void roomRefund_is70Percent() {
        RoomDesign cockpit = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        int cost = calc.roomConstructionCost(cockpit);
        assertEquals((int)(cost * 0.7f), calc.roomRefund(cockpit));
    }

    @Test
    void hullCost_scalesBySizeClass() {
        assertEquals(10000, calc.hullCost(ShipSizeClass.SMALL, 10f));
        assertEquals(25000, calc.hullCost(ShipSizeClass.MEDIUM, 10f));
        assertEquals(50000, calc.hullCost(ShipSizeClass.LARGE, 10f));
    }

    @Test
    void totalDesignCost_sumsAllComponents() {
        ShipDesign design = ShipDesign.fromSeed(42L, ShipSizeClass.SMALL);
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.setModule("WPN-1", new ModuleAssignment("laser_mk1", "bp_laser_mk1"));
        int total = calc.totalDesignCost(design);
        assertTrue(total > 0);
        int expectedHull = calc.hullCost(ShipSizeClass.SMALL, design.hull.estimateSpineLength());
        int expectedRoom = calc.roomConstructionCost(design.rooms.get(0));
        int expectedModule = calc.moduleInstallFee();
        assertEquals(expectedHull + expectedRoom + expectedModule, total);
    }
}
```

- [ ] **Step 4: Run BuildCostCalculatorTest**

Run: `./gradlew :core:test --tests "com.galacticodyssey.shipbuilder.BuildCostCalculatorTest" --info`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/BuildCostCalculator.java \
        core/src/main/resources/data/shipbuilder/build_costs.json \
        core/src/test/java/com/galacticodyssey/shipbuilder/BuildCostCalculatorTest.java
git commit -m "feat(shipbuilder): add build cost calculator with data-driven pricing"
```

---

## Task 5: Validation & Layout Bonuses

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/ShipDesignValidator.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/AdjacencyBonusCalculator.java`
- Create: `core/src/main/resources/data/shipbuilder/adjacency_bonuses.json`
- Test: `core/src/test/java/com/galacticodyssey/shipbuilder/ShipDesignValidatorTest.java`
- Test: `core/src/test/java/com/galacticodyssey/shipbuilder/AdjacencyBonusCalculatorTest.java`

- [ ] **Step 1: Write ShipDesignValidator**

```java
// ShipDesignValidator.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;

import java.util.*;

public class ShipDesignValidator {

    public static class ValidationError {
        public final String code;
        public final String message;
        public ValidationError(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    public List<ValidationError> validate(ShipDesign design, boolean[][][] hullMask) {
        List<ValidationError> errors = new ArrayList<>();
        validateRequiredRooms(design, errors);
        validateNoOverlaps(design, errors);
        if (hullMask != null) {
            validateRoomsFitInHull(design, hullMask, errors);
        }
        validateConnectivity(design, errors);
        return errors;
    }

    private void validateRequiredRooms(ShipDesign design, List<ValidationError> errors) {
        boolean hasCockpit = false, hasEngine = false;
        for (RoomDesign r : design.rooms) {
            if (r.type == RoomType.COCKPIT) hasCockpit = true;
            if (r.type == RoomType.ENGINE_ROOM) hasEngine = true;
        }
        if (!hasCockpit) errors.add(new ValidationError("MISSING_COCKPIT", "Cockpit is required"));
        if (!hasEngine) errors.add(new ValidationError("MISSING_ENGINE_ROOM", "Engine Room is required"));
    }

    private void validateNoOverlaps(ShipDesign design, List<ValidationError> errors) {
        for (int i = 0; i < design.rooms.size(); i++) {
            for (int j = i + 1; j < design.rooms.size(); j++) {
                if (design.rooms.get(i).overlaps(design.rooms.get(j))) {
                    errors.add(new ValidationError("ROOM_OVERLAP",
                        "Room " + design.rooms.get(i).type + " overlaps " + design.rooms.get(j).type));
                }
            }
        }
    }

    private void validateRoomsFitInHull(ShipDesign design, boolean[][][] hullMask, List<ValidationError> errors) {
        int maxX = hullMask.length;
        int maxY = hullMask[0].length;
        int maxZ = hullMask[0][0].length;
        for (RoomDesign room : design.rooms) {
            for (int x = room.gridX; x < room.gridX + room.sizeX; x++) {
                for (int y = room.gridY; y < room.gridY + room.sizeY; y++) {
                    for (int z = room.gridZ; z < room.gridZ + room.sizeZ; z++) {
                        if (x < 0 || x >= maxX || y < 0 || y >= maxY || z < 0 || z >= maxZ
                            || !hullMask[x][y][z]) {
                            errors.add(new ValidationError("ROOM_OUTSIDE_HULL",
                                room.type + " extends outside hull at (" + x + "," + y + "," + z + ")"));
                            return;
                        }
                    }
                }
            }
        }
    }

    private void validateConnectivity(ShipDesign design, List<ValidationError> errors) {
        if (design.rooms.size() <= 1) return;
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        visited.add(0);
        queue.add(0);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            RoomDesign currentRoom = design.rooms.get(current);
            for (int i = 0; i < design.rooms.size(); i++) {
                if (visited.contains(i)) continue;
                if (areAdjacent(currentRoom, design.rooms.get(i))) {
                    visited.add(i);
                    queue.add(i);
                }
            }
        }
        if (visited.size() < design.rooms.size()) {
            errors.add(new ValidationError("DISCONNECTED_ROOMS", "Not all rooms are reachable"));
        }
    }

    public boolean areAdjacent(RoomDesign a, RoomDesign b) {
        boolean xOverlap = a.gridX < b.gridX + b.sizeX && a.gridX + a.sizeX > b.gridX;
        boolean yOverlap = a.gridY < b.gridY + b.sizeY && a.gridY + a.sizeY > b.gridY;
        boolean zOverlap = a.gridZ < b.gridZ + b.sizeZ && a.gridZ + a.sizeZ > b.gridZ;

        boolean xTouching = (a.gridX + a.sizeX == b.gridX || b.gridX + b.sizeX == a.gridX);
        boolean yTouching = (a.gridY + a.sizeY == b.gridY || b.gridY + b.sizeY == a.gridY);
        boolean zTouching = (a.gridZ + a.sizeZ == b.gridZ || b.gridZ + b.sizeZ == a.gridZ);

        return (xTouching && yOverlap && zOverlap)
            || (yTouching && xOverlap && zOverlap)
            || (zTouching && xOverlap && yOverlap);
    }

    public boolean canPlaceRoom(ShipDesign design, RoomDesign candidate, boolean[][][] hullMask) {
        for (RoomDesign existing : design.rooms) {
            if (candidate.overlaps(existing)) return false;
        }
        if (hullMask != null) {
            int maxX = hullMask.length, maxY = hullMask[0].length, maxZ = hullMask[0][0].length;
            for (int x = candidate.gridX; x < candidate.gridX + candidate.sizeX; x++) {
                for (int y = candidate.gridY; y < candidate.gridY + candidate.sizeY; y++) {
                    for (int z = candidate.gridZ; z < candidate.gridZ + candidate.sizeZ; z++) {
                        if (x < 0 || x >= maxX || y < 0 || y >= maxY || z < 0 || z >= maxZ
                            || !hullMask[x][y][z]) {
                            return false;
                        }
                    }
                }
            }
        }
        if (!design.rooms.isEmpty()) {
            boolean adjacentToAny = false;
            for (RoomDesign existing : design.rooms) {
                if (areAdjacent(candidate, existing)) { adjacentToAny = true; break; }
            }
            if (!adjacentToAny) return false;
        }
        return true;
    }
}
```

- [ ] **Step 2: Write ShipDesignValidatorTest**

```java
// ShipDesignValidatorTest.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShipDesignValidatorTest {
    private ShipDesignValidator validator;
    private ShipDesign design;

    @BeforeEach
    void setUp() {
        validator = new ShipDesignValidator();
        design = new ShipDesign(ShipSizeClass.SMALL);
    }

    @Test
    void validate_missingRequiredRooms() {
        List<ShipDesignValidator.ValidationError> errors = validator.validate(design, null);
        assertTrue(errors.stream().anyMatch(e -> e.code.equals("MISSING_COCKPIT")));
        assertTrue(errors.stream().anyMatch(e -> e.code.equals("MISSING_ENGINE_ROOM")));
    }

    @Test
    void validate_noErrorsWithRequiredRooms() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 4, 0, 0, 4, 3, 3));
        List<ShipDesignValidator.ValidationError> errors = validator.validate(design, null);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_detectsOverlap() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 3, 0, 0, 4, 3, 3));
        List<ShipDesignValidator.ValidationError> errors = validator.validate(design, null);
        assertTrue(errors.stream().anyMatch(e -> e.code.equals("ROOM_OVERLAP")));
    }

    @Test
    void validate_detectsDisconnectedRooms() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 20, 0, 0, 4, 3, 3));
        List<ShipDesignValidator.ValidationError> errors = validator.validate(design, null);
        assertTrue(errors.stream().anyMatch(e -> e.code.equals("DISCONNECTED_ROOMS")));
    }

    @Test
    void validate_roomOutsideHull() {
        boolean[][][] mask = new boolean[10][5][5];
        for (int x = 0; x < 10; x++)
            for (int y = 0; y < 5; y++)
                for (int z = 0; z < 5; z++)
                    mask[x][y][z] = true;
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 8, 0, 0, 4, 3, 3));
        List<ShipDesignValidator.ValidationError> errors = validator.validate(design, mask);
        assertTrue(errors.stream().anyMatch(e -> e.code.equals("ROOM_OUTSIDE_HULL")));
    }

    @Test
    void canPlaceRoom_rejectsOverlap() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        RoomDesign candidate = new RoomDesign(RoomType.MEDBAY, 2, 0, 0, 3, 3, 3);
        assertFalse(validator.canPlaceRoom(design, candidate, null));
    }

    @Test
    void canPlaceRoom_acceptsAdjacentRoom() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        RoomDesign candidate = new RoomDesign(RoomType.MEDBAY, 4, 0, 0, 3, 3, 3);
        assertTrue(validator.canPlaceRoom(design, candidate, null));
    }

    @Test
    void canPlaceRoom_firstRoomAlwaysValid() {
        RoomDesign candidate = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        assertTrue(validator.canPlaceRoom(design, candidate, null));
    }

    @Test
    void areAdjacent_touchingOnXAxis() {
        RoomDesign a = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        RoomDesign b = new RoomDesign(RoomType.MEDBAY, 4, 0, 0, 3, 3, 3);
        assertTrue(validator.areAdjacent(a, b));
    }

    @Test
    void areAdjacent_notTouching() {
        RoomDesign a = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        RoomDesign b = new RoomDesign(RoomType.MEDBAY, 5, 0, 0, 3, 3, 3);
        assertFalse(validator.areAdjacent(a, b));
    }
}
```

- [ ] **Step 3: Run ShipDesignValidatorTest**

Run: `./gradlew :core:test --tests "com.galacticodyssey.shipbuilder.ShipDesignValidatorTest" --info`
Expected: All 10 tests PASS

- [ ] **Step 4: Write adjacency_bonuses.json**

```json
[
  { "type": "ADJACENCY", "roomA": "MEDBAY", "roomB": "CREW_QUARTERS", "stat": "healingRate", "bonus": 0.15, "label": "+15% Healing" },
  { "type": "ADJACENCY", "roomA": "ARMORY", "roomB": "CARGO_BAY", "stat": "reloadSpeed", "bonus": 0.10, "label": "+10% Reload" },
  { "type": "POSITIONAL", "room": "ENGINE_ROOM", "condition": "STERN", "stat": "fuelEfficiency", "bonus": 0.05, "label": "+5% Fuel Eff." },
  { "type": "POSITIONAL", "room": "COCKPIT", "condition": "BOW", "stat": "sensorRange", "bonus": 0.10, "label": "+10% Sensors" }
]
```

- [ ] **Step 5: Write AdjacencyBonusCalculator**

```java
// AdjacencyBonusCalculator.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import java.util.*;

public class AdjacencyBonusCalculator {

    public static class LayoutBonus {
        public final String stat;
        public final float bonus;
        public final String label;
        public LayoutBonus(String stat, float bonus, String label) {
            this.stat = stat;
            this.bonus = bonus;
            this.label = label;
        }
    }

    private final ShipDesignValidator validator = new ShipDesignValidator();

    public List<LayoutBonus> computeBonuses(ShipDesign design) {
        List<LayoutBonus> bonuses = new ArrayList<>();
        computeAdjacencyBonuses(design, bonuses);
        computePositionalBonuses(design, bonuses);
        return bonuses;
    }

    private void computeAdjacencyBonuses(ShipDesign design, List<LayoutBonus> bonuses) {
        for (RoomDesign a : design.rooms) {
            for (RoomDesign b : design.rooms) {
                if (a == b) continue;
                if (a.type == RoomType.MEDBAY && b.type == RoomType.CREW_QUARTERS
                    && validator.areAdjacent(a, b)) {
                    bonuses.add(new LayoutBonus("healingRate", 0.15f, "+15% Healing"));
                    return;
                }
            }
        }
        for (RoomDesign a : design.rooms) {
            for (RoomDesign b : design.rooms) {
                if (a == b) continue;
                if (a.type == RoomType.ARMORY && b.type == RoomType.CARGO_BAY
                    && validator.areAdjacent(a, b)) {
                    bonuses.add(new LayoutBonus("reloadSpeed", 0.10f, "+10% Reload"));
                    return;
                }
            }
        }
    }

    private void computePositionalBonuses(ShipDesign design, List<LayoutBonus> bonuses) {
        if (design.rooms.isEmpty()) return;
        int maxGridX = 0;
        for (RoomDesign r : design.rooms) {
            maxGridX = Math.max(maxGridX, r.gridX + r.sizeX);
        }
        for (RoomDesign room : design.rooms) {
            float normalizedPosition = (float) room.gridX / Math.max(maxGridX, 1);
            if (room.type == RoomType.ENGINE_ROOM && normalizedPosition > 0.6f) {
                bonuses.add(new LayoutBonus("fuelEfficiency", 0.05f, "+5% Fuel Eff."));
            }
            if (room.type == RoomType.COCKPIT && normalizedPosition < 0.3f) {
                bonuses.add(new LayoutBonus("sensorRange", 0.10f, "+10% Sensors"));
            }
        }
    }
}
```

- [ ] **Step 6: Write AdjacencyBonusCalculatorTest**

```java
// AdjacencyBonusCalculatorTest.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AdjacencyBonusCalculatorTest {
    private AdjacencyBonusCalculator calc;
    private ShipDesign design;

    @BeforeEach
    void setUp() {
        calc = new AdjacencyBonusCalculator();
        design = new ShipDesign(ShipSizeClass.MEDIUM);
    }

    @Test
    void medbayNextToCrewQuarters_grantsHealingBonus() {
        design.addRoom(new RoomDesign(RoomType.CREW_QUARTERS, 0, 0, 0, 3, 3, 3));
        design.addRoom(new RoomDesign(RoomType.MEDBAY, 3, 0, 0, 3, 3, 3));
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        assertTrue(bonuses.stream().anyMatch(b -> b.stat.equals("healingRate") && b.bonus == 0.15f));
    }

    @Test
    void medbayFarFromCrewQuarters_noHealingBonus() {
        design.addRoom(new RoomDesign(RoomType.CREW_QUARTERS, 0, 0, 0, 3, 3, 3));
        design.addRoom(new RoomDesign(RoomType.MEDBAY, 10, 0, 0, 3, 3, 3));
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        assertFalse(bonuses.stream().anyMatch(b -> b.stat.equals("healingRate")));
    }

    @Test
    void engineRoomAtStern_grantsFuelBonus() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 3, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 15, 0, 0, 4, 3, 3));
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        assertTrue(bonuses.stream().anyMatch(b -> b.stat.equals("fuelEfficiency")));
    }

    @Test
    void cockpitAtBow_grantsSensorBonus() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 3, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 15, 0, 0, 4, 3, 3));
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        assertTrue(bonuses.stream().anyMatch(b -> b.stat.equals("sensorRange")));
    }

    @Test
    void noBonuses_whenNoMatchingConditions() {
        design.addRoom(new RoomDesign(RoomType.CARGO_BAY, 5, 0, 0, 4, 3, 3));
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        assertTrue(bonuses.isEmpty());
    }
}
```

- [ ] **Step 7: Run AdjacencyBonusCalculatorTest**

Run: `./gradlew :core:test --tests "com.galacticodyssey.shipbuilder.AdjacencyBonusCalculatorTest" --info`
Expected: All 5 tests PASS

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/ShipDesignValidator.java \
        core/src/main/java/com/galacticodyssey/shipbuilder/AdjacencyBonusCalculator.java \
        core/src/main/resources/data/shipbuilder/adjacency_bonuses.json \
        core/src/test/java/com/galacticodyssey/shipbuilder/ShipDesignValidatorTest.java \
        core/src/test/java/com/galacticodyssey/shipbuilder/AdjacencyBonusCalculatorTest.java
git commit -m "feat(shipbuilder): add design validator and layout bonus calculator"
```

---

## Task 6: Ship Stats Calculator & Module Catalog

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/ShipStatsCalculator.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/ModuleCatalogEntry.java`
- Create: `core/src/main/resources/data/shipbuilder/module_catalog.json`
- Test: `core/src/test/java/com/galacticodyssey/shipbuilder/ShipStatsCalculatorTest.java`

- [ ] **Step 1: Write ModuleCatalogEntry and module_catalog.json**

```java
// ModuleCatalogEntry.java
package com.galacticodyssey.shipbuilder;

public class ModuleCatalogEntry {
    public enum HardpointType { WEAPON, ENGINE, UTILITY }
    public enum HardpointSize { S, M, L }

    public String moduleId;
    public String name;
    public HardpointType hardpointType;
    public HardpointSize minSize;
    public float powerDraw;
    public float weight;
    public int price;

    public float dps;
    public float range;
    public float thrust;
    public float topSpeedBonus;
    public float shieldHp;
    public float sensorRange;
    public float cargoCapacity;

    public ModuleCatalogEntry() {}
}
```

```json
// module_catalog.json
[
  { "moduleId": "laser_mk1", "name": "Laser Cannon Mk1", "hardpointType": "WEAPON", "minSize": "S", "powerDraw": 8, "weight": 200, "price": 3000, "dps": 25, "range": 500 },
  { "moduleId": "laser_mk2", "name": "Laser Cannon Mk2", "hardpointType": "WEAPON", "minSize": "S", "powerDraw": 12, "weight": 300, "price": 8000, "dps": 40, "range": 600 },
  { "moduleId": "missile_rack_s", "name": "Missile Rack S", "hardpointType": "WEAPON", "minSize": "M", "powerDraw": 15, "weight": 500, "price": 15000, "dps": 60, "range": 1200 },
  { "moduleId": "plasma_cannon_s", "name": "Plasma Cannon S", "hardpointType": "WEAPON", "minSize": "M", "powerDraw": 20, "weight": 400, "price": 18000, "dps": 55, "range": 400 },
  { "moduleId": "thruster_basic", "name": "Basic Thruster", "hardpointType": "ENGINE", "minSize": "S", "powerDraw": 5, "weight": 300, "price": 2000, "thrust": 15000 },
  { "moduleId": "fusion_drive_m", "name": "Fusion Drive M", "hardpointType": "ENGINE", "minSize": "M", "powerDraw": 18, "weight": 800, "price": 25000, "thrust": 50000 },
  { "moduleId": "shield_gen_mk1", "name": "Shield Generator Mk1", "hardpointType": "UTILITY", "minSize": "S", "powerDraw": 15, "weight": 400, "price": 12000, "shieldHp": 200 },
  { "moduleId": "scanner_mk1", "name": "Sensor Array Mk1", "hardpointType": "UTILITY", "minSize": "S", "powerDraw": 5, "weight": 100, "price": 4000, "sensorRange": 2000 }
]
```

- [ ] **Step 2: Write ShipStatsCalculator**

```java
// ShipStatsCalculator.java
package com.galacticodyssey.shipbuilder;

import java.util.*;

public class ShipStatsCalculator {

    public static class ShipStats {
        public float totalDps;
        public float maxWeaponRange;
        public float totalThrust;
        public float totalShieldHp;
        public float totalSensorRange;
        public float totalCargoCapacity;
        public float totalPowerDraw;
        public float totalWeight;
        public int crewCapacity;
    }

    private final Map<String, ModuleCatalogEntry> catalog = new LinkedHashMap<>();

    public void loadCatalog(List<ModuleCatalogEntry> entries) {
        for (ModuleCatalogEntry e : entries) catalog.put(e.moduleId, e);
    }

    public ModuleCatalogEntry getModule(String moduleId) {
        return catalog.get(moduleId);
    }

    public List<ModuleCatalogEntry> getModulesByType(ModuleCatalogEntry.HardpointType type) {
        List<ModuleCatalogEntry> result = new ArrayList<>();
        for (ModuleCatalogEntry e : catalog.values()) {
            if (e.hardpointType == type) result.add(e);
        }
        return result;
    }

    public ShipStats computeStats(ShipDesign design) {
        ShipStats stats = new ShipStats();
        for (ModuleAssignment assignment : design.modules.values()) {
            if (assignment == null) continue;
            ModuleCatalogEntry module = catalog.get(assignment.moduleId);
            if (module == null) continue;
            stats.totalDps += module.dps;
            stats.maxWeaponRange = Math.max(stats.maxWeaponRange, module.range);
            stats.totalThrust += module.thrust;
            stats.totalShieldHp += module.shieldHp;
            stats.totalSensorRange = Math.max(stats.totalSensorRange, module.sensorRange);
            stats.totalCargoCapacity += module.cargoCapacity;
            stats.totalPowerDraw += module.powerDraw;
            stats.totalWeight += module.weight;
        }
        for (RoomDesign room : design.rooms) {
            stats.totalWeight += room.volume() * 50;
        }
        return stats;
    }
}
```

- [ ] **Step 3: Write ShipStatsCalculatorTest**

```java
// ShipStatsCalculatorTest.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipStatsCalculatorTest {
    private ShipStatsCalculator calc;
    private ShipDesign design;

    @BeforeEach
    void setUp() {
        calc = new ShipStatsCalculator();

        ModuleCatalogEntry laser = new ModuleCatalogEntry();
        laser.moduleId = "laser_mk1";
        laser.hardpointType = ModuleCatalogEntry.HardpointType.WEAPON;
        laser.dps = 25; laser.range = 500; laser.powerDraw = 8; laser.weight = 200;

        ModuleCatalogEntry thruster = new ModuleCatalogEntry();
        thruster.moduleId = "thruster_basic";
        thruster.hardpointType = ModuleCatalogEntry.HardpointType.ENGINE;
        thruster.thrust = 15000; thruster.powerDraw = 5; thruster.weight = 300;

        calc.loadCatalog(Arrays.asList(laser, thruster));

        design = new ShipDesign(ShipSizeClass.SMALL);
        design.setModule("WPN-1", new ModuleAssignment("laser_mk1", "bp_laser_mk1"));
        design.setModule("ENG-1", new ModuleAssignment("thruster_basic", "bp_thruster_basic"));
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 3, 3, 3));
    }

    @Test
    void computeStats_sumsDps() {
        ShipStatsCalculator.ShipStats stats = calc.computeStats(design);
        assertEquals(25f, stats.totalDps, 0.01f);
    }

    @Test
    void computeStats_sumsThrust() {
        ShipStatsCalculator.ShipStats stats = calc.computeStats(design);
        assertEquals(15000f, stats.totalThrust, 0.01f);
    }

    @Test
    void computeStats_sumsPowerDraw() {
        ShipStatsCalculator.ShipStats stats = calc.computeStats(design);
        assertEquals(13f, stats.totalPowerDraw, 0.01f);
    }

    @Test
    void computeStats_includesRoomWeight() {
        ShipStatsCalculator.ShipStats stats = calc.computeStats(design);
        float moduleWeight = 200 + 300;
        float roomWeight = 27 * 50;
        assertEquals(moduleWeight + roomWeight, stats.totalWeight, 0.01f);
    }

    @Test
    void getModulesByType_filters() {
        List<ModuleCatalogEntry> weapons = calc.getModulesByType(ModuleCatalogEntry.HardpointType.WEAPON);
        assertEquals(1, weapons.size());
        assertEquals("laser_mk1", weapons.get(0).moduleId);
    }
}
```

- [ ] **Step 4: Run ShipStatsCalculatorTest**

Run: `./gradlew :core:test --tests "com.galacticodyssey.shipbuilder.ShipStatsCalculatorTest" --info`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/ShipStatsCalculator.java \
        core/src/main/java/com/galacticodyssey/shipbuilder/ModuleCatalogEntry.java \
        core/src/main/resources/data/shipbuilder/module_catalog.json \
        core/src/test/java/com/galacticodyssey/shipbuilder/ShipStatsCalculatorTest.java
git commit -m "feat(shipbuilder): add ship stats calculator and module catalog"
```

---

## Task 7: Events & Phase Controller

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/events/EnterDrydockEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/events/ExitDrydockEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/events/BuildPhaseChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/events/ShipDesignCommittedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/events/BuildOrderQueuedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/events/BuildOrderAppliedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/BuilderPhase.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/BuilderPhaseController.java`
- Test: `core/src/test/java/com/galacticodyssey/shipbuilder/BuilderPhaseControllerTest.java`

- [ ] **Step 1: Write event classes**

```java
// EnterDrydockEvent.java
package com.galacticodyssey.shipbuilder.events;

import com.galacticodyssey.shipbuilder.ShipDesign;

public class EnterDrydockEvent {
    public final ShipDesign design;
    public EnterDrydockEvent(ShipDesign design) { this.design = design; }
}
```

```java
// ExitDrydockEvent.java
package com.galacticodyssey.shipbuilder.events;

public class ExitDrydockEvent {
    public final boolean committed;
    public ExitDrydockEvent(boolean committed) { this.committed = committed; }
}
```

```java
// BuildPhaseChangedEvent.java
package com.galacticodyssey.shipbuilder.events;

import com.galacticodyssey.shipbuilder.BuilderPhase;

public class BuildPhaseChangedEvent {
    public final BuilderPhase previousPhase;
    public final BuilderPhase newPhase;
    public BuildPhaseChangedEvent(BuilderPhase previousPhase, BuilderPhase newPhase) {
        this.previousPhase = previousPhase;
        this.newPhase = newPhase;
    }
}
```

```java
// ShipDesignCommittedEvent.java
package com.galacticodyssey.shipbuilder.events;

import com.galacticodyssey.shipbuilder.ShipDesign;

public class ShipDesignCommittedEvent {
    public final ShipDesign design;
    public ShipDesignCommittedEvent(ShipDesign design) { this.design = design; }
}
```

```java
// BuildOrderQueuedEvent.java
package com.galacticodyssey.shipbuilder.events;

import com.galacticodyssey.shipbuilder.planning.BuildAction;

public class BuildOrderQueuedEvent {
    public final BuildAction action;
    public BuildOrderQueuedEvent(BuildAction action) { this.action = action; }
}
```

```java
// BuildOrderAppliedEvent.java
package com.galacticodyssey.shipbuilder.events;

import com.galacticodyssey.shipbuilder.planning.BuildOrder;

public class BuildOrderAppliedEvent {
    public final BuildOrder order;
    public BuildOrderAppliedEvent(BuildOrder order) { this.order = order; }
}
```

- [ ] **Step 2: Write BuilderPhase enum**

```java
// BuilderPhase.java
package com.galacticodyssey.shipbuilder;

public enum BuilderPhase {
    HULL_SCULPT("Hull Sculpt", 1),
    ROOM_LAYOUT("Room Layout", 2),
    MODULE_FIT("Module Fit", 3);

    public final String displayName;
    public final int order;

    BuilderPhase(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }
}
```

- [ ] **Step 3: Write BuilderPhaseController**

```java
// BuilderPhaseController.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.shipbuilder.events.BuildPhaseChangedEvent;

import java.util.ArrayList;
import java.util.List;

public class BuilderPhaseController {
    private BuilderPhase currentPhase = BuilderPhase.HULL_SCULPT;
    private final ShipDesign design;
    private final ShipDesignValidator validator;
    private final EventBus eventBus;
    private boolean roomsInvalidated;
    private boolean modulesInvalidated;

    public BuilderPhaseController(ShipDesign design, ShipDesignValidator validator, EventBus eventBus) {
        this.design = design;
        this.validator = validator;
        this.eventBus = eventBus;
    }

    public BuilderPhase getCurrentPhase() {
        return currentPhase;
    }

    public boolean canAdvance() {
        if (currentPhase == BuilderPhase.HULL_SCULPT) {
            return design.hull.spinePoints.size() >= 3 && design.hull.crossSections.size() >= 2;
        }
        if (currentPhase == BuilderPhase.ROOM_LAYOUT) {
            List<ShipDesignValidator.ValidationError> errors = validator.validate(design, null);
            return errors.isEmpty();
        }
        return false;
    }

    public List<String> getAdvanceBlockers() {
        List<String> blockers = new ArrayList<>();
        if (currentPhase == BuilderPhase.HULL_SCULPT) {
            if (design.hull.spinePoints.size() < 3) blockers.add("Need at least 3 spine points");
            if (design.hull.crossSections.size() < 2) blockers.add("Need at least 2 cross-sections");
        }
        if (currentPhase == BuilderPhase.ROOM_LAYOUT) {
            for (ShipDesignValidator.ValidationError e : validator.validate(design, null)) {
                blockers.add(e.message);
            }
        }
        return blockers;
    }

    public boolean advance() {
        if (!canAdvance()) return false;
        BuilderPhase previous = currentPhase;
        if (currentPhase == BuilderPhase.HULL_SCULPT) {
            currentPhase = BuilderPhase.ROOM_LAYOUT;
        } else if (currentPhase == BuilderPhase.ROOM_LAYOUT) {
            currentPhase = BuilderPhase.MODULE_FIT;
        } else {
            return false;
        }
        roomsInvalidated = false;
        modulesInvalidated = false;
        eventBus.publish(new BuildPhaseChangedEvent(previous, currentPhase));
        return true;
    }

    public boolean goBack() {
        BuilderPhase previous = currentPhase;
        if (currentPhase == BuilderPhase.MODULE_FIT) {
            currentPhase = BuilderPhase.ROOM_LAYOUT;
            modulesInvalidated = true;
        } else if (currentPhase == BuilderPhase.ROOM_LAYOUT) {
            currentPhase = BuilderPhase.HULL_SCULPT;
            roomsInvalidated = true;
            modulesInvalidated = true;
        } else {
            return false;
        }
        eventBus.publish(new BuildPhaseChangedEvent(previous, currentPhase));
        return true;
    }

    public boolean areRoomsInvalidated() { return roomsInvalidated; }
    public boolean areModulesInvalidated() { return modulesInvalidated; }
}
```

- [ ] **Step 4: Write BuilderPhaseControllerTest**

```java
// BuilderPhaseControllerTest.java
package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuilderPhaseControllerTest {
    private BuilderPhaseController controller;
    private ShipDesign design;

    @BeforeEach
    void setUp() {
        design = new ShipDesign(ShipSizeClass.SMALL);
        design.hull.spinePoints.add(new Vector3(0, 0, 0));
        design.hull.spinePoints.add(new Vector3(0, 0, 5));
        design.hull.spinePoints.add(new Vector3(0, 0, 10));
        design.hull.addCrossSection(new CrossSectionDef(0f, 2f, 2f, 2f));
        design.hull.addCrossSection(new CrossSectionDef(1f, 1f, 1f, 2f));

        controller = new BuilderPhaseController(design, new ShipDesignValidator(), new EventBus());
    }

    @Test
    void startsAtHullSculpt() {
        assertEquals(BuilderPhase.HULL_SCULPT, controller.getCurrentPhase());
    }

    @Test
    void advance_fromHullSculptToRoomLayout() {
        assertTrue(controller.advance());
        assertEquals(BuilderPhase.ROOM_LAYOUT, controller.getCurrentPhase());
    }

    @Test
    void advance_fromRoomLayoutRequiresValidDesign() {
        controller.advance();
        assertFalse(controller.canAdvance());
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 4, 0, 0, 4, 3, 3));
        assertTrue(controller.canAdvance());
        assertTrue(controller.advance());
        assertEquals(BuilderPhase.MODULE_FIT, controller.getCurrentPhase());
    }

    @Test
    void goBack_fromModuleFitToRoomLayout() {
        controller.advance();
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 4, 0, 0, 4, 3, 3));
        controller.advance();
        assertTrue(controller.goBack());
        assertEquals(BuilderPhase.ROOM_LAYOUT, controller.getCurrentPhase());
        assertTrue(controller.areModulesInvalidated());
    }

    @Test
    void goBack_fromRoomLayoutInvalidatesRooms() {
        controller.advance();
        assertTrue(controller.goBack());
        assertEquals(BuilderPhase.HULL_SCULPT, controller.getCurrentPhase());
        assertTrue(controller.areRoomsInvalidated());
        assertTrue(controller.areModulesInvalidated());
    }

    @Test
    void goBack_fromHullSculptReturnsFalse() {
        assertFalse(controller.goBack());
    }

    @Test
    void canAdvance_failsWithoutMinimumHullData() {
        ShipDesign emptyDesign = new ShipDesign(ShipSizeClass.SMALL);
        BuilderPhaseController ctrl = new BuilderPhaseController(emptyDesign, new ShipDesignValidator(), new EventBus());
        assertFalse(ctrl.canAdvance());
    }
}
```

- [ ] **Step 5: Run BuilderPhaseControllerTest**

Run: `./gradlew :core:test --tests "com.galacticodyssey.shipbuilder.BuilderPhaseControllerTest" --info`
Expected: All 7 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/events/ \
        core/src/main/java/com/galacticodyssey/shipbuilder/BuilderPhase.java \
        core/src/main/java/com/galacticodyssey/shipbuilder/BuilderPhaseController.java \
        core/src/test/java/com/galacticodyssey/shipbuilder/BuilderPhaseControllerTest.java
git commit -m "feat(shipbuilder): add events, phase enum, and phase controller"
```

---

## Task 8: DrydockScreen & DrydockScene

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/DrydockScreen.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/DrydockScene.java`
- Modify: `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java` — add `createShipFromDesign`

- [ ] **Step 1: Write DrydockScene**

```java
// DrydockScene.java
package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.ship.HullGeometry;
import com.galacticodyssey.ship.ShipHullGenerator;

public class DrydockScene implements Disposable {
    private final Environment environment;
    private final ModelBatch modelBatch;
    private final ShipHullGenerator hullGenerator = new ShipHullGenerator();
    private Mesh hullMesh;
    private Model hullModel;
    private ModelInstance hullInstance;
    private boolean meshDirty = true;
    private long lastRegenTime;
    private static final long REGEN_THROTTLE_MS = 100;

    public DrydockScene() {
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.35f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.9f, -0.5f, -1f, -0.3f));
    }

    public void markMeshDirty() {
        meshDirty = true;
    }

    public void updateMesh(ShipDesign design) {
        if (!meshDirty) return;
        long now = System.currentTimeMillis();
        if (now - lastRegenTime < REGEN_THROTTLE_MS) return;
        lastRegenTime = now;

        if (hullModel != null) hullModel.dispose();
        if (hullMesh != null) hullMesh.dispose();

        HullGeometry hull = hullGenerator.generate(design.toBlueprint());
        hullMesh = new Mesh(true, hull.vertexCount(), hull.indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
            new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_color"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_emissive"));
        hullMesh.setVertices(hull.vertices);
        hullMesh.setIndices(hull.indices);

        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.part("hull", hullMesh, GL20.GL_TRIANGLES,
            new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        hullModel = mb.end();
        hullInstance = new ModelInstance(hullModel);
        meshDirty = false;
    }

    public void render(Camera camera) {
        modelBatch.begin(camera);
        if (hullInstance != null) {
            modelBatch.render(hullInstance, environment);
        }
        modelBatch.end();
    }

    public HullGeometry getCurrentHullGeometry(ShipDesign design) {
        return hullGenerator.generate(design.toBlueprint());
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        if (hullModel != null) hullModel.dispose();
        if (hullMesh != null) hullMesh.dispose();
    }
}
```

- [ ] **Step 2: Write DrydockScreen**

```java
// DrydockScreen.java
package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.shipbuilder.events.EnterDrydockEvent;
import com.galacticodyssey.shipbuilder.events.ExitDrydockEvent;
import com.galacticodyssey.shipbuilder.events.ShipDesignCommittedEvent;

public class DrydockScreen implements Screen {
    private final Game game;
    private final Screen previousScreen;
    private final EventBus eventBus;
    private final ShipDesign design;
    private final DrydockScene scene;
    private final BuilderPhaseController phaseController;
    private final ShipDesignValidator validator;
    private final BlueprintRegistry blueprintRegistry;
    private final BuildCostCalculator costCalculator;

    private PerspectiveCamera camera;
    private Stage uiStage;
    private InputMultiplexer inputMultiplexer;

    public DrydockScreen(Game game, Screen previousScreen, EventBus eventBus,
                         ShipDesign design, BlueprintRegistry blueprintRegistry) {
        this.game = game;
        this.previousScreen = previousScreen;
        this.eventBus = eventBus;
        this.design = design;
        this.validator = new ShipDesignValidator();
        this.phaseController = new BuilderPhaseController(design, validator, eventBus);
        this.scene = new DrydockScene();
        this.blueprintRegistry = blueprintRegistry;
        this.costCalculator = new BuildCostCalculator();
    }

    @Override
    public void show() {
        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0, 5, 20);
        camera.lookAt(0, 0, 0);
        camera.near = 0.1f;
        camera.far = 500f;
        camera.update();

        uiStage = new Stage(new ScreenViewport());
        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(uiStage);
        Gdx.input.setInputProcessor(inputMultiplexer);

        scene.markMeshDirty();
        eventBus.publish(new EnterDrydockEvent(design));
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        scene.updateMesh(design);
        scene.render(camera);

        uiStage.act(delta);
        uiStage.draw();
    }

    public void commitBuild() {
        eventBus.publish(new ShipDesignCommittedEvent(design));
        exitDrydock(true);
    }

    public void cancelBuild() {
        exitDrydock(false);
    }

    private void exitDrydock(boolean committed) {
        eventBus.publish(new ExitDrydockEvent(committed));
        game.setScreen(previousScreen);
    }

    public ShipDesign getDesign() { return design; }
    public BuilderPhaseController getPhaseController() { return phaseController; }
    public BlueprintRegistry getBlueprintRegistry() { return blueprintRegistry; }
    public BuildCostCalculator getCostCalculator() { return costCalculator; }
    public PerspectiveCamera getCamera() { return camera; }
    public Stage getUiStage() { return uiStage; }
    public InputMultiplexer getInputMultiplexer() { return inputMultiplexer; }
    public DrydockScene getScene() { return scene; }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        uiStage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        scene.dispose();
        uiStage.dispose();
    }
}
```

- [ ] **Step 3: Add createShipFromDesign to ShipFactory**

Add this method to the existing `ShipFactory.java`:

```java
public Entity createShipFromDesign(ShipDesign design, float x, float y, float z) {
    ShipBlueprint blueprint = design.toBlueprint();
    HullGeometry hull = hullGenerator.generate(blueprint);
    InteriorLayout interior = interiorGenerator.generate(blueprint, hull);

    float bottomY = hull.boundingBox.min.y;
    float adjustedY = y - bottomY;

    Entity entity = engine.createEntity();

    TransformComponent transform = engine.createComponent(TransformComponent.class);
    transform.position.set(x, adjustedY, z);
    entity.add(transform);

    int sizeIdx = design.sizeClass.ordinal();
    float mass = (MASS_MIN[sizeIdx] + MASS_MAX[sizeIdx]) / 2f;

    ShipDataComponent data = engine.createComponent(ShipDataComponent.class);
    data.blueprint = blueprint;
    data.mass = mass;
    data.maxThrust = MAX_THRUST[sizeIdx];
    data.maxTurnRate = TURN_RATE[sizeIdx];
    data.maxSpeed = MAX_SPEED[sizeIdx];
    data.hullHp = HULL_HP[sizeIdx];
    data.currentHullHp = data.hullHp;
    data.hullGeometry = hull;
    entity.add(data);

    ShipMeshComponent meshComp = engine.createComponent(ShipMeshComponent.class);
    meshComp.vertexStride = hull.vertexStride;
    entity.add(meshComp);

    entity.add(buildInteriorComponent(interior));
    entity.add(buildFlightComponent(design.sizeClass));

    PilotSeatComponent seat = engine.createComponent(PilotSeatComponent.class);
    seat.interiorPosition.set(interior.pilotSeatPosition);
    entity.add(seat);

    ShipEntryPointComponent entry = engine.createComponent(ShipEntryPointComponent.class);
    entry.interiorPosition.set(interior.airlockPosition);
    entry.localExteriorPosition.set(0, hull.boundingBox.min.y - 1f, interior.airlockPosition.z);
    entry.worldPosition.set(x + entry.localExteriorPosition.x,
                            adjustedY + entry.localExteriorPosition.y,
                            z + entry.localExteriorPosition.z);
    entity.add(entry);

    if (reactorSpecRegistry != null) {
        PowerStateComponent power = engine.createComponent(PowerStateComponent.class);
        entity.add(power);
    }

    btRigidBody body = buildExteriorPhysicsBody(hull, mass, x, adjustedY, z);
    PhysicsBodyComponent physComp = engine.createComponent(PhysicsBodyComponent.class);
    physComp.body = body;
    entity.add(physComp);

    engine.addEntity(entity);
    return entity;
}
```

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/DrydockScreen.java \
        core/src/main/java/com/galacticodyssey/shipbuilder/DrydockScene.java \
        core/src/main/java/com/galacticodyssey/ship/ShipFactory.java
git commit -m "feat(shipbuilder): add DrydockScreen, DrydockScene, and createShipFromDesign"
```

---

## Task 9: Phase 1 — Hull Sculpt Input & HUD

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase1/HullSculptInputProcessor.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase1/ControlPointGizmo.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase1/CrossSectionEditor.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase1/AppendageCatalog.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase1/HullSculptHUD.java`

- [ ] **Step 1: Write ControlPointGizmo**

```java
// ControlPointGizmo.java
package com.galacticodyssey.shipbuilder.phase1;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.List;

public class ControlPointGizmo implements Disposable {
    private static final float SPHERE_RADIUS = 0.3f;
    private static final float PICK_RADIUS = 0.5f;

    private Model sphereModel;
    private final Array<ModelInstance> instances = new Array<>();
    private int selectedIndex = -1;
    private boolean dragging;
    private final Vector3 dragPlaneNormal = new Vector3();
    private final Plane dragPlane = new Plane();
    private final Vector3 tmpIntersection = new Vector3();

    public void build() {
        if (sphereModel != null) sphereModel.dispose();
        ModelBuilder mb = new ModelBuilder();
        sphereModel = mb.createSphere(SPHERE_RADIUS * 2, SPHERE_RADIUS * 2, SPHERE_RADIUS * 2, 12, 8,
            new Material(ColorAttribute.createDiffuse(Color.RED)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    public void updatePositions(List<Vector3> spinePoints) {
        instances.clear();
        for (Vector3 point : spinePoints) {
            ModelInstance inst = new ModelInstance(sphereModel);
            inst.transform.setToTranslation(point);
            instances.add(inst);
        }
        if (selectedIndex >= 0 && selectedIndex < instances.size) {
            instances.get(selectedIndex).materials.get(0)
                .set(ColorAttribute.createDiffuse(Color.YELLOW));
        }
    }

    public int pick(Ray ray, List<Vector3> spinePoints) {
        float closestDist = Float.MAX_VALUE;
        int closestIdx = -1;
        for (int i = 0; i < spinePoints.size(); i++) {
            Vector3 point = spinePoints.get(i);
            float dist = ray.origin.dst(point);
            Vector3 projected = new Vector3();
            float t = ray.direction.dot(new Vector3(point).sub(ray.origin));
            projected.set(ray.direction).scl(t).add(ray.origin);
            float pickDist = projected.dst(point);
            if (pickDist < PICK_RADIUS && dist < closestDist) {
                closestDist = dist;
                closestIdx = i;
            }
        }
        selectedIndex = closestIdx;
        return closestIdx;
    }

    public void beginDrag(Camera camera) {
        dragging = true;
        dragPlaneNormal.set(camera.direction).scl(-1);
    }

    public Vector3 drag(Ray ray, Vector3 currentPosition) {
        if (!dragging || selectedIndex < 0) return currentPosition;
        dragPlane.set(currentPosition, dragPlaneNormal);
        if (Intersector.intersectRayPlane(ray, dragPlane, tmpIntersection)) {
            return tmpIntersection;
        }
        return currentPosition;
    }

    public void endDrag() {
        dragging = false;
    }

    public int getSelectedIndex() { return selectedIndex; }
    public void clearSelection() { selectedIndex = -1; }
    public boolean isDragging() { return dragging; }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance inst : instances) {
            batch.render(inst, env);
        }
    }

    @Override
    public void dispose() {
        if (sphereModel != null) sphereModel.dispose();
    }
}
```

- [ ] **Step 2: Write HullSculptInputProcessor**

```java
// HullSculptInputProcessor.java
package com.galacticodyssey.shipbuilder.phase1;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.galacticodyssey.shipbuilder.DrydockScene;
import com.galacticodyssey.shipbuilder.ShipDesign;

public class HullSculptInputProcessor extends InputAdapter {
    private final PerspectiveCamera camera;
    private final ShipDesign design;
    private final DrydockScene scene;
    private final ControlPointGizmo gizmo;

    private float orbitAzimuth = 0f;
    private float orbitElevation = 30f;
    private float orbitDistance = 25f;
    private final Vector3 orbitTarget = new Vector3(0, 0, 5);
    private int lastX, lastY;
    private boolean orbiting;

    public HullSculptInputProcessor(PerspectiveCamera camera, ShipDesign design,
                                     DrydockScene scene, ControlPointGizmo gizmo) {
        this.camera = camera;
        this.design = design;
        this.scene = scene;
        this.gizmo = gizmo;
        updateCameraOrbit();
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        lastX = screenX;
        lastY = screenY;
        if (button == 1) {
            orbiting = true;
            return true;
        }
        if (button == 0) {
            Ray ray = camera.getPickRay(screenX, screenY);
            int picked = gizmo.pick(ray, design.hull.spinePoints);
            if (picked >= 0) {
                gizmo.beginDrag(camera);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        int dx = screenX - lastX;
        int dy = screenY - lastY;
        lastX = screenX;
        lastY = screenY;

        if (orbiting) {
            orbitAzimuth -= dx * 0.5f;
            orbitElevation = MathUtils.clamp(orbitElevation + dy * 0.5f, -89f, 89f);
            updateCameraOrbit();
            return true;
        }

        if (gizmo.isDragging()) {
            Ray ray = camera.getPickRay(screenX, screenY);
            int idx = gizmo.getSelectedIndex();
            Vector3 current = design.hull.spinePoints.get(idx);
            Vector3 newPos = gizmo.drag(ray, current);
            design.hull.moveSpinePoint(idx, newPos);
            scene.markMeshDirty();
            gizmo.updatePositions(design.hull.spinePoints);
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == 1) orbiting = false;
        if (gizmo.isDragging()) gizmo.endDrag();
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        orbitDistance = MathUtils.clamp(orbitDistance + amountY * 2f, 5f, 100f);
        updateCameraOrbit();
        return true;
    }

    private void updateCameraOrbit() {
        float azRad = orbitAzimuth * MathUtils.degreesToRadians;
        float elRad = orbitElevation * MathUtils.degreesToRadians;
        float cosEl = MathUtils.cos(elRad);
        camera.position.set(
            orbitTarget.x + orbitDistance * cosEl * MathUtils.sin(azRad),
            orbitTarget.y + orbitDistance * MathUtils.sin(elRad),
            orbitTarget.z + orbitDistance * cosEl * MathUtils.cos(azRad)
        );
        camera.lookAt(orbitTarget);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    public void setOrbitTarget(Vector3 target) {
        orbitTarget.set(target);
        updateCameraOrbit();
    }
}
```

- [ ] **Step 3: Write CrossSectionEditor**

```java
// CrossSectionEditor.java
package com.galacticodyssey.shipbuilder.phase1;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.galacticodyssey.shipbuilder.CrossSectionDef;
import com.galacticodyssey.shipbuilder.DrydockScene;

public class CrossSectionEditor extends Window {
    private final CrossSectionDef crossSection;
    private final DrydockScene scene;
    private final Slider widthSlider;
    private final Slider heightSlider;
    private final Slider exponentSlider;
    private final Label widthLabel;
    private final Label heightLabel;
    private final Label exponentLabel;

    public CrossSectionEditor(CrossSectionDef crossSection, Skin skin, DrydockScene scene) {
        super("Cross-Section Editor (t=" + String.format("%.2f", crossSection.t) + ")", skin);
        this.crossSection = crossSection;
        this.scene = scene;

        widthSlider = new Slider(0.5f, 15f, 0.1f, false, skin);
        widthSlider.setValue(crossSection.width);
        widthLabel = new Label(String.format("%.1f", crossSection.width), skin);

        heightSlider = new Slider(0.5f, 15f, 0.1f, false, skin);
        heightSlider.setValue(crossSection.height);
        heightLabel = new Label(String.format("%.1f", crossSection.height), skin);

        exponentSlider = new Slider(1f, 6f, 0.1f, false, skin);
        exponentSlider.setValue(crossSection.exponent);
        exponentLabel = new Label(String.format("%.1f", crossSection.exponent), skin);

        ChangeListener listener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                crossSection.width = widthSlider.getValue();
                crossSection.height = heightSlider.getValue();
                crossSection.exponent = exponentSlider.getValue();
                widthLabel.setText(String.format("%.1f", crossSection.width));
                heightLabel.setText(String.format("%.1f", crossSection.height));
                exponentLabel.setText(String.format("%.1f", crossSection.exponent));
                scene.markMeshDirty();
            }
        };
        widthSlider.addListener(listener);
        heightSlider.addListener(listener);
        exponentSlider.addListener(listener);

        defaults().pad(4);
        add("Width:").left();
        add(widthSlider).width(200);
        add(widthLabel).width(40).row();
        add("Height:").left();
        add(heightSlider).width(200);
        add(heightLabel).width(40).row();
        add("Shape:").left();
        add(exponentSlider).width(200);
        add(exponentLabel).width(40).row();
        add("(2=round, >3=boxy)").colspan(3).center().row();
        pack();
        setModal(false);
        setMovable(true);
    }
}
```

- [ ] **Step 4: Write AppendageCatalog**

```java
// AppendageCatalog.java
package com.galacticodyssey.shipbuilder.phase1;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.galacticodyssey.shipbuilder.AppendageDef;

import java.util.function.Consumer;

public class AppendageCatalog extends Window {
    public AppendageCatalog(Skin skin, Consumer<AppendageDef.AppendageType> onSelect) {
        super("Appendage Catalog", skin);

        for (AppendageDef.AppendageType type : AppendageDef.AppendageType.values()) {
            TextButton btn = new TextButton(type.name().replace('_', ' '), skin);
            btn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onSelect.accept(type);
                    remove();
                }
            });
            add(btn).fillX().pad(4).row();
        }
        pack();
        setModal(true);
        setMovable(true);
    }
}
```

- [ ] **Step 5: Write HullSculptHUD**

```java
// HullSculptHUD.java
package com.galacticodyssey.shipbuilder.phase1;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.shipbuilder.ShipDesign;

public class HullSculptHUD {
    private final Table statsTable;
    private final Label volumeLabel;
    private final Label lengthLabel;
    private final Label widthLabel;
    private final Label heightLabel;
    private final Label spinePointsLabel;
    private final Label crossSectionsLabel;

    public HullSculptHUD(Skin skin) {
        statsTable = new Table(skin);
        statsTable.setBackground("default-rect");
        statsTable.defaults().pad(4).left();

        statsTable.add("HULL STATS").colspan(2).center().row();
        statsTable.add("Length:").left(); lengthLabel = new Label("0m", skin); statsTable.add(lengthLabel).right().row();
        statsTable.add("Max Width:").left(); widthLabel = new Label("0m", skin); statsTable.add(widthLabel).right().row();
        statsTable.add("Max Height:").left(); heightLabel = new Label("0m", skin); statsTable.add(heightLabel).right().row();
        statsTable.add("Int. Volume:").left(); volumeLabel = new Label("0 m³", skin); statsTable.add(volumeLabel).right().row();
        statsTable.add("Spine Pts:").left(); spinePointsLabel = new Label("0", skin); statsTable.add(spinePointsLabel).right().row();
        statsTable.add("Cross-Sects:").left(); crossSectionsLabel = new Label("0", skin); statsTable.add(crossSectionsLabel).right().row();
        statsTable.pack();
    }

    public void update(ShipDesign design) {
        float length = design.hull.estimateSpineLength();
        float width = design.hull.estimateMaxWidth();
        float height = design.hull.estimateMaxHeight();
        float volume = length * width * height * 0.5f;
        lengthLabel.setText(String.format("%.1fm", length));
        widthLabel.setText(String.format("%.1fm", width));
        heightLabel.setText(String.format("%.1fm", height));
        volumeLabel.setText(String.format("%.0f m³", volume));
        spinePointsLabel.setText(String.valueOf(design.hull.spinePoints.size()));
        crossSectionsLabel.setText(String.valueOf(design.hull.crossSections.size()));
    }

    public void attachTo(Stage stage) {
        Table root = new Table();
        root.setFillParent(true);
        root.top().right();
        root.add(statsTable).pad(10);
        stage.addActor(root);
    }

    public void detach() {
        statsTable.remove();
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/phase1/
git commit -m "feat(shipbuilder): add Phase 1 hull sculpt input, gizmos, cross-section editor, and HUD"
```

---

## Task 10: Phase 2 — Room Layout

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase2/GhostVolume.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase2/RoomGridRenderer.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase2/CorridorPreview.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase2/RoomLayoutInputProcessor.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase2/RoomLayoutHUD.java`

- [ ] **Step 1: Write GhostVolume**

```java
// GhostVolume.java
package com.galacticodyssey.shipbuilder.phase2;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.shipbuilder.RoomDesign;

public class GhostVolume implements Disposable {
    private Model model;
    private ModelInstance instance;
    private RoomType roomType;
    private int sizeX, sizeY, sizeZ;
    private int gridX, gridY, gridZ;
    private int rotation;
    private boolean valid;
    private boolean active;

    private static final Color VALID_COLOR = new Color(0.2f, 0.8f, 0.2f, 0.3f);
    private static final Color INVALID_COLOR = new Color(0.8f, 0.2f, 0.2f, 0.3f);

    public void activate(RoomType type) {
        this.roomType = type;
        this.sizeX = type.minSizeX;
        this.sizeY = type.minSizeY;
        this.sizeZ = type.minSizeZ;
        this.rotation = 0;
        this.active = true;
        rebuildModel();
    }

    public void deactivate() {
        active = false;
        if (model != null) { model.dispose(); model = null; }
        instance = null;
    }

    public void setGridPosition(int x, int y, int z) {
        this.gridX = x;
        this.gridY = y;
        this.gridZ = z;
        if (instance != null) {
            instance.transform.setToTranslation(x, y, z);
        }
    }

    public void rotate90() {
        rotation = (rotation + 1) % 4;
        if (rotation % 2 == 1) {
            int tmp = sizeX;
            sizeX = sizeZ;
            sizeZ = tmp;
        } else {
            sizeX = roomType.minSizeX;
            sizeZ = roomType.minSizeZ;
        }
        rebuildModel();
    }

    public void setValid(boolean valid) {
        this.valid = valid;
        if (instance != null) {
            Color c = valid ? VALID_COLOR : INVALID_COLOR;
            instance.materials.get(0).set(ColorAttribute.createDiffuse(c));
        }
    }

    public RoomDesign toRoomDesign() {
        return new RoomDesign(roomType, gridX, gridY, gridZ, sizeX, sizeY, sizeZ);
    }

    private void rebuildModel() {
        if (model != null) model.dispose();
        ModelBuilder mb = new ModelBuilder();
        Color c = valid ? VALID_COLOR : INVALID_COLOR;
        Material mat = new Material(
            ColorAttribute.createDiffuse(c),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        );
        model = mb.createBox(sizeX, sizeY, sizeZ, mat,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instance = new ModelInstance(model);
        instance.transform.setToTranslation(
            gridX + sizeX / 2f, gridY + sizeY / 2f, gridZ + sizeZ / 2f
        );
    }

    public void render(ModelBatch batch, Environment env) {
        if (active && instance != null) {
            batch.render(instance, env);
        }
    }

    public boolean isActive() { return active; }
    public RoomType getRoomType() { return roomType; }

    @Override
    public void dispose() {
        if (model != null) model.dispose();
    }
}
```

- [ ] **Step 2: Write RoomGridRenderer**

```java
// RoomGridRenderer.java
package com.galacticodyssey.shipbuilder.phase2;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.math.Matrix4;

public class RoomGridRenderer {
    private final ImmediateModeRenderer20 renderer;
    private static final Color GRID_COLOR = new Color(0.3f, 0.8f, 0.8f, 0.15f);

    public RoomGridRenderer() {
        renderer = new ImmediateModeRenderer20(false, true, 0);
    }

    public void render(boolean[][][] hullMask, Camera camera) {
        if (hullMask == null) return;
        int maxX = hullMask.length;
        int maxY = hullMask[0].length;
        int maxZ = hullMask[0][0].length;

        Matrix4 projView = new Matrix4(camera.combined);
        renderer.begin(projView, GL20.GL_LINES);

        for (int x = 0; x < maxX; x++) {
            for (int z = 0; z < maxZ; z++) {
                if (hullMask[x][0][z]) {
                    renderer.color(GRID_COLOR);
                    renderer.vertex(x, 0, z);
                    renderer.color(GRID_COLOR);
                    renderer.vertex(x + 1, 0, z);

                    renderer.color(GRID_COLOR);
                    renderer.vertex(x, 0, z);
                    renderer.color(GRID_COLOR);
                    renderer.vertex(x, 0, z + 1);
                }
            }
        }
        renderer.end();
    }

    public void dispose() {
        renderer.dispose();
    }
}
```

- [ ] **Step 3: Write CorridorPreview**

```java
// CorridorPreview.java
package com.galacticodyssey.shipbuilder.phase2;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.shipbuilder.RoomDesign;
import com.galacticodyssey.shipbuilder.ShipDesignValidator;

import java.util.*;

public class CorridorPreview implements Disposable {
    private final Array<ModelInstance> corridorInstances = new Array<>();
    private final Array<Model> corridorModels = new Array<>();
    private final ShipDesignValidator validator = new ShipDesignValidator();

    public void update(List<RoomDesign> rooms) {
        dispose();
        ModelBuilder mb = new ModelBuilder();
        Material mat = new Material(
            ColorAttribute.createDiffuse(new Color(0.5f, 0.5f, 0.5f, 0.2f)),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        );

        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                if (validator.areAdjacent(rooms.get(i), rooms.get(j))) {
                    RoomDesign a = rooms.get(i);
                    RoomDesign b = rooms.get(j);
                    float cx = (a.gridX + a.sizeX / 2f + b.gridX + b.sizeX / 2f) / 2f;
                    float cy = 0.5f;
                    float cz = (a.gridZ + a.sizeZ / 2f + b.gridZ + b.sizeZ / 2f) / 2f;
                    Model m = mb.createBox(1, 1, 1, mat,
                        VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
                    ModelInstance inst = new ModelInstance(m);
                    inst.transform.setToTranslation(cx, cy, cz);
                    corridorModels.add(m);
                    corridorInstances.add(inst);
                }
            }
        }
    }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance inst : corridorInstances) {
            batch.render(inst, env);
        }
    }

    @Override
    public void dispose() {
        for (Model m : corridorModels) m.dispose();
        corridorModels.clear();
        corridorInstances.clear();
    }
}
```

- [ ] **Step 4: Write RoomLayoutInputProcessor**

```java
// RoomLayoutInputProcessor.java
package com.galacticodyssey.shipbuilder.phase2;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.shipbuilder.*;

public class RoomLayoutInputProcessor extends InputAdapter {
    private final PerspectiveCamera camera;
    private final ShipDesign design;
    private final ShipDesignValidator validator;
    private final GhostVolume ghost;
    private final CorridorPreview corridorPreview;
    private final DrydockScene scene;
    private boolean[][][] hullMask;

    private float yaw, pitch;
    private final Vector3 position = new Vector3(0, 1.7f, 0);
    private static final float MOVE_SPEED = 5f;
    private static final float MOUSE_SENSITIVITY = 0.15f;

    private boolean forward, backward, left, right;

    public RoomLayoutInputProcessor(PerspectiveCamera camera, ShipDesign design,
                                     ShipDesignValidator validator, GhostVolume ghost,
                                     CorridorPreview corridorPreview, DrydockScene scene) {
        this.camera = camera;
        this.design = design;
        this.validator = validator;
        this.ghost = ghost;
        this.corridorPreview = corridorPreview;
        this.scene = scene;
    }

    public void setHullMask(boolean[][][] mask) {
        this.hullMask = mask;
    }

    public void update(float delta) {
        Vector3 dir = new Vector3(camera.direction).nor();
        dir.y = 0;
        dir.nor();
        Vector3 strafe = new Vector3(dir).crs(Vector3.Y).nor();

        if (forward) position.mulAdd(dir, MOVE_SPEED * delta);
        if (backward) position.mulAdd(dir, -MOVE_SPEED * delta);
        if (left) position.mulAdd(strafe, -MOVE_SPEED * delta);
        if (right) position.mulAdd(strafe, MOVE_SPEED * delta);

        camera.position.set(position);
        camera.direction.set(
            MathUtils.cosDeg(pitch) * MathUtils.sinDeg(yaw),
            MathUtils.sinDeg(pitch),
            MathUtils.cosDeg(pitch) * MathUtils.cosDeg(yaw)
        ).nor();
        camera.up.set(Vector3.Y);
        camera.update();

        if (ghost.isActive()) {
            Vector3 lookAt = new Vector3(camera.direction).scl(5f).add(camera.position);
            int gx = MathUtils.floor(lookAt.x);
            int gy = 0;
            int gz = MathUtils.floor(lookAt.z);
            ghost.setGridPosition(gx, gy, gz);
            RoomDesign candidate = ghost.toRoomDesign();
            ghost.setValid(validator.canPlaceRoom(design, candidate, hullMask));
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.W: forward = true; return true;
            case Input.Keys.S: backward = true; return true;
            case Input.Keys.A: left = true; return true;
            case Input.Keys.D: right = true; return true;
            case Input.Keys.NUM_1: ghost.activate(RoomType.COCKPIT); return true;
            case Input.Keys.NUM_2: ghost.activate(RoomType.ENGINE_ROOM); return true;
            case Input.Keys.NUM_3: ghost.activate(RoomType.CREW_QUARTERS); return true;
            case Input.Keys.NUM_4: ghost.activate(RoomType.MEDBAY); return true;
            case Input.Keys.NUM_5: ghost.activate(RoomType.ARMORY); return true;
            case Input.Keys.NUM_6: ghost.activate(RoomType.CARGO_BAY); return true;
            case Input.Keys.NUM_7: ghost.activate(RoomType.BRIG); return true;
            case Input.Keys.ESCAPE: ghost.deactivate(); return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        switch (keycode) {
            case Input.Keys.W: forward = false; return true;
            case Input.Keys.S: backward = false; return true;
            case Input.Keys.A: left = false; return true;
            case Input.Keys.D: right = false; return true;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        yaw -= screenX * MOUSE_SENSITIVITY;
        pitch = MathUtils.clamp(pitch - screenY * MOUSE_SENSITIVITY, -89f, 89f);
        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == 0 && ghost.isActive()) {
            RoomDesign candidate = ghost.toRoomDesign();
            if (validator.canPlaceRoom(design, candidate, hullMask)) {
                design.addRoom(candidate);
                corridorPreview.update(design.rooms);
                ghost.deactivate();
                return true;
            }
        }
        if (button == 1) {
            ghost.deactivate();
            return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (ghost.isActive()) {
            ghost.rotate90();
            return true;
        }
        return false;
    }

    public void setPosition(Vector3 pos) { position.set(pos); }
}
```

- [ ] **Step 5: Write RoomLayoutHUD**

```java
// RoomLayoutHUD.java
package com.galacticodyssey.shipbuilder.phase2;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.shipbuilder.ShipDesign;
import com.galacticodyssey.shipbuilder.AdjacencyBonusCalculator;

import java.util.List;

public class RoomLayoutHUD {
    private final Table budgetTable;
    private final Label powerLabel;
    private final Label weightLabel;
    private final Label volumeLabel;
    private final Label bonusLabel;

    public RoomLayoutHUD(Skin skin) {
        budgetTable = new Table(skin);
        budgetTable.setBackground("default-rect");
        budgetTable.defaults().pad(4).left();

        budgetTable.add("SHIP BUDGET").colspan(2).center().row();
        budgetTable.add("Power:").left(); powerLabel = new Label("0 / 100 kW", skin); budgetTable.add(powerLabel).right().row();
        budgetTable.add("Weight:").left(); weightLabel = new Label("0 / 20 t", skin); budgetTable.add(weightLabel).right().row();
        budgetTable.add("Volume:").left(); volumeLabel = new Label("0 / 300 m³", skin); budgetTable.add(volumeLabel).right().row();
        budgetTable.add("Bonuses:").left(); bonusLabel = new Label("None", skin); budgetTable.add(bonusLabel).right().row();
        budgetTable.pack();
    }

    public void update(ShipDesign design) {
        int totalVolume = design.totalRoomVolume();
        volumeLabel.setText(totalVolume + " m³");

        float totalWeight = 0;
        for (var room : design.rooms) totalWeight += room.volume() * 50;
        weightLabel.setText(String.format("%.1f t", totalWeight / 1000f));

        AdjacencyBonusCalculator calc = new AdjacencyBonusCalculator();
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        if (bonuses.isEmpty()) {
            bonusLabel.setText("None");
        } else {
            StringBuilder sb = new StringBuilder();
            for (AdjacencyBonusCalculator.LayoutBonus b : bonuses) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(b.label);
            }
            bonusLabel.setText(sb.toString());
        }
    }

    public void attachTo(Stage stage) {
        Table root = new Table();
        root.setFillParent(true);
        root.top().right();
        root.add(budgetTable).pad(10);
        stage.addActor(root);
    }

    public void detach() {
        budgetTable.remove();
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/phase2/
git commit -m "feat(shipbuilder): add Phase 2 room layout with ghost volumes, grid, and corridors"
```

---

## Task 11: Phase 3 — Module Fit

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase3/HardpointMarkerRenderer.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase3/ModuleBrowserPanel.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase3/ModuleFitInputProcessor.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/phase3/ModuleFitHUD.java`

- [ ] **Step 1: Write HardpointMarkerRenderer**

```java
// HardpointMarkerRenderer.java
package com.galacticodyssey.shipbuilder.phase3;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.ship.HullGeometry;
import com.galacticodyssey.shipbuilder.ModuleCatalogEntry;

import java.util.*;

public class HardpointMarkerRenderer implements Disposable {
    public static class HardpointDef {
        public final String id;
        public final Vector3 position;
        public final ModuleCatalogEntry.HardpointType type;
        public final ModuleCatalogEntry.HardpointSize size;
        public HardpointDef(String id, Vector3 position, ModuleCatalogEntry.HardpointType type,
                           ModuleCatalogEntry.HardpointSize size) {
            this.id = id;
            this.position = position;
            this.type = type;
            this.size = size;
        }
    }

    private final List<HardpointDef> hardpoints = new ArrayList<>();
    private final Array<ModelInstance> markers = new Array<>();
    private Model weaponMarker, engineMarker, utilityMarker;
    private int selectedIndex = -1;

    public void build() {
        ModelBuilder mb = new ModelBuilder();
        float s = 0.4f;
        weaponMarker = mb.createBox(s, s, s,
            new Material(ColorAttribute.createDiffuse(Color.RED)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        engineMarker = mb.createSphere(s, s, s, 8, 6,
            new Material(ColorAttribute.createDiffuse(Color.YELLOW)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        utilityMarker = mb.createSphere(s, s, s, 6, 6,
            new Material(ColorAttribute.createDiffuse(Color.CYAN)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    public void generateFromHull(HullGeometry hull) {
        hardpoints.clear();
        markers.clear();
        if (hull.hardpoints == null) return;
        for (int i = 0; i < hull.hardpoints.length; i++) {
            Vector3 pos = hull.hardpoints[i];
            ModuleCatalogEntry.HardpointType type = ModuleCatalogEntry.HardpointType.WEAPON;
            String prefix = "WPN";
            if (pos.z > hull.boundingBox.max.z * 0.7f) {
                type = ModuleCatalogEntry.HardpointType.ENGINE;
                prefix = "ENG";
            } else if (Math.abs(pos.y) > hull.boundingBox.max.y * 0.5f) {
                type = ModuleCatalogEntry.HardpointType.UTILITY;
                prefix = "UTL";
            }
            String id = prefix + "-" + (i + 1);
            hardpoints.add(new HardpointDef(id, pos, type, ModuleCatalogEntry.HardpointSize.M));

            Model m = type == ModuleCatalogEntry.HardpointType.WEAPON ? weaponMarker
                    : type == ModuleCatalogEntry.HardpointType.ENGINE ? engineMarker
                    : utilityMarker;
            ModelInstance inst = new ModelInstance(m);
            inst.transform.setToTranslation(pos);
            markers.add(inst);
        }
    }

    public int pick(Ray ray) {
        float closest = Float.MAX_VALUE;
        int idx = -1;
        for (int i = 0; i < hardpoints.size(); i++) {
            Vector3 p = hardpoints.get(i).position;
            Vector3 projected = new Vector3();
            float t = ray.direction.dot(new Vector3(p).sub(ray.origin));
            projected.set(ray.direction).scl(t).add(ray.origin);
            float dist = projected.dst(p);
            float camDist = ray.origin.dst(p);
            if (dist < 0.6f && camDist < closest) {
                closest = camDist;
                idx = i;
            }
        }
        selectedIndex = idx;
        return idx;
    }

    public HardpointDef getHardpoint(int index) {
        return hardpoints.get(index);
    }

    public List<HardpointDef> getHardpoints() { return hardpoints; }
    public int getSelectedIndex() { return selectedIndex; }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance inst : markers) batch.render(inst, env);
    }

    @Override
    public void dispose() {
        if (weaponMarker != null) weaponMarker.dispose();
        if (engineMarker != null) engineMarker.dispose();
        if (utilityMarker != null) utilityMarker.dispose();
    }
}
```

- [ ] **Step 2: Write ModuleBrowserPanel**

```java
// ModuleBrowserPanel.java
package com.galacticodyssey.shipbuilder.phase3;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.galacticodyssey.shipbuilder.*;

import java.util.List;
import java.util.function.Consumer;

public class ModuleBrowserPanel extends Window {
    public ModuleBrowserPanel(String hardpointId,
                              HardpointMarkerRenderer.HardpointDef hardpoint,
                              ShipStatsCalculator statsCalc,
                              BlueprintRegistry blueprints,
                              ShipDesign design,
                              Skin skin,
                              Consumer<ModuleAssignment> onInstall) {
        super("Module Browser — " + hardpointId, skin);

        List<ModuleCatalogEntry> modules = statsCalc.getModulesByType(hardpoint.type);
        ModuleAssignment current = design.modules.get(hardpointId);

        Table list = new Table(skin);
        for (ModuleCatalogEntry module : modules) {
            boolean unlocked = blueprints.isUnlocked(findBlueprintId(blueprints, module.moduleId));
            Table row = new Table(skin);
            Label nameLabel = new Label(module.name, skin);
            if (!unlocked) nameLabel.setColor(0.5f, 0.5f, 0.5f, 1f);
            row.add(nameLabel).left().expandX();
            row.add(new Label(String.format("%.0f DPS", module.dps), skin)).padLeft(10);
            row.add(new Label(String.format("%.0f kW", module.powerDraw), skin)).padLeft(10);
            row.add(new Label(module.price + " cr", skin)).padLeft(10);

            boolean isCurrent = current != null && current.moduleId.equals(module.moduleId);
            if (isCurrent) {
                row.add(new Label("[INSTALLED]", skin)).padLeft(10);
            } else if (unlocked) {
                TextButton installBtn = new TextButton("Install", skin);
                installBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        onInstall.accept(new ModuleAssignment(module.moduleId,
                            findBlueprintId(blueprints, module.moduleId)));
                        remove();
                    }
                });
                row.add(installBtn).padLeft(10);
            } else {
                row.add(new Label("[LOCKED]", skin)).padLeft(10);
            }

            list.add(row).fillX().padBottom(4).row();
        }

        ScrollPane scroll = new ScrollPane(list, skin);
        add(scroll).expand().fill().width(500).height(300);
        pack();
        setModal(true);
        setMovable(true);
    }

    private String findBlueprintId(BlueprintRegistry registry, String moduleId) {
        for (BlueprintData bp : registry.getByType(BlueprintData.BlueprintType.MODULE)) {
            if (bp.unlocks.equals(moduleId)) return bp.blueprintId;
        }
        return "";
    }
}
```

- [ ] **Step 3: Write ModuleFitInputProcessor and ModuleFitHUD**

```java
// ModuleFitInputProcessor.java
package com.galacticodyssey.shipbuilder.phase3;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.galacticodyssey.shipbuilder.DrydockScene;
import com.galacticodyssey.shipbuilder.ShipDesign;

public class ModuleFitInputProcessor extends InputAdapter {
    private final PerspectiveCamera camera;
    private final ShipDesign design;
    private final HardpointMarkerRenderer hardpointRenderer;
    private final DrydockScene scene;
    private HardpointClickCallback clickCallback;

    private boolean exteriorMode = true;
    private float orbitAzimuth = 0f, orbitElevation = 30f, orbitDistance = 25f;
    private final Vector3 orbitTarget = new Vector3(0, 0, 5);
    private boolean orbiting;
    private int lastX, lastY;

    public interface HardpointClickCallback {
        void onHardpointClicked(int index, HardpointMarkerRenderer.HardpointDef def);
    }

    public ModuleFitInputProcessor(PerspectiveCamera camera, ShipDesign design,
                                    HardpointMarkerRenderer hardpointRenderer, DrydockScene scene) {
        this.camera = camera;
        this.design = design;
        this.hardpointRenderer = hardpointRenderer;
        this.scene = scene;
        updateCameraOrbit();
    }

    public void setClickCallback(HardpointClickCallback callback) {
        this.clickCallback = callback;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.V) {
            exteriorMode = !exteriorMode;
            if (exteriorMode) updateCameraOrbit();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        lastX = screenX;
        lastY = screenY;
        if (button == 1 && exteriorMode) {
            orbiting = true;
            return true;
        }
        if (button == 0 && exteriorMode) {
            Ray ray = camera.getPickRay(screenX, screenY);
            int picked = hardpointRenderer.pick(ray);
            if (picked >= 0 && clickCallback != null) {
                clickCallback.onHardpointClicked(picked, hardpointRenderer.getHardpoint(picked));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (orbiting) {
            int dx = screenX - lastX;
            int dy = screenY - lastY;
            lastX = screenX;
            lastY = screenY;
            orbitAzimuth -= dx * 0.5f;
            orbitElevation = MathUtils.clamp(orbitElevation + dy * 0.5f, -89f, 89f);
            updateCameraOrbit();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == 1) orbiting = false;
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (exteriorMode) {
            orbitDistance = MathUtils.clamp(orbitDistance + amountY * 2f, 5f, 100f);
            updateCameraOrbit();
            return true;
        }
        return false;
    }

    private void updateCameraOrbit() {
        float azRad = orbitAzimuth * MathUtils.degreesToRadians;
        float elRad = orbitElevation * MathUtils.degreesToRadians;
        float cosEl = MathUtils.cos(elRad);
        camera.position.set(
            orbitTarget.x + orbitDistance * cosEl * MathUtils.sin(azRad),
            orbitTarget.y + orbitDistance * MathUtils.sin(elRad),
            orbitTarget.z + orbitDistance * cosEl * MathUtils.cos(azRad)
        );
        camera.lookAt(orbitTarget);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    public boolean isExteriorMode() { return exteriorMode; }
}
```

```java
// ModuleFitHUD.java
package com.galacticodyssey.shipbuilder.phase3;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.shipbuilder.*;

public class ModuleFitHUD {
    private final Table statsTable;
    private final Label dpsLabel, rangeLabel, thrustLabel, shieldLabel, powerLabel, weightLabel;

    public ModuleFitHUD(Skin skin) {
        statsTable = new Table(skin);
        statsTable.setBackground("default-rect");
        statsTable.defaults().pad(4).left();

        statsTable.add("SHIP PERFORMANCE").colspan(2).center().row();
        statsTable.add("DPS:").left(); dpsLabel = new Label("0", skin); statsTable.add(dpsLabel).right().row();
        statsTable.add("Range:").left(); rangeLabel = new Label("0m", skin); statsTable.add(rangeLabel).right().row();
        statsTable.add("Thrust:").left(); thrustLabel = new Label("0 kN", skin); statsTable.add(thrustLabel).right().row();
        statsTable.add("Shields:").left(); shieldLabel = new Label("0 HP", skin); statsTable.add(shieldLabel).right().row();
        statsTable.add("Power:").left(); powerLabel = new Label("0 kW", skin); statsTable.add(powerLabel).right().row();
        statsTable.add("Weight:").left(); weightLabel = new Label("0 t", skin); statsTable.add(weightLabel).right().row();
        statsTable.pack();
    }

    public void update(ShipStatsCalculator calc, ShipDesign design) {
        ShipStatsCalculator.ShipStats stats = calc.computeStats(design);
        dpsLabel.setText(String.format("%.0f", stats.totalDps));
        rangeLabel.setText(String.format("%.0fm", stats.maxWeaponRange));
        thrustLabel.setText(String.format("%.0f kN", stats.totalThrust / 1000f));
        shieldLabel.setText(String.format("%.0f HP", stats.totalShieldHp));
        powerLabel.setText(String.format("%.0f kW", stats.totalPowerDraw));
        weightLabel.setText(String.format("%.1f t", stats.totalWeight / 1000f));
    }

    public void attachTo(Stage stage) {
        Table root = new Table();
        root.setFillParent(true);
        root.top().right();
        root.add(statsTable).pad(10);
        stage.addActor(root);
    }

    public void detach() {
        statsTable.remove();
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/phase3/
git commit -m "feat(shipbuilder): add Phase 3 module fit with hardpoint markers, browser, and HUD"
```

---

## Task 12: In-Flight Planning Mode

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/planning/BuildAction.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/planning/BuildOrder.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/planning/PlanningOverlay.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/planning/ShipSchematicRenderer.java`
- Create: `core/src/main/java/com/galacticodyssey/shipbuilder/planning/BuildQueuePanel.java`
- Test: `core/src/test/java/com/galacticodyssey/shipbuilder/BuildOrderTest.java`

- [ ] **Step 1: Write BuildAction and BuildOrder**

```java
// BuildAction.java
package com.galacticodyssey.shipbuilder.planning;

import com.galacticodyssey.shipbuilder.ModuleAssignment;
import com.galacticodyssey.shipbuilder.RoomDesign;

public class BuildAction {
    public enum ActionType { ADD_ROOM, REMOVE_ROOM, SWAP_MODULE, HULL_TWEAK }

    public ActionType type;
    public RoomDesign roomDesign;
    public int roomIndex;
    public String hardpointId;
    public ModuleAssignment moduleAssignment;
    public String description;
    public int cost;

    public BuildAction() {}

    public static BuildAction addRoom(RoomDesign room, int cost) {
        BuildAction a = new BuildAction();
        a.type = ActionType.ADD_ROOM;
        a.roomDesign = room;
        a.description = "+ Add " + room.type.name();
        a.cost = cost;
        return a;
    }

    public static BuildAction removeRoom(int roomIndex, String roomTypeName, int refund) {
        BuildAction a = new BuildAction();
        a.type = ActionType.REMOVE_ROOM;
        a.roomIndex = roomIndex;
        a.description = "- Remove " + roomTypeName;
        a.cost = -refund;
        return a;
    }

    public static BuildAction swapModule(String hardpointId, ModuleAssignment assignment,
                                          String moduleName, int cost) {
        BuildAction a = new BuildAction();
        a.type = ActionType.SWAP_MODULE;
        a.hardpointId = hardpointId;
        a.moduleAssignment = assignment;
        a.description = "⇄ " + hardpointId + " → " + moduleName;
        a.cost = cost;
        return a;
    }
}
```

```java
// BuildOrder.java
package com.galacticodyssey.shipbuilder.planning;

import com.galacticodyssey.shipbuilder.ShipDesign;

import java.util.ArrayList;
import java.util.List;

public class BuildOrder {
    public final List<BuildAction> actions = new ArrayList<>();

    public void addAction(BuildAction action) {
        actions.add(action);
    }

    public void removeAction(int index) {
        actions.remove(index);
    }

    public void reorder(int fromIndex, int toIndex) {
        BuildAction action = actions.remove(fromIndex);
        actions.add(toIndex, action);
    }

    public int totalCost() {
        int total = 0;
        for (BuildAction a : actions) total += a.cost;
        return total;
    }

    public void applyTo(ShipDesign design) {
        for (BuildAction action : actions) {
            switch (action.type) {
                case ADD_ROOM:
                    design.addRoom(action.roomDesign.copy());
                    break;
                case REMOVE_ROOM:
                    if (action.roomIndex >= 0 && action.roomIndex < design.rooms.size()) {
                        design.removeRoom(action.roomIndex);
                    }
                    break;
                case SWAP_MODULE:
                    design.setModule(action.hardpointId, action.moduleAssignment);
                    break;
                case HULL_TWEAK:
                    break;
            }
        }
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }

    public void clear() {
        actions.clear();
    }
}
```

- [ ] **Step 2: Write BuildOrderTest**

```java
// BuildOrderTest.java
package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.shipbuilder.planning.BuildAction;
import com.galacticodyssey.shipbuilder.planning.BuildOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildOrderTest {
    private BuildOrder order;
    private ShipDesign design;

    @BeforeEach
    void setUp() {
        order = new BuildOrder();
        design = new ShipDesign(ShipSizeClass.SMALL);
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 4, 0, 0, 4, 3, 3));
    }

    @Test
    void totalCost_sumsAllActions() {
        order.addAction(BuildAction.addRoom(
            new RoomDesign(RoomType.MEDBAY, 8, 0, 0, 3, 3, 3), 12000));
        order.addAction(BuildAction.swapModule("WPN-1",
            new ModuleAssignment("laser_mk2", "bp_laser_mk2"), "Laser Mk2", 8500));
        assertEquals(20500, order.totalCost());
    }

    @Test
    void totalCost_subtractsRefunds() {
        order.addAction(BuildAction.addRoom(
            new RoomDesign(RoomType.MEDBAY, 8, 0, 0, 3, 3, 3), 12000));
        order.addAction(BuildAction.removeRoom(1, "ENGINE_ROOM", 5000));
        assertEquals(12000 - 5000, order.totalCost());
    }

    @Test
    void applyTo_addsRoom() {
        order.addAction(BuildAction.addRoom(
            new RoomDesign(RoomType.MEDBAY, 8, 0, 0, 3, 3, 3), 12000));
        order.applyTo(design);
        assertEquals(3, design.rooms.size());
        assertEquals(RoomType.MEDBAY, design.rooms.get(2).type);
    }

    @Test
    void applyTo_swapsModule() {
        order.addAction(BuildAction.swapModule("WPN-1",
            new ModuleAssignment("laser_mk2", "bp_laser_mk2"), "Laser Mk2", 8500));
        order.applyTo(design);
        assertNotNull(design.modules.get("WPN-1"));
        assertEquals("laser_mk2", design.modules.get("WPN-1").moduleId);
    }

    @Test
    void removeAction_updatesTotalCost() {
        order.addAction(BuildAction.addRoom(
            new RoomDesign(RoomType.MEDBAY, 8, 0, 0, 3, 3, 3), 12000));
        order.addAction(BuildAction.swapModule("WPN-1",
            new ModuleAssignment("laser_mk2", "bp_laser_mk2"), "Laser Mk2", 8500));
        order.removeAction(0);
        assertEquals(8500, order.totalCost());
    }

    @Test
    void clear_emptiesQueue() {
        order.addAction(BuildAction.addRoom(
            new RoomDesign(RoomType.MEDBAY, 8, 0, 0, 3, 3, 3), 12000));
        order.clear();
        assertTrue(order.isEmpty());
        assertEquals(0, order.totalCost());
    }
}
```

- [ ] **Step 3: Run BuildOrderTest**

Run: `./gradlew :core:test --tests "com.galacticodyssey.shipbuilder.BuildOrderTest" --info`
Expected: All 6 tests PASS

- [ ] **Step 4: Write PlanningOverlay, ShipSchematicRenderer, BuildQueuePanel**

```java
// ShipSchematicRenderer.java
package com.galacticodyssey.shipbuilder.planning;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.shipbuilder.RoomDesign;
import com.galacticodyssey.shipbuilder.ShipDesign;

public class ShipSchematicRenderer {
    private final ShapeRenderer shapeRenderer;
    private static final float SCALE = 8f;
    private float offsetX, offsetY;

    public ShipSchematicRenderer() {
        shapeRenderer = new ShapeRenderer();
    }

    public void render(ShipDesign design, float x, float y, float width, float height) {
        offsetX = x + 20;
        offsetY = y + 20;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        float hullLength = design.hull.estimateSpineLength() * SCALE;
        float hullWidth = design.hull.estimateMaxWidth() * SCALE;
        shapeRenderer.setColor(0.3f, 0.8f, 0.8f, 0.3f);
        shapeRenderer.ellipse(offsetX, offsetY + height / 2 - hullWidth / 2, hullLength, hullWidth);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (RoomDesign room : design.rooms) {
            Color c = roomColor(room.type);
            shapeRenderer.setColor(c.r, c.g, c.b, 0.6f);
            shapeRenderer.rect(
                offsetX + room.gridX * SCALE,
                offsetY + height / 2 - room.gridZ * SCALE / 2,
                room.sizeX * SCALE,
                room.sizeZ * SCALE
            );
        }
        shapeRenderer.end();
    }

    private Color roomColor(RoomType type) {
        switch (type) {
            case COCKPIT: return Color.CYAN;
            case ENGINE_ROOM: return Color.ORANGE;
            case CREW_QUARTERS: return Color.YELLOW;
            case MEDBAY: return new Color(0.6f, 0.6f, 1f, 1f);
            case ARMORY: return Color.RED;
            case CARGO_BAY: return Color.GRAY;
            case BRIG: return Color.DARK_GRAY;
            default: return Color.WHITE;
        }
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
```

```java
// BuildQueuePanel.java
package com.galacticodyssey.shipbuilder.planning;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

public class BuildQueuePanel extends Table {
    private final BuildOrder buildOrder;
    private final Skin skin;
    private final Label totalCostLabel;
    private final Table actionList;

    public BuildQueuePanel(BuildOrder buildOrder, int playerCredits, Skin skin) {
        super(skin);
        this.buildOrder = buildOrder;
        this.skin = skin;

        add(new Label("BUILD ORDER QUEUE", skin)).colspan(2).center().padBottom(8).row();

        actionList = new Table(skin);
        ScrollPane scroll = new ScrollPane(actionList, skin);
        add(scroll).expand().fill().colspan(2).row();

        add(new Label("Total Cost:", skin)).left().padTop(8);
        totalCostLabel = new Label("0 cr", skin);
        add(totalCostLabel).right().padTop(8).row();

        add(new Label("Credits:", skin)).left();
        add(new Label(playerCredits + " cr", skin)).right().row();

        add(new Label("Apply at next Shipyard dock", skin)).colspan(2).center().padTop(12).row();

        refresh();
    }

    public void refresh() {
        actionList.clear();
        for (int i = 0; i < buildOrder.actions.size(); i++) {
            BuildAction action = buildOrder.actions.get(i);
            Table row = new Table(skin);
            row.add(new Label(action.description, skin)).expandX().left();
            row.add(new Label(action.cost + " cr", skin)).right();

            int idx = i;
            TextButton removeBtn = new TextButton("X", skin);
            removeBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    buildOrder.removeAction(idx);
                    refresh();
                }
            });
            row.add(removeBtn).padLeft(8);
            actionList.add(row).fillX().padBottom(4).row();
        }
        totalCostLabel.setText(buildOrder.totalCost() + " cr");
    }
}
```

```java
// PlanningOverlay.java
package com.galacticodyssey.shipbuilder.planning;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.shipbuilder.ShipDesign;

public class PlanningOverlay {
    private final Table rootTable;
    private final ShipSchematicRenderer schematicRenderer;
    private final BuildQueuePanel queuePanel;
    private boolean visible;

    public PlanningOverlay(ShipDesign design, BuildOrder buildOrder, int playerCredits, Skin skin) {
        schematicRenderer = new ShipSchematicRenderer();
        queuePanel = new BuildQueuePanel(buildOrder, playerCredits, skin);

        rootTable = new Table(skin);
        rootTable.setFillParent(true);
        rootTable.setBackground("default-rect");

        Table header = new Table(skin);
        header.add(new Label("SHIP MODIFICATIONS — " + design.name, skin)).expandX().left();
        header.add(new Label("[ESC to close]", skin)).right();
        rootTable.add(header).fillX().pad(8).row();

        Table content = new Table(skin);
        Table schematicArea = new Table(skin);
        schematicArea.add(new Label("SHIP SCHEMATIC — TOP VIEW", skin)).row();
        schematicArea.add(new Label("(2D schematic rendered separately)", skin)).expand().fill();
        content.add(schematicArea).expand().fill().pad(4);
        content.add(queuePanel).width(300).fillY().pad(4);
        rootTable.add(content).expand().fill().row();
    }

    public void show(Stage stage) {
        stage.addActor(rootTable);
        visible = true;
    }

    public void hide() {
        rootTable.remove();
        visible = false;
    }

    public boolean isVisible() { return visible; }

    public void refreshQueue() {
        queuePanel.refresh();
    }

    public void dispose() {
        schematicRenderer.dispose();
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/shipbuilder/planning/ \
        core/src/test/java/com/galacticodyssey/shipbuilder/BuildOrderTest.java
git commit -m "feat(shipbuilder): add in-flight planning mode with build order queue"
```

---

## Task 13: Persistence Integration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/data/GameSession.java`

- [ ] **Step 1: Add ShipDesign and BuildOrder fields to GameSession**

Add these fields and accessors to the existing `GameSession.java`:

```java
// Add imports
import com.galacticodyssey.shipbuilder.ShipDesign;
import com.galacticodyssey.shipbuilder.planning.BuildOrder;

// Add fields
private ShipDesign playerShipDesign;
private BuildOrder pendingBuildOrder;

// Add accessors
public ShipDesign getPlayerShipDesign() { return playerShipDesign; }
public void setPlayerShipDesign(ShipDesign design) { this.playerShipDesign = design; }

public BuildOrder getPendingBuildOrder() { return pendingBuildOrder; }
public void setPendingBuildOrder(BuildOrder order) { this.pendingBuildOrder = order; }
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/GameSession.java
git commit -m "feat(shipbuilder): add ShipDesign and BuildOrder persistence to GameSession"
```

---

## Task 14: Run Full Test Suite & Final Verification

- [ ] **Step 1: Run all shipbuilder tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.shipbuilder.*" --info`
Expected: All tests PASS (HullDesignTest, RoomDesignTest, ShipDesignTest, BlueprintRegistryTest, BuildCostCalculatorTest, ShipDesignValidatorTest, AdjacencyBonusCalculatorTest, ShipStatsCalculatorTest, BuilderPhaseControllerTest, BuildOrderTest)

- [ ] **Step 2: Run existing ship tests to verify no regressions**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.*" --info`
Expected: All existing ship tests PASS (ShipFactory, ShipHullGenerator, ShipInteriorGenerator, SpineCurve, CrossSection, ShipColorPalette tests)

- [ ] **Step 3: Verify compilation of all modules**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Final commit with all remaining files**

```bash
git status
# Ensure all files are committed. If any remain:
git add -A
git commit -m "chore: ensure all shipbuilder files are tracked"
```
