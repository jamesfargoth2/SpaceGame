# Weapons, Equipment & VFX System Design

**Date:** 2026-05-26  
**Scope:** FPS personal weapons + ship-mounted weapons, full inventory/equipment system, complete VFX particle pipeline, shooting feedback (recoil, crosshair, screen shake, ADS)

## Architecture: Domain-Split with Shared Event Bus

Four new domains, each owning its boundaries, communicating through the existing `EventBus` and shared ECS components:

| Domain | Package | Owns | Talks to others via |
|--------|---------|------|---------------------|
| Equipment & Inventory | `equipment/` | Grid backpack, loadout slots, loot, weapon assembly | `EquipmentChangedEvent`, `ItemAddedEvent`, `ItemRemovedEvent` |
| Ship Weapons | `ship/weapons/` | Hardpoints, turrets, ship firing, heat | `ShipWeaponFiredEvent`, reuses `ProjectileComponent` + damage pipeline |
| VFX / Particles | `vfx/` | Emitters, particle pool, rendering | Subscribes to combat/ship events, never writes game state |
| Shooting Feedback | `player/` (extends existing) | Recoil, crosshair, screen shake, ADS | Subscribes to combat events, writes only to camera/UI components |

---

## 1. Equipment & Inventory System

### 1.1 Components

**InventoryComponent** (Ashley component, on any entity that can hold items):
- `gridSlots: Item[][]` — 2D grid backpack, configurable size (e.g. 8x6)
- `gridWidth / gridHeight: int` — grid dimensions (capacity = width × height)
- `currentWeight / maxWeight: float`

**EquipmentSlotsComponent** (equipped loadout, separate from backpack):
- `PRIMARY_WEAPON` → `WeaponItem`
- `SECONDARY_WEAPON` → `WeaponItem`
- `MELEE_WEAPON` → `MeleeWeaponItem`
- `HELMET` → `ArmorItem`
- `CHEST` → `ArmorItem`
- `LEGS` → `ArmorItem`
- `BOOTS` → `ArmorItem`
- `UTILITY_1, UTILITY_2` → `ConsumableItem`

**LootDropComponent** (on world entities representing dropped loot):
- `items: List<Item>` — items available for pickup
- `position: Vector3` — world-space drop location
- `despawnTimer: float` — seconds until auto-despawn (-1 for persistent)

### 1.2 Item Hierarchy

All items are plain data objects (not ECS entities):

```
Item (abstract base)
├── id: String
├── name: String
├── description: String
├── icon: String (texture atlas region)
├── qualityTier: QualityTier
├── gridWidth / gridHeight: int
├── weight: float
├── stackable: boolean
├── maxStack / currentStack: int
│
├── WeaponItem → wraps WeaponAssembly (frame + barrel + ammo + mods + quality)
├── MeleeWeaponItem → wraps melee frame + quality
├── ArmorItem → armorRating, resistances: Map<DamageType, Float>, slotType, durability
├── AmmoItem → ammoTypeId, quantity (stackable)
├── ModItem → weaponModId
├── ComponentItem → crafting part (frame, barrel as loose items for assembly)
├── ConsumableItem → healAmount, buffEffect, useTime
└── JunkItem → sellValue, salvageYields: Map<String, Integer>
```

### 1.3 Systems

**InventorySystem:**
- Grid placement with auto-fit algorithm (first-fit scan, top-left to bottom-right)
- Stack merging for stackable items
- Weight validation on add
- Publishes `ItemAddedEvent(entity, item, gridPos)` / `ItemRemovedEvent(entity, item)`

**EquipmentSystem:**
- Equip/unequip validation: slot compatibility, level/stat requirements
- Swaps item between grid inventory and equipment slot
- On equip change → publishes `EquipmentChangedEvent(entity, slot, oldItem, newItem)`
- Listener on `EquipmentChangedEvent` syncs the existing `WeaponInventoryComponent` and `ArmorComponent` with resolved stats from `WeaponStatsResolver`

**LootGenerationSystem:**
- Subscribes to `EntityKilledEvent`
- Rolls against loot tables (JSON-defined per enemy archetype)
- Creates `Item` instances with randomized quality tier and optional random mod attachments
- Spawns loot as world entities with `LootDropComponent` (position, item list, despawn timer)

**WeaponAssemblySystem:**
- Combines loose `ComponentItem`s (frame + barrel + ammo type + mods) into a `WeaponItem`
- Uses existing `WeaponStatsResolver` for final stat calculation
- Validates compatibility (e.g. barrel fits frame category, mod slot count not exceeded)

### 1.4 Bridge to Existing Combat

When `EquipmentChangedEvent` fires for a weapon slot:
1. `WeaponStatsResolver` computes final stats from the `WeaponAssembly`
2. The entity's `RangedWeaponComponent` (or `MeleeWeaponComponent`) is updated with resolved values
3. Combat systems (`WeaponSystem`, `HitscanSystem`, `DamageSystem`) continue reading the same components they already read — zero changes to combat code

When `EquipmentChangedEvent` fires for an armor slot:
1. The entity's `ArmorComponent` region ratings and resistance map are recalculated from all equipped `ArmorItem`s
2. `DamageSystem` reads `ArmorComponent` as before — no changes

### 1.5 Data Files

- `data/equipment/loot_tables.json` — drop rates per enemy archetype, rarity weights
- `data/equipment/armor_definitions.json` — all armor pieces with base stats
- `data/equipment/consumables.json` — heal kits, stim packs, buff items
- `data/equipment/item_templates.json` — base item definitions for non-weapon/non-armor items

---

## 2. Ship Weapons & Hardpoints

### 2.1 Components

**ShipHardpointComponent** (on ship entity):
- `hardpoints: List<Hardpoint>`

Each `Hardpoint`:
- `id: String` (e.g. `"bow_turret_1"`)
- `position: Vector3` (local offset from ship origin)
- `type: HardpointType` — `TURRET`, `FIXED`, `BROADSIDE`, `MISSILE_BAY`, `POINT_DEFENSE`
- `sizeClass: HardpointSize` — `SMALL`, `MEDIUM`, `LARGE`, `CAPITAL`
- `arcMin / arcMax: float` (firing arc in degrees; 360 for full-rotation turrets)
- `mountedWeapon: ShipWeaponData` (nullable if hardpoint is empty)
- `currentState: HardpointState` — `IDLE`, `TRACKING`, `FIRING`, `RELOADING`, `DISABLED`
- `currentRotation: Quaternion` (turret facing)

**ShipWeaponData** (data object):
- `id, name: String`
- `category: ShipWeaponCategory` — `BALLISTIC_CANNON`, `LASER_ARRAY`, `PLASMA_TURRET`, `MISSILE_LAUNCHER`, `RAILGUN`, `EMP_PROJECTOR`, `POINT_DEFENSE`, `FLAK_CANNON`
- `damage: float`, `damageType: DamageType`
- `fireRate: float`, `projectileSpeed: float`
- `range: float`, `energyCost: float`, `heatPerShot: float`
- `ammoCapacity / currentAmmo: Integer` (null for energy weapons — infinite ammo, limited by heat/energy)
- `trackingSpeed: float` (turret rotation deg/sec)
- `burstCount: int`, `burstDelay: float`

**ShipWeaponHeatComponent** (on ship entity):
- `heatPerHardpoint: Map<String, Float>` (hardpoint id → current heat 0.0–1.0)
- `maxHeat: float`
- `dissipationRate: float` (per second)
- `overheatThreshold: float` (locks at this, unlocks at e.g. 0.5)
- `overheatedHardpoints: Set<String>`

**GuidedProjectileComponent** (on missile/torpedo entities):
- `targetEntity: Entity`
- `turnRate: float` (deg/sec)
- `armingDistance: float` (won't detonate before this range from launch point)
- `flareVulnerability: float` (chance to lose lock on countermeasure)

### 2.2 Systems

**TurretTrackingSystem** (priority 3):
- Iterates `ShipHardpointComponent` entities
- For `TURRET` type: rotates toward current target at `trackingSpeed`, clamped to arc
- For `FIXED`: no rotation, checks if target is within fixed forward cone
- For `BROADSIDE`: checks if target is within side arc window
- Sets hardpoint state to `TRACKING` when target is in arc, `IDLE` when not

**ShipWeaponSystem** (priority 4):
- Handles fire commands from player input or AI targeting
- Validates: target in arc, range check, heat check, ammo check, energy check
- On fire → adds heat, deducts ammo/energy, publishes `ShipWeaponFiredEvent(shipEntity, hardpointId, origin, direction, weaponData)`

**ShipProjectileSystem** (priority 7):
- Subscribes to `ShipWeaponFiredEvent`
- Spawns projectile entity with `ProjectileComponent` (reuses existing component)
- Missiles also get `GuidedProjectileComponent` for tracking behavior
- Guided missiles: each frame, adjust velocity toward target within `turnRate`

**PointDefenseSystem** (priority 5):
- Scans for `ProjectileComponent` entities within point defense range of the ship
- Auto-targets closest incoming missile/torpedo
- Fires autonomously at `fireRate`, publishes `PointDefenseEngagedEvent`

**ShipHeatSystem** (priority 9):
- Dissipates heat per hardpoint at `dissipationRate` per second
- When heat >= `maxHeat` → adds to `overheatedHardpoints`, publishes `ShipOverheatEvent`
- When heat drops below `overheatThreshold` → removes from overheated set

### 2.3 Events

- `ShipWeaponFiredEvent(shipEntity, hardpointId, origin, direction, weaponData)`
- `ShipOverheatEvent(shipEntity, hardpointId)`
- `MissileLockedEvent(targetEntity, missileEntity)`
- `PointDefenseEngagedEvent(shipEntity, interceptedProjectile)`

### 2.4 Bridge to Existing Damage Pipeline

Ship projectiles carry the same `ProjectileComponent` as FPS projectiles. On collision, the existing `ProjectileSystem` publishes `ProjectileHitEvent` and `DamageSystem` runs the shield → armor → health pipeline. Ship-scale damage is simply larger values through the same math.

### 2.5 Hardpoint Integration with Ship Blueprints

`ShipBlueprint` gains a `hardpoints: List<HardpointTemplate>` field defining default hardpoint layout per ship class. When a ship is spawned, `ShipHardpointComponent` is populated from the blueprint. Players can swap mounted weapons at stations via the equipment system.

### 2.6 Data Files

- `data/weapons/ship_weapons.json` — all ship weapon definitions
- `data/weapons/hardpoint_templates.json` — default hardpoint layouts per ship blueprint

---

## 3. Particle & VFX System

### 3.1 Core Architecture

Fully event-driven, read-only with respect to game state. Subscribes to events, spawns visual effects, never modifies game logic.

### 3.2 Components

**ParticleEmitterComponent** (on any entity that emits):
- `activeEmitters: List<ActiveEmitter>`

Each `ActiveEmitter`:
- `definitionId: String` (references JSON effect definition)
- `elapsed / duration: float` (`-1` for looping emitters)
- `localOffset: Vector3` (relative to entity transform)
- `localRotation: Quaternion`
- `state: EmitterState` — `PLAYING`, `PAUSED`, `STOPPING` (drain remaining, don't spawn new)

**ParticlePoolComponent** (singleton entity — global particle manager):
- `particles: Pool<Particle>` (libGDX `Pool`, pre-allocated)

Each `Particle`:
- `position, velocity, acceleration: Vector3`
- `life / maxLife: float`
- `size / sizeEnd: float` (lerp over lifetime)
- `color / colorEnd: Color` (lerp over lifetime)
- `rotation, angularVelocity: float`
- `textureRegion: TextureRegion`
- `flags: int` (bitfield: `ADDITIVE_BLEND`, `FACE_CAMERA`, `WORLD_SPACE`)

### 3.3 Effect Definitions (JSON)

```json
{
  "id": "muzzle_flash_ballistic",
  "maxParticles": 12,
  "emitRate": 0,
  "burstCount": 12,
  "lifetime": [0.05, 0.12],
  "speed": [2.0, 8.0],
  "spread": 25.0,
  "size": [0.1, 0.3],
  "sizeEnd": 0.0,
  "color": "#FFA500",
  "colorEnd": "#FF4500",
  "texture": "particles/spark.png",
  "blendMode": "ADDITIVE",
  "gravity": 0.0
}
```

Fields use `[min, max]` arrays for randomized ranges. `emitRate: 0` with `burstCount > 0` means one-shot burst. `emitRate > 0` with `duration: -1` means continuous looping.

### 3.4 Systems

**ParticleSpawnSystem** (priority 12):
Subscribes to events and spawns appropriate emitters:

| Event | Effect | Attachment |
|-------|--------|------------|
| `WeaponFiredEvent` | Muzzle flash (varies by ammo damage type) | Weapon entity, barrel offset |
| `HitscanHitEvent` | Impact sparks + decal | World-space at hit point |
| `ProjectileHitEvent` | Explosion / impact (varies by damage type) | World-space at hit point |
| `ShipWeaponFiredEvent` | Ship muzzle flash (scaled up) | Hardpoint position |
| `MeleeHitEvent` | Slash trail / blood spray | Hit point |
| `ShieldAbsorbEvent` | Shield ripple / hex pattern | Defender entity |
| `StatusEffectAppliedEvent` | Looping aura (burn/cryo/emp/bleed) | Affected entity |
| `EntityKilledEvent` | Death burst | Entity position |
| `ReloadStartedEvent` | Mag eject particles | Weapon entity |
| `ShipOverheatEvent` | Vent steam / sparks | Hardpoint position |

**ParticleUpdateSystem** (priority 13):
- Ticks all live particles: integrate velocity + acceleration, interpolate size and color over lifetime
- Kills expired particles, returns them to pool
- Updates emitters: continuous emitters spawn new particles at `emitRate`, advances `elapsed`, stops emitters past `duration`

**ParticleRenderSystem** (priority 20, render phase):
- Batches particles by texture atlas region and blend mode
- Nearby particles: full 3D billboarded quads via `DecalBatch`
- Distant particles: collapsed to point sprites for performance
- Additive blended particles rendered in a separate pass

### 3.5 Ambient / Environmental VFX

Looping emitters not driven by combat events:

| Effect | Attached to | Behavior |
|--------|-------------|----------|
| Engine exhaust | Ship thruster positions | Intensity scales with `ShipFlightComponent` throttle |
| Thruster glow | Ship nozzle positions | Additive point sprites |
| Warp trail | Ship entity | Activated on `WarpBeginEvent`, stretched trail particles |
| Dust/debris field | Camera-relative singleton | Sparse particles in asteroid fields / nebulae, offset by floating origin |
| Sparking damage | Entity with < 25% HP | Looping sparks on damaged hull sections |
| Shield idle shimmer | Entity with active shield | Faint hex-grid, intensifies on `ShieldAbsorbEvent` |

### 3.6 Performance Guardrails

- **Global particle cap:** 4096 particles. When exceeded, oldest particles culled first.
- **LOD:** Emitters beyond a configurable distance threshold reduce `emitRate` by 50% (medium) or skip entirely (far).
- **Pool pre-allocation:** All particles allocated at startup via libGDX `Pool<Particle>`. Zero GC during gameplay.
- **Hybrid batching:** Nearby particles get full 3D billboarding; particles beyond LOD distance collapse to cheaper point sprites.

### 3.7 Data Files

- `data/vfx/*.json` — one file per effect definition
- `data/vfx/vfx_event_bindings.json` — maps event class names to effect definition IDs, allowing re-binding without code changes

---

## 4. Shooting Feedback Systems

### 4.1 Recoil

**RecoilComponent** (on player entity):
- `currentPunch: Vector2` (accumulated pitch/yaw offset applied to camera)
- `recoverySpeed: float` (how fast crosshair returns to center)
- `pattern: Vector2[]` (per-shot recoil vectors, indexed by consecutive fire count)
- `patternIndex: int` (resets after `patternResetDelay` seconds of not firing)
- `patternResetDelay: float` (e.g. 0.3s)
- `maxPunch: Vector2` (clamp to prevent camera flip)

**RecoilSystem** (priority 10):
- Subscribes to `RecoilEvent` (already published by `HitscanSystem`)
- Applies recoil punch from pattern at current index to `FPSCameraComponent` euler angles
- Each frame: decays `currentPunch` toward zero at `recoverySpeed`
- Advances `patternIndex` on consecutive shots, resets after pause

### 4.2 Crosshair

**CrosshairComponent** (on player entity):
- `baseSize: float`
- `currentBloom: float` (expands on fire, decays over time)
- `bloomPerShot: float`
- `bloomDecayRate: float`
- `style: CrosshairStyle` — `DOT`, `CROSS`, `CIRCLE`, `DYNAMIC`
- `hitMarkerTimer: float` (flash duration on confirmed hit)
- `killConfirmTimer: float` (distinct flash on kill)

**CrosshairSystem** (priority 15, UI phase):
- Reads `RangedWeaponComponent.spread` + `currentBloom` to compute crosshair size
- Subscribes to `DamageDealtEvent` → triggers hit marker flash
- Subscribes to `EntityKilledEvent` → triggers kill confirm indicator
- Renders crosshair via Scene2D overlay

### 4.3 Screen Shake

**ScreenShakeComponent** (on camera entity):
- `trauma: float` (0.0–1.0, added by events, decays continuously)
- `decayRate: float`
- `maxOffset: Vector3` (translation shake bounds)
- `maxAngle: Vector2` (rotation shake bounds in pitch/yaw)
- `frequency: float` (Perlin noise sample rate for smooth organic shake)

**ScreenShakeSystem** (priority 14):
- Subscribes to events with configured trauma values:
  - `WeaponFiredEvent` → small trauma (0.05–0.1)
  - `ProjectileHitEvent` near player → medium (0.2–0.3)
  - `ShipWeaponFiredEvent` from own ship → medium (0.15–0.25)
  - Explosions near player → large (0.4–0.6)
- Shake intensity = `trauma²` (quadratic falloff feels natural)
- Offset computed from Perlin noise at `frequency`, scaled by intensity × `maxOffset`/`maxAngle`
- Applied as additive offset to camera transform each frame

### 4.4 ADS (Aim Down Sights)

**ADSComponent** (on player entity):
- `adsProgress: float` (0.0 = hip fire, 1.0 = fully aimed, lerped each frame)
- `adsSpeed: float` (transition speed, varies by weapon weight)
- `zoomMultiplier: float` (FOV reduction, e.g. 0.7 = 70% of base FOV)
- `spreadMultiplier: float` (tighter spread when aimed, e.g. 0.3)
- `moveSpeedMultiplier: float` (slower while aiming, e.g. 0.6)

**ADSSystem** (priority 11):
- Reads `PlayerInputComponent.aim` flag
- Lerps `adsProgress` toward 1.0 (aiming) or 0.0 (hip) at `adsSpeed`
- Applies `zoomMultiplier` to camera FOV
- Applies `spreadMultiplier` to `RangedWeaponComponent.spread` (read by `HitscanSystem`)
- Applies `moveSpeedMultiplier` to movement speed
- Suppresses weapon sway/bob proportional to `adsProgress`

### 4.5 Weapon Sway & Bob

Added to existing `FPSCameraComponent`:
- Procedural sinusoidal bob tied to `MovementStateComponent` velocity magnitude
- Idle breathing sway (slow low-amplitude sine wave)
- Both suppressed proportionally by `ADSComponent.adsProgress`
- Implemented in a **WeaponSwaySystem** (priority 11) that adds offset to camera, not a new component

### 4.6 Audio Bindings

Audio subscribes to the same events as VFX, independently:

| Event | Sound |
|-------|-------|
| `WeaponFiredEvent` | Fire sound (by weapon category + ammo type) |
| `ReloadStartedEvent` | Reload sound |
| `HitscanHitEvent` / `ProjectileHitEvent` | Impact sound (by surface/material) |
| `ShieldAbsorbEvent` | Shield ping |
| `EntityKilledEvent` | Death sound |
| `ShipWeaponFiredEvent` | Ship weapon fire (distance-attenuated) |
| `ShipOverheatEvent` | Overheat alarm |

### 4.7 Data Files

- `data/weapons/recoil_patterns.json` — per-weapon-category recoil vector sequences
- `data/vfx/screen_shake_config.json` — trauma values per event type
- `data/audio/sound_bindings.json` — maps event types to sound asset IDs

---

## 5. New Events Summary

| Event | Published by | Consumed by |
|-------|-------------|-------------|
| `ItemAddedEvent` | InventorySystem | UI |
| `ItemRemovedEvent` | InventorySystem | UI |
| `EquipmentChangedEvent` | EquipmentSystem | Combat component sync, UI |
| `LootDroppedEvent` | LootGenerationSystem | UI, VFX |
| `ShipWeaponFiredEvent` | ShipWeaponSystem | ShipProjectileSystem, VFX, Audio |
| `ShipOverheatEvent` | ShipHeatSystem | UI, VFX, Audio |
| `MissileLockedEvent` | ShipProjectileSystem | UI (lock warning), PointDefenseSystem |
| `PointDefenseEngagedEvent` | PointDefenseSystem | VFX, Audio |

Existing events (`WeaponFiredEvent`, `HitscanHitEvent`, `ProjectileHitEvent`, `DamageDealtEvent`, `EntityKilledEvent`, etc.) gain new subscribers in VFX, audio, and feedback systems but are not modified.

---

## 6. Package Layout

```
core/src/main/java/com/galacticodyssey/
  equipment/
    components/
      InventoryComponent.java
      EquipmentSlotsComponent.java
      LootDropComponent.java
    items/
      Item.java
      WeaponItem.java
      MeleeWeaponItem.java
      ArmorItem.java
      AmmoItem.java
      ModItem.java
      ComponentItem.java
      ConsumableItem.java
      JunkItem.java
    systems/
      InventorySystem.java
      EquipmentSystem.java
      LootGenerationSystem.java
      WeaponAssemblySystem.java
    data/
      LootTable.java
      LootTableRegistry.java
    events/
      ItemAddedEvent.java
      ItemRemovedEvent.java
      EquipmentChangedEvent.java
      LootDroppedEvent.java

  ship/weapons/
    components/
      ShipHardpointComponent.java
      ShipWeaponHeatComponent.java
      GuidedProjectileComponent.java
    data/
      ShipWeaponData.java
      Hardpoint.java
      HardpointTemplate.java
      ShipWeaponRegistry.java
    systems/
      TurretTrackingSystem.java
      ShipWeaponSystem.java
      ShipProjectileSystem.java
      PointDefenseSystem.java
      ShipHeatSystem.java
    events/
      ShipWeaponFiredEvent.java
      ShipOverheatEvent.java
      MissileLockedEvent.java
      PointDefenseEngagedEvent.java
    enums/
      HardpointType.java
      HardpointSize.java
      HardpointState.java
      ShipWeaponCategory.java

  vfx/
    components/
      ParticleEmitterComponent.java
      ParticlePoolComponent.java
    data/
      ParticleEffectDefinition.java
      VFXEventBindings.java
      VFXRegistry.java
    systems/
      ParticleSpawnSystem.java
      ParticleUpdateSystem.java
      ParticleRenderSystem.java
    Particle.java
    ActiveEmitter.java

  player/
    components/
      RecoilComponent.java
      CrosshairComponent.java
      ScreenShakeComponent.java
      ADSComponent.java
    systems/
      RecoilSystem.java
      CrosshairSystem.java
      ScreenShakeSystem.java
      ADSSystem.java
      WeaponSwaySystem.java

core/src/main/resources/
  data/
    equipment/
      loot_tables.json
      armor_definitions.json
      consumables.json
      item_templates.json
    weapons/
      ship_weapons.json
      hardpoint_templates.json
      recoil_patterns.json
    vfx/
      muzzle_flash_ballistic.json
      muzzle_flash_energy.json
      muzzle_flash_plasma.json
      impact_sparks.json
      impact_explosion.json
      shield_ripple.json
      burn_aura.json
      cryo_frost.json
      emp_sparks.json
      bleed_drip.json
      engine_exhaust.json
      thruster_glow.json
      warp_trail.json
      dust_field.json
      damage_sparks.json
      shield_shimmer.json
      vfx_event_bindings.json
      screen_shake_config.json
    audio/
      sound_bindings.json
```

---

## 7. ECS System Priority Map

Including existing systems for context on where new systems slot in:

| Priority | System | Domain | Phase |
|----------|--------|--------|-------|
| 1 | PlayerInputSystem | player | Input |
| 2 | MovementSystem | player | Input |
| 3 | TurretTrackingSystem | ship/weapons | **NEW** Simulation |
| 4 | WeaponSystem | combat (existing) | Simulation |
| 4 | ShipWeaponSystem | ship/weapons | **NEW** Simulation |
| 5 | MeleeSystem | combat (existing) | Simulation |
| 5 | PointDefenseSystem | ship/weapons | **NEW** Simulation |
| 6 | HitscanSystem | combat (existing) | Simulation |
| 7 | ProjectileSystem | combat (existing) | Simulation |
| 7 | ShipProjectileSystem | ship/weapons | **NEW** Simulation |
| 8 | DamageSystem | combat (existing) | Simulation |
| 9 | StatusEffectSystem | combat (existing) | Simulation |
| 9 | ShipHeatSystem | ship/weapons | **NEW** Simulation |
| 10 | RecoilSystem | player | **NEW** Feedback |
| 11 | ADSSystem | player | **NEW** Feedback |
| 11 | WeaponSwaySystem | player | **NEW** Feedback |
| 12 | ParticleSpawnSystem | vfx | **NEW** VFX |
| 13 | ParticleUpdateSystem | vfx | **NEW** VFX |
| 14 | ScreenShakeSystem | player | **NEW** Feedback |
| 15 | CrosshairSystem | player | **NEW** UI |
| 20 | ParticleRenderSystem | vfx | **NEW** Render |
