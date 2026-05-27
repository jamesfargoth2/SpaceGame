---
name: libgdx-crafting-system
description: >
  Enforces correct resource tier hierarchy, gathering methods, crafting recipe
  architecture, material quality modifiers, and skill-based crafting outcomes
  for a libGDX 3D space game using Ashley ECS. Use this skill whenever writing
  or modifying: resource definitions and tier classifications, gathering mechanics
  (mining, salvaging, harvesting, refining), crafting recipe data structures,
  material quality tiers affecting output stats, Engineering skill integration
  with crafting quality, weapon/armor/component fabrication, refinery module
  simulation, or crafting UI. Also triggers when adding new resource types,
  balancing crafting recipes, or implementing ship component upgrade paths.
---

# libGDX Crafting System

## Resource Tier Hierarchy

| Tier | Examples | Source | Rarity |
|---|---|---|---|
| Common | Iron, Copper, Silicon, Carbon | Everywhere | Abundant |
| Uncommon | Titanium, Lithium, Tungsten | Specific asteroids, certain planets | Moderate |
| Rare | Iridium, Neutronium, Dark Crystals | Dangerous regions, guarded deposits | Scarce |
| Exotic | Zero-Point Cells, Quantum Foam, Void Essence | Anomalies, precursor sites | Very scarce |
| Alien | Species bio-materials, tech fragments | Alien trade or conflict | Context-dependent |

Define all resources in data files, not code.

## Inventory Component

```java
public class InventoryComponent implements Component, Pool.Poolable {
    public int credits;
    public ObjectMap<String, Integer> resources = new ObjectMap<>();
    public Array<ItemInstance> items = new Array<>();

    public boolean hasResource(String id, int amount) {
        return resources.get(id, 0) >= amount;
    }
    public boolean consume(String id, int amount) {
        int current = resources.get(id, 0);
        if (current < amount) return false;
        resources.put(id, current - amount);
        return true;
    }
    public void addResource(String id, int amount) {
        resources.put(id, resources.get(id, 0) + amount);
    }
    @Override public void reset() { credits = 0; resources.clear(); items.clear(); }
}
```

## Gathering Methods

| Method | Mechanic | Skill Integration |
|---|---|---|
| Mining | Ship-mounted or handheld drill | Mining skill affects speed and rare chance |
| Salvaging | Scan wrecked ships, extract components | Repair skill affects yield |
| Trading | Purchase from NPC merchants | Trading skill affects price |
| Looting | Enemy drops on destruction | Drop tables in data |
| Harvesting | Biological collection from planets | Science skill for identification |
| Refining | Combine/upgrade raw materials | Engineering affects output quality |

```java
public class MiningSystem extends IteratingSystem {
    public MiningSystem() {
        super(Family.all(MiningActivityComponent.class).get());
    }
    @Override
    protected void processEntity(Entity entity, float dt) {
        MiningActivityComponent mining = Mappers.mining.get(entity);
        PlayerStatsComponent stats = Mappers.playerStats.get(mining.miner);
        float miningLevel = stats.realTimeSkills.get(RealTimeSkill.MINING).level;
        float speedMod = 1f + miningLevel * 0.01f;
        mining.progress += mining.baseRate * speedMod * dt;
        if (mining.progress >= 1f) {
            mining.progress = 0f;
            ResourceDeposit deposit = Mappers.deposit.get(mining.target);
            int yield = deposit.baseYield;
            if (MathUtils.random() < miningLevel * 0.002f) yield *= 2;
            Mappers.inventory.get(mining.miner).addResource(deposit.resourceId, yield);
            deposit.remaining -= yield;
            EventBus.post(new ResourceGatheredEvent(mining.miner, deposit.resourceId, yield));
            if (deposit.remaining <= 0) getEngine().removeEntity(mining.target);
        }
    }
}
```

## Material Quality Tiers

| Quality | Stat Multiplier | Requirements |
|---|---|---|
| Salvaged | 0.7x | No skill, common mats only |
| Common | 1.0x | Base recipe output |
| Refined | 1.15x | Engineering 20+ |
| Military | 1.3x | Engineering 40+, uncommon+ materials |
| Experimental | 1.5x | Engineering 60+, rare materials |
| Alien | 1.7x | Alien tech fragments as input |
| Precursor | 2.0x | Exotic precursor materials, Engineering 80+ |

```java
public class CraftingSystem {
    public ItemInstance craft(Entity player, CraftingRecipe recipe) {
        InventoryComponent inv = Mappers.inventory.get(player);
        for (RecipeInput input : recipe.inputs) {
            if (!inv.consume(input.resource, input.amount)) return null;
        }
        PlayerStatsComponent stats = Mappers.playerStats.get(player);
        float engLevel = stats.pointSkills.get(PointSkill.ENGINEERING, 0);
        MaterialQuality quality = determineQuality(recipe, engLevel);
        float qualityMod = PlayerStatQuery.getCraftingQuality(stats);
        ItemInstance item = new ItemInstance();
        item.itemId = recipe.output.itemId;
        item.quality = quality;
        item.statMultiplier = quality.multiplier * qualityMod;
        inv.items.add(item);
        EventBus.post(new ItemCraftedEvent(player, item));
        return item;
    }
}
```

## Modular Weapon Crafting

Weapons use Frame + Barrel + Ammo Type + Mod Slots + Material Quality. Each component is crafted or found separately.

## Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| Mining speed base rate | 0.1 units/sec | Extraction speed |
| Lucky strike chance | 0.2% per mining level | Double yield probability |
| Refinery process time | 60s per batch | Background refining speed |
| Quality multiplier range | 0.7x - 2.0x | Salvaged to Precursor |
| Engineering quality bonus | 0.5% per level | Additional stat multiplier |

## Common Mistakes

| Mistake | Fix |
|---|---|
| Hardcoding recipes in Java | Define all recipes in JSON/YAML data files |
| Consuming resources before validation | Pre-check all inputs before consuming any |
| Ignoring Engineering skill in quality | Quality must incorporate Engineering level |
| Flat resource drops ignoring skill | Skill should affect yield and rare chance |
| Refinery running without ship power | Check ship power state before processing |
| Weapon mods without compatibility check | Verify mod slot compatibility with frame type |
