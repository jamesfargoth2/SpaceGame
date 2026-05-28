# Remaining Systems — TODO

Systems described in [DESIGN.md](DESIGN.md) that are not yet implemented. Organised by domain. Cross-reference the implemented system docs in [systems/](systems/).

---

## Rendering Pipeline

The game currently has placeholder rendering (`SkyRenderer`, `AtmosphericSkyRenderer`, `FogShaderProvider`, `StarfieldBackground`). The design calls for a full custom deferred PBR pipeline.

| System | What's needed |
|---|---|
| Deferred rendering pipeline | G-buffer pass (albedo, normals, roughness/metallic, emissive), lighting pass, HDR tone-mapping |
| Volumetric lighting | Atmospheric scattering, god rays, volumetric fog in nebulae |
| Screen-space reflections (SSR) | Reflective surfaces on ships, wet planetary terrain |
| Bloom + post-processing stack | HDR bloom, lens flare, chromatic aberration at extreme speeds |
| LOD terrain streaming | Per-planet: render as dot → sphere with atmosphere → textured terrain → ground-level detail as player approaches |
| Scene streaming / SceneManager | Additive loading/unloading of zones (deep space, orbital, planet surface, station interior). Currently no `SceneManager` class exists. |
| Cockpit HUD rendering | In-world cockpit instruments rendered as 3D mesh overlays, not Scene2D. Currently only a placeholder `CockpitHUDSystem` exists. |

---

## Networking & Multiplayer

The `networking/` package exists but no transport, replication, or prediction code was found.

| System | What's needed |
|---|---|
| KryoNet transport layer | Connection management, message serialisation, heartbeat |
| Server-authoritative game loop | Dedicated server runs the simulation; clients receive state snapshots |
| Client-side prediction | Player inputs applied locally, reconciled against server state |
| Entity interpolation | Smooth rendering of remotely-controlled entities between server ticks |
| Zone-based synchronisation | Only sync entities within a configurable radius of each connected player |
| Server module | The `server/` Gradle module is scaffolded but needs a headless game loop, player session manager, and PostgreSQL/Redis persistence |

---

## Asset & Scene Streaming

`AssetHandle` and `AssetCategory` exist as data classes but the streaming manager was not found.

| System | What's needed |
|---|---|
| Streaming AssetManager | Extends libGDX `AssetManager` with async priority loading, ref-counting, and eviction |
| LOD streaming coordinator | Triggers mesh/texture swaps as distance changes; works with `DebrisLODSystem` pattern |
| Sector Manager | Loads/unloads sector scenes around the player's galaxy-space position |

---

## Crafting & Resource Gathering

Weapon assembly (`WeaponAssemblySystem`) exists, but the broader crafting and resource loop is missing.

| System | What's needed |
|---|---|
| Mining system | Handheld and ship-mounted mining: target a deposit, beam duration, resource extraction quantity |
| Salvage system | Strip components from derelict ships and battlefield debris |
| Refinery system | Convert raw ore → refined materials at a station or ship refinery module |
| Crafting UI | Workbench-style UI for combining materials into components, equipment, and ship modules |
| Resource data | `ResourceRegistry` with all material tiers (common → exotic), yield rates, refinery recipes |
| Harvesting | Biological resources from planet fauna/flora; alien specimens |

---

## Faction & Reputation System

The galaxy package has `FactionEthos` and `PoliticalRelation` enums but no runtime system to track or mutate player reputation.

| System | What's needed |
|---|---|
| `FactionRegistry` | Loads faction definitions (name, ethos, territory, allied/hostile factions) from data |
| `FactionReputationComponent` | Player reputation score (−100 to +100) per faction |
| `ReputationSystem` | Applies reputation deltas from events (mission completed, ship destroyed, trade) |
| Access control integration | Check reputation before allowing docking, job acceptance, and hardware purchases |
| Faction-controlled space | Mark system/station ownership; hostile factions scramble patrols on entry |
| Smuggling detection | Contraband scan at checkpoints; failure triggers reputation loss and combat |

---

## Dialogue System

No dialogue system was found. The design calls for NPC conversations with branching and quest-giving.

| System | What's needed |
|---|---|
| Dialogue data model | `DialogueNode`, `DialogueChoice`, `DialogueCondition` (reputation, inventory, quest state) |
| `DialogueRegistry` | Loads dialogue trees from JSON/Ink-style scripts |
| `DialogueSystem` | Drives active conversation: presents node text, filters choices by condition, advances the tree |
| Dialogue UI | Scene2D panel: portrait, speaker name, body text, choice buttons |
| Quest-give integration | Dialogue nodes can create `JobInstance` or advance `SagaInstance` |

---

## Stealth System

Stealth is listed as a real-time skill and mentioned as part of the smuggling loadout, but no stealth mechanics exist.

| System | What's needed |
|---|---|
| `StealthComponent` | Current detection level, visibility signature, noise signature |
| `DetectionSystem` | NPC perception: tests line-of-sight + light level + movement noise vs. NPC awareness |
| Visibility modifiers | Crouching, dim lighting, stealth suit bonus reduce signature |
| Alert state machine | NPC transitions: unaware → suspicious → alert → searching → combat |

---

## Hacking System

Listed as a point-based skill in the design. No implementation found.

| System | What's needed |
|---|---|
| `HackableComponent` | Tags a terminal/door/ship system as hackable, with difficulty rating |
| `HackingMinigame` | In-world UI puzzle for hacking (can be simple — timed, word, or circuit variant) |
| Outcome effects | Unlock doors, disable ship systems remotely, access restricted data, plant malware |

---

## Energy & Power Management

The design describes a reactor module that constrains the ship loadout. No `ReactorModule` or power budget system was found.

| System | What's needed |
|---|---|
| `ReactorComponent` | Max power output, current draw, efficiency curve |
| `PowerGridSystem` | Sums draw from all active subsystems (weapons, shields, engines, life support); throttles or sheds load when over budget |
| Power priority UI | Player-adjustable sliders to redistribute power between systems (combat: more weapons/shields; travel: more engines) |

---

## Boarding System

Ship docking exists (`DockingSystem`). The full boarding flow — breaching, FPS combat through enemy corridors, and post-capture choices — is not implemented.

| System | What's needed |
|---|---|
| Breaching pod | Deployable one-way docking: burns through hull at target point, creates entry breach |
| Boarding initiation flow | State machine: disable target engines → approach → dock/breach → FPS interior combat |
| Enemy crew spawning | Procedurally spawn defending crew in the target ship's interior on boarding |
| Capture resolution | Post-combat choices: hijack (take control), scrap (strip components), ransom (faction reputation), tow |

---

## Fleet & Capital Ship Combat

Individual ship combat exists. Large-scale fleet coordination and capital ship broadsides are not implemented.

| System | What's needed |
|---|---|
| Fleet command UI | Issue orders to NPC wing-mates (attack, defend, withdraw, form up) |
| Broadside weapons | Fixed-arc batteries that fire in coordinated salvoes |
| NPC pilot AI (ship-scale) | NPC ships use steering behaviours + threat assessment to dogfight and escort |
| Capital ship subsystem targeting | Target specific subsystems (engines, shields, bridge) for precision disabling |
| Fighter carrier operations | Launch/recover fighter wings from carrier bays |

---

## Vehicle Bay & Deployment

Mech locomotion (`mech/`) exists. The vehicle bay system for storing and deploying vehicles from ship bays is not implemented.

| System | What's needed |
|---|---|
| `VehicleBayComponent` | Bay slots, stored vehicle entities, ramp state |
| Vehicle deployment flow | Player selects vehicle in bay UI, ramp opens, vehicle is placed at the ramp exit |
| Vehicle retrieval | Drive back to ship, trigger retrieval interaction, vehicle stowed |
| Ground vehicle data | `VehicleRegistry` with rover, tank, mech loadout configurations |

---

## Player Levelling & Perks

`RealTimeSkillSystem` and `PointSkill` exist. The character level and perk system do not.

| System | What's needed |
|---|---|
| Character level accumulation | Aggregate XP from all real-time skills; milestone grants a character level |
| Skill point allocation | Each character level awards 2–3 points spendable on point-based skills via a UI |
| Perk system | Every 5 character levels, player picks one `PerkDefinition` from a filtered list (eligibility based on skill levels) |
| Perk application | Perks apply passive modifiers to stats, unlock abilities, or modify system behaviour |
| Level-up UI | Screen or overlay showing level-up, skill point award, perk selection |

---

## Alien Species & Pre-FTL Civilisations

`SpeciesDefinition` in `NpcDataRegistry` holds crew species data, but the full alien civilisation model is missing.

| System | What's needed |
|---|---|
| `AlienCivilisationData` | Tech level, culture, economy tier, hostility, government type (per design §4.10) |
| Civilisation registry | Loads 5–8 major species + procedurally generated minor species |
| Pre-FTL detection | Flag planets containing pre-FTL civilisations; disable standard trade/docking UI |
| Interaction choices | Uplift / Observe / Exploit / Ignore — each a quest branch with faction and story consequences |

---

## City & Settlement Generation

NPC schedules exist (`NpcScheduleComponent`). The city/settlement layout generation and population placement are not implemented.

| System | What's needed |
|---|---|
| City layout generator | Landmark district placement (spaceport, commercial, slums) + procedural street fill |
| Building interior generation | Key buildings (shops, cantinas, faction HQs) with functional rooms |
| NPC population seeding | Spawn NPCs with appropriate roles and assign schedule routes through city |
| Day/night cycle | Time-of-day that drives NPC schedule execution and lighting |
| City market integration | Planetary city provides a physical market (`MarketComponent`) with its own stock |

---

## Sector-Level & Galactic Economy

`PlanetaryEconomyManager` and `PlanetaryStockSystem` cover the local tier. The sector and galactic simulation tiers are not implemented.

| System | What's needed |
|---|---|
| `SectorEconomySystem` | Aggregates planetary production/consumption per sector; updates inter-planet trade routes |
| `GalacticEconomySystem` | Daily tick; sets sector-level supply baselines from galaxy-wide demand |
| Trade route simulation | NPC freighters travel between stations; their cargo loads shift local stock |
| Faction economic control | Faction territory affects tax rates, tariffs, and contraband classification |

---

## Companion & Story Quests

The saga system provides the graph structure. Authored content is not yet present.

| System | What's needed |
|---|---|
| Main story arc data | `SagaData` JSON files for the galaxy-spanning narrative |
| Faction quest chains | Per-faction `SagaData` unlocking at reputation thresholds |
| Companion quests | `SagaData` driven by individual crew member backstory hooks (`HookType`) |
| Discovery-triggered quests | `DiscoveryLead` → saga trigger when the player investigates a site |

---

## Rare Astronomical Phenomena (Partial)

Black holes and nebulae are implemented. Several phenomena from design §4.13 are missing.

| Phenomenon | Status | What's needed |
|---|---|---|
| Black holes | Implemented | — |
| Nebulae | Implemented (region type + hazards) | Volumetric visual rendering |
| Pulsars | Missing | Periodic radiation burst events, navigation hazard zone |
| Binary/trinary star systems | Missing | Multi-star orbital dynamics at system generation |
| Neutron stars | Missing | FTL supercharge interaction, unique material spawning |
| Wormholes | Missing | Unstable transit point: entry/exit pair, collapse timer |
| Supernova remnants | Missing | Radiation field, precursor artifact spawning, ruins POI |
| Dyson structures | Missing | Mega-structure POI, associated quest chain |
| Rogue planets | Missing | Interstellar wandering body generation |

---

## Miscellaneous Missing Systems

| System | Notes |
|---|---|
| Grenade system | Grenade item types in the design (frag, EMP, incendiary, flash) — no `GrenadeSystem` or `GrenadeComponent` found |
| Ship exterior customisation | Paint schemes, decals, hull mods — `ShipColorPalette` exists but no player-driven customisation flow |
| Ship room customisation | Player placing/moving room modules and decorations within the interior grid |
| Heavy weapons | Rocket launchers, miniguns — categories in design but no distinct component for shoulder-fired heavy weapons |
| Power armour | Late-game personal armour type with its own locomotion modifiers |
| Encyclopedia content | `EncyclopediaScreen` exists; needs data population (species, factions, locations, equipment) |
| Debug / editor tooling | imgui-java overlay for in-editor spawning, stat inspection, and economy tuning — planned in the design |
