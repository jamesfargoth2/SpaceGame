# Project Galactic Odyssey — Master Game Plan

## 1. Vision

A galaxy-scale space game combining FPS on-foot gameplay with 6DOF ship piloting. Players walk inside their ships, sit in the pilot seat to fly, explore planets on foot or in vehicles, engage in space and ground combat, trade across a living economy, recruit and manage crew, and uncover a galaxy-spanning narrative. Built in Java with libGDX.

---

## 2. Technology Stack

### 2.1 Engine: libGDX + Custom Rendering

| Component | Technology | Why |
|-----------|-----------|-----|
| **Framework** | libGDX 1.12+ (LWJGL3 backend) | Cross-platform, mature, full control over rendering pipeline |
| **Rendering** | Custom deferred pipeline with PBR shaders (OpenGL 4.x) | HDRP-equivalent visual fidelity requires a custom pipeline — libGDX's default forward renderer won't cut it for volumetric lighting, bloom, SSR |
| **Window/Input** | LWJGL3 | Modern windowing, Vulkan-ready path for future |

### 2.2 Networking Stack

| Technology | Purpose | Notes |
|------------|---------|-------|
| **KryoNet** | Transport layer | Fast, Java-native, Kryo serialization |
| **Custom deterministic netcode** | Long-term ideal | Build once core gameplay is locked |

### 2.3 Core Frameworks and Libraries

| System | Technology | Why |
|--------|-----------|-----|
| **Entity Architecture** | Ashley ECS (libGDX) or Artemis-odb | Thousands of ships, NPCs, projectiles need ECS performance |
| **Physics** | gdx-bullet (Bullet wrapper) + custom spatial partitioning | Java Bullet bindings via libGDX, handles rigid body dynamics |
| **AI** | Custom Utility AI + Behavior Trees (gdx-ai) | gdx-ai provides BT framework, steering behaviors, state machines |
| **UI** | Scene2D.UI (libGDX) | Skinnable widget library, table-based layout |
| **Dialogue** | Custom or Ink (Java port) | Narrative scripting with branching |
| **Audio** | OpenAL via libGDX Audio API | 3D positional audio, streaming music |
| **Procedural Generation** | FastNoiseLite (Java) + custom | Planet terrain, asteroid fields, nebulae |
| **Save System** | Kryo binary serialization + SQLite (JDBC) | Complex game state demands structured storage |
| **Database (Server)** | PostgreSQL + Redis (Jedis/Lettuce) | Persistent universe state, economy, player data |
| **Asset Streaming** | Custom asset manager extending libGDX AssetManager | Async loading, reference counting, LOD streaming |
| **Build** | Gradle multi-module | Separate modules for core, desktop, server |
| **Testing** | JUnit 5 + Mockito | Unit and integration tests without GL context |

### 2.4 Key Libraries

- **gdx-ai** — Behavior trees, finite state machines, steering behaviors
- **gdx-bullet** — Bullet physics for 3D collision and dynamics
- **gdx-controllers** — Gamepad, HOTAS, controller support
- **gdx-gltf** — glTF 2.0 model loading with PBR materials
- **imgui-java** — Debug/editor tooling overlay (development builds only)
- **FastNoiseLite** — Procedural noise for terrain and nebulae generation
- **Kryo** — Fast binary serialization for saves and networking

---

## 3. Game Architecture

### 3.1 High-Level Architecture Diagram

```
+-------------------------------------------------------------+
|                      CLIENT (libGDX)                         |
|  +----------+ +----------+ +----------+ +--------------+    |
|  | Rendering| |  Input   | |   UI     | |    Audio     |    |
|  | Pipeline | | Manager  | | Scene2D  | |  (OpenAL)    |    |
|  +----+-----+ +----+-----+ +----+-----+ +------+-------+    |
|       +--------------+-----------+---------------+           |
|                          |                                   |
|              +-----------v-----------+                       |
|              |   Game State Manager  |                       |
|              |  (ECS World + Events) |                       |
|              +-----------+-----------+                       |
|       +----------+-------+-------+----------+                |
|  +----v---+ +----v---+ +v-----+ +v--------+ +v-----------+  |
|  | Ship   | | Combat | | NPC  | | Economy | | Player     |  |
|  | System | | System | |  AI  | |  Sim    | | Inventory  |  |
|  +--------+ +--------+ +------+ +---------+ +------------+  |
|                          |                                   |
|              +-----------v-----------+                       |
|              |   Network Transport   |                       |
|              |     (KryoNet)         |                       |
|              +-----------+-----------+                       |
+---------------------------+----------------------------------+
                            |
               +------------v------------+
               |    DEDICATED SERVER     |
               |  +------------------+   |
               |  | Authoritative    |   |
               |  | Game Simulation  |   |
               |  +--------+---------+   |
               |  +--------v---------+   |
               |  |  World Persistence|  |
               |  |  (PostgreSQL)    |   |
               |  +--------+---------+   |
               |  +--------v---------+   |
               |  |  Economy Engine  |   |
               |  |  (server-side)   |   |
               |  +------------------+   |
               +-------------------------+
```

### 3.2 The Floating Origin Problem (Critical for Space Games)

Standard 32-bit floats lose precision beyond ~10km from origin, causing visible jitter. A galaxy is billions of km wide. This must be solved on day one.

**Solution: Hybrid Coordinate System**

```
Galaxy Map (64-bit double coordinates)
  +-- Sector (each sector = one scene/level)
        +-- Local Space (floating origin, player always near 0,0,0)
              +-- Ship Interior (nested local space via separate btDynamicsWorld)
```

- The **player is always at or near world origin (0,0,0)**. The universe moves around the player.
- When the player moves far from origin, **re-center everything** by subtracting the player's position from all objects and resetting the player to zero.
- Use **64-bit doubles** for the galaxy map / sector map, converting to 32-bit floats only for the active local scene.
- Ship interiors use a **separate Bullet physics world** (`btDynamicsWorld`) — the interior has independent local physics so the player can walk around while the ship moves.

### 3.3 Scene and World Streaming Architecture

```
+---------------------------------------------+
|              Universe Manager               |
|  (64-bit galaxy coordinates, star catalog)  |
+---------------------+-----------------------+
                      |
        +-------------v--------------+
        |       Sector Manager       |
        |  (loads/unloads sectors    |
        |   around player position)  |
        +-------------+--------------+
                      |
    +---------+-------+-------+----------+
    v         v       v       v          v
 Deep      Orbital  Planet  Station   Asteroid
 Space     Space   Surface  Interior   Field
 (sparse)  (LOD    (terrain (separate  (instanced
            rings)  stream)  scene)     chunks)
```

- **Additive Scene Loading** — Each zone (planet surface, station interior, deep space) is a separate scene loaded additively. Only 1-2 active at a time. Managed by a custom SceneManager.
- **LOD Streaming** — Planets go from a dot to sphere with atmosphere to terrain with biomes to ground-level detail as you approach.
- **Hybrid Procedural + Handcrafted** — Generate the galaxy procedurally from a seed, then overlay handcrafted story locations, cities, and points of interest.

---

## 4. Core Systems Design

### 4.1 Player Controller: Dual-Mode (FPS + Ship Pilot)

The player has two primary control states, managed by a state machine:

**State: On Foot (FPS Mode)**
- Kinematic character controller using Bullet's `btKinematicCharacterController` or custom kinematic body
- Gravity-relative movement (supports walking inside rotating stations or ships)
- Interaction system (doors, terminals, NPCs, loot, vehicle entry)
- Combat: firearms, melee, grenades, abilities

**State: Piloting (Ship Mode)**
- Camera switches to cockpit view or third-person ship view
- Input maps to 6DOF ship physics (pitch, yaw, roll, thrust, strafe, vertical)
- HUD changes to ship instruments, radar, targeting
- Ship systems become interactive (weapons, shields, engines, subsystems)

**Transition:** Player walks to pilot seat, interaction prompt appears, animation plays, control state switches, input rebinds, HUD swaps. The ship interior remains loaded but the camera and input context change.

### 4.2 Ship System Architecture

```java
public class ShipData {
    public String shipId;
    public ShipHullDefinition hull;           // Base stats, hardpoint slots, size class
    public List<WeaponMount> weaponMounts;    // Hardpoint type + equipped weapon
    public List<ArmorPlate> armorSlots;       // Per-section armor (fore, aft, port, starboard)
    public ShieldGenerator shield;
    public EngineModule engine;
    public ReactorModule reactor;             // Power budget constrains loadout
    public CargoHold cargo;
    public List<VehicleBay> vehicleBays;      // Stored land vehicles
    public CrewManifest crew;                 // NPC crew assignments
    public InteriorLayout interior;           // Customized room layout
    public List<Decoration> decorations;      // Cosmetic items placed by player
}
```

**Ship Building (Interior + Exterior):**
- Hull defines external shape + hardpoint positions + room grid
- Interior uses a **modular room system** — rooms snap to a grid within the hull boundary
- Room types: Bridge, Engineering, Medbay, Armory, Crew Quarters, Cargo Bay, Vehicle Bay, Brig
- Each room has functional stations (repair console, weapon rack, medical station)
- Decorations are placed freely within rooms
- Exterior customization: paint, decals, hull modifications, antenna/sensor arrays

**Vehicle Storage and Launch:**
- Ships with Vehicle Bays can store ground vehicles (rovers, tanks, mechs)
- Landing on a planet opens the vehicle bay, player selects vehicle, deploys via ramp/drop
- Vehicles have their own simplified component system (weapons, armor, speed)

### 4.3 Combat Architecture

Combat spans three domains with a unified damage model:

```
+---------------------------------------------+
|            Unified Damage Model             |
|  Attack -> Hit Detection -> Shield Check -> |
|  Armor Mitigation -> Hull/Health Damage ->  |
|  Subsystem Damage -> Status Effects         |
+---------------------------------------------+
        |              |              |
   +----v----+   +-----v-----+  +----v----+
   |  Space  |   |    Air    |  | Ground  |
   | Combat  |   |  Combat   |  | Combat  |
   +---------+   +-----------+  +---------+
   |Capital  |   |Fighters in|  |FPS on   |
   |ship     |   |atmosphere |  |foot     |
   |broadsides|  |Gunships   |  |Vehicles |
   |Fighter  |   |AA from    |  |Turrets  |
   |dogfights|   |ground     |  |Ship CAS |
   |Boarding |   |Ship CAS   |  |Siege    |
   +---------+   +-----------+  +---------+
```

**Large-Scale Combat:**
- Capital ships exchange broadside fire, fighters dogfight, bombers make attack runs
- Player can command NPC crew to manage turrets, shields, repairs
- Boarding: disable shields, dock/breach, FPS combat inside enemy ship, capture or scrap

**Small-Scale Combat:**
- FPS: standard shooter mechanics with RPG stat modifiers
- Ground vehicles: tank battles, troop transports, recon
- Air support: ships can provide close air support to ground forces

**Ship Boarding Flow:**
1. Target ship, disable engines (EMP, precision shots)
2. Approach, docking clamp or breaching pod
3. Transition to FPS inside enemy ship interior
4. Fight through corridors, reach bridge
5. Hijack (take control), Scrap (strip components), Ransom (faction reputation), Tow (store in player garage)

### 4.4 Weapon and Gear Arsenal

Modular component system rather than hand-designing hundreds of unique weapons:

```
Weapon = Base Frame + Barrel + Ammo Type + Mod Slots + Material Quality

Example:
  Plasma Rifle = Rifle Frame + Long Barrel + Plasma Cell + [Scope, Stabilizer] + Rare Alloy
  Scrap Pistol = Pistol Frame + Short Barrel + Ballistic + [None] + Salvaged Metal
```

**Weapon Categories (Ship):**
Ballistic Cannons, Laser Arrays, Plasma Turrets, Missile Launchers, Railguns, EMP Projectors, Mining Lasers, Tractor Beams, Point Defense Systems, Torpedo Tubes, Broadside Batteries, Flak Cannons

**Weapon Categories (Personal):**
Pistols, Rifles, Shotguns, SMGs, Sniper Rifles, Melee (blades, staves, hammers), Grenades (frag, EMP, incendiary, flash), Heavy Weapons (rocket launchers, miniguns), Energy Weapons (laser, plasma, particle), Alien Tech Weapons

**Armor (Ship):**
Hull Plating (ballistic resist), Energy Shields (energy resist), Composite Armor (balanced), Reactive Armor (explosive resist), Stealth Coating (reduced signature), Regenerative Plating (slow self-repair)

**Armor (Personal):**
Light Suit, Medium Armor, Heavy Exosuit, EVA Suit (space), Hazard Suit (toxic/radiation), Power Armor (late-game)

**Material Quality Tiers:**
Salvaged, Common, Refined, Military, Experimental, Alien, Precursor (legendary)

### 4.5 Resource and Crafting System

```
Resource Tiers:
  Common     — Iron, Copper, Silicon, Carbon (found everywhere)
  Uncommon   — Titanium, Lithium, Tungsten (asteroid mining, specific planets)
  Rare       — Iridium, Neutronium, Dark Crystals (dangerous regions, guarded deposits)
  Exotic     — Zero-Point Cells, Quantum Foam, Void Essence (anomalies, precursor sites)
  Alien      — Species-specific bio-materials, tech fragments (trade or conflict)

Gathering Methods:
  Mining (ship-mounted or handheld) — Asteroids, planet surfaces
  Salvaging — Wrecked ships, derelict stations, battlefield debris
  Trading — NPC merchants, player markets
  Looting — Enemy drops, mission rewards
  Harvesting — Biological resources from planets, alien specimens
  Refining — Combine/upgrade raw materials at stations or ship refinery module
```

### 4.6 Player RPG Stat System (Skyrim + Fallout Hybrid)

**Real-Time Leveled Skills (Skyrim-style — improve by doing):**

| Skill | Leveled By |
|-------|-----------|
| Firearms | Shooting enemies with ballistic weapons |
| Energy Weapons | Shooting enemies with energy weapons |
| Melee | Melee combat hits |
| Piloting | Flying ships, performing maneuvers, combat piloting |
| Athletics | Sprinting, jumping, climbing |
| Stealth | Remaining undetected while near enemies |
| Trading | Completing buy/sell transactions |
| Mining | Extracting resources |
| Repair | Fixing ship components and equipment |

**Point-Based Skills (Fallout-style — spend skill points on level-up):**

| Skill | Effect |
|-------|--------|
| Medicine | Healing effectiveness, crafting medical items, crew health buffs |
| Hacking | Bypass security systems, access locked data, disable ship systems |
| Engineering | Unlock advanced ship mods, improve crafting quality, power efficiency |
| Leadership | Max crew size, crew XP gain rate, crew morale bonuses |
| Diplomacy | Better prices, unlock dialogue options, faction reputation gains |
| Science | Scan efficiency, anomaly analysis, exotic material processing |
| Tactics | Squad command effectiveness, boarding success rate |
| Survival | Resist environmental hazards, food/oxygen efficiency |

**Leveling Flow:**
1. Real-time skills gain XP through use, skill level increases passively
2. When enough total skill XP accumulates, player gains a Character Level
3. Each Character Level grants 2-3 Skill Points to spend on point-based skills
4. Every 5 Character Levels, choose a Perk (powerful passive ability)

### 4.7 NPC Crew System

```
CrewMember:
  Name, Species, Background, Portrait
  Role: Pilot | Gunner | Engineer | Medic | Marine | Scientist | Navigator
  Level: Recruit -> Crewman -> Specialist -> Veteran -> Officer -> Commander
  Stats: Accuracy, Repair, Medical, Morale, Loyalty
  XP: Gains from missions, combat, successful tasks
  Perks: Unlocked at promotion thresholds
  Morale: Affected by pay, living conditions, leadership skill, combat losses
  Assignment: Which station/room they're posted to on the ship

Promotion unlocks:
  Specialist — Can train other crew in their role
  Veteran — Autonomous decision-making in combat (less micromanagement)
  Officer — Can command a wing/squad, unlocks officer quarters
  Commander — Can captain a secondary ship in your fleet
```

### 4.8 Economy Simulation

```
+--------------------------------------------------+
|               Galactic Economy                    |
|  (aggregate supply/demand across all sectors)     |
|  Tick rate: once per in-game day                  |
+----------------------+---------------------------+
                       |
         +-------------v--------------+
         |      Sector Economy        |
         |  (regional supply/demand,  |
         |   faction control,         |
         |   trade route modifiers)   |
         |  Tick rate: hourly         |
         +-------------+--------------+
                       |
         +-------------v--------------+
         |     Planetary Economy      |
         |  (production, consumption, |
         |   population, industry)    |
         |  Tick rate: every 10 min   |
         +-------------+--------------+
                       |
         +-------------v--------------+
         |      Local Economy         |
         |  (individual stations,     |
         |   shops, black markets)    |
         |  Tick rate: real-time      |
         +--------------+-------------+
```

Each node produces and consumes goods. Prices are driven by supply and demand. Player actions (flooding a market, destroying a supply route, completing trade missions) ripple upward through the tiers.

**Smuggling:** Certain goods are illegal in certain faction territories. Smuggling pays well but risks faction reputation loss and hostile patrols. Requires stealth loadout or bribery.

### 4.9 Faction and Reputation System

```
Faction Reputation Scale:
  -100 Hostile     — KOS, no docking, military pursuit
  -50  Unfriendly  — Denied most services, higher prices
   0   Neutral     — Basic trading and docking
  +25  Friendly    — Discounts, access to faction missions
  +50  Allied      — Access to military hardware, faction bases
  +75  Honored     — Unique ships, crew recruits, story missions
  +100 Exalted     — Faction leadership influence, endgame content

Actions that modify reputation:
  Complete faction missions: +5 to +20
  Trade with faction: +1 per transaction
  Destroy faction ships: -10 to -50
  Smuggle contraband: -5 to -30 if caught
  Aid faction enemy: -10 to -25
  Diplomacy skill checks: variable bonus
```

**Faction Types:**
- Major galactic powers (3-5 large civilizations)
- Minor factions (pirates, mercenary guilds, trade consortiums, religious orders)
- Alien species governments (from primitive to advanced)
- Lawless zones (no faction control, anything goes)

### 4.10 Alien Species Design

Design at least 5-8 major species, each with:

| Attribute | Range |
|-----------|-------|
| Tech Level | Pre-Industrial, Industrial, Space Age, FTL, Post-Singularity |
| Culture | Militaristic / Diplomatic / Mercantile / Isolationist / Nomadic / Hive |
| Economy | Impoverished / Developing / Prosperous / Wealthy / Decadent |
| Hostility | Pacifist / Cautious / Neutral / Aggressive / Xenophobic |
| Government | Democracy / Theocracy / Monarchy / Corporate / Collective / Anarchy |

**Pre-FTL Civilizations:**
- Found on procedurally generated planets
- Cannot be traded with normally (Prime Directive-style ethical choice)
- Player can choose to uplift, observe, exploit, or ignore
- Consequences ripple through faction reputation and story

### 4.11 Missions, Jobs, and Quests

**Procedural Job Board (always available):**
- Cargo Hauling: Move goods from A to B (legal or smuggling variant)
- Bounty Hunting: Track and eliminate/capture a target
- Mercenary Contracts: Participate in faction conflicts
- Exploration Surveys: Scan uncharted systems, anomalies, planets
- Mining Contracts: Gather X amount of resource Y
- Escort Missions: Protect a convoy from pirates
- Salvage Operations: Recover cargo/data from wrecks in dangerous areas

**Handcrafted Quest Lines:**
- Main story arc (galaxy-spanning narrative)
- Faction-specific story chains (unlock at reputation thresholds)
- Companion quest lines (deep crew member backstories)
- Discovery-triggered quests (find an anomaly, quest begins)

### 4.12 Planetary Exploration

**Large Cities:**
- Handcrafted districts with procedural fill (landmark buildings hand-placed, side streets generated)
- Districts: Spaceport, Commercial, Residential, Industrial, Government, Slums/Underground
- Interiors for key buildings (shops, quest givers, cantinas, faction HQs)
- NPC population with day/night schedules
- City-specific missions, markets, and reputation

**Small Settlements/Villages:**
- Procedurally placed with biome-appropriate architecture
- 5-15 NPCs with basic trading and quest-giving
- May be faction-aligned, independent, or alien

**Wilderness:**
- Procedural terrain with biome diversity (desert, jungle, ice, volcanic, ocean, toxic)
- Resource deposits, ruins, caves, creature habitats
- Environmental hazards requiring appropriate gear

### 4.13 Rare Astronomical Phenomena

- **Pulsars** — Periodic radiation bursts, navigation hazard, rare resources nearby
- **Black Holes** — Gravitational lensing, time dilation effects, high-risk/high-reward exploration
- **Nebulae** — Sensor disruption, hidden pirate bases, rare gas harvesting
- **Binary/Trinary Star Systems** — Complex orbits, unique planet conditions
- **Rogue Planets** — Wandering between systems, extremely rare encounters
- **Neutron Stars** — Supercharge FTL drives (risky), unique materials
- **Wormholes** — Shortcuts across the galaxy, unstable, may collapse
- **Supernova Remnants** — Former civilizations, precursor artifacts, dangerous radiation
- **Dyson Structures** — Evidence of post-singularity civilizations
- **Anomalies** — Unexplainable events, quest triggers, exotic resources

---

## 5. Technical Risk Register

| Risk | Severity | Mitigation |
|------|----------|------------|
| Floating-point precision at scale | Critical | Floating origin + 64-bit coords — solve in Phase 1 |
| Custom rendering pipeline complexity | High | Start with forward PBR, evolve to deferred incrementally; use gdx-gltf for PBR material loading |
| Seamless space-to-ground transition | High | LOD streaming + scene transitions with loading disguised as atmosphere entry |
| Multiplayer at galaxy scale | High | Spatial partitioning — only sync objects near each player |
| AI performance with many NPCs | High | Ashley/Artemis ECS for NPC simulation, LOD AI (full behavior near player, simplified at distance) |
| Scope creep | Critical | Strict phase gating — each phase must be playable before moving on |
| GC pressure in Java | Medium | Object pooling (libGDX Pool), avoid allocations in hot paths, profile with VisualVM |
| Memory/asset streaming | High | Custom AssetManager with async loading + aggressive LOD + occlusion culling |
| Economy balance | Medium | Simulate economy offline before integrating, expose tuning tools |
| Ship interior physics while flying | Medium | Separate btDynamicsWorld per interior — interior has its own physics isolated from world space |
| No built-in editor | Medium | Build lightweight imgui-java debug tools; use Tiled or custom JSON scene format for level editing |

---

## 6. Key Architectural Principles

1. **Data-Driven Everything** — Ship stats, weapons, species, factions, resources, missions — all defined in JSON or YAML data files. Never hardcode game content.

2. **Event-Driven Communication** — Systems talk through an event bus, not direct references. The combat system fires `ShipDamagedEvent`; the UI, audio, and VFX systems listen independently. This prevents spaghetti.

3. **Server-Authoritative for Multiplayer** — The server owns all game state. Clients predict and interpolate. Never trust the client.

4. **Build Modularly, Test Independently** — Each system (economy, combat, crew, crafting) should function in an isolated JUnit test before integration. No system should require a GL context for logic testing.

5. **Pool and Reuse** — Java's garbage collector can cause frame hitches. Use libGDX's `Pool<T>` for vectors, matrices, events, particles, and any object allocated per-frame. Avoid `new` in update loops.

6. **Dispose Everything** — Every libGDX resource (`Texture`, `Model`, `ShaderProgram`, `FrameBuffer`, `SpriteBatch`, `ModelBatch`) must be properly disposed. Implement `Disposable` on any class that owns GL resources.

---

## 7. First Week Action Items

**Day 1-2:** Set up Gradle project with core/desktop/server modules. Configure Git (`.gitignore` for build artifacts, `.gradle/`, IDE files). Create package structure (`core`, `ship`, `combat`, `player`, `economy`, `npc`, `ui`, `networking`). Add libGDX, gdx-bullet, gdx-ai, Ashley dependencies.

**Day 3-4:** Implement floating origin system. Create a test with two objects 1,000,000 units apart — verify no jitter. Build the 64-bit coordinate manager with explicit double-to-float conversion at the boundary.

**Day 5-6:** Basic 6DOF ship controller. WASD + QE for roll, mouse for pitch/yaw. Get a placeholder ship flying in space with a skybox. Make it feel good — this is your core verb.

**Day 7:** FPS character controller inside a box (placeholder ship interior). Walk around. Add a "pilot seat" trigger. Pressing E switches from FPS to ship control. This is the foundational transition the whole game rests on.

**End of Week 1 Goal:** You can walk inside a ship, sit in the pilot seat, fly the ship in space, get up, and walk around again. If this feels good, you have a foundation. If it doesn't, iterate until it does.
