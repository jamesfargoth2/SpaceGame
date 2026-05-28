# Hacking System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a real-time circuit-puzzle hacking system that lets the player interact with any electronic entity — terminals, doors, cameras, turrets, ship subsystems, and drones.

**Architecture:** `PlayerHackingSystem` handles range detection and interaction prompts; when the player presses F on a hackable entity, it instantiates a `HackingController` (plain Java, no GL deps) that owns the puzzle grid and timer. `HackingSystem` ticks lockout/effect timers and applies hack effects via the event bus. A Scene2D `HackingOverlay` renders the puzzle UI by subscribing to hack events.

**Tech Stack:** Ashley ECS, libGDX Scene2D, EventBus (synchronous), libGDX Json, JUnit 5

---

## File Map

### New files — `core/src/main/java/com/galacticodyssey/hacking/`

```
hacking/
  HackEffect.java                  — enum: UNLOCK, ACCESS_DATA, DISABLE_*, SUBVERT_DRONE
  HackableComponent.java           — Ashley component on every hackable entity
  HackingStateComponent.java       — Ashley component on the player
  TileType.java                    — enum: STRAIGHT, ELBOW, TEE, CROSS, EMPTY + rotation logic
  GridTile.java                    — single tile: type, rotation, powered, isSource, isTarget
  PuzzleGrid.java                  — N×N grid + BFS power propagation + win check
  PuzzleGridFactory.java           — generates a solvable L-path puzzle with scrambling + skill assists
  HackingController.java           — state machine (IDLE→ACTIVE→SUCCESS|FAILED), owns grid + timer
  systems/
    HackingSystem.java             — IteratingSystem: ticks lockout/effectTimers, handles hack events
    PlayerHackingSystem.java       — EntitySystem: range detection, prompt, hack lifecycle
  data/
    HackableTypeData.java          — POJO matching hackable_types.json entries
    HackableTypeRegistry.java      — loads and looks up hackable type presets
  events/
    HackStartedEvent.java
    HackSucceededEvent.java
    HackFailedEvent.java
    HackEffectExpiredEvent.java
    SecurityAlarmEvent.java
    DataAccessedEvent.java
  ui/
    HackingOverlay.java            — Scene2D overlay, renders puzzle grid + timer
```

### New data file
- `core/src/main/resources/data/hacking/hackable_types.json`

### New test files
- `core/src/test/java/com/galacticodyssey/hacking/PuzzleGridTest.java`
- `core/src/test/java/com/galacticodyssey/hacking/HackingControllerTest.java`
- `core/src/test/java/com/galacticodyssey/hacking/HackingSystemTest.java`

### Modified files
- `core/src/main/java/com/galacticodyssey/core/GameWorld.java` — register systems, add HackingStateComponent to player
- `core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java` — add `hackCancelPressed`
- `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` — instantiate HackingOverlay, call render/resize/dispose

---

## Task 1: Event Classes

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/hacking/events/HackStartedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/hacking/events/HackSucceededEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/hacking/events/HackFailedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/hacking/events/HackEffectExpiredEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/hacking/events/SecurityAlarmEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/hacking/events/DataAccessedEvent.java`

Note: `HackEffect` doesn't exist yet — use a placeholder `Object effect` in `HackSucceededEvent` and `HackEffectExpiredEvent` for now. Task 2 defines `HackEffect` properly; update these two events in that task.

- [ ] **Step 1: Create HackStartedEvent**

```java
// core/src/main/java/com/galacticodyssey/hacking/events/HackStartedEvent.java
package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;

public final class HackStartedEvent {
    public final Entity player;
    public final Entity target;

    public HackStartedEvent(Entity player, Entity target) {
        this.player = player;
        this.target = target;
    }
}
```

- [ ] **Step 2: Create HackFailedEvent**

```java
// core/src/main/java/com/galacticodyssey/hacking/events/HackFailedEvent.java
package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;

public final class HackFailedEvent {
    public final Entity player;
    public final Entity target;

    public HackFailedEvent(Entity player, Entity target) {
        this.player = player;
        this.target = target;
    }
}
```

- [ ] **Step 3: Create SecurityAlarmEvent**

```java
// core/src/main/java/com/galacticodyssey/hacking/events/SecurityAlarmEvent.java
package com.galacticodyssey.hacking.events;

import com.badlogic.gdx.math.Vector3;

public final class SecurityAlarmEvent {
    public final Vector3 location;
    public final float radius;

    public SecurityAlarmEvent(Vector3 location, float radius) {
        this.location = new Vector3(location);
        this.radius = radius;
    }
}
```

- [ ] **Step 4: Create DataAccessedEvent**

```java
// core/src/main/java/com/galacticodyssey/hacking/events/DataAccessedEvent.java
package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;

public final class DataAccessedEvent {
    public final Entity player;
    public final Entity terminal;
    public final String terminalId;

    public DataAccessedEvent(Entity player, Entity terminal, String terminalId) {
        this.player = player;
        this.terminal = terminal;
        this.terminalId = terminalId;
    }
}
```

- [ ] **Step 5: Create HackSucceededEvent and HackEffectExpiredEvent (with temporary Object placeholder)**

```java
// core/src/main/java/com/galacticodyssey/hacking/events/HackSucceededEvent.java
package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;

public final class HackSucceededEvent {
    public final Entity player;
    public final Entity target;
    public final Object effect; // replaced with HackEffect in Task 2

    public HackSucceededEvent(Entity player, Entity target, Object effect) {
        this.player = player;
        this.target = target;
        this.effect = effect;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/hacking/events/HackEffectExpiredEvent.java
package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;

public final class HackEffectExpiredEvent {
    public final Entity target;
    public final Object effect; // replaced with HackEffect in Task 2

    public HackEffectExpiredEvent(Entity target, Object effect) {
        this.target = target;
        this.effect = effect;
    }
}
```

- [ ] **Step 6: Verify compilation**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:classes --quiet
```

Expected: BUILD SUCCESSFUL, no errors.

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/hacking/events/
git commit -m "feat(hacking): add hacking event classes"
```

---

## Task 2: HackEffect, HackableComponent, HackingStateComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/hacking/HackEffect.java`
- Create: `core/src/main/java/com/galacticodyssey/hacking/HackableComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/hacking/HackingStateComponent.java`
- Modify: `HackSucceededEvent.java` — replace `Object effect` with `HackEffect effect`
- Modify: `HackEffectExpiredEvent.java` — replace `Object effect` with `HackEffect effect`

- [ ] **Step 1: Create HackEffect enum**

```java
// core/src/main/java/com/galacticodyssey/hacking/HackEffect.java
package com.galacticodyssey.hacking;

public enum HackEffect {
    UNLOCK,
    ACCESS_DATA,
    DISABLE_CAMERA,
    DISABLE_TURRET,
    DISABLE_ENGINES,
    DISABLE_WEAPONS,
    DISABLE_SHIELDS,
    SUBVERT_DRONE
}
```

- [ ] **Step 2: Create HackableComponent**

```java
// core/src/main/java/com/galacticodyssey/hacking/HackableComponent.java
package com.galacticodyssey.hacking;

import com.badlogic.ashley.core.Component;

public class HackableComponent implements Component {
    public String typeId = "";
    public int difficulty = 1;
    public HackEffect effect = HackEffect.ACCESS_DATA;
    public float lockoutDuration = 45f;
    public float lockoutTimer = 0f;
    public float effectTimer = 0f;
    public boolean requiresPhysicalAccess = true;
    public float interactionRange = 2.5f;
    public boolean unlocked = false;    // set true on UNLOCK effect
    public String terminalId = "";      // used by ACCESS_DATA effect
}
```

- [ ] **Step 3: Create HackingStateComponent**

```java
// core/src/main/java/com/galacticodyssey/hacking/HackingStateComponent.java
package com.galacticodyssey.hacking;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class HackingStateComponent implements Component {
    public Entity currentTarget = null;
    public HackingController controller = null;
    public boolean isRemoteHack = false;
}
```

- [ ] **Step 4: Update HackSucceededEvent to use HackEffect**

Replace:
```java
public final Object effect;
public HackSucceededEvent(Entity player, Entity target, Object effect) {
```
With:
```java
public final HackEffect effect;
public HackSucceededEvent(Entity player, Entity target, HackEffect effect) {
```
Add import: `import com.galacticodyssey.hacking.HackEffect;`

- [ ] **Step 5: Update HackEffectExpiredEvent to use HackEffect**

Replace:
```java
public final Object effect;
public HackEffectExpiredEvent(Entity target, Object effect) {
```
With:
```java
public final HackEffect effect;
public HackEffectExpiredEvent(Entity target, HackEffect effect) {
```
Add import: `import com.galacticodyssey.hacking.HackEffect;`

- [ ] **Step 6: Verify compilation**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:classes --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/hacking/
git commit -m "feat(hacking): add HackEffect enum and component types"
```

---

## Task 3: TileType and GridTile

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/hacking/TileType.java`
- Create: `core/src/main/java/com/galacticodyssey/hacking/GridTile.java`

- [ ] **Step 1: Create TileType with rotation-aware connector logic**

Bit layout for openings: bit 0 = North, bit 1 = East, bit 2 = South, bit 3 = West.
Clockwise rotation rule: `new = (old >> 3) | ((old << 1) & 0xE)`

```java
// core/src/main/java/com/galacticodyssey/hacking/TileType.java
package com.galacticodyssey.hacking;

public enum TileType {
    STRAIGHT, // base: N+S (0b0101)
    ELBOW,    // base: N+E (0b0011)
    TEE,      // base: N+E+S (0b0111)
    CROSS,    // base: all (0b1111)
    EMPTY;    // base: none (0b0000)

    private int baseSides() {
        switch (this) {
            case STRAIGHT: return 0b0101;
            case ELBOW:    return 0b0011;
            case TEE:      return 0b0111;
            case CROSS:    return 0b1111;
            default:       return 0b0000;
        }
    }

    /** Returns open-side bitmask after `rotation` clockwise 90° steps. */
    public int openSides(int rotation) {
        int base = baseSides();
        for (int i = 0; i < rotation; i++) {
            base = (base >> 3) | ((base << 1) & 0xE);
        }
        return base;
    }

    public boolean hasNorth(int rotation) { return (openSides(rotation) & 0b0001) != 0; }
    public boolean hasEast(int rotation)  { return (openSides(rotation) & 0b0010) != 0; }
    public boolean hasSouth(int rotation) { return (openSides(rotation) & 0b0100) != 0; }
    public boolean hasWest(int rotation)  { return (openSides(rotation) & 0b1000) != 0; }
}
```

- [ ] **Step 2: Create GridTile**

```java
// core/src/main/java/com/galacticodyssey/hacking/GridTile.java
package com.galacticodyssey.hacking;

public class GridTile {
    public TileType type;
    public int rotation;  // 0–3, clockwise steps
    public boolean powered;
    public boolean isSource;
    public boolean isTarget;

    public GridTile(TileType type, int rotation) {
        this.type = type;
        this.rotation = rotation;
    }

    public void rotateClockwise() {
        rotation = (rotation + 1) % 4;
    }
}
```

- [ ] **Step 3: Verify compilation**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:classes --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/hacking/TileType.java
git add core/src/main/java/com/galacticodyssey/hacking/GridTile.java
git commit -m "feat(hacking): add TileType and GridTile with rotation logic"
```

---

## Task 4: PuzzleGrid — write failing tests first

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/hacking/PuzzleGridTest.java`

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/hacking/PuzzleGridTest.java
package com.galacticodyssey.hacking;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PuzzleGridTest {

    @Test
    void sourceIsPoweredAtStart() {
        PuzzleGrid grid = new PuzzleGrid(3);
        grid.setSource(0, 0);
        grid.setTargets(new int[]{2}, new int[]{2});
        grid.setTile(0, 0, TileType.CROSS, 0);
        grid.propagatePower();
        assertTrue(grid.getTile(0, 0).powered);
    }

    @Test
    void powerDoesNotFlowThroughDisconnectedTiles() {
        PuzzleGrid grid = new PuzzleGrid(3);
        grid.setSource(0, 0);
        grid.setTargets(new int[]{0}, new int[]{2});
        grid.setTile(0, 0, TileType.CROSS, 0);
        grid.setTile(0, 1, TileType.EMPTY, 0);
        grid.setTile(0, 2, TileType.CROSS, 0);
        grid.propagatePower();
        assertFalse(grid.getTile(0, 2).powered);
        assertFalse(grid.isWon());
    }

    @Test
    void powerFlowsAlongHorizontalPath() {
        // STRAIGHT rotation 1 = E+W (0b1010)
        PuzzleGrid grid = new PuzzleGrid(3);
        grid.setSource(0, 0);
        grid.setTargets(new int[]{0}, new int[]{2});
        grid.setTile(0, 0, TileType.STRAIGHT, 1); // E+W
        grid.setTile(0, 1, TileType.STRAIGHT, 1); // E+W
        grid.setTile(0, 2, TileType.STRAIGHT, 1); // E+W
        grid.propagatePower();
        assertTrue(grid.getTile(0, 2).powered);
        assertTrue(grid.isWon());
    }

    @Test
    void rotateTileRecalculatesPowerAndDetectsWin() {
        PuzzleGrid grid = new PuzzleGrid(2);
        grid.setSource(0, 0);
        grid.setTargets(new int[]{0}, new int[]{1});
        // Source: STRAIGHT N+S (rotation 0) — east closed, no connection to right
        grid.setTile(0, 0, TileType.STRAIGHT, 0);
        // Target: STRAIGHT E+W (rotation 1) — west open
        grid.setTile(0, 1, TileType.STRAIGHT, 1);
        grid.propagatePower();
        assertFalse(grid.isWon());

        // Rotate source to E+W — now east connects to target's west
        grid.rotateTile(0, 0);
        assertTrue(grid.getTile(0, 1).powered);
        assertTrue(grid.isWon());
    }

    @Test
    void crossTileConnectsAllNeighbors() {
        PuzzleGrid grid = new PuzzleGrid(3);
        grid.setSource(1, 1);
        // All four orthogonal neighbors are targets
        grid.setTargets(new int[]{0, 1, 2, 1}, new int[]{1, 0, 1, 2});
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                grid.setTile(r, c, TileType.CROSS, 0);
        grid.propagatePower();
        assertTrue(grid.isWon());
    }

    @Test
    void elbowCornerRoutesCorrectly() {
        // Path: [0,0] -east-> [0,1] -south-> [1,1]
        // [0,0] STRAIGHT E+W (rot 1): east open
        // [0,1] ELBOW S+W (rot 2): west + south open → corner
        // [1,1] STRAIGHT N+S (rot 0): north open
        PuzzleGrid grid = new PuzzleGrid(2);
        grid.setSource(0, 0);
        grid.setTargets(new int[]{1}, new int[]{1});
        grid.setTile(0, 0, TileType.STRAIGHT, 1);
        grid.setTile(0, 1, TileType.ELBOW, 2);
        grid.setTile(1, 0, TileType.EMPTY, 0);
        grid.setTile(1, 1, TileType.STRAIGHT, 0);
        grid.propagatePower();
        assertTrue(grid.isWon());
    }

    @Test
    void isWonReturnsFalseWhenNoTargetsSet() {
        PuzzleGrid grid = new PuzzleGrid(3);
        grid.setSource(0, 0);
        grid.setTile(0, 0, TileType.CROSS, 0);
        grid.propagatePower();
        assertFalse(grid.isWon());
    }
}
```

- [ ] **Step 2: Run tests and confirm they fail (PuzzleGrid class does not exist yet)**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.hacking.PuzzleGridTest" --quiet
```

Expected: compilation error — `PuzzleGrid` not found.

- [ ] **Step 3: Commit the failing tests**

```
git add core/src/test/java/com/galacticodyssey/hacking/PuzzleGridTest.java
git commit -m "test(hacking): add failing PuzzleGrid tests"
```

---

## Task 5: Implement PuzzleGrid

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/hacking/PuzzleGrid.java`

- [ ] **Step 1: Implement PuzzleGrid**

```java
// core/src/main/java/com/galacticodyssey/hacking/PuzzleGrid.java
package com.galacticodyssey.hacking;

import java.util.ArrayDeque;
import java.util.Queue;

public class PuzzleGrid {
    private final int size;
    private final GridTile[][] tiles;
    private int sourceRow, sourceCol;
    private int[] targetRows, targetCols;

    public PuzzleGrid(int size) {
        this.size = size;
        this.tiles = new GridTile[size][size];
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                tiles[r][c] = new GridTile(TileType.EMPTY, 0);
    }

    public void setSource(int row, int col) {
        sourceRow = row;
        sourceCol = col;
        tiles[row][col].isSource = true;
    }

    public void setTargets(int[] rows, int[] cols) {
        targetRows = rows;
        targetCols = cols;
        for (int i = 0; i < rows.length; i++)
            tiles[rows[i]][cols[i]].isTarget = true;
    }

    public void setTile(int row, int col, TileType type, int rotation) {
        tiles[row][col].type = type;
        tiles[row][col].rotation = rotation;
    }

    /** Rotate tile clockwise and immediately recalculate power propagation. */
    public void rotateTile(int row, int col) {
        tiles[row][col].rotateClockwise();
        propagatePower();
    }

    /** BFS from source; a tile is powered when both it and its neighbor open toward each other. */
    public void propagatePower() {
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                tiles[r][c].powered = false;

        tiles[sourceRow][sourceCol].powered = true;
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sourceRow, sourceCol});

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int r = pos[0], c = pos[1];
            GridTile tile = tiles[r][c];

            // North
            if (r > 0 && tile.type.hasNorth(tile.rotation)) {
                GridTile nb = tiles[r - 1][c];
                if (!nb.powered && nb.type.hasSouth(nb.rotation)) {
                    nb.powered = true;
                    queue.add(new int[]{r - 1, c});
                }
            }
            // East
            if (c < size - 1 && tile.type.hasEast(tile.rotation)) {
                GridTile nb = tiles[r][c + 1];
                if (!nb.powered && nb.type.hasWest(nb.rotation)) {
                    nb.powered = true;
                    queue.add(new int[]{r, c + 1});
                }
            }
            // South
            if (r < size - 1 && tile.type.hasSouth(tile.rotation)) {
                GridTile nb = tiles[r + 1][c];
                if (!nb.powered && nb.type.hasNorth(nb.rotation)) {
                    nb.powered = true;
                    queue.add(new int[]{r + 1, c});
                }
            }
            // West
            if (c > 0 && tile.type.hasWest(tile.rotation)) {
                GridTile nb = tiles[r][c - 1];
                if (!nb.powered && nb.type.hasEast(nb.rotation)) {
                    nb.powered = true;
                    queue.add(new int[]{r, c - 1});
                }
            }
        }
    }

    /** True when all target tiles are powered simultaneously. */
    public boolean isWon() {
        if (targetRows == null || targetRows.length == 0) return false;
        for (int i = 0; i < targetRows.length; i++)
            if (!tiles[targetRows[i]][targetCols[i]].powered) return false;
        return true;
    }

    public GridTile getTile(int row, int col) { return tiles[row][col]; }
    public int getSize() { return size; }
}
```

- [ ] **Step 2: Run tests — all must pass**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.hacking.PuzzleGridTest" --quiet
```

Expected: BUILD SUCCESSFUL, 6 tests passed.

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/hacking/PuzzleGrid.java
git commit -m "feat(hacking): implement PuzzleGrid with BFS power propagation"
```

---

## Task 6: PuzzleGridFactory

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/hacking/PuzzleGridFactory.java`

The factory generates a solvable puzzle using an L-shaped path (east along row 0, then south to bottom-right corner). Intermediate path tiles are scrambled; `hackingSkill - difficulty` of them are pre-solved as skill assists, always leaving at least one scrambled.

- [ ] **Step 1: Implement PuzzleGridFactory**

```java
// core/src/main/java/com/galacticodyssey/hacking/PuzzleGridFactory.java
package com.galacticodyssey.hacking;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PuzzleGridFactory {

    private PuzzleGridFactory() {}

    /**
     * Creates a solvable puzzle grid.
     *
     * @param gridSize      dimension of the square grid
     * @param difficulty    1–5, used to cap skill assists
     * @param hackingSkill  player's HACKING point-skill rank
     */
    public static PuzzleGrid create(int gridSize, int difficulty, int hackingSkill) {
        Random rng = new Random();
        PuzzleGrid grid = new PuzzleGrid(gridSize);
        grid.setSource(0, 0);
        grid.setTargets(new int[]{gridSize - 1}, new int[]{gridSize - 1});

        // L-path: east along row 0, then south down last column
        List<int[]> path = new ArrayList<>();
        for (int c = 0; c < gridSize; c++) path.add(new int[]{0, c});
        for (int r = 1; r < gridSize; r++) path.add(new int[]{r, gridSize - 1});

        // Determine solved tile types and rotations for the L-path
        // cornerIndex = gridSize - 1 (last cell in row 0, where the turn happens)
        int cornerIndex = gridSize - 1;
        TileType[] solvedTypes = new TileType[path.size()];
        int[] solvedRotations = new int[path.size()];

        for (int i = 0; i < path.size(); i++) {
            if (i < cornerIndex) {
                // Horizontal segment: STRAIGHT E+W (rotation 1)
                solvedTypes[i] = TileType.STRAIGHT;
                solvedRotations[i] = 1;
            } else if (i == cornerIndex) {
                // Corner turning east→south: ELBOW S+W (rotation 2)
                solvedTypes[i] = TileType.ELBOW;
                solvedRotations[i] = 2;
            } else {
                // Vertical segment: STRAIGHT N+S (rotation 0)
                solvedTypes[i] = TileType.STRAIGHT;
                solvedRotations[i] = 0;
            }
            grid.setTile(path.get(i)[0], path.get(i)[1], solvedTypes[i], solvedRotations[i]);
        }

        // Fill non-path cells with random tiles at random rotations
        boolean[][] onPath = new boolean[gridSize][gridSize];
        for (int[] p : path) onPath[p[0]][p[1]] = true;
        TileType[] fillers = {TileType.STRAIGHT, TileType.ELBOW, TileType.TEE};
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (!onPath[r][c]) {
                    grid.setTile(r, c, fillers[rng.nextInt(3)], rng.nextInt(4));
                }
            }
        }

        // Scramble intermediate path tiles (index 1 to path.size()-2, excluding source and target)
        for (int i = 1; i < path.size() - 1; i++) {
            int scrambled;
            do { scrambled = rng.nextInt(4); } while (scrambled == solvedRotations[i]);
            grid.getTile(path.get(i)[0], path.get(i)[1]).rotation = scrambled;
        }

        // Skill assists: pre-solve first N intermediate tiles, always leaving at least 1 scrambled
        int maxAssists = path.size() - 3; // leave at least 1 intermediate tile scrambled
        int assists = Math.min(Math.max(0, hackingSkill - difficulty), Math.max(0, maxAssists));
        for (int i = 1; i <= assists; i++) {
            grid.getTile(path.get(i)[0], path.get(i)[1]).rotation = solvedRotations[i];
        }

        grid.propagatePower();
        return grid;
    }
}
```

- [ ] **Step 2: Verify compilation**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:classes --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/hacking/PuzzleGridFactory.java
git commit -m "feat(hacking): add PuzzleGridFactory with L-path generation and skill assists"
```

---

## Task 7: HackingController — write failing tests first

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/hacking/HackingControllerTest.java`

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/hacking/HackingControllerTest.java
package com.galacticodyssey.hacking;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.hacking.events.HackFailedEvent;
import com.galacticodyssey.hacking.events.HackSucceededEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HackingControllerTest {

    private HackableComponent hackable(int difficulty) {
        HackableComponent h = new HackableComponent();
        h.difficulty = difficulty;
        h.effect = HackEffect.UNLOCK;
        return h;
    }

    @Test
    void startsInActiveState() {
        HackingController c = new HackingController(
            new EventBus(), new Entity(), new Entity(), hackable(1), 1, false);
        assertEquals(HackingController.State.ACTIVE, c.getState());
    }

    @Test
    void timerExpiryPublishesHackFailedEvent() {
        EventBus bus = new EventBus();
        Entity player = new Entity();
        Entity target = new Entity();
        HackingController c = new HackingController(bus, player, target, hackable(1), 1, false);

        HackFailedEvent[] received = {null};
        bus.subscribe(HackFailedEvent.class, e -> received[0] = e);

        c.tick(31f); // difficulty-1 time limit is 30s

        assertEquals(HackingController.State.FAILED, c.getState());
        assertNotNull(received[0]);
        assertSame(player, received[0].player);
        assertSame(target, received[0].target);
    }

    @Test
    void tickDoesNothingAfterFailed() {
        EventBus bus = new EventBus();
        HackingController c = new HackingController(
            bus, new Entity(), new Entity(), hackable(1), 1, false);
        c.tick(31f); // expires

        int[] callCount = {0};
        bus.subscribe(HackFailedEvent.class, e -> callCount[0]++);
        c.tick(5f); // should be no-op

        assertEquals(0, callCount[0]);
    }

    @Test
    void cancelSilentlyTransitionsToFailed() {
        EventBus bus = new EventBus();
        HackingController c = new HackingController(
            bus, new Entity(), new Entity(), hackable(1), 1, false);

        int[] callCount = {0};
        bus.subscribe(HackFailedEvent.class, e -> callCount[0]++);

        c.cancel();

        assertEquals(HackingController.State.FAILED, c.getState());
        assertEquals(0, callCount[0]); // no event published
    }

    @Test
    void winningGridPublishesHackSucceededEvent() {
        EventBus bus = new EventBus();
        Entity player = new Entity();
        Entity target = new Entity();
        HackableComponent h = hackable(1);
        h.effect = HackEffect.ACCESS_DATA;
        HackingController c = new HackingController(bus, player, target, h, 1, false);

        HackSucceededEvent[] received = {null};
        bus.subscribe(HackSucceededEvent.class, e -> received[0] = e);

        // Force-win: replace all tiles with CROSS and rotate any tile to trigger win check
        PuzzleGrid grid = c.getGrid();
        int size = grid.getSize();
        for (int r = 0; r < size; r++)
            for (int col = 0; col < size; col++)
                grid.setTile(r, col, TileType.CROSS, 0);
        c.rotateTile(0, 0); // CROSS rotation 0→1, still all-connected, win detected

        assertEquals(HackingController.State.SUCCESS, c.getState());
        assertNotNull(received[0]);
        assertEquals(HackEffect.ACCESS_DATA, received[0].effect);
        assertSame(player, received[0].player);
    }

    @Test
    void remoteHackAddsDifficultyPenalty() {
        // Remote hack: +1 effective difficulty. Diff 1 normally → 3x3 grid, 30s.
        // Remote: effective diff 2 → 3x3, 20s, then -10s → 10s.
        HackingController cRemote = new HackingController(
            new EventBus(), new Entity(), new Entity(), hackable(1), 1, true);
        HackingController cPhysical = new HackingController(
            new EventBus(), new Entity(), new Entity(), hackable(1), 1, false);
        assertTrue(cRemote.getTimeRemaining() < cPhysical.getTimeRemaining());
    }

    @Test
    void puzzleIsNotPreWonWithZeroAssists() {
        // hackingSkill == difficulty → 0 assists → at least 1 tile scrambled → not yet won
        HackingController c = new HackingController(
            new EventBus(), new Entity(), new Entity(), hackable(1), 1, false);
        assertFalse(c.getGrid().isWon());
    }
}
```

- [ ] **Step 2: Run — confirm compilation error (HackingController not found)**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.hacking.HackingControllerTest" --quiet
```

Expected: compilation error.

- [ ] **Step 3: Commit the failing tests**

```
git add core/src/test/java/com/galacticodyssey/hacking/HackingControllerTest.java
git commit -m "test(hacking): add failing HackingController tests"
```

---

## Task 8: Implement HackingController

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/hacking/HackingController.java`

- [ ] **Step 1: Implement HackingController**

```java
// core/src/main/java/com/galacticodyssey/hacking/HackingController.java
package com.galacticodyssey.hacking;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.hacking.events.HackFailedEvent;
import com.galacticodyssey.hacking.events.HackSucceededEvent;

public class HackingController {

    public enum State { IDLE, ACTIVE, SUCCESS, FAILED }

    private State state = State.ACTIVE;
    private float timeRemaining;
    private final PuzzleGrid grid;
    private final EventBus eventBus;
    private final Entity player;
    private final Entity target;
    private final HackEffect effect;

    public HackingController(EventBus eventBus, Entity player, Entity target,
                             HackableComponent hackable, int hackingSkill, boolean remoteHack) {
        this.eventBus = eventBus;
        this.player = player;
        this.target = target;
        this.effect = hackable.effect;

        int effectiveDifficulty = Math.min(5, hackable.difficulty + (remoteHack ? 1 : 0));
        int gridSize = (effectiveDifficulty <= 2) ? 3 : (effectiveDifficulty <= 4) ? 4 : 5;
        this.grid = PuzzleGridFactory.create(gridSize, effectiveDifficulty, hackingSkill);

        float base = baseTime(effectiveDifficulty);
        if (hackingSkill < hackable.difficulty) base -= 5f;
        if (remoteHack) base -= 10f;
        this.timeRemaining = Math.max(5f, base);
    }

    private static float baseTime(int difficulty) {
        switch (difficulty) {
            case 1: return 30f;
            case 2: return 20f;
            case 3: return 30f;
            case 4: return 20f;
            default: return 25f;
        }
    }

    /** Advance the timer. Publishes HackFailedEvent on expiry. */
    public void tick(float dt) {
        if (state != State.ACTIVE) return;
        timeRemaining -= dt;
        if (timeRemaining <= 0f) {
            timeRemaining = 0f;
            state = State.FAILED;
            eventBus.publish(new HackFailedEvent(player, target));
        }
    }

    /** Rotate tile at (row, col) clockwise and check for win. Publishes HackSucceededEvent if won. */
    public void rotateTile(int row, int col) {
        if (state != State.ACTIVE) return;
        grid.rotateTile(row, col);
        if (grid.isWon()) {
            state = State.SUCCESS;
            eventBus.publish(new HackSucceededEvent(player, target, effect));
        }
    }

    /** Cancel silently — no event published. Use when hack is interrupted by environment. */
    public void cancel() {
        if (state == State.ACTIVE) state = State.FAILED;
    }

    public State getState() { return state; }
    public float getTimeRemaining() { return timeRemaining; }
    public PuzzleGrid getGrid() { return grid; }
    public HackEffect getEffect() { return effect; }
}
```

- [ ] **Step 2: Run tests — all must pass**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.hacking.HackingControllerTest" --quiet
```

Expected: BUILD SUCCESSFUL, 7 tests passed.

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/hacking/HackingController.java
git commit -m "feat(hacking): implement HackingController with puzzle state machine and timer"
```

---

## Task 9: HackableTypeData, HackableTypeRegistry, and JSON Config

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/hacking/data/HackableTypeData.java`
- Create: `core/src/main/java/com/galacticodyssey/hacking/data/HackableTypeRegistry.java`
- Create: `core/src/main/resources/data/hacking/hackable_types.json`

- [ ] **Step 1: Create HackableTypeData**

```java
// core/src/main/java/com/galacticodyssey/hacking/data/HackableTypeData.java
package com.galacticodyssey.hacking.data;

import com.galacticodyssey.hacking.HackEffect;

public class HackableTypeData {
    public String id = "";
    public int difficulty = 1;
    public HackEffect effect = HackEffect.ACCESS_DATA;
    public float lockoutDuration = 45f;
    public boolean requiresPhysicalAccess = true;
    public float interactionRange = 2.5f;
}
```

- [ ] **Step 2: Create HackableTypeRegistry**

Follows the same pattern as `WaterDataRegistry` — uses libGDX `Json` and `JsonReader`.

```java
// core/src/main/java/com/galacticodyssey/hacking/data/HackableTypeRegistry.java
package com.galacticodyssey.hacking.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.hacking.HackableComponent;

import java.util.HashMap;
import java.util.Map;

public class HackableTypeRegistry {

    private final Map<String, HackableTypeData> types = new HashMap<>();

    public void loadFromFiles() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/hacking/hackable_types.json"));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            HackableTypeData data = json.readValue(HackableTypeData.class, entry);
            data.id = entry.name;
            types.put(data.id, data);
        }
    }

    /** Apply preset values from typeId onto the given component. No-op if typeId not found. */
    public void configure(HackableComponent component) {
        HackableTypeData data = types.get(component.typeId);
        if (data == null) return;
        component.difficulty = data.difficulty;
        component.effect = data.effect;
        component.lockoutDuration = data.lockoutDuration;
        component.requiresPhysicalAccess = data.requiresPhysicalAccess;
        component.interactionRange = data.interactionRange;
    }

    public HackableTypeData get(String id) { return types.get(id); }

    /** For tests: register a type without loading from disk. */
    public void register(HackableTypeData data) { types.put(data.id, data); }
}
```

- [ ] **Step 3: Create hackable_types.json**

```json
{
  "standard_door":     { "difficulty": 1, "effect": "UNLOCK",          "lockoutDuration": 30, "requiresPhysicalAccess": true,  "interactionRange": 2.5 },
  "camera_mk1":        { "difficulty": 2, "effect": "DISABLE_CAMERA",  "lockoutDuration": 30, "requiresPhysicalAccess": false, "interactionRange": 2.5 },
  "turret_mk1":        { "difficulty": 2, "effect": "DISABLE_TURRET",  "lockoutDuration": 45, "requiresPhysicalAccess": false, "interactionRange": 2.5 },
  "security_terminal": { "difficulty": 3, "effect": "ACCESS_DATA",     "lockoutDuration": 45, "requiresPhysicalAccess": true,  "interactionRange": 2.5 },
  "combat_drone":      { "difficulty": 3, "effect": "SUBVERT_DRONE",   "lockoutDuration": 45, "requiresPhysicalAccess": false, "interactionRange": 2.5 },
  "ship_engines":      { "difficulty": 4, "effect": "DISABLE_ENGINES", "lockoutDuration": 60, "requiresPhysicalAccess": false, "interactionRange": 2.5 },
  "ship_weapons":      { "difficulty": 4, "effect": "DISABLE_WEAPONS", "lockoutDuration": 60, "requiresPhysicalAccess": false, "interactionRange": 2.5 },
  "ship_shields":      { "difficulty": 5, "effect": "DISABLE_SHIELDS", "lockoutDuration": 60, "requiresPhysicalAccess": false, "interactionRange": 2.5 }
}
```

- [ ] **Step 4: Verify compilation**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:classes --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/hacking/data/
git add core/src/main/resources/data/hacking/
git commit -m "feat(hacking): add HackableTypeRegistry and JSON config"
```

---

## Task 10: HackingSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/hacking/systems/HackingSystem.java`

`HackingSystem` is an `IteratingSystem` over all `HackableComponent` entities. It ticks timers every frame and subscribes to `HackSucceededEvent` / `HackFailedEvent` on the event bus (synchronous delivery — events fire during `PlayerHackingSystem.update()` and listeners execute immediately).

- [ ] **Step 1: Implement HackingSystem**

```java
// core/src/main/java/com/galacticodyssey/hacking/systems/HackingSystem.java
package com.galacticodyssey.hacking.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.hacking.HackableComponent;
import com.galacticodyssey.hacking.HackEffect;
import com.galacticodyssey.hacking.events.*;

public class HackingSystem extends IteratingSystem {

    private final ComponentMapper<HackableComponent> hackableMapper =
        ComponentMapper.getFor(HackableComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private final EventBus eventBus;

    public HackingSystem(int priority, EventBus eventBus) {
        super(Family.all(HackableComponent.class).get(), priority);
        this.eventBus = eventBus;

        eventBus.subscribe(HackFailedEvent.class, this::onHackFailed);
        eventBus.subscribe(HackSucceededEvent.class, this::onHackSucceeded);
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        HackableComponent hackable = hackableMapper.get(entity);

        if (hackable.lockoutTimer > 0f) {
            hackable.lockoutTimer = Math.max(0f, hackable.lockoutTimer - dt);
        }

        if (hackable.effectTimer > 0f) {
            hackable.effectTimer -= dt;
            if (hackable.effectTimer <= 0f) {
                hackable.effectTimer = 0f;
                eventBus.publish(new HackEffectExpiredEvent(entity, hackable.effect));
            }
        }
    }

    private void onHackFailed(HackFailedEvent event) {
        HackableComponent hackable = hackableMapper.get(event.target);
        if (hackable == null) return;
        hackable.lockoutTimer = hackable.lockoutDuration;

        TransformComponent transform = transformMapper.get(event.target);
        Vector3 location = transform != null ? transform.position : new Vector3();
        eventBus.publish(new SecurityAlarmEvent(location, 50f));
    }

    private void onHackSucceeded(HackSucceededEvent event) {
        HackableComponent hackable = hackableMapper.get(event.target);
        if (hackable == null) return;

        switch (event.effect) {
            case UNLOCK:
                hackable.unlocked = true;
                break;
            case ACCESS_DATA:
                eventBus.publish(new DataAccessedEvent(event.player, event.target, hackable.terminalId));
                break;
            case DISABLE_CAMERA:
                hackable.effectTimer = 60f; break;
            case DISABLE_TURRET:
                hackable.effectTimer = 45f; break;
            case DISABLE_ENGINES:
                hackable.effectTimer = 30f; break;
            case DISABLE_WEAPONS:
                hackable.effectTimer = 30f; break;
            case DISABLE_SHIELDS:
                hackable.effectTimer = 20f; break;
            case SUBVERT_DRONE:
                hackable.effectTimer = 90f; break;
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:classes --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/hacking/systems/HackingSystem.java
git commit -m "feat(hacking): implement HackingSystem for timer ticks and effect application"
```

---

## Task 11: PlayerHackingSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/hacking/systems/PlayerHackingSystem.java`

This system runs at priority **-1** so it executes before `InteractionSystem` (priority 0) and consumes `interactPressed` when it handles a hack action.

- [ ] **Step 1: Implement PlayerHackingSystem**

```java
// core/src/main/java/com/galacticodyssey/hacking/systems/PlayerHackingSystem.java
package com.galacticodyssey.hacking.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.InteractionPromptEvent;
import com.galacticodyssey.hacking.HackableComponent;
import com.galacticodyssey.hacking.HackingController;
import com.galacticodyssey.hacking.HackingStateComponent;
import com.galacticodyssey.hacking.events.HackStartedEvent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;

public class PlayerHackingSystem extends EntitySystem {

    private final EventBus eventBus;

    private final ComponentMapper<HackingStateComponent> hackStateMapper =
        ComponentMapper.getFor(HackingStateComponent.class);
    private final ComponentMapper<HackableComponent> hackableMapper =
        ComponentMapper.getFor(HackableComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<PlayerStatsComponent> statsMapper =
        ComponentMapper.getFor(PlayerStatsComponent.class);

    private ImmutableArray<Entity> playerEntities;
    private ImmutableArray<Entity> hackableEntities;

    private final Vector3 tempVec = new Vector3();

    public PlayerHackingSystem(EventBus eventBus) {
        super(-1); // runs before InteractionSystem (priority 0)
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
            HackingStateComponent.class, TransformComponent.class,
            PlayerInputComponent.class, PlayerStatsComponent.class).get());
        hackableEntities = engine.getEntitiesFor(
            Family.all(HackableComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float dt) {
        for (int i = 0; i < playerEntities.size(); i++) {
            processPlayer(playerEntities.get(i), dt);
        }
    }

    private void processPlayer(Entity player, float dt) {
        HackingStateComponent hackState = hackStateMapper.get(player);
        TransformComponent playerTransform = transformMapper.get(player);
        PlayerInputComponent input = inputMapper.get(player);
        PlayerStatsComponent stats = statsMapper.get(player);

        int hackingSkill = stats.pointSkills.get(PointSkill.HACKING, 0);

        // — Active hack: tick and check for interruption —
        if (hackState.controller != null) {
            HackingController controller = hackState.controller;
            HackableComponent hackable = hackableMapper.get(hackState.currentTarget);

            // Target destroyed
            if (hackable == null) {
                controller.cancel();
                clearHack(hackState);
                return;
            }

            // Physical hack: cancel if player moved out of range
            if (!hackState.isRemoteHack) {
                TransformComponent targetTransform = transformMapper.get(hackState.currentTarget);
                float dist = tempVec.set(playerTransform.position).dst(targetTransform.position);
                if (dist > hackable.interactionRange + 0.5f) {
                    controller.cancel();
                    clearHack(hackState);
                    eventBus.publish(new InteractionPromptEvent("", false));
                    return;
                }
            }

            controller.tick(dt);

            if (controller.getState() != HackingController.State.ACTIVE) {
                clearHack(hackState); // HackingSystem applied lockout via event subscription
            }
            return;
        }

        // — No active hack: find nearest in-range hackable target —
        Entity nearest = null;
        float nearestDist = Float.MAX_VALUE;
        boolean nearestIsRemote = false;

        for (int i = 0; i < hackableEntities.size(); i++) {
            Entity target = hackableEntities.get(i);
            HackableComponent hackable = hackableMapper.get(target);
            TransformComponent targetTransform = transformMapper.get(target);
            float dist = tempVec.set(playerTransform.position).dst(targetTransform.position);

            boolean inRange = false;
            boolean isRemote = false;

            if (dist <= hackable.interactionRange) {
                inRange = true;
            } else if (hackingSkill >= 5 && !hackable.requiresPhysicalAccess) {
                float remoteRange = 10f + (hackingSkill - 5) * 2f;
                if (dist <= remoteRange) { inRange = true; isRemote = true; }
            }

            if (inRange && dist < nearestDist) {
                nearest = target;
                nearestDist = dist;
                nearestIsRemote = isRemote;
            }
        }

        if (nearest == null) {
            eventBus.publish(new InteractionPromptEvent("", false));
            return;
        }

        HackableComponent hackable = hackableMapper.get(nearest);

        if (hackable.lockoutTimer > 0f) {
            eventBus.publish(new InteractionPromptEvent(
                String.format("[LOCKED OUT: %.0fs]", hackable.lockoutTimer), true));
            return;
        }

        if (hackingSkill < hackable.difficulty) {
            eventBus.publish(new InteractionPromptEvent(
                String.format("[HACKING %d REQUIRED]", hackable.difficulty), true));
            return;
        }

        String remoteLabel = nearestIsRemote ? " [REMOTE]" : "";
        eventBus.publish(new InteractionPromptEvent(
            String.format("[F] Hack%s  Rank %d", remoteLabel, hackable.difficulty), true));

        if (input.interactPressed) {
            input.interactPressed = false; // consume so InteractionSystem doesn't also fire
            HackingController controller = new HackingController(
                eventBus, player, nearest, hackable, hackingSkill, nearestIsRemote);
            hackState.controller = controller;
            hackState.currentTarget = nearest;
            hackState.isRemoteHack = nearestIsRemote;
            eventBus.publish(new HackStartedEvent(player, nearest));
        }
    }

    private void clearHack(HackingStateComponent hackState) {
        hackState.controller = null;
        hackState.currentTarget = null;
        hackState.isRemoteHack = false;
    }
}
```

- [ ] **Step 2: Verify compilation**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:classes --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/hacking/systems/PlayerHackingSystem.java
git commit -m "feat(hacking): implement PlayerHackingSystem for range detection and hack lifecycle"
```

---

## Task 12: HackingOverlay (Scene2D UI)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/hacking/ui/HackingOverlay.java`

The overlay renders the puzzle grid as a table of clickable `TextButton` widgets. Tile symbols are ASCII box-drawing characters. The overlay shows/hides based on hack events and calls `HackingController.rotateTile()` on tile click.

- [ ] **Step 1: Implement HackingOverlay**

```java
// core/src/main/java/com/galacticodyssey/hacking/ui/HackingOverlay.java
package com.galacticodyssey.hacking.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.hacking.*;
import com.galacticodyssey.hacking.events.*;

public class HackingOverlay implements Disposable {

    private final Stage stage;
    private final Skin skin;
    private HackingController activeController;
    private Table tileTable;
    private Label timerLabel;
    private Label statusLabel;
    private Window window;

    public HackingOverlay(EventBus eventBus, Skin skin) {
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());

        eventBus.subscribe(HackSucceededEvent.class, e -> showStatus("ACCESS GRANTED", Color.GREEN));
        eventBus.subscribe(HackFailedEvent.class, e -> showStatus("ACCESS DENIED", Color.RED));
    }

    /** Called by GameScreen's HackStartedEvent subscription, after the controller is set. */
    public void show(HackingController controller) {
        this.activeController = controller;
        rebuildGrid(); // also calls stage.addActor(window)
        Gdx.input.setInputProcessor(stage);
    }

    private void rebuildGrid() {
        stage.clear();
        if (activeController == null) return;

        window = new Window("HACKING", skin);
        window.setMovable(false);

        timerLabel = new Label("", skin);
        statusLabel = new Label("", skin);

        tileTable = new Table();
        PuzzleGrid grid = activeController.getGrid();
        int size = grid.getSize();

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                final int row = r, col = c;
                TextButton btn = new TextButton(tileSymbol(grid.getTile(r, c)), skin);
                btn.getColor().set(grid.getTile(r, c).powered ? Color.GREEN : Color.DARK_GRAY);
                if (grid.getTile(r, c).isSource) btn.getColor().set(Color.YELLOW);
                if (grid.getTile(r, c).isTarget) btn.getColor().set(Color.RED);
                btn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        activeController.rotateTile(row, col);
                        refreshGrid();
                    }
                });
                tileTable.add(btn).size(60, 60).pad(4);
            }
            tileTable.row();
        }

        window.add(timerLabel).colspan(size).center().padBottom(8).row();
        window.add(tileTable).colspan(size).row();
        window.add(statusLabel).colspan(size).center().padTop(8).row();
        window.pack();
        window.setPosition(
            (stage.getWidth() - window.getWidth()) / 2f,
            (stage.getHeight() - window.getHeight()) / 2f);
        stage.addActor(window);
    }

    private void refreshGrid() {
        if (activeController == null) return;
        PuzzleGrid grid = activeController.getGrid();
        int size = grid.getSize();
        // Re-read each button's symbol and color from grid state
        int btnIdx = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                GridTile tile = grid.getTile(r, c);
                // tileTable children: row * size + col (cells are added left-to-right, top-to-bottom)
                Cell<?> cell = tileTable.getCells().get(btnIdx++);
                TextButton btn = (TextButton) cell.getActor();
                btn.setText(tileSymbol(tile));
                if (tile.isSource) btn.getColor().set(Color.YELLOW);
                else if (tile.isTarget) btn.getColor().set(Color.RED);
                else btn.getColor().set(tile.powered ? Color.GREEN : Color.DARK_GRAY);
            }
        }
    }

    private void showStatus(String text, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(text);
            statusLabel.setColor(color);
        }
        // Dismiss after a short delay — handled externally via hide()
    }

    public void hide() {
        activeController = null;
        stage.clear();
    }

    public void render(float dt) {
        if (activeController == null) return;
        if (timerLabel != null) {
            timerLabel.setText(String.format("%.1f s", activeController.getTimeRemaining()));
        }
        stage.act(dt);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    // ASCII tile symbols
    private static String tileSymbol(GridTile tile) {
        if (tile.isSource) return "S";
        if (tile.isTarget) return "T";
        switch (tile.type) {
            case STRAIGHT: return tile.rotation % 2 == 0 ? "|" : "-";
            case ELBOW:
                switch (tile.rotation) {
                    case 0: return "L";  // N+E (└ approximation)
                    case 1: return "r";  // E+S (┌)
                    case 2: return "7";  // S+W (┐)
                    default: return "J"; // N+W (┘)
                }
            case TEE:
                switch (tile.rotation) {
                    case 0: return "T";
                    case 1: return "|>";
                    case 2: return "P";
                    default: return "<|";
                }
            case CROSS:  return "+";
            default:     return " ";
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:classes --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/hacking/ui/HackingOverlay.java
git commit -m "feat(hacking): add HackingOverlay Scene2D puzzle UI"
```

---

## Task 13: Integration — GameWorld and GameScreen

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

### GameWorld changes

- [ ] **Step 1: Add imports to GameWorld**

Add these imports to the import block (after the existing mission/npc imports):

```java
import com.galacticodyssey.hacking.HackingStateComponent;
import com.galacticodyssey.hacking.data.HackableTypeRegistry;
import com.galacticodyssey.hacking.systems.HackingSystem;
import com.galacticodyssey.hacking.systems.PlayerHackingSystem;
```

- [ ] **Step 2: Add fields to GameWorld**

After the `private DialogSystem dialogSystem;` field, add:

```java
private HackableTypeRegistry hackableTypeRegistry;
private PlayerHackingSystem playerHackingSystem;
```

- [ ] **Step 3: Register systems in the GameWorld constructor**

After the `engine.addSystem(dialogSystem);` line (near the end of the constructor), add:

```java
// Hacking systems
hackableTypeRegistry = new HackableTypeRegistry();
if (com.badlogic.gdx.Gdx.files != null) {
    hackableTypeRegistry.loadFromFiles();
}
playerHackingSystem = new PlayerHackingSystem(eventBus);
engine.addSystem(playerHackingSystem);
engine.addSystem(new HackingSystem(25, eventBus));
```

- [ ] **Step 4: Add HackingStateComponent to the player entity**

In `createPlayerEntity()`, after `player.add(new SwimmingStateComponent());`, add:

```java
player.add(new HackingStateComponent());
```

- [ ] **Step 5: Add accessor**

After `public DialogSystem getDialogSystem()`, add:

```java
public HackableTypeRegistry getHackableTypeRegistry() { return hackableTypeRegistry; }
```

### GameScreen changes

- [ ] **Step 6: Find GameScreen and add HackingOverlay**

Open `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`. Add a `HackingOverlay hackingOverlay;` field.

In the method where the game UI is initialized (likely `show()` or a constructor, after the Skin is available), add:

```java
hackingOverlay = new HackingOverlay(gameWorld.getEventBus(), skin);
```

Wire `PlayerHackingSystem` to call `hackingOverlay.show(controller)` after publishing `HackStartedEvent`. The cleanest way: subscribe in GameScreen after constructing the overlay:

```java
gameWorld.getEventBus().subscribe(HackStartedEvent.class, event -> {
    Entity player = event.player;
    HackingStateComponent hackState = player.getComponent(HackingStateComponent.class);
    if (hackState != null && hackState.controller != null) {
        hackingOverlay.show(hackState.controller);
    }
});
gameWorld.getEventBus().subscribe(HackSucceededEvent.class, e -> {
    // Brief delay before hiding — let the "ACCESS GRANTED" message show
    hackingOverlay.hide();
    Gdx.input.setInputProcessor(gameInputProcessor); // restore game input
});
gameWorld.getEventBus().subscribe(HackFailedEvent.class, e -> {
    hackingOverlay.hide();
    Gdx.input.setInputProcessor(gameInputProcessor);
});
```

(Replace `gameInputProcessor` with whatever the existing game input processor reference is in GameScreen.)

- [ ] **Step 7: Call render and resize in GameScreen**

In `render(float delta)`, after all 3D rendering is done and before/after existing HUD render calls, add:

```java
hackingOverlay.render(delta);
```

In `resize(int width, int height)`, add:

```java
hackingOverlay.resize(width, height);
```

In `dispose()`, add:

```java
hackingOverlay.dispose();
```

- [ ] **Step 8: Verify full compilation**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:classes --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(hacking): wire HackingSystem and HackingOverlay into GameWorld and GameScreen"
```

---

## Task 14: Integration Tests — HackingSystem

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/hacking/HackingSystemTest.java`

These tests use a headless Ashley engine — no GL context required.

- [ ] **Step 1: Write tests**

```java
// core/src/test/java/com/galacticodyssey/hacking/HackingSystemTest.java
package com.galacticodyssey.hacking;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.hacking.events.*;
import com.galacticodyssey.hacking.systems.HackingSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HackingSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private HackingSystem hackingSystem;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        hackingSystem = new HackingSystem(0, eventBus);
        engine = new Engine();
        engine.addSystem(hackingSystem);
    }

    private Entity entityWithHackable(int difficulty, HackEffect effect) {
        Entity e = new Entity();
        HackableComponent h = new HackableComponent();
        h.difficulty = difficulty;
        h.effect = effect;
        h.lockoutDuration = 45f;
        e.add(h);
        engine.addEntity(e);
        return e;
    }

    @Test
    void lockoutTimerCountsDownEachFrame() {
        Entity target = entityWithHackable(1, HackEffect.ACCESS_DATA);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        hackable.lockoutTimer = 10f;

        engine.update(1f);

        assertEquals(9f, hackable.lockoutTimer, 0.01f);
    }

    @Test
    void lockoutTimerDoesNotGoBelowZero() {
        Entity target = entityWithHackable(1, HackEffect.ACCESS_DATA);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        hackable.lockoutTimer = 0.5f;

        engine.update(2f);

        assertEquals(0f, hackable.lockoutTimer, 0.001f);
    }

    @Test
    void onHackFailedSetsLockoutTimer() {
        Entity target = entityWithHackable(1, HackEffect.ACCESS_DATA);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        assertEquals(0f, hackable.lockoutTimer);

        eventBus.publish(new HackFailedEvent(new Entity(), target));

        assertEquals(45f, hackable.lockoutTimer, 0.001f);
    }

    @Test
    void onHackSucceededUnlockSetsUnlockedFlag() {
        Entity target = entityWithHackable(1, HackEffect.UNLOCK);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        assertFalse(hackable.unlocked);

        eventBus.publish(new HackSucceededEvent(new Entity(), target, HackEffect.UNLOCK));

        assertTrue(hackable.unlocked);
    }

    @Test
    void onHackSucceededDisableTurretSetsEffectTimer() {
        Entity target = entityWithHackable(2, HackEffect.DISABLE_TURRET);
        HackableComponent hackable = target.getComponent(HackableComponent.class);

        eventBus.publish(new HackSucceededEvent(new Entity(), target, HackEffect.DISABLE_TURRET));

        assertEquals(45f, hackable.effectTimer, 0.001f);
    }

    @Test
    void effectTimerExpiryPublishesHackEffectExpiredEvent() {
        Entity target = entityWithHackable(2, HackEffect.DISABLE_CAMERA);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        hackable.effectTimer = 0.5f;

        HackEffectExpiredEvent[] received = {null};
        eventBus.subscribe(HackEffectExpiredEvent.class, e -> received[0] = e);

        engine.update(1f);

        assertNotNull(received[0]);
        assertSame(target, received[0].target);
        assertEquals(HackEffect.DISABLE_CAMERA, received[0].effect);
        assertEquals(0f, hackable.effectTimer, 0.001f);
    }

    @Test
    void onHackSucceededAccessDataPublishesDataAccessedEvent() {
        Entity player = new Entity();
        Entity target = entityWithHackable(3, HackEffect.ACCESS_DATA);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        hackable.terminalId = "vault_alpha";

        DataAccessedEvent[] received = {null};
        eventBus.subscribe(DataAccessedEvent.class, e -> received[0] = e);

        eventBus.publish(new HackSucceededEvent(player, target, HackEffect.ACCESS_DATA));

        assertNotNull(received[0]);
        assertEquals("vault_alpha", received[0].terminalId);
        assertSame(player, received[0].player);
    }
}
```

- [ ] **Step 2: Run all hacking tests**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --tests "com.galacticodyssey.hacking.*" --quiet
```

Expected: BUILD SUCCESSFUL, all tests in `PuzzleGridTest`, `HackingControllerTest`, and `HackingSystemTest` pass.

- [ ] **Step 3: Run full test suite to check for regressions**

```powershell
$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"
.\gradlew.bat :core:test --quiet
```

Expected: BUILD SUCCESSFUL. Pre-existing failures (`GalaxyGenerationPipelineTest::shipSpawnIs75mEastOfPlayer` and `PlayerMovementSystemTest::staminaDrainsWhileSprinting`) are known and unrelated to this feature.

- [ ] **Step 4: Commit**

```
git add core/src/test/java/com/galacticodyssey/hacking/HackingSystemTest.java
git commit -m "test(hacking): add HackingSystem integration tests"
```

---

## Post-Implementation Notes

**Spawning hackable entities:** To add a hackable door/turret/terminal to the world, create an entity with `HackableComponent`, set `typeId`, call `hackableTypeRegistry.configure(hackableComponent)` to apply the JSON preset, and add `TransformComponent` with the entity's world position. The entity must be added to the Ashley engine.

**Effect receivers:** `HackEffectExpiredEvent` and the timed-effect mechanism are wired up — subscribe to them in future camera/turret/ship systems to restore AI behavior when effects expire. The `HackableComponent.effectTimer > 0` flag is available for per-frame checks in those systems.

**Visual polish:** `HackingOverlay` uses ASCII tile symbols. Replace with TextureRegion-based icons when art assets are available by swapping `TextButton` for `ImageButton` in `rebuildGrid()`.

**Combat interruption:** Not implemented in this version. Future work: subscribe to a `CombatEngagedEvent` in `PlayerHackingSystem` and call `controller.cancel()` followed by publishing `HackFailedEvent` manually.
