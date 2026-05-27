---
name: libgdx-rare-phenomena
description: >
  Enforces correct implementation of rare astronomical phenomena including
  pulsars, nebulae, wormholes, neutron stars, rogue planets, supernova
  remnants, Dyson structures, and anomalies for a libGDX 3D space game using
  Ashley ECS. Use this skill whenever writing or modifying: phenomenon placement,
  phenomenon-specific physics modifiers, scanning and discovery mechanics,
  phenomenon-triggered quests, exotic resource harvesting, or navigation hazards.
  Also triggers when adding VFX for phenomena or wormhole traversal mechanics.
  See libgdx-black-hole-physics for black hole details and libgdx-relativistic-effects
  for time dilation.
---

# libGDX Rare Phenomena

## Phenomenon Catalog

| Phenomenon | Danger | Resources | Quest Potential |
|---|---|---|---|
| Pulsar | Medium | Rare minerals nearby | Navigation challenges |
| Black Hole | Extreme | Exotic matter | High-risk exploration |
| Nebula | Low-Medium | Rare gas harvesting | Pirate bases, stealth |
| Binary/Trinary Stars | Low | Unique planets | Orbital navigation |
| Rogue Planet | Low | Unique biomes | Survival exploration |
| Neutron Star | High | Exotic materials, FTL boost | Risk/reward |
| Wormhole | Variable | None | Cross-galaxy shortcuts |
| Supernova Remnant | Medium | Precursor artifacts | Archaeology |
| Dyson Structure | None | Unknown alien tech | Endgame lore |
| Anomaly | Variable | Exotic resources | Discovery quests |

## Data Model

```java
public class PhenomenonComponent implements Component, Pool.Poolable {
    public PhenomenonType type;
    public float dangerRadius, effectRadius, detectionRadius;
    public float intensity;
    public boolean discovered;
    public String loreId;
}
```

## Galaxy Placement

Seed-based for reproducibility. Wormholes always in linked pairs. Distribution: Black holes 0.5%, Pulsars 2%, Nebulae 3%, Neutron stars 1%, Wormholes 0.7%, Anomalies 2.5%.

## Pulsar

Periodic radiation bursts in rotating cone (~25 degrees). Raycast beam; occluded ships protected.

## Nebula

Sensor disruption up to 80% range reduction. Pirate bases hidden deep inside.

## Wormhole

Cross-galaxy teleport. Unstable wormholes have 10-30% collapse chance per use.

## Neutron Star

FTL supercharge up to 4x jump range at risk of hull damage proportional to proximity.

## Exotic Resources

| Phenomenon | Resource | Use |
|---|---|---|
| Black Hole | Hawking Particles | Exotic weapons |
| Neutron Star | Degenerate Matter | FTL upgrades |
| Nebula | Ionized Gas | Shield enhancements |
| Anomaly | Void Essence | Precursor crafting |

Resources exclusive to their phenomenon source.

## Discovery

Undiscovered phenomena still affect physics (gravity felt before seen) but don't appear on map. Scanning requires minimum Science level per type.

## Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| Black hole density | 0.5% | Rarity |
| Pulsar burst interval | 5s | Hazard timing |
| Nebula sensor reduction | Up to 80% | Stealth gameplay |
| Wormhole collapse chance | 10-30% | Risk factor |
| Neutron star jump boost | Up to 4x | Reward |
| Discovery XP base | 500 | Exploration motivation |

## Common Mistakes

| Mistake | Fix |
|---|---|
| Phenomena with no gameplay effect | Every type needs physics, resources, or quests |
| Unpaired wormholes | Always create in linked pairs |
| Undiscovered = invisible to physics | Physics applies regardless |
| Exotic resources elsewhere | Must be exclusive to phenomenon |
| Generic anomalies | Each needs unique effect + quest |
| Pulsar ignoring occlusion | Raycast beam; occluded = protected |
