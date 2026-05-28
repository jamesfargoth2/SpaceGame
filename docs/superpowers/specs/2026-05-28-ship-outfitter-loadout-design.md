# Ship Outfitter / Loadout Screen Design

## Overview

A ship-centric outfitter screen where the player customizes their ship's weapons, internal modules, and (eventually) cosmetics. The screen centers on a top-down ship silhouette with clickable hardpoints and module slots, flanked by an inventory panel and a stat comparison panel, with power/mass budget gauges along the bottom.

**Access modes:**
- **Station mode** — full access: browse station stock, buy/sell, install/uninstall
- **Field mode** — rearrange from cargo only, no buying or selling

## Data Model

### ShipModuleData

Module definitions loaded from JSON (`data/modules/ship_modules.json`). Each module has:

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique identifier (e.g., `shield_gen_mk1`) |
| `name` | String | Display name |
| `description` | String | Flavor/functional description |
| `category` | ShipModuleCategory | `REACTOR`, `ENGINE`, `SHIELD_GENERATOR`, `ARMOR_PLATING`, `CARGO_EXPANDER`, `SCANNER`, `ECM`, `REPAIR_DRONE`, `MINING_LASER`, `TRACTOR_BEAM` |
| `size` | HardpointSize | `SMALL`, `MEDIUM`, `LARGE`, `CAPITAL` — reuses existing enum |
| `powerDraw` | float | MW consumed. Negative for reactors (which generate power) |
| `mass` | float | Tonnes added to ship |
| `stats` | Map<String, Float> | Arbitrary stat map (e.g., `shieldHp: 200`, `thrustMultiplier: 1.2`) |
| `qualityTier` | QualityTier | Reuses existing enum: SALVAGED through PRECURSOR |
| `price` | int | Base credit cost |

### ShipModuleCategory

New enum:
- `REACTOR` — power generation
- `ENGINE` — thrust, maneuverability, max speed
- `SHIELD_GENERATOR` — shield HP, recharge rate
- `ARMOR_PLATING` — hull defense, damage resistance
- `CARGO_EXPANDER` — additional cargo capacity
- `SCANNER` — detection range, scan resolution
- `ECM` — electronic countermeasures, signature reduction
- `REPAIR_DRONE` — passive hull repair
- `MINING_LASER` — resource extraction
- `TRACTOR_BEAM` — object manipulation, salvage

### ShipModuleSlot

A slot on a specific ship, analogous to `Hardpoint` for weapons:

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique slot identifier (e.g., `reactor_0`, `internal_2`) |
| `slotType` | ModuleSlotType | `REACTOR`, `ENGINE`, `INTERNAL` |
| `size` | HardpointSize | Max module size this slot accepts |
| `position` | Vector2 | 2D position for silhouette rendering |
| `installedModule` | ShipModuleData | Currently installed module (nullable for INTERNAL) |
| `mandatory` | boolean | If true, cannot uninstall without a replacement |

**ModuleSlotType** enum:
- `REACTOR` — one per ship, mandatory, only accepts REACTOR category modules
- `ENGINE` — one per ship, mandatory, only accepts ENGINE category modules
- `INTERNAL` — flexible, accepts any non-REACTOR/ENGINE category module

### ShipLoadoutComponent

New ECS component on ship entities, alongside the existing `ShipHardpointComponent`:

| Field | Type | Description |
|-------|------|-------------|
| `moduleSlots` | List<ShipModuleSlot> | All module slots for this ship |

Computed methods:
- `getTotalPowerDraw()` — sum of all installed module powerDraw values (positive only)
- `getTotalPowerGeneration()` — absolute value of reactor's powerDraw (negative = generation)
- `getTotalModuleMass()` — sum of all installed module mass values
- `getAvailablePower()` — generation minus draw
- `getSlot(id)` — lookup by slot id
- `getSlotsOfType(type)` — filter by slot type

### ShipModuleRegistry

Mirrors `ShipWeaponRegistry`. Loads from `data/modules/ship_modules.json` at startup.

Methods:
- `getModule(id)` — get module definition by id
- `createModuleInstance(id)` — create a copy for installation
- `getModulesByCategory(category)` — filter by category
- `getModulesForSize(size)` — all modules that fit a given size (equal or smaller)
- `registerModule(data)` — runtime registration

### Data Files

**`data/modules/ship_modules.json`** — all module definitions.

**`data/modules/ship_module_slots.json`** — per-ship-class slot layouts, keyed by ship class id:
```
{
  "corvette_scout": {
    "maxMass": 30.0,
    "silhouettePoints": [[100,10], [130,60], ...],
    "moduleSlots": [
      { "id": "reactor_0", "slotType": "REACTOR", "size": "SMALL", "position": [100, 140], "mandatory": true },
      { "id": "engine_0", "slotType": "ENGINE", "size": "SMALL", "position": [100, 270], "mandatory": true },
      { "id": "internal_0", "slotType": "INTERNAL", "size": "SMALL", "position": [100, 175] },
      { "id": "internal_1", "slotType": "INTERNAL", "size": "SMALL", "position": [100, 210] },
      { "id": "internal_2", "slotType": "INTERNAL", "size": "SMALL", "position": [100, 245] }
    ]
  }
}
```

Ship class data also defines `maxMass` — the hull's maximum total loadout mass (weapons + modules). The `silhouettePoints` array defines the 2D hull outline polygon for rendering.

Existing `data/weapons/ship_hardpoints.json` continues to define weapon hardpoints — no changes needed.

## Screen Architecture

### OutfitterScreen

Full-screen overlay rendered on top of `GameScreen`, using its own `Stage` with a `FitViewport`. Game simulation pauses while open.

**Mode:** `STATION` or `FIELD`, set at construction based on whether the player is docked.

**Child panels:**
- `OutfitterInventoryPanel` (left, 220px)
- `ShipSilhouettePanel` (center, flex)
- `OutfitterDetailPanel` (right, 240px)
- `OutfitterBudgetBar` (bottom strip)
- Top bar with category tabs and ship name

**Selection state (owned by OutfitterScreen):**
- `selectedSlotId` — which hardpoint or module slot is currently selected
- `candidateItem` — which inventory/stock item is being previewed for comparison
- `activeTab` — WEAPONS, MODULES, or COSMETICS

### ShipSilhouettePanel

Center panel rendering the top-down ship view.

- Renders hull outline from `silhouettePoints` using `ShapeRenderer`
- Hardpoint slots rendered as circles (from `ShipHardpointComponent` data, positions projected from 3D → 2D)
- Module slots rendered as rounded rectangles (from `ShipLoadoutComponent` data)
- Each slot is a Scene2D `Actor` overlaid on the shape rendering for click handling
- Mandatory slots (reactor, engine) have solid borders; optional slots have dashed borders
- Selected slot pulses with a highlight color
- Category tabs filter visibility: Weapons tab shows hardpoints only, Modules tab shows module slots only
- Color coding: weapon slots amber (#f59e0b), core modules cyan (#22d3ee), optional slots grey (#475569)
- Scales the silhouette to fit the center panel regardless of ship size class

### OutfitterInventoryPanel

Left panel with available items.

**Sub-tabs:**
- "Cargo" — items from the player's ship cargo (always visible)
- "Station Stock" — items the docked station sells (hidden in FIELD mode)

**Filtering:**
- Auto-filters to items compatible with the selected slot: matching size (equal or smaller), matching type (weapons for hardpoints, correct category for mandatory module slots, any non-reactor/engine for internal slots)
- Text search field at top for manual filtering by name
- Items that would exceed power or mass budget shown with a warning icon but still selectable (for comparison)

**Item rows:**
- Name colored by quality tier (reuses `ItemDetailPanel.getQualityColor()`)
- Category tag and primary stat value
- Station stock items show price
- Click to select as comparison candidate
- Double-click to install directly (with validation)

**Scrollable** via Scene2D `ScrollPane`.

### OutfitterDetailPanel

Right panel showing slot info and stat comparison.

**Slot header:** Slot name, type badge, size badge.

**Currently Installed section:**
- Module/weapon name (quality-colored)
- Full stat breakdown
- Power draw and mass values

**Comparing To section** (visible when a candidate is selected):
- Candidate name (quality-colored)
- Same stat breakdown with green (▲ better) / red (▼ worse) delta indicators
- Power draw and mass deltas

**Action buttons:**
- "Install" — install candidate from cargo (both modes)
- "Buy & Install" — purchase from station stock and install (station mode only)
- "Uninstall" — remove current module/weapon to cargo
- "Sell" — uninstall and sell for credits (station mode only)
- Buttons disabled with tooltip when validation fails (e.g., over budget, no cargo space)

### OutfitterBudgetBar

Bottom strip with live resource gauges.

**Power gauge:**
- Current draw / max generation from reactor
- Updates in real-time when hovering a candidate (shows projected value)
- Color: green (<60%), yellow (60-85%), red (85%+), flashing red (overbudget — blocks install)

**Mass gauge:**
- Current total mass / ship's maxMass
- Same color thresholds and hover preview behavior

**Credits display** (station mode only): current balance.

## Interaction Model

### Installing

1. Click a slot on the ship silhouette to select it
2. Left panel filters to compatible items
3. Click an item in the left panel to preview (comparison appears on right, budget bars show projected values)
4. Click "Install" / "Buy & Install" to confirm
5. Old item in slot (if any) returns to cargo
6. Validation must pass: size fits, power budget not exceeded, mass not exceeded, cargo has space for displaced item

**Special case — reactor swap:** When replacing a reactor, validate that the new reactor's output still covers existing power draw from all other modules. If not, block with a message explaining which modules would need to be removed first.

### Uninstalling

1. Click a slot, then click "Uninstall" on the right panel (or right-click the slot)
2. Item moves to cargo
3. Blocked if: mandatory slot with no replacement, removing reactor would leave negative power budget, no cargo space

### Drag and Drop

- Drag from left panel → slot on silhouette: install
- Drag from slot → left panel: uninstall to cargo
- Drag between two compatible slots: swap installed items
- Invalid drops show a red X and snap back

### Field Mode Restrictions

- No "Station Stock" sub-tab
- No "Buy & Install" or "Sell" buttons
- Only cargo items available for installation
- All other interactions work identically

### Keyboard Shortcuts

- `ESC` — close outfitter
- `1` / `2` / `3` — switch to Weapons / Modules / Cosmetics tab
- `Tab` — toggle Cargo / Station Stock sub-tab (station mode)

## Events

| Event | Fired When | Consumed By |
|-------|-----------|-------------|
| `OutfitterOpenedEvent` | Player opens outfitter | Input routing, game pause |
| `OutfitterClosedEvent` | Player closes outfitter | Input routing, game unpause |
| `SlotSelectedEvent` | Slot clicked on silhouette | Inventory panel (filter), detail panel (populate) |
| `ModuleInstalledEvent` | Module placed in slot | OutfitterSystem (recalc stats), budget bar (update) |
| `ModuleUninstalledEvent` | Module removed from slot | OutfitterSystem (recalc stats), budget bar (update) |
| `WeaponInstalledEvent` | Weapon placed in hardpoint | OutfitterSystem (recalc stats), budget bar (update) |
| `WeaponUninstalledEvent` | Weapon removed from hardpoint | OutfitterSystem (recalc stats), budget bar (update) |

## OutfitterSystem

ECS system that listens to install/uninstall events and recalculates ship stats:

- **Power budget:** Sum all module powerDraw, compare against reactor output
- **Mass:** Sum all module + weapon mass, store on `ShipDataComponent.mass` (affects acceleration = thrust / mass)
- **Engine stats:** Apply engine module's `thrustMultiplier`, `turnRateMultiplier`, `maxSpeedBonus` to `ShipDataComponent`
- **Shield stats:** Populate shield HP/recharge from shield generator module (or zero if none installed)
- **Armor stats:** Apply armor plating resistances to ship's damage resistance map

This system runs the same recalculation whether changes come from the outfitter UI or from loading a save file.

## Ship Silhouette Rendering

Each ship class defines a `silhouettePoints` polygon in the module slots JSON. The `ShipSilhouettePanel` renders this as:

1. A filled dark polygon for the hull body
2. A stroked outline in blue (#3b82f6)
3. Subtle grid lines in the background for a sci-fi aesthetic
4. Engine glow ellipses at the aft
5. Slot actors positioned at their defined coordinates, scaled to the panel size

For the initial implementation, silhouettes are hand-authored per ship class in the JSON data. This is simple and gives full art control. Procedural silhouette generation from the 3D hull mesh is a potential future enhancement.

## Cosmetics Tab (Stub)

The "Cosmetics" tab appears in the top bar and is clickable. When selected:
- Center panel shows a centered message: "Cosmetic customization coming soon"
- Left and right panels clear
- Budget bar remains visible but inactive

No data model, rendering, or interaction logic for cosmetics in this pass.

## Persistence

The `ShipLoadoutComponent` integrates with the existing `SnapshotComponentRegistry` for save/load. Module slot state (which module is installed in each slot) serializes as a map of slot id → module id. On load, the `OutfitterSystem` runs a full stat recalculation.

## Ship Weapons and Modules as Cargo

The existing `Item` hierarchy covers personal equipment. Ship weapons (`ShipWeaponData`) and modules (`ShipModuleData`) are a separate data domain. Rather than merging them into the personal inventory system, ship-level equipment uses its own cargo concept:

**ShipCargoComponent** — new ECS component on ship entities:
- `List<ShipWeaponData> storedWeapons` — uninstalled ship weapons in cargo
- `List<ShipModuleData> storedModules` — uninstalled ship modules in cargo
- Weight of stored ship equipment counts against the ship's mass budget

This keeps ship equipment separate from the player's personal grid inventory (which holds personal weapons, armor, consumables). The outfitter's left panel reads from `ShipCargoComponent` (Cargo tab) and `ShipModuleRegistry` / `ShipWeaponRegistry` (Station Stock tab).

When uninstalling, items go to `ShipCargoComponent`. When selling, items are removed from `ShipCargoComponent` and credits are added.

## Weapon Power Draw

The existing `ShipWeaponData.energyCost` is per-shot energy, not continuous draw. For the power budget, add a new `powerDraw` field to `ShipWeaponData`:

- `powerDraw` (float) — continuous MW draw for the weapon's tracking, cooling, and standby systems
- This is separate from `energyCost` (per-shot draw from the ship's energy capacitor)
- The outfitter power budget sums continuous `powerDraw` from all installed weapons AND modules

Existing weapons in `ship_weapons.json` will need this field added. Smaller weapons draw less idle power; capital weapons draw significantly more.

## Opening the Outfitter

- **Station mode:** Player interacts with a station outfitting terminal (interaction prompt, like existing station services). This opens the outfitter in STATION mode.
- **Field mode:** Player interacts with the ship's engineering console in the ship interior (a station inside the ship, near the engineering room). Opens the outfitter in FIELD mode.
- Both use the existing `InteractionSystem` prompt pattern — no global keybind needed.

## Integration Points

- **ShipFactory** — when creating a ship, populate `ShipLoadoutComponent` with default modules per ship class (defined in slot layout JSON as `defaultModuleId` per slot), and initialize `ShipCargoComponent`
- **GameScreen** — outfitter opens via interaction events, not a global keybind
- **InteractionSystem** — station terminal and ship engineering console trigger `OutfitterOpenedEvent` with the appropriate mode
- **PlayerInputSystem** — route input to outfitter stage when open
- **CombatInputSystem** — respect outfitter-open state (block combat input)
- **SnapshotComponentRegistry** — register `ShipLoadoutComponent` and `ShipCargoComponent` for persistence
