---
name: procgen-ship-interior
description: >
  Enforces procedural ship interior layout generation using bow-to-stern deck
  topology, hull-class-scaled room graphs, ship-specific room types (bridge,
  engine room, reactor core, cargo hold, brig, armoury, medbay, crew quarters,
  hangar), gravity-direction tagging per deck, airlock placement between
  pressurised sections, section-state propagation from the derelict system,
  interior navigation mesh flagging, NPC patrol seeding, and per-hull-class
  floor-count and room-density scaling for a libGDX 3D space game. Use this
  skill whenever writing or modifying: ship interior generation for boarding
  missions, walkable ship compartments, FPS-mode shipboard exploration, ship
  section connectivity, interior airlock placement, gravity direction in ship
  interiors, or any code that produces a traversable interior for a ship of
  any class. Read together with procgen-ship-generation (hull stats),
  procgen-ship-hull-geometry (exterior shape), and procgen-dungeon-interior
  (the general room-graph primitives this skill builds on top of).
  Also triggers when adding boarding mechanics, interior hazards, crew patrol
  routes inside ships, or loot/salvage room placement aboard any vessel.
---

# Procedural Ship Interior Generation

## Relationship to procgen-dungeon-interior

Ship interiors share the same BSP / MST room-graph primitives from
`procgen-dungeon-interior`, but add ship-specific constraints:

- **Topology is bow→stern**, not free 2D. Rooms connect along a deck axis.
- **Gravity direction** varies by ship design (usually deck-perpendicular,
  but zero-g in certain bays and sections when power is off).
- **Critical rooms are positionally fixed** (bridge always at bow,
  engineering always at stern; this is a ship law, not a preference).
- **Airlocks** appear between pressurised sections and at hull exits.
- **Section states** propagate from `procgen-derelict-wreck` when the ship
  is a wreck — alive ships have all sections INTACT.
- **Hull class drives room count** much more tightly than building archetypes.

---

## Ship Interior Room Types

```java
public enum ShipRoomType {

    // ── COMMAND / NAVIGATION ───────────────────────────────────────────────
    BRIDGE,              // helmsman, navigator, captain's chair; always at bow
    CIC,                 // Combat Information Centre; large-ship only, adjacent to bridge
    TACTICAL_STATION,    // weapons control consoles; frigate+
    SENSOR_ARRAY_ROOM,   // large ship sensor control; screens, operators
    COMMUNICATIONS_ROOM, // comms consoles, encryption gear, antenna controls

    // ── PROPULSION / POWER ─────────────────────────────────────────────────
    ENGINE_ROOM,         // main drive hardware; always at stern; hot, loud
    REACTOR_CORE,        // power source; irradiation risk; access restricted
    FUEL_STORAGE,        // tanks and feed lines; fire/explosion risk
    THRUSTER_CONTROL,    // RCS/manoeuvring thruster management
    POWER_RELAY_ROOM,    // circuit panels, breakers, power distribution

    // ── CREW ───────────────────────────────────────────────────────────────
    CREW_QUARTERS,       // bunks, lockers; number scales with crew complement
    OFFICERS_QUARTERS,   // private cabin; 1–3 per ship depending on size
    CAPTAINS_CABIN,      // single, near bridge; private, locker, desk
    MESS_HALL,           // communal dining; scales with crew size
    GALLEY,              // kitchen; adjacent to mess hall
    RECREATION_ROOM,     // gym equipment, game tables; larger ships only
    SHOWER_LAUNDRY,      // sanitation; small room, adjacent to crew quarters

    // ── MEDICAL ────────────────────────────────────────────────────────────
    MEDBAY,              // sickbay; surgical table, beds; corvette+
    QUARANTINE_POD,      // sealed isolation; cruiser+
    MORGUE_LOCKER,       // body storage; destroyer+

    // ── CARGO / STORAGE ────────────────────────────────────────────────────
    CARGO_HOLD,          // large open bay; pallets, containers, crane
    CARGO_LOCK,          // pressurised staging area for EVA cargo transfer
    SUPPLY_ROOM,         // consumables, spare parts, tools
    ARMORY,              // ship weapons, crew equipment, secured
    VAULT_STRONG_ROOM,   // secure cargo; private courier ships, flagships

    // ── HANGAR / FLIGHT OPS ────────────────────────────────────────────────
    HANGAR_BAY,          // internal small-craft storage; carrier+, large cruiser+
    FLIGHT_DECK,         // launch/recovery area; carrier only
    MAINTENANCE_HANGAR,  // fighter/shuttle maintenance; carrier+
    CREW_LOCKER_FLIGHT,  // pilot suits, helmets, egress equipment

    // ── SECURITY / DETENTION ───────────────────────────────────────────────
    BRIG,                // prisoner cells; frigate+
    SECURITY_STATION,    // guard post, weapon locker, monitoring console
    INTERROGATION_ROOM,  // single chair, one-way glass

    // ── ENGINEERING / SYSTEMS ─────────────────────────────────────────────
    LIFE_SUPPORT,        // O₂/CO₂ scrubbers, atmosphere control; critical system
    SHIELD_GENERATOR_ROOM,// shield emitter hardware; corvette+
    SENSOR_CORE,         // passive/active sensor hardware; near bow
    DAMAGE_CONTROL_STATION,// damage assessment consoles, repair teams

    // ── AIRLOCKS / TRANSITIONS ────────────────────────────────────────────
    AIRLOCK_MAIN,        // primary external airlock; 1 per ship minimum
    AIRLOCK_SECONDARY,   // additional exit point; destroyer+
    DOCKING_COLLAR_ROOM, // pressurised collar for ship-to-ship connection
    EVA_PREP_ROOM,       // suit-up area adjacent to external airlock

    // ── INFRASTRUCTURE ────────────────────────────────────────────────────
    CORRIDOR_MAIN,       // primary running corridor, bow to stern
    CORRIDOR_CROSS,      // lateral passage connecting port/starboard
    MAINTENANCE_CRAWLWAY,// tight passage for systems access
    VERTICAL_SHAFT,      // ladder/lift between decks; tall ships only
}
```

---

## Room Count by Hull Class

```java
public class ShipInteriorConfig {

    public static ShipInteriorSpec specFor(HullClass cls) {
        return switch (cls) {
            case SHUTTLE     -> new ShipInteriorSpec(1, 2,  3,   false, false, false);
            case FIGHTER     -> new ShipInteriorSpec(1, 2,  4,   false, false, false);
            case CORVETTE    -> new ShipInteriorSpec(1, 3,  8,   false, false, false);
            case FRIGATE     -> new ShipInteriorSpec(2, 4,  18,  false, false, true);
            case DESTROYER   -> new ShipInteriorSpec(2, 5,  30,  false, false, true);
            case CRUISER     -> new ShipInteriorSpec(3, 7,  55,  false, true,  true);
            case BATTLECRUISER->new ShipInteriorSpec(4, 8,  90,  false, true,  true);
            case BATTLESHIP  -> new ShipInteriorSpec(5, 10, 150, true,  true,  true);
            case CARRIER     -> new ShipInteriorSpec(5, 12, 200, true,  true,  true);
            case FREIGHTER   -> new ShipInteriorSpec(2, 4,  20,  false, false, false);
            case TANKER      -> new ShipInteriorSpec(2, 3,  14,  false, false, false);
            case MINING_BARGE-> new ShipInteriorSpec(1, 3,  12,  false, false, false);
            default          -> new ShipInteriorSpec(2, 4,  20,  false, false, false);
        };
    }
}

public class ShipInteriorSpec {
    public int     deckCount;        // floors / gravity decks
    public int     bowToSternDecks;  // how many sections along the main axis
    public int     targetRoomCount;
    public boolean hasHangar;
    public boolean hasCIC;
    public boolean hasBrig;
}
```

---

## Interior Generation Pipeline

```java
public class ShipInteriorGenerator {

    public GeneratedShipInterior generate(ShipInteriorConfig cfg,
                                           GeneratedShip ship, Random rng) {
        GeneratedShipInterior interior = new GeneratedShipInterior();
        ShipInteriorSpec spec = ShipInteriorConfig.specFor(ship.hullClass);

        // 1. Build the bow→stern deck axis with fixed-position mandatory rooms
        interior.decks = buildDeckAxis(spec, ship, rng);

        // 2. Fill each deck section with context-appropriate rooms
        for (DeckSection section : interior.decks) {
            fillSection(section, spec, ship, rng);
        }

        // 3. Connect rooms via main corridor + cross corridors
        buildCorridorNetwork(interior, spec);

        // 4. Place airlocks at hull exit points
        placeAirlocks(interior, ship, rng);

        // 5. Tag gravity direction per room
        assignGravityDirections(interior, ship);

        // 6. Seed NPC patrol points
        seedNPCSpawns(interior, ship, rng);

        // 7. Place loot and interactables
        placeLootAndInteractables(interior, ship, cfg, rng);

        // 8. Flag for navmesh rebuild
        interior.navmeshDirty = true;

        return interior;
    }
}
```

---

## Bow→Stern Deck Layout

```java
public class DeckAxisBuilder {

    /**
     * The ship spine is divided into sections from bow to stern.
     * Mandatory rooms are pinned at fixed section positions;
     * optional rooms fill the gaps.
     */
    public Array<DeckSection> build(ShipInteriorSpec spec, GeneratedShip ship,
                                     Random rng) {
        Array<DeckSection> decks = new Array<>();
        int sections = spec.bowToSternDecks;

        for (int i = 0; i < sections; i++) {
            float t = i / (float)(sections - 1); // 0 = bow, 1 = stern
            DeckSection ds = new DeckSection();
            ds.bowSternT   = t;
            ds.deckIndex   = i;

            // Mandatory room placement by position
            if (i == 0)          ds.mandatoryType = ShipRoomType.BRIDGE;
            else if (i == 1 && spec.hasCIC)
                                 ds.mandatoryType = ShipRoomType.CIC;
            else if (i == sections - 1)
                                 ds.mandatoryType = ShipRoomType.ENGINE_ROOM;
            else if (i == sections - 2)
                                 ds.mandatoryType = ShipRoomType.REACTOR_CORE;
            else if (t > 0.4f && t < 0.6f)
                                 ds.mandatoryType = ShipRoomType.LIFE_SUPPORT; // midship
            else                 ds.mandatoryType = null; // optional fill

            decks.add(ds);
        }
        return decks;
    }
}
```

---

## Section Room Pools

```java
public class ShipSectionRoomPools {

    public WeightedPool<ShipRoomType> poolFor(float t, ShipInteriorSpec spec,
                                               ShipRole role, Random rng) {
        WeightedPool<ShipRoomType> pool = new WeightedPool<>();

        if (t < 0.25f) {
            // Bow: command, sensors, communications
            pool.add(ShipRoomType.TACTICAL_STATION,    25);
            pool.add(ShipRoomType.SENSOR_ARRAY_ROOM,   20);
            pool.add(ShipRoomType.COMMUNICATIONS_ROOM, 20);
            pool.add(ShipRoomType.CAPTAINS_CABIN,      15);
            pool.add(ShipRoomType.SENSOR_CORE,         20);

        } else if (t < 0.55f) {
            // Midship: crew, cargo, security
            pool.add(ShipRoomType.CREW_QUARTERS,       30);
            pool.add(ShipRoomType.MESS_HALL,           15);
            pool.add(ShipRoomType.CARGO_HOLD,          role == ShipRole.MERCHANT ? 40 : 15);
            pool.add(ShipRoomType.ARMORY,              10);
            pool.add(ShipRoomType.MEDBAY,              12);
            pool.add(ShipRoomType.OFFICERS_QUARTERS,    8);
            pool.add(ShipRoomType.RECREATION_ROOM,     spec.deckCount >= 3 ? 10 : 0);
            if (spec.hasBrig) pool.add(ShipRoomType.BRIG, 8);
            if (spec.hasHangar) pool.add(ShipRoomType.HANGAR_BAY, 12);

        } else {
            // Stern: propulsion, engineering, damage control
            pool.add(ShipRoomType.THRUSTER_CONTROL,    25);
            pool.add(ShipRoomType.POWER_RELAY_ROOM,    20);
            pool.add(ShipRoomType.FUEL_STORAGE,        25);
            pool.add(ShipRoomType.DAMAGE_CONTROL_STATION, 15);
            pool.add(ShipRoomType.SHIELD_GENERATOR_ROOM, 15);
        }
        return pool;
    }
}
```

---

## Airlock Placement

```java
public class AirlockPlacer {

    /**
     * Every ship has at least one external airlock.
     * Position on the hull determines interior placement.
     * Boarding tubes connect at AIRLOCK_MAIN.
     */
    public void place(GeneratedShipInterior interior, GeneratedShip ship, Random rng) {
        ShipInteriorSpec spec = ShipInteriorConfig.specFor(ship.hullClass);

        // Primary airlock: always exists, midship dorsal or port
        DeckSection midSection = interior.decks.get(interior.decks.size / 2);
        addAirlock(interior, midSection, ShipRoomType.AIRLOCK_MAIN,
                   AirlockFacing.DORSAL);

        // Secondary airlocks for larger ships
        if (spec.deckCount >= 2) {
            // Port/starboard airlocks for destroyer+
            addAirlock(interior, midSection, ShipRoomType.AIRLOCK_SECONDARY,
                       AirlockFacing.PORT);
            addAirlock(interior, midSection, ShipRoomType.AIRLOCK_SECONDARY,
                       AirlockFacing.STARBOARD);
        }

        // Docking collar: ships that can dock ship-to-ship
        if (ship.hullClass.ordinal() >= HullClass.CORVETTE.ordinal()) {
            DeckSection bowSection = interior.decks.get(0);
            addAirlock(interior, bowSection, ShipRoomType.DOCKING_COLLAR_ROOM,
                       AirlockFacing.BOW);
        }

        // EVA prep room always adjacent to primary airlock
        addAdjacentRoom(interior, midSection, ShipRoomType.EVA_PREP_ROOM,
                        ShipRoomType.AIRLOCK_MAIN);
    }
}
```

---

## Gravity Direction Assignment

```java
public class GravityAssigner {

    /**
     * Assign gravity vectors per room.
     * Most rooms: gravity perpendicular to deck (downward in ship frame).
     * Hangar bay: zero-g when bay doors open.
     * Cargo hold: zero-g on ships without spin or thrust gravity.
     * Reactor core: zero-g maintenance mode.
     */
    public void assign(GeneratedShipInterior interior, GeneratedShip ship) {
        Vector3 shipDown = new Vector3(0, -1, 0); // ship's internal "down"
        boolean hasArtGravity = ship.hullClass.ordinal() >= HullClass.FRIGATE.ordinal();

        for (DeckSection section : interior.decks) {
            for (InteriorRoom room : section.rooms) {
                switch (room.type) {
                    case HANGAR_BAY:
                    case FLIGHT_DECK:
                    case CARGO_LOCK:
                        room.gravityVector = Vector3.Zero; // zero-g when bay is open
                        room.hasZeroG      = true;
                        break;
                    case REACTOR_CORE:
                        room.gravityVector = Vector3.Zero; // zero-g maintenance
                        room.hasZeroG      = true;
                        break;
                    case ENGINE_ROOM:
                        // Thrust gravity if ship is accelerating; zero-g otherwise
                        room.gravityVector  = shipDown.cpy();
                        room.gravitySource  = GravitySource.THRUST;
                        break;
                    default:
                        room.gravityVector  = hasArtGravity ? shipDown.cpy() : Vector3.Zero;
                        room.hasZeroG       = !hasArtGravity;
                        break;
                }
            }
        }
    }
}
```

---

## NPC Patrol Seeding

```java
public class ShipInteriorNPCSeed {

    public void seed(GeneratedShipInterior interior, GeneratedShip ship,
                      ShipRole role, Random rng) {
        for (DeckSection section : interior.decks) {
            for (InteriorRoom room : section.rooms) {
                switch (room.type) {
                    case BRIDGE:
                        room.npcSpawns.add(new NPCSpawn(NPCRole.CAPTAIN,
                            captainChairPos(room), NPCBehaviour.STATIONARY));
                        room.npcSpawns.add(new NPCSpawn(NPCRole.PILOT,
                            helmPos(room), NPCBehaviour.STATIONARY));
                        room.npcSpawns.add(new NPCSpawn(NPCRole.NAVIGATOR,
                            navPos(room), NPCBehaviour.STATIONARY));
                        break;

                    case ENGINE_ROOM:
                        room.npcSpawns.add(new NPCSpawn(NPCRole.ENGINEER,
                            engineConsolePos(room), NPCBehaviour.WORK_STATION));
                        if (rng.nextFloat() < 0.5f)
                            room.npcSpawns.add(new NPCSpawn(NPCRole.ENGINEER,
                                randomInteriorPos(room, rng), NPCBehaviour.PATROL_ROOM));
                        break;

                    case BRIG:
                        room.npcSpawns.add(new NPCSpawn(NPCRole.GUARD,
                            nearDoor(room), NPCBehaviour.PATROL_DOOR));
                        if (rng.nextFloat() < 0.4f)
                            room.npcSpawns.add(new NPCSpawn(NPCRole.PRISONER,
                                cellPos(room), NPCBehaviour.RESTRAINED));
                        break;

                    case CARGO_HOLD:
                        if (role == ShipRole.MERCHANT) {
                            int workers = 1 + rng.nextInt(3);
                            for (int i = 0; i < workers; i++)
                                room.npcSpawns.add(new NPCSpawn(NPCRole.WORKER,
                                    randomInteriorPos(room, rng), NPCBehaviour.WORK_STATION));
                        } else {
                            room.npcSpawns.add(new NPCSpawn(NPCRole.GUARD,
                                nearDoor(room), NPCBehaviour.PATROL_DOOR));
                        }
                        break;

                    case MEDBAY:
                        room.npcSpawns.add(new NPCSpawn(NPCRole.MEDIC,
                            nearCrashCart(room), NPCBehaviour.PATROL_ROOM));
                        break;

                    case CREW_QUARTERS:
                        int sleepers = 1 + rng.nextInt(3);
                        for (int i = 0; i < sleepers; i++)
                            room.npcSpawns.add(new NPCSpawn(NPCRole.SOLDIER,
                                atBunk(room, i), NPCBehaviour.RESTING));
                        break;

                    case ARMORY:
                        room.npcSpawns.add(new NPCSpawn(NPCRole.GUARD,
                            nearDoor(room), NPCBehaviour.PATROL_DOOR));
                        break;
                }
            }
        }
    }
}
```

---

## Loot Placement

```java
public class ShipLootPlacer {

    public void place(GeneratedShipInterior interior, GeneratedShip ship,
                       float conditionFactor, Random rng) {
        for (DeckSection section : interior.decks) {
            for (InteriorRoom room : section.rooms) {
                float baseValue = roomLootValue(room.type, ship.hullClass,
                                                 ship.faction, conditionFactor);
                if (baseValue <= 0f) continue;

                // Containers
                if (hasContainers(room.type)) {
                    int count = containerCount(room.type);
                    for (int i = 0; i < count; i++) {
                        LootContainer lc = new LootContainer();
                        lc.type         = containerTypeFor(room.type);
                        lc.position     = randomWallPos(room, new Vector2(0.6f, 0.4f), rng);
                        lc.lootTable    = lootTableFor(room.type, ship, baseValue, rng);
                        lc.isLocked     = shouldLock(room.type);
                        room.interactables.add(lc);
                    }
                }

                // Data terminals (mission-critical data, ship logs)
                if (hasTerminal(room.type)) {
                    room.interactables.add(new DataTerminal(
                        atConsolePos(room), TerminalContentType.SHIP_LOG));
                }
            }
        }
    }

    private float roomLootValue(ShipRoomType type, HullClass cls,
                                 FactionData faction, float condition) {
        float base = switch (type) {
            case VAULT_STRONG_ROOM    -> 2000f;
            case ARMORY               -> 500f;
            case CAPTAINS_CABIN       -> 300f;
            case REACTOR_CORE         -> 400f;  // salvage components
            case MEDBAY               -> 250f;
            case CARGO_HOLD           -> 150f * cls.cargoTonnageFactor();
            case OFFICERS_QUARTERS    -> 150f;
            case SUPPLY_ROOM          -> 80f;
            case CREW_QUARTERS        -> 40f;
            case ENGINE_ROOM          -> 200f;  // engine components
            case SHIELD_GENERATOR_ROOM-> 300f;
            default                   -> 20f;
        };
        return base * condition * (faction != null ? faction.techLevel : 0.5f);
    }
}
```

---

## Integration with Derelict System

```java
// When a ship is a derelict, section states come from procgen-derelict-wreck.
// Propagate section states into the ship interior rooms.

public class DerelictSectionStateApplier {

    public void apply(GeneratedShipInterior interior,
                       Map<String, SectionState> sectionStates) {
        for (DeckSection section : interior.decks) {
            SectionState state = sectionStates.getOrDefault(
                section.id, SectionState.INTACT);
            for (InteriorRoom room : section.rooms) {
                room.sectionState   = state;
                room.hasAtmosphere  = (state == SectionState.INTACT);
                room.isTraversable  = (state != SectionState.DESTROYED);
                // Zero-g if ship power is off AND section is breached/intact
                if (state == SectionState.BREACHED)
                    room.hasZeroG = true;
            }
        }
    }
}
```

---

## Physics World Isolation

```java
// Ship interiors run in a dedicated btDynamicsWorld.
// See CLAUDE.md architectural rule 6.
// The interior world moves with the ship's rigid body in the main world.

public class ShipInteriorPhysics {
    private btDynamicsWorld interiorWorld;

    public void activate(ShipEntity ship) {
        if (interiorWorld != null) return;
        btBroadphaseInterface broadphase           = new btDbvtBroadphase();
        btDefaultCollisionConfiguration cfg        = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher           = new btCollisionDispatcher(cfg);
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        interiorWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, cfg);
        // Gravity from ship physics: thrust acceleration or zero-g
        interiorWorld.setGravity(ship.currentInteriorGravity());
        addInteriorCollision(ship.interior);
    }

    public void step(float delta, GeneratedShip ship) {
        if (interiorWorld == null) return;
        // Keep gravity synced with ship's real acceleration
        interiorWorld.setGravity(ship.currentInteriorGravity());
        interiorWorld.stepSimulation(delta, 5, 1f / 60f);
    }
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Bridge not at bow | Bridge is always the first section (t=0); never randomise its position |
| Engine room not at stern | ENGINE_ROOM and REACTOR_CORE are always the last two sections; pinned |
| Life support randomly placed | Life support is midship (t=0.4–0.6); it needs to be equidistant from bow and stern |
| Uniform gravity in all rooms | Hangar bay, cargo lock, and reactor core are zero-g; engine room uses thrust gravity |
| Airlock with no EVA prep room | Every AIRLOCK_MAIN must have an adjacent EVA_PREP_ROOM |
| Fighter with 30+ rooms | Fighter has 3–5 rooms max; room count scales tightly with hull class |
| Interior physics in main world | Ship interior btDynamicsWorld must be isolated (CLAUDE.md rule 6); always |
| No section state propagation | On derelict ships, always apply DerelictSectionStateApplier after generating the interior |
| Crew NPCs all stationary | Mix STATIONARY (captain, pilot) with PATROL_ROOM (engineers, guards off-duty) |
| Brig on shuttles/fighters | BRIG only on FRIGATE and above; smaller ships don't have a dedicated cell block |
