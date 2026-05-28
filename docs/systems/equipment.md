# Equipment & Inventory Systems

The `equipment` package covers the item model, inventory management, equipment slots, procedural loot generation, and the weapon assembly system.

---

## Item Model

All items share a common structure. Concrete item types extend or implement a shared item interface:

| Class | What it represents |
|---|---|
| `WeaponItem` | Ranged weapon (assembled from parts) |
| `MeleeWeaponItem` | Melee weapon with damage and reach |
| `ArmorItem` | Armour piece for a specific body slot |
| `AmmoItem` | Ammunition stack with ammo type and quantity |
| `ConsumableItem` | Single-use item (medkit, stimpack, repair kit) |
| `ModItem` | Weapon or armour modification |
| `ComponentItem` | Ship module or component |
| `JunkItem` | Salvage with base sell value |

Items are not ECS entities — they are plain Java objects stored in inventory collections. Only entities that carry items (player, containers, NPCs) have inventory components.

**`EquipmentEnums`**

Centralises item-related enums:
- `ItemType` — weapon, armour, ammo, consumable, mod, component, junk
- `ItemRarity` — common, uncommon, rare, epic, legendary
- `EquipmentSlot` — head, torso, legs, feet (for armour), and weapon slots

---

## Inventory

**`InventoryComponent`**

General-purpose item storage. Tracks:
- List of item objects
- Current total weight (float)
- Current total volume (float)
- Weight and volume capacity limits

Adding an item checks both limits; `add()` returns false if either is exceeded.

**`CargoBayComponent`** (in `economy/`)

Commodity-specific storage for trade goods. Uses a commodity-ID → stack map rather than a generic item list. Has separate weight and volume limits.

---

## Equipment Slots

**`EquipmentSlotsComponent`**

Tracks the item currently equipped in each `EquipmentSlot`. Only one item per slot. Armour stats are applied when the item is in a slot and removed when unequipped.

**`EquipmentSystem`**

Handles equip and unequip requests:
1. Validates the item fits the requested slot.
2. Moves the item from `InventoryComponent` to `EquipmentSlotsComponent`.
3. Applies stat modifiers from `ArmorItem` to `ArmorComponent`.
4. Publishes `ItemEquippedEvent`.

---

## Loot Generation

**`LootGenerationSystem`**

Fires when an entity with `LootDropComponent` is killed or a container is opened. Reads the entity's loot table key, looks up the `LootTable` in `LootTableRegistry`, and rolls for drops:

1. For each entry in the table, rolls against its probability weight.
2. Selected items are created with quality and rarity drawn from the roll's tier range.
3. Items are placed in the world as a loot container entity, or directly in the triggering entity's inventory if it's a pickup.

**`LootTableRegistry`**

Loads `data/loot/` JSON files. Provides lookup by table ID.

**`LootTable`**

A weighted list of loot entries. Each entry specifies:
- Item type and subtype (or a nested table reference)
- Probability weight
- Quantity range
- Rarity tier range
- Condition (what state the item spawns in)

**`ArchetypeComponent`**

Tags an item entity with its archetype ID (used to look up base stats from the registry) and its rolled rarity.

**`LootDropComponent`**

Tags an entity (enemy, container, crate) as a loot source, with a loot table key.

---

## Weapon Assembly

**`WeaponAssemblySystem`**

Combines weapon parts into a usable `WeaponItem`. The assembly process:
1. Takes a `WeaponAssembly` (receiver + barrel + stock + mod slots).
2. Calls `WeaponStatsResolver.resolve(assembly)` to compute final stats.
3. Creates a `WeaponItem` with the resolved stats and attaches it to the player's inventory or hotbar.

This system is triggered by the crafting UI when the player confirms an assembly.

**`WeaponAssembly`** (in `combat/data/`)

Describes a weapon configuration: receiver type, barrel type, installed mods. The assembly is serialisable so it can be saved as an item.

**`WeaponStatsResolver`** (in `combat/data/`)

Resolves final weapon stats from an assembly by summing base stats from the receiver and applying additive/multiplicative modifiers from barrel type, installed mods, and quality tier.

---

## Events

| Event | When published |
|---|---|
| `ItemEquippedEvent` | Item moved into an equipment slot (includes slot and item reference) |
