# Inventory / Equipment Screen Design

**Date:** 2026-05-28
**Status:** Approved

## Overview

A real-time overlay toggled by TAB that lets the player manage their inventory and equipped gear while the game world continues running. Center-focused layout with equipment slots arranged around a character silhouette, inventory grid panels on both sides, and an item detail panel at the bottom. Supports drag-and-drop and right-click-to-equip.

## Layout

```
┌─────────────────────────────────────────────────────────┐
│  [INVENTORY]                         Weight: 42/100 kg  │
│                                                         │
│  ┌──────────┐   ┌──────────────────┐   ┌──────────────┐│
│  │          │   │    [HELMET]      │   │              ││
│  │ INVENTORY│   │  [CHEST] [UTIL1] │   │  INVENTORY   ││
│  │  GRID    │   │                  │   │   GRID       ││
│  │  (left)  │   │  [PRIMARY][SEC]  │   │   (right)    ││
│  │  5 cols  │   │  [MELEE] [UTIL2] │   │   5 cols     ││
│  │          │   │  [LEGS]          │   │              ││
│  │          │   │  [BOOTS]         │   │              ││
│  └──────────┘   └──────────────────┘   └──────────────┘│
│                                                         │
│  ┌─────────────────────────────────────────────────────┐│
│  │ Item Name (Rare)           ATK: +15  DEF: +8       ││
│  │ Description text here...   Weight: 2.5 kg           ││
│  └─────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────┘
```

- **Center panel**: Equipment slots in a body-shape arrangement — helmet at top, chest in the middle, legs/boots at the bottom, weapon slots on the sides, utility slots flanking the chest.
- **Left & right panels**: One continuous inventory grid rendered across two panels. The left panel shows columns 0–4, the right panel shows columns 5–9 (for a default 10-column grid). Items can span the boundary between panels. Vertically scrollable if items exceed visible rows.
- **Bottom panel**: Item detail tooltip showing name, quality-colored border, stat summary, description, and weight. Updates on hover or selection.
- **Top bar**: "INVENTORY" title on the left, weight indicator (current/max) on the right with color shift (white → yellow → red as capacity fills).

## Input Handling

### Keybindings

| Key | Action |
|-----|--------|
| TAB | Toggle inventory open/closed |
| ESC | Close inventory (if open) |
| Right-click | Auto-equip item to matching slot, or unequip back to first available grid space |
| Left-click + drag | Move items between grid cells or to/from equipment slots |
| Mouse hover | Show item details in bottom panel |

### TAB Rebind

TAB is currently bound to "next target" in `PlayerInputSystem`. This binding moves to the T key, which becomes dual-purpose:
- **Tap T**: Cycle to next target (previously TAB)
- **Hold T**: Target lock (existing behavior)

### Input Multiplexer Integration

When inventory opens:
1. Inventory Stage is pushed to the top of the `InputMultiplexer`
2. `PlayerInputSystem` is disabled via `setEnabled(false)` — player cannot move or shoot
3. Game simulation keeps running (enemies move, projectiles fly, timers tick)
4. `InventoryOpenedEvent` published on EventBus

When inventory closes:
1. Inventory Stage is removed from the `InputMultiplexer`
2. `PlayerInputSystem` re-enabled
3. `InventoryClosedEvent` published on EventBus

## Architecture

### New Classes

#### `InventoryScreenSystem` (Ashley EntitySystem)
- Location: `core/src/main/java/com/galacticodyssey/ui/systems/InventoryScreenSystem.java`
- Owns a Scene2D `Stage` with `FitViewport`
- Manages open/close state, renders the semi-transparent backdrop + UI
- Queries the player entity for `InventoryComponent` and `EquipmentSlotsComponent`
- Subscribes to `ItemAddedEvent`, `ItemRemovedEvent`, `EquipmentChangedEvent` to refresh display
- Uses Scene2D `DragAndDrop` for item movement
- `update(float delta)` calls `stage.act()` and `stage.draw()` when open

#### `InventoryGridActor` (Scene2D Group)
- Location: `core/src/main/java/com/galacticodyssey/ui/actors/InventoryGridActor.java`
- Renders a grid of cells based on `InventoryComponent` dimensions
- Each cell is a clickable/draggable actor
- Items that span multiple cells (e.g., 2x3 rifle) render across those cells
- Cells occupied by a multi-cell item but not the origin show as "blocked"
- Scrollable via Scene2D `ScrollPane` if grid exceeds visible height

#### `EquipmentSlotsActor` (Scene2D Group)
- Location: `core/src/main/java/com/galacticodyssey/ui/actors/EquipmentSlotsActor.java`
- Renders the 9 equipment slots in a body-shape layout using absolute positioning within the group
- Each slot shows: slot icon (empty state), equipped item icon (filled state), slot type label
- Slots validate item compatibility on drop (e.g., only ArmorItem with region HELMET goes in HELMET slot)

#### `ItemDetailPanel` (Scene2D Table)
- Location: `core/src/main/java/com/galacticodyssey/ui/actors/ItemDetailPanel.java`
- Displays: item name (colored by quality), item type, stat lines, description, weight
- Updated via `showItem(Item item)` / `clear()` methods
- Quality colors: Common (white), Uncommon (green), Rare (blue), Exotic (purple), Alien (orange)

#### `DraggedItemActor` (Scene2D Image)
- Location: `core/src/main/java/com/galacticodyssey/ui/actors/DraggedItemActor.java`
- Floating actor that follows the cursor during drag
- Shows the item's icon at 80% opacity
- Snaps back to origin if the drop is invalid

#### Events
- `InventoryOpenedEvent` — published when inventory opens; other systems react (disable input, hide crosshair HUD elements)
- `InventoryClosedEvent` — published when inventory closes; systems restore normal state

### Integration Points

| System | Integration |
|--------|------------|
| `GameScreen` | Adds/removes inventory Stage in InputMultiplexer; calls `inventoryScreenSystem.render()` after world render |
| `PlayerInputSystem` | Disabled while inventory is open; TAB handler delegates to GameScreen toggle; T key absorbs old TAB behavior |
| `EquipmentSystem` | Called for equip/unequip operations — the UI never modifies components directly |
| `CockpitHUDSystem` | Subscribes to InventoryOpenedEvent/ClosedEvent to hide/show crosshair |
| `EventBus` | Routes all inventory-related events |

### Drag-and-Drop

Uses Scene2D's `DragAndDrop` utility class:

**Sources:** Every occupied grid cell and every filled equipment slot.

**Targets:** Every grid cell and every equipment slot.

**Validation on drop:**
- Grid → Grid: Check if destination cells are free (accounting for multi-cell items). If occupied by a single other item, swap positions.
- Grid → Equipment slot: Check item type matches slot type (e.g., `ArmorItem` with `HitRegion.HEAD` → `HELMET` slot). Check slot is empty or swap with currently equipped item.
- Equipment slot → Grid: Check grid has space for the item's dimensions. Find first available position if dropping on an occupied cell.
- Any → Any: Check weight limit won't be exceeded (equipping doesn't change weight, but swaps might).

**Visual feedback:**
- Valid drop target: green-tinted highlight on the target cell(s)
- Invalid drop target: red-tinted highlight
- Dragging: source cell(s) show a dimmed version of the item

### Weight System

- Weight bar in the top-right reflects `InventoryComponent.getCurrentWeight()` / `maxWeight`
- Bar color: white (0–60%), yellow (60–85%), red (85–100%), pulsing red (over 100% if over-encumbered is allowed)
- Over-encumbered state: published via event, consumed by `PlayerMovementSystem` to reduce move speed

## Rendering

- Semi-transparent black backdrop (alpha ~0.6) drawn behind the UI to dim the game world
- The game world continues rendering normally underneath
- Inventory Stage renders on top with its own viewport (`FitViewport` sized to reference resolution)
- Item icons use `TextureRegion` from a shared item atlas
- Quality tier is indicated by a colored border around the item icon in both grid and equipment slots

## Existing Systems Reused

- `InventoryComponent` — grid storage, weight tracking, item placement (already implemented)
- `EquipmentSlotsComponent` — slot-based equipped items (already implemented)
- `EquipmentSystem` — equip/unequip logic with event publishing (already implemented)
- `Item` hierarchy — all item types with icons, stats, grid dimensions (already implemented)
- `EventBus` — pub/sub for InventoryOpened/Closed and equipment change events
- Snapshot persistence — both components already support `takeSnapshot()` / `restoreFromSnapshot()`

## File Locations

| File | Path |
|------|------|
| InventoryScreenSystem | `core/src/main/java/com/galacticodyssey/ui/systems/InventoryScreenSystem.java` |
| InventoryGridActor | `core/src/main/java/com/galacticodyssey/ui/actors/InventoryGridActor.java` |
| EquipmentSlotsActor | `core/src/main/java/com/galacticodyssey/ui/actors/EquipmentSlotsActor.java` |
| ItemDetailPanel | `core/src/main/java/com/galacticodyssey/ui/actors/ItemDetailPanel.java` |
| DraggedItemActor | `core/src/main/java/com/galacticodyssey/ui/actors/DraggedItemActor.java` |
| InventoryOpenedEvent | `core/src/main/java/com/galacticodyssey/ui/events/InventoryOpenedEvent.java` |
| InventoryClosedEvent | `core/src/main/java/com/galacticodyssey/ui/events/InventoryClosedEvent.java` |
