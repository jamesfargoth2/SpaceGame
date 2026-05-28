# Ship Builder & Interior Customization — Design Spec

## Overview

A phased ship builder accessible at shipyard stations, allowing players to sculpt hull shapes via control point editing, place rooms in first-person 3D inside the empty hull shell, and slot functional modules into hardpoints. A lightweight in-flight planning mode lets players queue modifications for their next dock.

## Architecture

### Drydock Scene

The ship builder runs in a dedicated `DrydockScreen` — a separate scene from gameplay, not an overlay. The player enters by interacting with a drydock terminal at a shipyard station. The ship is rendered at origin in an empty drydock environment with ambient lighting.

All edits are applied to a `ShipDesign` data object (not directly to ECS components). When the player commits the build, `ShipDesign` generates a `ShipBlueprint` that feeds into the existing `ShipFactory` pipeline to produce the final ship entity.

### Three Sequential Phases

| Phase | Camera | Primary Action | Locks On Advance |
|-------|--------|---------------|-----------------|
| 1. Hull Sculpt | Orbital (orbit/zoom/pan) | Manipulate spine control points, cross-sections, appendages | Hull shape |
| 2. Room Layout | First-person interior | Walk through empty shell, place room ghost volumes on grid | Room placements |
| 3. Module Fit | Interior + exterior toggle | Click hardpoints, browse and install modules | Module assignments |

The player can go back to earlier phases. Going back unlocks that phase for editing and flags later work for re-validation (e.g., returning to Phase 1 may invalidate rooms that no longer fit the modified hull).

### Phase Transitions

- **Phase 1 → 2:** Hull mesh locks and renders as translucent wireframe from inside. Interior volume voxelized into 1m grid cells (existing `ShipInteriorGenerator` logic). Temporary floor plane generated. Player spawns at airlock position in first-person. Grid overlay and room palette HUD appear.
- **Phase 2 → 3:** Room layout locks. Corridors finalized with doors. Full interior mesh generated (solid walls/floors). Exterior hardpoints activate with glowing markers. Module inventory panel becomes accessible.
- **Phase 3 → Commit:** `ShipDesign` validated. Build cost deducted. `ShipBlueprint` generated. `ShipFactory` regenerates the ship entity with new mesh, interior, physics, and components.

### Input & HUD Per Phase

Each phase has its own `InputProcessor` and HUD overlay. A phase toolbar at the top shows the current phase with back/forward navigation. An undo/redo stack tracks all edit operations per phase.

---

## Phase 1: Hull Sculpt

### Camera

Orbital camera controller: right-mouse-drag to orbit, scroll to zoom, middle-mouse to pan. Ship centered at origin.

### Spine Control Points

- Red sphere gizmos rendered at each Bezier control point along the hull spine.
- Click to select — translation gizmo appears (3-axis arrows).
- Drag along spine axis to reposition the point longitudinally.
- Drag perpendicular to the spine to bend the hull curve.
- Insert new control points between existing ones (context menu or toolbar button).
- Delete control points (minimum 3, maximum 8 per size class).
- Hull mesh regenerates in real-time as control points are dragged.

### Cross-Section Editing

- Dashed yellow ellipse rings rendered at each cross-section position along the spine.
- Click a cross-section ring to open a 2D editor overlay.
- Superellipse parameters exposed: width, height, exponent (controls round vs. boxy).
- Four corner handles for asymmetric shaping.
- Cross-sections interpolate smoothly between neighbors.
- Add/remove cross-sections along the spine.
- The 2D overlay shows the cross-section shape updating live; the 3D hull updates on the ring as well.

### Appendage Hardpoints

- Wings, fins, and engine pods snap to parametric positions on the hull surface.
- Hardpoint markers rendered as colored diamonds on the hull.
- Click a hardpoint to open an appendage catalog (filtered by size class).
- Drag along the hull surface to reposition (snaps to discrete parametric intervals along the spine, e.g., 0.05 increments of spine-t).
- Mirror mode (on by default): symmetric placement for paired appendages.
- Appendage count limited by size class.

### Hull Stats Sidebar

Live-updating panel showing:
- Interior volume (m³) — determines room capacity
- Hull surface area — affects armor weight
- Estimated mass (empty hull)
- Drag coefficient — affects top speed
- Hardpoint count by type (weapon / utility / engine)
- Size class indicator with warnings if parameters drift out of bounds

### Mesh Regeneration Strategy

The existing `ShipHullGenerator` takes a `ShipBlueprint` and produces a `HullGeometry`. For real-time editing:

- **Throttled regen:** Rebuild mesh at most every 100ms during drag operations.
- **LOD preview:** Use reduced cross-section resolution during drags, full quality on mouse-up.
- **Incremental update:** Only rebuild affected spine segments when possible.
- **Collision shape:** Rebuilt on mouse-up only (convex hull computation is expensive).
- **Pipeline:** `ShipDesign` → `ShipBlueprint` → `ShipHullGenerator.generate()` → update `ModelInstance`.

---

## Phase 2: Room Layout

### Entry

When transitioning from Phase 1:

1. Hull mesh locks (translucent wireframe from inside).
2. Interior volume voxelized into 1m grid cells.
3. Temporary floor plane generated at the hull's lowest interior extent.
4. Player spawns at airlock position in first-person mode.
5. Grid overlay visible on floor/walls showing placeable cells.
6. Room palette HUD appears at bottom of screen.

### Ghost Volume Placement

- Select a room type from the palette (number keys 1–7 or click).
- A translucent ghost volume appears in front of the player, snapping to the 1m grid.
- WASD to walk, mouse to look — ghost follows the player's gaze, projected onto the grid.
- Scroll wheel rotates the room in 90° increments.
- Ghost color: green = valid placement, red = invalid placement.
- Left-click to stamp the room into place. Right-click to cancel.
- After placement, drag handles appear on edges/corners for resizing (snaps to grid, clamped to `RoomType` min/max sizes, re-validates on release).

### Validation Rules (Hard Constraints)

- Room must fit entirely within hull interior voxels.
- Cannot overlap any existing room.
- Must be corridor-reachable: an A* path must exist from the new room to at least one existing room. The first room placed is exempt (it becomes the root of the connectivity graph).
- Total room weight must stay within the hull's mass budget.
- Total power draw must stay within the reactor's capacity.
- Cockpit is required (cannot be deleted).
- Engine Room is required (cannot be deleted).

### Layout Bonuses (Soft Constraints)

Position-sensitive bonuses that reward thoughtful layout. Two types: adjacency bonuses (rooms near each other) and positional bonuses (room placed at a specific hull region):

| Type | Room | Condition | Bonus |
|------|------|-----------|-------|
| Adjacency | Medbay | Near Crew Quarters | +15% healing rate |
| Adjacency | Armory | Near Cargo Bay | +10% reload speed |
| Positional | Engine Room | At stern (high spine-t) | +5% fuel efficiency |
| Positional | Cockpit | At bow (low spine-t) | +10% sensor range |

Bonuses shown as floating icons above rooms during placement. A summary panel lists all active bonuses.

### Corridor Auto-Generation

- Corridors generated automatically via A* pathfinding (existing `ShipInteriorGenerator` logic).
- Preview corridors shown as dashed outlines during room placement.
- Corridors update live as rooms are added or removed.
- Player can lock corridor segments to prevent rerouting.
- Minimum 1m wide, expand to 2m if weight/volume budget allows.
- Doors auto-placed at room-corridor boundaries.

### Room Editing After Placement

- **Select:** Look at a placed room + left-click → selection highlight and resize handles appear.
- **Resize:** Drag corner/edge handles to grow or shrink (grid-snapped, re-validates on release).
- **Move:** Hold Shift + drag to relocate the entire room (ghost mode while dragging).
- **Delete:** Select + Delete key → room removed, corridors recalculate.
- **Info tooltip:** Hover over placed room to see weight, power draw, crew capacity, and active bonuses.

### Budget Sidebar

Always-visible panel showing:
- Power draw (current / max kW)
- Weight (current / max tonnes)
- Life support (current / max crew)
- Interior volume used (current / total m³)

Progress bars with color coding: green = safe, yellow = approaching limit, red = over budget.

---

## Phase 3: Module Fit

### Entry

When transitioning from Phase 2:

1. Room layout locks — rooms rendered as solid walls and floors (full interior mesh generated).
2. Corridors finalized with doors.
3. Exterior hardpoints activate — glowing markers appear on hull surface.
4. Player can freely switch between interior (first-person) and exterior (orbital cam) with the V key.
5. Module inventory panel accessible via Tab key.

### Hardpoint Types

Three hardpoint types, auto-placed by the hull generator based on hull shape and appendage placement:

| Type | Shape | Placement | Modules |
|------|-------|-----------|---------|
| Weapon | Red diamond | Wing tips, nose | Guns, missiles, turrets, point defense |
| Engine | Yellow circle | Stern, engine pods | Thrusters, afterburners, maneuvering jets |
| Utility | Cyan hexagon | Dorsal/ventral hull | Shields, scanners, ECM, tractor beams |

Each hardpoint has a size rating (S / M / L) determined by the appendage it's attached to. Modules must match or be smaller than the hardpoint size.

### Module Slotting Workflow

1. Click a hardpoint marker (exterior view) or walk up to a corresponding interior console (interior view).
2. Module browser panel opens (Scene2D overlay).
3. Panel has three tabs: **Owned Inventory** | **Station Shop** | **Blueprints**.
4. Each module entry shows: name, key stats, power draw, weight, cost.
5. Comparison view: current installed module vs. candidate, with delta indicators.
6. Greyed out if blueprint not unlocked or insufficient credits.
7. Click to install — the old module returns to player inventory.
8. Installed modules render visually on the hull mesh (weapon models on mounts, engine nozzles, etc.).

### Ship Performance Panel

Always-visible, toggleable sidebar updating live as modules are installed or removed:

- **Combat:** DPS, burst damage, weapon range, fire rate
- **Mobility:** Top speed, acceleration, turn rate, boost duration
- **Defense:** Shield HP, armor rating, hull HP, regen rate
- **Utility:** Sensor range, cargo capacity, crew count
- **Budget:** Remaining power, weight, credits

Delta indicators shown when previewing a module swap: green ▲+12% / red ▼-5%.

### Interior ↔ Exterior Toggle

- **V key:** Smooth camera transition between first-person interior and orbital exterior (uses existing `ShipCameraSystem` transition logic).
- **Interior view:** Walk through finished rooms, inspect internal consoles tied to hardpoints.
- **Exterior view:** Orbit camera around hull, interact with exterior hardpoint markers.
- Module browser works from both views.

---

## Data Model

### ShipDesign

The central mutable data object for the builder. Serialized to JSON for save/load.

```
ShipDesign
├── designId: String (UUID)
├── name: String
├── sizeClass: ShipSizeClass
├── hull: HullDesign
│   ├── spinePoints: List<Vector3>        // Bezier control points
│   ├── crossSections: List<CrossSectionDef>  // t, width, height, exponent
│   └── appendages: List<AppendageDef>    // type, spineT, side, scale
├── rooms: List<RoomDesign>
│   └── each: type, gridPos, size
├── modules: Map<String, ModuleAssignment>  // hardpointId → moduleId + blueprintId
└── metadata: DesignMetadata
    ├── createdAt, lastModified
    ├── buildCost
    ├── totalMass
    └── totalPowerDraw
```

### Key Operations

- `ShipDesign.toBlueprint()` → generates a `ShipBlueprint` compatible with the existing `ShipFactory` pipeline.
- `ShipDesign.validate()` → returns a list of validation errors (hard constraint violations).
- `ShipDesign.computeBonuses()` → returns active adjacency bonuses based on room positions.
- `ShipDesign.computeStats()` → returns aggregate ship stats (combat, mobility, defense, utility).
- `ShipDesign.computeCost()` → returns total build cost in credits.

### BuildOrder (In-Flight Planning)

```
BuildOrder
├── actions: List<BuildAction>
│   └── each: type (ADD_ROOM, REMOVE_ROOM, SWAP_MODULE, HULL_TWEAK), params, cost
├── totalCost: int
└── validationState: per-action valid/invalid flags
```

Saved to the player's `GameSession`. Applied atomically when docking at a shipyard.

---

## Blueprint & Economy System

### Blueprint Data

Defined in `data/shipbuilder/blueprints.json`. Each blueprint entry:

```json
{
  "blueprintId": "bp_medbay",
  "type": "ROOM",
  "unlocks": "MEDBAY",
  "rarity": "UNCOMMON",
  "shopPrice": 15000,
  "description": "Medical bay construction plans"
}
```

Types: `ROOM`, `MODULE`, `APPENDAGE`.

### Starting Blueprints

Players begin with: Cockpit, Engine Room, Crew Quarters, Cargo Bay, basic thruster, basic laser, basic wing.

### Acquisition Sources

- **Station shops:** Common and uncommon blueprints. Stock varies by faction.
- **Loot drops:** Rare blueprints from combat salvage and derelict wrecks.
- **Mission rewards:** Faction missions grant exclusive blueprints.
- **Research:** Future system — spend resources and time to develop new blueprints.

Blueprints are permanent unlocks that persist across ships.

### Build Costs

| Item | Cost Formula |
|------|-------------|
| Hull modification | Base cost per meter × size class multiplier |
| Room construction | Base cost per room type + per-m³ scaling |
| Module purchase | Module price (from station shop) |
| Module swap (from inventory) | Flat installation fee (500 cr) |
| Room removal | 70% refund of original construction cost |

Size class hull cost multipliers: Small = 1,000 cr/m, Medium = 2,500 cr/m, Large = 5,000 cr/m.

Room costs:

| Room Type | Base Cost | Per m³ |
|-----------|-----------|--------|
| Cockpit | 5,000 | 200 |
| Engine Room | 8,000 | 300 |
| Crew Quarters | 3,000 | 150 |
| Medbay | 6,000 | 250 |
| Armory | 7,000 | 280 |
| Cargo Bay | 2,000 | 100 |
| Brig | 4,000 | 200 |

---

## In-Flight Planning Mode

### Access

Tab → Ship → Plan Modifications. Opens a Scene2D overlay over the gameplay viewport.

### Layout

- **Left panel:** 2D top-down schematic of current ship. Shows hull outline, room blocks (color-coded by type), hardpoint markers, and queued changes (dashed outlines for additions, strikethrough for removals).
- **Right panel:** Build order queue. Each entry shows action type, target, cost. Running total at bottom with player credit balance. "Apply at next Shipyard dock" footer.

### Capabilities

- Queue room additions/removals on the 2D grid.
- Queue module swaps from owned inventory (no station shop access).
- Queue minor hull parameter tweaks (cross-section adjustments via sliders, not control point dragging).
- Reorder, remove, or edit queued actions.
- View current ship stats and projected stats after applying the queue.

### Limitations vs. Full Builder

- No hull control point dragging (sliders for cross-section params only).
- Room placement on 2D grid overlay (no first-person walk-through).
- Module swaps from inventory only.
- Cannot commit changes — queued for next dock.

---

## Integration with Existing Systems

### ShipFactory Pipeline

`ShipDesign` produces a `ShipBlueprint` that plugs into the existing generation pipeline:

```
ShipDesign.toBlueprint()
  → ShipBlueprint (seed + size class + hull params)
  → ShipHullGenerator.generate(blueprint)
  → HullGeometry (vertices, indices, hardpoints)
  → ShipInteriorGenerator.generate(blueprint, hullGeometry)
  → InteriorLayout (rooms, corridors, meshes)
  → ShipFactory.createShipEntity(hullGeometry, interiorLayout, modules)
  → Ashley Entity with all components
```

The key extension: `ShipBlueprint` currently derives hull params from a seed. `ShipDesign.toBlueprint()` overrides seed-derived params with the player's explicit control point and cross-section choices while preserving the same data structures.

### ECS Components

No new ECS components needed for the builder itself (it operates on `ShipDesign`, not entities). The existing ship components (`ShipDataComponent`, `ShipMeshComponent`, `ShipInteriorComponent`, `ShipFlightComponent`, hardpoint components) are populated by `ShipFactory` from the design output as they are today.

New components for the builder scene:

- `DrydockComponent` — marks the drydock station entity, holds available blueprints and shop inventory.

### Event Bus Integration

New events:

| Event | Published When |
|-------|---------------|
| `EnterDrydockEvent` | Player interacts with drydock terminal |
| `ExitDrydockEvent` | Player leaves the builder (commit or cancel) |
| `BuildPhaseChangedEvent` | Phase transition (1→2, 2→3, back) |
| `ShipDesignCommittedEvent` | Player commits the build — carries the final `ShipDesign` |
| `BuildOrderQueuedEvent` | In-flight: player adds an action to the build queue |
| `BuildOrderAppliedEvent` | Docking at shipyard: queued build order applied |

### Persistence

- `ShipDesign` serialized to JSON in the player's save file (within `GameSession`).
- `BuildOrder` (in-flight queue) also serialized in `GameSession`.
- Blueprint unlock state stored in player progression data.
- The save/load system (existing `SnapshotComponentRegistry`) handles ship entity reconstruction from `ShipDesign` on load.

---

## New Files

### Java Classes

```
core/src/main/java/com/galacticodyssey/shipbuilder/
  ShipDesign.java                    // Mutable design data object
  HullDesign.java                    // Hull spine + cross-sections + appendages
  RoomDesign.java                    // Single room placement definition
  ModuleAssignment.java              // Module → hardpoint binding
  DesignMetadata.java                // Cost, mass, power aggregates
  BuildOrder.java                    // In-flight modification queue
  BuildAction.java                   // Single queued modification
  ShipDesignValidator.java           // Hard constraint validation
  AdjacencyBonusCalculator.java      // Soft bonus computation
  ShipStatsCalculator.java           // Aggregate stat computation
  BuildCostCalculator.java           // Cost computation from design
  BlueprintRegistry.java             // Loaded blueprint definitions, unlock state

  DrydockScreen.java                 // Main builder screen (extends libGDX Screen)
  DrydockScene.java                  // 3D scene management (drydock environment, ship preview)
  BuilderPhase.java                  // Enum: HULL_SCULPT, ROOM_LAYOUT, MODULE_FIT
  BuilderPhaseController.java        // Phase state machine, transitions, validation gates

  phase1/
    HullSculptInputProcessor.java    // Orbital camera + gizmo interaction
    HullSculptHUD.java               // Stats sidebar, toolbar
    ControlPointGizmo.java           // 3D translation gizmo for spine points
    CrossSectionEditor.java          // 2D overlay for cross-section editing
    AppendageCatalog.java            // Appendage type browser

  phase2/
    RoomLayoutInputProcessor.java    // First-person movement + ghost placement
    RoomLayoutHUD.java               // Room palette, budget sidebar
    GhostVolume.java                 // Translucent room preview that follows gaze
    RoomGridRenderer.java            // Grid overlay on interior surfaces
    CorridorPreview.java             // A* corridor preview rendering

  phase3/
    ModuleFitInputProcessor.java     // Hardpoint click + interior/exterior toggle
    ModuleFitHUD.java                // Performance panel, module browser
    ModuleBrowserPanel.java          // Scene2D panel: inventory/shop/blueprints tabs
    HardpointMarkerRenderer.java     // Glowing hardpoint indicators on hull

  planning/
    PlanningOverlay.java             // In-flight Scene2D overlay
    ShipSchematicRenderer.java       // 2D top-down ship schematic
    BuildQueuePanel.java             // Build order list with costs
```

### Data Files

```
core/src/main/resources/data/shipbuilder/
  blueprints.json                    // Blueprint definitions (rooms, modules, appendages)
  build_costs.json                   // Room costs, hull cost multipliers, fees
  adjacency_bonuses.json             // Room adjacency bonus definitions
  module_catalog.json                // All installable modules with stats
  starting_blueprints.json           // Default unlocked blueprints for new players
```

---

## Scope Boundaries

### In Scope

- Drydock scene with three-phase builder
- Hull control point and cross-section editing with real-time mesh regen
- First-person 3D room placement with ghost volumes
- Hard constraint validation (hull fit, overlap, connectivity, weight, power)
- Soft adjacency bonuses
- Auto-corridor generation with A* pathfinding
- Hardpoint-based module slotting with module browser
- Blueprint unlock system with acquisition from shops/loot/missions
- Credit-based build costs with refund on removal
- In-flight planning mode with build order queue
- Integration with existing ShipFactory pipeline
- Save/load of ShipDesign and BuildOrder

### Out of Scope

- Interior furniture/prop placement within rooms (future feature)
- Ship painting/cosmetic customization (future feature)
- Multiplayer ship design sharing
- AI-assisted auto-layout suggestions
- Ship design templates / presets library
- Research tree for blueprint development
- Animated build sequences (ship construction montage)
