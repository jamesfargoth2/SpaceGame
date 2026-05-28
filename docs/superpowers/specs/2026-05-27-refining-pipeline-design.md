# Refining Pipeline Design Spec

## Overview

A three-tier material conversion system where raw gathered resources are transformed through processing and refinement stages into crafting-ready materials. Refining happens at ship-mounted refinery modules (tiered) and station refineries, with time-based job queuing and Engineering skill yield bonuses.

This spec covers the backend pipeline only: components, systems, events, data files, and tests. UI is out of scope — it will be a separate spec that consumes this system through events.

---

## Material Model

### Three-Tier Hierarchy

```
Tier 1: Raw Materials       (gathered: mining, salvaging, harvesting, looting, trading)
  ↓  [Processing — Refinery Tier 1+]
Tier 2: Processed Materials  (intermediates: concentrates, purified compounds)
  ↓  [Refinement — Refinery Tier 2+]
Tier 3: Refined Materials    (crafting-ready: ingots, alloys, composites)
```

### Usage Rules

- **Raw materials (Tier 1)**: usable in basic/crude recipes only (field repairs, primitive tools, emergency patches). Cannot be used in advanced crafting.
- **Processed materials (Tier 2)**: usable in mid-tier crafting recipes. Required minimum for most standard equipment.
- **Refined materials (Tier 3)**: required for advanced recipes (weapons, ship modules, high-tier gear).
- **Alloys**: Tier 3 recipes that combine Tier 2+ inputs (at least one Tier 3 required) into a composite material (e.g., Steel Alloy = Iron Ingot + Carbon Powder). Output is Tier 3.

### MaterialItem

New item type extending the existing `Item` abstract class:

```java
public class MaterialItem extends Item {
    private String materialId;
    private MaterialTier tier;       // RAW, PROCESSED, REFINED
    private MaterialCategory category; // METAL, MINERAL, ORGANIC, CHEMICAL, EXOTIC, ALIEN
    private String commodityLink;    // optional — maps to CommodityDefinition for market buy/sell
}
```

- `commodityLink` bridges the economy system: buying "Iron Ore" at a market creates a `MaterialItem` in inventory; selling a `MaterialItem` with a `commodityLink` converts it back to a commodity transaction.
- `MaterialItem` instances are stackable (use existing `maxStack` from `Item`).

### MaterialTier Enum

```java
public enum MaterialTier {
    RAW(1), PROCESSED(2), REFINED(3);
    public final int level;
}
```

### MaterialCategory Enum

```java
public enum MaterialCategory {
    METAL, MINERAL, ORGANIC, CHEMICAL, EXOTIC, ALIEN
}
```

---

## Refinery Types

### Ship Refinery Module

Installed as a ship component. Tiered to gate recipe access:

| Module | Tier | Recipes | Queue Depth | Speed | Power Draw | Weight | Cost |
|--------|------|---------|-------------|-------|------------|--------|------|
| Basic Ship Refinery | 1 | Processing only (Raw → Processed) | 2 | 1.0x | 10 | 500 | 5,000 |
| Advanced Ship Refinery | 2 | Processing + Refinement | 4 | 1.5x | 20 | 800 | 25,000 |
| Industrial Ship Refinery | 3 | All recipes + Alloys | 6 | 2.0x | 35 | 1,200 | 100,000 |

- Consumes reactor power while active — competes with shields, weapons, engines.
- If insufficient power, active job pauses (no progress) until power is available.

### Station Refinery

Found at industrial stations:

- Always Tier 3 capable (all recipes).
- Queue depth: 10 slots.
- Speed multiplier: 2.5x (faster than any ship module).
- Charges a credit fee per job (influenced by local economy pricing).
- Available recipes may vary by station faction and economy type.

---

## Refining Recipe System

### Recipe Structure

```json
{
  "recipeId": "process_iron_ore",
  "name": "Process Iron Ore",
  "category": "PROCESSING",
  "requiredTier": 1,
  "inputs": [
    { "materialId": "iron_ore", "quantity": 5 }
  ],
  "outputs": [
    { "materialId": "iron_concentrate", "baseQuantity": 3 }
  ],
  "processingTime": 30.0,
  "powerCost": 10
}
```

| Field | Type | Description |
|-------|------|-------------|
| `recipeId` | String | Unique identifier |
| `name` | String | Display name |
| `category` | Enum | PROCESSING, REFINEMENT, or ALLOY |
| `requiredTier` | int | Minimum refinery tier needed (1, 2, or 3) |
| `inputs` | List | Required materials with quantities |
| `outputs` | List | Produced materials with base quantities |
| `processingTime` | float | Base seconds to complete |
| `powerCost` | float | Reactor power draw per second while active |

### Recipe Categories

- **PROCESSING** (Tier 1): Raw → Processed. Simple extraction/purification.
- **REFINEMENT** (Tier 2): Processed → Refined. Smelting, distillation, synthesis.
- **ALLOY** (Tier 3): Multiple Refined → Alloy. Combining refined materials into composites.

### RefiningRecipeRegistry

Singleton registry loaded from `refining_recipes.json` at startup:

```java
public class RefiningRecipeRegistry {
    Map<String, RefiningRecipe> recipesById;
    Map<Integer, List<RefiningRecipe>> recipesByTier;
    Map<String, List<RefiningRecipe>> recipesByOutput;

    public RefiningRecipe getRecipe(String recipeId);
    public List<RefiningRecipe> getRecipesForTier(int tier);
    public List<RefiningRecipe> getRecipesProducing(String materialId);
    public boolean validate(); // cross-references MaterialRegistry
}
```

---

## Job Queue System

### RefiningJob

Plain object (not an ECS component) representing a single refining task:

```java
public class RefiningJob {
    private String jobId;          // UUID
    private String recipeId;
    private RefiningJobState state; // QUEUED, ACTIVE, COMPLETE, CANCELLED
    private float progress;        // 0.0 to 1.0
    private float totalTime;       // seconds (processingTime / speedMultiplier)
    private Map<String, Integer> inputsConsumed; // snapshot of consumed materials
    private List<RecipeOutput> outputs; // materialId + quantity, after skill bonus
}
```

### Queue Behavior

- One job active at a time; remaining jobs wait in FIFO order.
- When active job completes: output materials created, next queued job becomes active.
- **Cancellation**: queued jobs return 100% of inputs. Active jobs return inputs proportional to remaining progress (e.g., 60% complete → 40% of inputs returned, rounded down).
- Queue persists across save/load with full progress state.

### RefiningJobState Enum

```java
public enum RefiningJobState {
    QUEUED, ACTIVE, PAUSED, COMPLETE, CANCELLED
}
```

`PAUSED` occurs when reactor power is insufficient. Resumes automatically when power is available.

---

## ECS Architecture

### New Components

#### RefineryComponent

```java
public class RefineryComponent implements Component {
    private int tier;
    private int maxQueueSize;
    private float speedMultiplier;
    private float powerCostPerSecond;
    private List<RefiningJob> jobQueue;

    public RefiningJob getActiveJob();
    public boolean isQueueFull();
    public boolean canProcessRecipe(RefiningRecipe recipe);
}
```

Attached to any entity that can refine (ship with refinery module, station entity).

#### MaterialStorageComponent

```java
public class MaterialStorageComponent implements Component {
    private Map<String, List<MaterialItem>> materialsByType;
    private float maxWeight;
    private float maxVolume;
    private float currentWeight;
    private float currentVolume;

    public int getQuantity(String materialId);
    public boolean hasEnough(String materialId, int quantity);
    public boolean tryConsume(String materialId, int quantity);
    public boolean tryAdd(MaterialItem item);
    public List<MaterialItem> getAllMaterials();
}
```

Dedicated material storage separate from the general `InventoryComponent`. Materials are consumed in bulk by recipes and queried by materialId frequently — a dedicated lookup is faster than scanning the grid inventory.

### New Systems

#### RefiningSystem

Ashley `IteratingSystem` processing entities with `RefineryComponent`:

```
processEntity(entity, deltaTime):
  refinery = entity.getComponent(RefineryComponent)
  activeJob = refinery.getActiveJob()
  if activeJob == null: return

  // Check power availability
  reactor = entity.getComponent(ReactorComponent)
  if reactor != null && reactor.getAvailablePower() < refinery.powerCostPerSecond:
    activeJob.state = PAUSED
    return

  // Consume power
  if reactor != null:
    reactor.consumePower(refinery.powerCostPerSecond * deltaTime)

  // Advance progress
  activeJob.state = ACTIVE
  activeJob.progress += (deltaTime * refinery.speedMultiplier) / activeJob.totalTime

  if activeJob.progress >= 1.0:
    completeJob(entity, refinery, activeJob)
```

On completion:
1. Create output `MaterialItem` instances with quantity = `outputQuantity`
2. Add to entity's `MaterialStorageComponent`
3. Fire `RefiningCompletedEvent`
4. Remove job from queue
5. Activate next queued job (if any)

Priority: runs after physics systems, before UI update systems.

#### RefiningRequestHandler

Listens for `RefiningRequestEvent` on the event bus:

```
handleRequest(event):
  entity = getEntity(event.entityId)
  refinery = entity.getComponent(RefineryComponent)
  storage = entity.getComponent(MaterialStorageComponent)
  recipe = recipeRegistry.getRecipe(event.recipeId)

  // Validation
  if refinery == null: fail(NO_REFINERY)
  if refinery.tier < recipe.requiredTier: fail(TIER_TOO_LOW)
  if refinery.isQueueFull(): fail(QUEUE_FULL)
  if !storage.hasEnough(recipe inputs): fail(INSUFFICIENT_MATERIALS)

  // Execute
  consumeInputs(storage, recipe.inputs)
  job = createJob(recipe, calculateYield(recipe, skillProvider))
  refinery.jobQueue.add(job)
  fire(RefiningStartedEvent(entity, job))
  fire(RefiningQueueChangedEvent(entity, refinery.jobQueue))
```

### New Events

| Event | Payload | Fired When |
|-------|---------|------------|
| `RefiningRequestEvent` | entityId, recipeId, quantity | Player submits a refine job |
| `RefiningStartedEvent` | entityId, RefiningJob | Job begins processing or is queued |
| `RefiningCompletedEvent` | entityId, RefiningJob, outputItems | Job finishes successfully |
| `RefiningFailedEvent` | entityId, RefiningFailureReason | Validation fails |
| `RefiningCancelledEvent` | entityId, RefiningJob, returnedInputs | Player cancels a job |
| `RefiningQueueChangedEvent` | entityId, queue snapshot | Queue add/remove/reorder |
| `RefiningPausedEvent` | entityId, RefiningJob | Power insufficient, job paused |
| `RefiningResumedEvent` | entityId, RefiningJob | Power restored, job resumed |

### RefiningFailureReason Enum

```java
public enum RefiningFailureReason {
    NO_REFINERY,
    TIER_TOO_LOW,
    QUEUE_FULL,
    INSUFFICIENT_MATERIALS,
    RECIPE_NOT_FOUND
}
```

---

## Skill Integration

### Yield Bonus Formula

Engineering skill applies a yield multiplier to `baseQuantity`:

```
finalYield = floor(baseQuantity * (1.0 + engineeringLevel * 0.005))
```

| Engineering Level | Multiplier | 3 base → | 5 base → |
|---|---|---|---|
| 0 | 1.0x | 3 | 5 |
| 25 | 1.125x | 3 | 5 |
| 50 | 1.25x | 3 | 6 |
| 75 | 1.375x | 4 | 6 |
| 100 | 1.5x | 4 | 7 |

The formula constant (0.005) is defined in a config file and can be tuned.

### SkillProvider Interface

```java
public interface SkillProvider {
    int getSkillLevel(Entity entity, String skillName);
}
```

`RefiningSystem` depends on this interface, not on the RPG skill system directly. Default implementation returns 0 for all skills. When the RPG stat system is built, it provides a real implementation.

---

## Data Files

### materials.json

Location: `core/src/main/resources/data/crafting/materials.json`

Defines all materials across all tiers. Each entry:

```json
{
  "materialId": "iron_ore",
  "name": "Iron Ore",
  "tier": "RAW",
  "category": "METAL",
  "weight": 2.0,
  "volume": 1.0,
  "maxStack": 99,
  "description": "Unprocessed iron-bearing rock",
  "commodityLink": "iron_ore"
}
```

### refining_recipes.json

Location: `core/src/main/resources/data/crafting/refining_recipes.json`

Defines all refining recipes. See Recipe Structure section above.

### refinery_modules.json

Location: `core/src/main/resources/data/crafting/refinery_modules.json`

Defines ship refinery module tiers. See Ship Refinery Module section above.

### Starter Material Set

5 material chains, 15+ materials, 10+ recipes, 1 alloy:

| Chain | Raw (Tier 1) | Processed (Tier 2) | Refined (Tier 3) |
|-------|---|---|---|
| Iron | Iron Ore | Iron Concentrate | Iron Ingot |
| Titanium | Titanium Ore | Titanium Sponge | Titanium Plate |
| Carbon | Carbon Deposit | Carbon Powder | Carbon Fiber |
| Lithium | Lithium Ore | Lithium Carbonate | Lithium Cell |
| Copper | Copper Ore | Copper Wire | Copper Coil |

**Alloy**: Steel Alloy = Iron Ingot (3) + Carbon Powder (1)

---

## Integration Points

### Economy System

- `commodityLink` field on `MaterialItem` maps to existing `CommodityDefinition`.
- Buying raw materials at a station market creates `MaterialItem` instances in `MaterialStorageComponent`.
- Selling materials with a `commodityLink` converts back to a commodity transaction via the existing `TransactionService`.

### Ship Power System

- `RefiningSystem` queries the ship's `ReactorComponent` for available power.
- If reactor is overloaded (shields + weapons consuming all power), refining pauses.
- Power is consumed per-second only while a job is actively processing.

### Persistence

Following the existing `SnapshotService` pattern:

- **`MaterialStorageSnapshot`**: serializes all materials with quantities.
- **`RefiningJobSnapshot`**: serializes job state (recipeId, progress, inputs consumed, state).
- **`RefinerySnapshot`**: serializes full refinery state (tier, queue of job snapshots).

On save: snapshot active refinery state. On load: restore jobs with progress intact — a 50% complete job resumes at 50%.

### Event Bus

All refining events flow through the existing `EventBus`. UI, audio, and VFX systems subscribe independently to render progress bars, play sounds, show particle effects, etc.

---

## Testing Strategy

All tests run without a GL context.

1. **`RefiningRecipeRegistryTest`** — load `refining_recipes.json`, validate all recipes reference valid materials in `MaterialRegistry`, no circular dependencies, tier requirements are consistent.
2. **`MaterialStorageComponentTest`** — add/remove materials, capacity limits (weight/volume), stacking behavior, `hasEnough`/`tryConsume` edge cases.
3. **`RefiningSystemTest`** — mock entities with `RefineryComponent`, queue jobs, advance time via delta ticks, verify: outputs created at correct quantities, events fired, power consumed, job state transitions (QUEUED → ACTIVE → COMPLETE).
4. **`RefiningRequestHandlerTest`** — validate: tier gating rejects under-tiered refineries, insufficient materials returns correct failure reason, full queue returns QUEUE_FULL, successful submission consumes inputs and fires events.
5. **`RefiningJobTest`** — progress calculation accuracy, cancellation returns proportional inputs, save/load round-trip preserves progress.
6. **`SkillYieldBonusTest`** — verify yield formula at Engineering levels 0, 25, 50, 75, 100. Verify floor behavior on fractional results.
7. **`RefiningPipelineIntegrationTest`** — full pipeline: add raw ore to `MaterialStorageComponent` → submit `RefiningRequestEvent` → tick `RefiningSystem` to completion → verify refined material in storage + all events fired in correct order.

---

## Out of Scope

- **UI**: crafting screen, refinery panel, material browser, progress bars — separate spec.
- **Mining/Salvaging/Harvesting**: gathering mechanics that produce raw materials — separate spec.
- **Crafting recipes**: recipes that consume refined materials to produce equipment — separate spec (builds on this pipeline).
- **RPG skill system**: full skill implementation — this spec defines the `SkillProvider` interface only.
- **Station refinery fee calculation**: depends on economy system pricing — placeholder flat fee for now.
