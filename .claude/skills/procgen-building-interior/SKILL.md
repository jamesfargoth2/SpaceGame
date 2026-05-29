---
name: procgen-building-interior
description: >
  Enforces procedural building interior generation using floor-plan room
  graphs, room-type assignment by building archetype and floor level,
  corridor connectivity, stairwell and lift placement, furniture and prop
  dressing per room function, NPC spawn point seeding, loot and interactive
  object placement, and physics-world isolation per building for a libGDX 3D
  space game. Use this skill whenever writing or modifying: building interior
  layout generation, room connectivity graphs, interior navigation meshes,
  staircase placement, furniture/prop spawning inside structures, NPC patrol
  routes inside buildings, lootable container placement, shop inventory
  display, interior lighting rigs, or any code that makes a building
  walkable from the inside. Also triggers when the player enters any
  building on a planet surface — shop, tavern, hospital, prison, casino,
  factory, temple, embassy, cult compound, or infrastructure building.
---

# Procedural Building Interior Generation

## Interior Generation Pipeline

```
1. Floor plan per floor  → divide floor plate into rooms via BSP or room-graph
2. Vertical circulation  → align stairs/lifts across all floors
3. Room purpose assign   → tag each room by archetype + floor level
4. Corridor network      → connect rooms; add loops to avoid dead ends
5. Door placement        → doorways at room–corridor junctions; lock gating
6. Furniture dressing    → spawn furniture prefabs per room purpose
7. Prop & loot seeding   → containers, interactables, collectibles
8. NPC spawn points      → guard posts, work stations, patrol waypoints
9. Lighting rig          → ambient level + fixture props per room type
10. NavMesh bake flag    → mark interior for nav graph rebuild on first enter
```

---

## Room Types — Complete Catalogue

```java
public enum InteriorRoomType {

    // ── UNIVERSAL (any building) ────────────────────────────────────────────
    ENTRANCE_HALL,        // reception, lobby, airlock
    CORRIDOR,             // connecting passage
    STAIRWELL,            // vertical circulation
    LIFT_SHAFT,           // elevator (4+ floor buildings)
    STORAGE_CLOSET,       // small utility storage
    TOILET_WASHROOM,      // sanitary facilities
    MAINTENANCE_ROOM,     // boiler/HVAC/electrical panel
    SECURE_CORRIDOR,      // locked-door passage between zones
    SAFE_ROOM,            // reinforced panic room, no windows

    // ── RESIDENTIAL ────────────────────────────────────────────────────────
    BEDROOM,
    LIVING_ROOM,
    KITCHEN,
    DINING_ROOM,
    STUDY,
    SERVANTS_QUARTERS,    // small spartan bedroom for staff
    WINE_CELLAR,          // underground storage, bottles/barrels
    TROPHY_ROOM,          // displays of wealth, hunt trophies, art
    MASTER_SUITE,         // large bedroom + private washroom, mansion/noble
    NURSERY,              // crib, toys, soft furnishings

    // ── COMMERCIAL / SHOP ──────────────────────────────────────────────────
    SHOP_FLOOR,           // display shelves, counter, till
    BACK_ROOM,            // crates, stock shelves, safe
    OFFICE,               // desk, terminal, filing
    VAULT,                // reinforced room: bank, strongbox, combination lock
    COUNTING_ROOM,        // bank interior: ledgers, accountant desks, cash drawers
    AUCTION_STAGE,        // podium, item display plinth, bidder seats
    PAWN_DISPLAY,         // cluttered shelving of mixed valuables
    APOTHECARY_BENCH,     // pharmacy: vials, mortar & pestle, medicine cabinet
    GUN_RACK_ROOM,        // weapons dealer: locked racks, ammo cabinet
    DATA_SERVER_ALCOVE,   // data broker: server racks, cold blue lighting, terminal
    REPAIR_BENCH,         // repair shop: tools, parts bins, lift platform
    MAP_ROOM,             // cartographer: large table, charts, surveying instruments
    SMUGGLERS_HOLD,       // hidden room: false wall entrance, stashed contraband

    // ── HOSPITALITY / ENTERTAINMENT ────────────────────────────────────────
    COMMON_ROOM,          // tavern: tables, bar counter, fireplace
    PRIVATE_BOOTH,        // enclosed table for discreet meetings
    KITCHEN_TAVERN,       // cooking equipment, food stores
    GUEST_ROOM,           // bed, chest, basin
    CASINO_FLOOR,         // slot machines, card tables, roulette
    HIGH_ROLLER_LOUNGE,   // private gaming, velvet furniture, security guard
    BETTING_BOOTH,        // small teller window for wagers
    BATHHOUSE_POOL,       // large pool, tiled, steam, benches
    STEAM_ROOM,           // small chamber, intense heat, wooden benches
    DRESSING_ROOM,        // backstage, costume racks, mirrors
    STAGE_PERFORMANCE,    // raised performance area, curtain, footlights
    AUDIENCE_CHAMBER,     // tiered seating facing stage or pit
    FIGHTING_PIT_FLOOR,   // sand-floored combat arena, drainage, spectator rail
    BARROOM,              // cantina/bar: long counter, stools, bottles wall
    PRIVATE_ROOM_BROTHEL, // bed, vanity, curtained alcove
    MADAMS_OFFICE,        // management room for brothel/pleasure dome
    VIP_LOUNGE,           // plush seating, private bar, mood lighting

    // ── MEDICAL / SCIENCE ──────────────────────────────────────────────────
    TRIAGE_WARD,          // clinic/hospital: beds in rows, curtains
    OPERATING_THEATER,    // surgical table, overhead light array, instrument trays
    RECOVERY_ROOM,        // individual patient beds, monitoring equipment
    MEDICAL_STORAGE,      // drug cabinet, supply shelves, locked
    WAITING_ROOM,         // chairs, number board, reception desk
    LABORATORY,           // research: benches, microscopes, specimen jars
    CONTAINMENT_CELL,     // quarantine/prison: sealed room, observation window
    SPECIMEN_ROOM,        // xenobiology: tanks of preserved aliens, dissection table
    MORGUE_SLAB,          // refrigerated drawers, autopsy table, drain
    GENETICS_SUITE,       // DNA sequencer, centrifuge, sterile prep bench
    CLEAN_ROOM,           // pressurised, gowns, ultra-filtered air, airlock entry
    COLD_STORAGE_MEDICAL, // organ/vaccine storage, sub-zero shelving
    PSYCHIATRIC_WARD_ROOM,// padded walls option, observation hatch, restraint chair

    // ── EDUCATION / CULTURE ────────────────────────────────────────────────
    CLASSROOM,            // student desks, chalkboard/holodisplay, teacher desk
    LECTURE_HALL,         // tiered seating, podium, projection screen
    TRAINING_FLOOR,       // combat/skill training: mats, dummies, weapon racks
    READING_ROOM,         // library: shelves floor to ceiling, reading tables
    ARCHIVE_STACKS,       // dense shelving, rolling ladder, restricted sections
    DISPLAY_GALLERY,      // museum: exhibits in cases, plaques, ambient lighting
    MAP_GALLERY,          // cartography museum: large framed maps, globes
    RESTORATION_STUDIO,   // museum: workbenches, magnifiers, conservation tools
    PERFORMANCE_HALL,     // theater: full stage, fly tower, orchestra pit
    PROJECTION_BOOTH,     // holotheater: projector array, control console
    REHEARSAL_ROOM,       // theater backstage: mirrors, practice space

    // ── CIVIC / GUILD ──────────────────────────────────────────────────────
    COUNCIL_CHAMBER,      // government: large table, seats, podium
    RECORDS_ROOM,         // filing cabinets, shelves, terminal
    HOLDING_CELL,         // single-person lockup, bars, bench
    ARMOURY,              // weapon racks, ammo crates
    DIPLOMATIC_RECEPTION, // embassy: formal lounge, flags, protocol furniture
    CIPHER_ROOM,          // embassy/intel: soundproofed, encryption terminals
    HEARING_ROOM,         // courthouse: judge bench, witness box, public gallery
    JURY_ROOM,            // private deliberation, long table, no windows
    EVIDENCE_VAULT,       // courthouse: sealed storage, logged intake desk
    PROPAGANDA_STUDIO,    // broadcast booth, camera rig, greenscreen or backdrop
    PRINTING_FLOOR,       // press room: large printing machines, ink vats
    SORTING_ROOM,         // postal: conveyor, pigeonhole array, labels
    SECURE_ARCHIVE,       // government: fireproof cabinets, retina scanner door

    // ── INDUSTRIAL / FACTORY ───────────────────────────────────────────────
    PRODUCTION_FLOOR,     // machines, conveyor, workstations
    CONTROL_ROOM,         // terminals, large display screens
    BREAK_ROOM,           // chairs, food dispenser
    LOADING_BAY,          // open bay, forklifts, pallet stacks
    FERMENTATION_ROOM,    // brewery: large vats, temperature gauges, smell
    BOTTLING_LINE,        // brewery: conveyor, bottling machines, crates
    DISTILLATION_COLUMN,  // tall room, pipe-wrapped column, condensate trays
    LOOM_ROOM,            // textile: rows of looms, thread spools overhead
    DYEING_VAT_ROOM,      // textile: large heated vats, colour-stained walls
    PRESS_ROOM_INDUSTRIAL,// printing: rotary presses, paper rolls
    CHEMICAL_MIXING_ROOM, // vat room, safety showers, hazmat suits
    QUALITY_CONTROL,      // inspection benches, measuring tools, reject bin
    COLD_CHAIN_ROOM,      // refrigerated conveyor, insulated storage
    FUEL_PUMP_ROOM,       // tanks, pump controls, spill containment trays
    WATER_PUMP_HALL,      // large centrifugal pumps, pressure gauges
    FILTRATION_HALL,      // filter beds, clarifier tanks, chemical dosing
    GENERATOR_ROOM,       // large diesel/plasma generators, loud, hot
    TRANSFORMER_VAULT,    // high-voltage equipment, no windows, warning signs

    // ── MILITARY / SECURITY ────────────────────────────────────────────────
    BUNK_ROOM,            // bunks, footlockers
    MESS_HALL,            // long tables, serving hatch
    BRIEFING_ROOM,        // chairs facing display wall
    ARMOURY_MILITARY,     // weapon lockers, heavier security
    INTERROGATION_ROOM,   // single chair, table, overhead light, one-way glass
    PRISON_CELL,          // bunks, sealed door, minimal furnishing
    WARDEN_OFFICE,        // desk, security monitor bank, key cabinet
    GUARD_POST_ROOM,      // small room at checkpoint, monitoring console
    MOTOR_POOL,           // vehicle parking/service within a building
    COMMUNICATIONS_OPS,   // radio/comms array, operator consoles, scrambler
    WAR_ROOM,             // large map table, tactical displays, command chairs
    MEDICAL_BAY_MILITARY, // field hospital inside barracks, resupply focus

    // ── RELIGIOUS / CULTURAL ───────────────────────────────────────────────
    SANCTUARY,            // altar, pews/prayer mats, candles/incense
    VESTRY,               // priest quarters, ritual objects, robing area
    CRYPT,                // sarcophagi, offerings, dim
    NAVE,                 // main body of temple: processional space
    CONFESSION_BOOTH,     // small enclosed cubicle pair
    RELIQUARY_DISPLAY,    // glass cases with sacred objects, guarded
    INITIATION_CHAMBER,   // cult: ritual room, symbolic decor, restricted
    MEDITATION_CELL,      // monastery: bare room, cushion, icon
    SCRIPTORIUM,          // monastery: copying desks, inkwells, illuminated manuscripts
    ABBOT_QUARTERS,       // monastery: private bedroom/study, slightly more comfortable
    NOVICE_DORMITORY,     // monastery: shared sparse sleeping
    REFECTORY,            // monastery/religious: communal dining, long benches
    ORACLE_CHAMBER_ROOM,  // smoke, carved symbols, trance-induction furniture
    CATACOMBS_PASSAGE,    // underground ossuary tunnel, niches with bones

    // ── SPACEPORT / TRANSIT ────────────────────────────────────────────────
    DEPARTURE_LOUNGE,     // seating rows, departure board
    CUSTOMS_CHECKPOINT,   // scanners, inspection tables, guards
    CARGO_PROCESSING,     // conveyor belts, scanners, crates
    CREW_QUARTERS,        // bunks, personal lockers
    IMMIGRATION_HALL,     // queue barriers, officer booths, biometric scanners
    QUARANTINE_BAY,       // sealed room, medical observation
    PASSENGER_CONCOURSE,  // shops and seating between gates
    FLIGHT_OPERATIONS,    // ATC-style consoles, panoramic window, approach data

    // ── INFRASTRUCTURE ─────────────────────────────────────────────────────
    PUMP_HALL,            // large mechanical pumps, pipe manifolds
    SERVER_ROOM,          // racks of servers, cold aisle/hot aisle, raised floor
    BROADCAST_CENTER,     // comms hub: large antenna control, frequency displays
    RELAY_CONTROL_ROOM,   // signal relay: telemetry console, patch bay
    ELECTRICAL_SWITCH_ROOM,// fuse boards, circuit panels, safety gear
    TANK_MONITORING_ROOM, // pressure gauges, overflow alerts, valve controls
}
```

---

## Room Purpose by Floor & Archetype (Extended)

```java
public WeightedPool<InteriorRoomType> poolFor(BuildingArchetype archetype,
                                               int floor, int totalFloors) {
    float rel = floor / (float) Math.max(1, totalFloors - 1);

    switch (archetype) {
        case SHOP:           return shopPool(floor);
        case GENERAL_STORE:  return shopPool(floor);
        case INN_TAVERN:     return tavernPool(floor, rel);
        case CANTINA:        return cantinaPool(floor);
        case CASINO:         return casinoPool(floor, rel);
        case BROTHEL:        return brothelPool(floor, rel);
        case BATHHOUSE:      return bathousePool(floor);
        case FIGHTING_PIT:   return fightingPitPool();
        case WEAPONS_DEALER: return weaponsDealerPool(floor);
        case ARMOUR_SHOP:    return shopPool(floor); // same as shop with gun_rack variant
        case PHARMACY:       return pharmacyPool(floor);
        case PAWNSHOP:       return pawnPool(floor);
        case BANK:           return bankPool(floor, rel);
        case AUCTION_HOUSE:  return auctionPool(floor);
        case DATA_BROKER_DEN:return dataBrokerPool();
        case BLACK_MARKET_FRONT: return blackMarketPool(floor);
        case FENCE_SHOP:     return fencePool(floor);
        case REPAIR_SHOP:    return repairShopPool(floor);
        case CLINIC:         return clinicPool(floor);
        case HOSPITAL:       return hospitalPool(floor, rel);
        case RESEARCH_LAB:   return researchLabPool(floor, rel);
        case GENETICS_LAB:   return geneticsLabPool(floor);
        case XENOBIOLOGY_INSTITUTE: return xenobioPool(floor);
        case QUARANTINE_FACILITY:   return quarantinePool(floor);
        case PSYCHIATRIC_WARD:      return psychiatricPool(floor, rel);
        case MORGUE:         return morguePool();
        case PHARMACEUTICAL_PLANT:  return pharmPlantPool(floor, rel);
        case SCHOOL:         return schoolPool(floor);
        case ACADEMY:        return academyPool(floor, rel);
        case TRAINING_HALL:  return trainingHallPool();
        case LIBRARY_ARCHIVE:return libraryPool(floor, rel);
        case MUSEUM:         return museumPool(floor, rel);
        case THEATER:        return theaterPool(floor, rel);
        case HOLOTHEATER:    return holotheatrePool(floor, rel);
        case ARENA_GRAND:
        case ARENA_SMALL:    return arenaPool(floor, rel);
        case BREWERY:        return breweryPool(floor, rel);
        case TEXTILE_MILL:   return textilePool(floor, rel);
        case PRINTING_WORKS: return printingPool(floor, rel);
        case CHEMICAL_PLANT: return chemicalPlantPool(floor, rel);
        case WATER_TREATMENT:return waterTreatPool(floor);
        case FUEL_DEPOT_SURFACE:
        case FUEL_DEPOT_SPACEPORT: return fuelDepotPool();
        case ELECTRICAL_HUB: return electricalHubPool(floor);
        case COMMUNICATIONS_HUB:   return commsHubPool(floor, rel);
        case BARRACKS:       return barracksPool(floor, rel);
        case PRISON:
        case DETENTION_CENTER:     return prisonPool(floor, rel);
        case MILITARY_OUTPOST:     return militaryOutpostPool(floor);
        case EMBASSY:        return embassyPool(floor, rel);
        case COURTHOUSE:     return courthousePool(floor, rel);
        case GUILD_HALL:
        case CITY_HALL:      return civicPool(floor, rel);
        case PROPAGANDA_MINISTRY:  return propagandaPool(floor);
        case POSTAL_OFFICE:  return postalPool(floor);
        case CUSTOMS_HOUSE:  return customsPool(floor);
        case TEMPLE:         return templePool(floor, rel, totalFloors);
        case MONASTERY:      return monasteryPool(floor, rel);
        case CULT_COMPOUND:  return cultPool(floor, rel);
        case MAUSOLEUM:      return mausoleumPool(floor);
        case ORACLE_CHAMBER: return oraclePool();
        case TERMINAL:       return spaceportTerminalPool(floor, rel);
        case HANGAR:         return hangarPool();
        case STARSHIP_REPAIR_DOCK: return repairDockPool(floor);
        case SEWAGE_PUMP_STATION:  return sewagePumpPool();
        case SIGNAL_RELAY_TOWER:   return relayTowerPool();
        default:             return genericResidentialPool(floor, rel);
    }
}

// Key pool implementations
private WeightedPool<InteriorRoomType> bankPool(int floor, float rel) {
    WeightedPool<InteriorRoomType> p = new WeightedPool<>();
    if (floor == 0) {
        p.add(InteriorRoomType.ENTRANCE_HALL,     30);
        p.add(InteriorRoomType.COUNTING_ROOM,     40);
        p.add(InteriorRoomType.WAITING_ROOM,      20);
        p.add(InteriorRoomType.VAULT,             10);
    } else {
        p.add(InteriorRoomType.OFFICE,            50);
        p.add(InteriorRoomType.VAULT,             20);
        p.add(InteriorRoomType.SAFE_ROOM,         15);
        p.add(InteriorRoomType.RECORDS_ROOM,      15);
    }
    return p;
}

private WeightedPool<InteriorRoomType> casinoPool(int floor, float rel) {
    WeightedPool<InteriorRoomType> p = new WeightedPool<>();
    if (floor == 0) {
        p.add(InteriorRoomType.CASINO_FLOOR,      70);
        p.add(InteriorRoomType.BARROOM,           20);
        p.add(InteriorRoomType.BETTING_BOOTH,     10);
    } else if (rel > 0.7f) {
        p.add(InteriorRoomType.HIGH_ROLLER_LOUNGE,50);
        p.add(InteriorRoomType.VIP_LOUNGE,        30);
        p.add(InteriorRoomType.WARDEN_OFFICE,     20); // casino security
    } else {
        p.add(InteriorRoomType.OFFICE,            40);
        p.add(InteriorRoomType.VAULT,             30);
        p.add(InteriorRoomType.STORAGE_CLOSET,    30);
    }
    return p;
}

private WeightedPool<InteriorRoomType> prisonPool(int floor, float rel) {
    WeightedPool<InteriorRoomType> p = new WeightedPool<>();
    if (floor == 0) {
        p.add(InteriorRoomType.GUARD_POST_ROOM,   30);
        p.add(InteriorRoomType.WARDEN_OFFICE,     20);
        p.add(InteriorRoomType.ARMOURY_MILITARY,  15);
        p.add(InteriorRoomType.HOLDING_CELL,      35);
    } else {
        p.add(InteriorRoomType.PRISON_CELL,       55);
        p.add(InteriorRoomType.CONTAINMENT_CELL,  15);
        p.add(InteriorRoomType.INTERROGATION_ROOM,15);
        p.add(InteriorRoomType.BUNK_ROOM,         10); // guards
        p.add(InteriorRoomType.SECURE_CORRIDOR,    5);
    }
    return p;
}

private WeightedPool<InteriorRoomType> hospitalPool(int floor, float rel) {
    WeightedPool<InteriorRoomType> p = new WeightedPool<>();
    if (floor == 0) {
        p.add(InteriorRoomType.ENTRANCE_HALL,     25);
        p.add(InteriorRoomType.WAITING_ROOM,      30);
        p.add(InteriorRoomType.TRIAGE_WARD,       30);
        p.add(InteriorRoomType.OFFICE,            15);
    } else if (rel > 0.7f) {
        p.add(InteriorRoomType.OPERATING_THEATER, 35);
        p.add(InteriorRoomType.CLEAN_ROOM,        25);
        p.add(InteriorRoomType.MEDICAL_STORAGE,   20);
        p.add(InteriorRoomType.OFFICE,            20);
    } else {
        p.add(InteriorRoomType.RECOVERY_ROOM,     40);
        p.add(InteriorRoomType.TRIAGE_WARD,       25);
        p.add(InteriorRoomType.MEDICAL_STORAGE,   20);
        p.add(InteriorRoomType.OFFICE,            15);
    }
    return p;
}

private WeightedPool<InteriorRoomType> embassyPool(int floor, float rel) {
    WeightedPool<InteriorRoomType> p = new WeightedPool<>();
    if (floor == 0) {
        p.add(InteriorRoomType.ENTRANCE_HALL,     30);
        p.add(InteriorRoomType.DIPLOMATIC_RECEPTION, 40);
        p.add(InteriorRoomType.WAITING_ROOM,      30);
    } else if (rel > 0.6f) {
        p.add(InteriorRoomType.CIPHER_ROOM,       35);
        p.add(InteriorRoomType.SECURE_ARCHIVE,    35);
        p.add(InteriorRoomType.SAFE_ROOM,         30);
    } else {
        p.add(InteriorRoomType.OFFICE,            50);
        p.add(InteriorRoomType.COUNCIL_CHAMBER,   25);
        p.add(InteriorRoomType.RECORDS_ROOM,      25);
    }
    return p;
}

private WeightedPool<InteriorRoomType> breweryPool(int floor, float rel) {
    WeightedPool<InteriorRoomType> p = new WeightedPool<>();
    if (floor == 0) {
        p.add(InteriorRoomType.LOADING_BAY,       30);
        p.add(InteriorRoomType.FERMENTATION_ROOM, 40);
        p.add(InteriorRoomType.BOTTLING_LINE,     30);
    } else {
        p.add(InteriorRoomType.DISTILLATION_COLUMN,35);
        p.add(InteriorRoomType.CONTROL_ROOM,      30);
        p.add(InteriorRoomType.BREAK_ROOM,        20);
        p.add(InteriorRoomType.OFFICE,            15);
    }
    return p;
}

private WeightedPool<InteriorRoomType> monasteryPool(int floor, float rel) {
    WeightedPool<InteriorRoomType> p = new WeightedPool<>();
    if (floor == 0) {
        p.add(InteriorRoomType.SANCTUARY,         40);
        p.add(InteriorRoomType.NAVE,              30);
        p.add(InteriorRoomType.ENTRANCE_HALL,     30);
    } else if (rel > 0.7f) {
        p.add(InteriorRoomType.ABBOT_QUARTERS,    40);
        p.add(InteriorRoomType.SCRIPTORIUM,       30);
        p.add(InteriorRoomType.SECURE_ARCHIVE,    30);
    } else {
        p.add(InteriorRoomType.NOVICE_DORMITORY,  30);
        p.add(InteriorRoomType.MEDITATION_CELL,   25);
        p.add(InteriorRoomType.REFECTORY,         20);
        p.add(InteriorRoomType.SCRIPTORIUM,       15);
        p.add(InteriorRoomType.VESTRY,            10);
    }
    return p;
}

private WeightedPool<InteriorRoomType> commsHubPool(int floor, float rel) {
    WeightedPool<InteriorRoomType> p = new WeightedPool<>();
    if (floor == 0) {
        p.add(InteriorRoomType.SERVER_ROOM,       40);
        p.add(InteriorRoomType.BROADCAST_CENTER,  35);
        p.add(InteriorRoomType.OFFICE,            25);
    } else {
        p.add(InteriorRoomType.RELAY_CONTROL_ROOM,50);
        p.add(InteriorRoomType.MAINTENANCE_ROOM,  30);
        p.add(InteriorRoomType.SECURE_CORRIDOR,   20);
    }
    return p;
}
```

---

## Furniture Dressing — Extended

```java
private Array<FurnitureSpec> furnitureSpecFor(InteriorRoomType type, float wealth) {
    switch (type) {

        case VAULT:
            return Array.with(
                new FurnitureSpec("strongbox_large",    vec2(1.0f, 0.8f), true,  1.00f),
                new FurnitureSpec("wall_safe",          vec2(0.4f, 0.1f), true,  0.80f),
                new FurnitureSpec("counting_table",     vec2(1.2f, 0.6f), false, 0.70f)
            );

        case CASINO_FLOOR:
            return Array.with(
                new FurnitureSpec("card_table",         vec2(1.4f, 0.8f), false, 1.00f),
                new FurnitureSpec("slot_machine",       vec2(0.6f, 0.8f), true,  0.90f),
                new FurnitureSpec("roulette_wheel",     vec2(1.6f, 1.6f), false, 0.70f),
                new FurnitureSpec("bar_stool",          vec2(0.4f, 0.4f), true,  0.80f)
            );

        case TRIAGE_WARD:
            return Array.with(
                new FurnitureSpec("hospital_bed",       vec2(0.9f, 2.0f), true,  1.00f),
                new FurnitureSpec("privacy_curtain",    vec2(0.1f, 2.0f), true,  0.90f),
                new FurnitureSpec("iv_stand",           vec2(0.3f, 0.3f), false, 0.80f),
                new FurnitureSpec("crash_cart",         vec2(0.6f, 0.4f), true,  0.60f)
            );

        case OPERATING_THEATER:
            return Array.with(
                new FurnitureSpec("surgical_table",     vec2(0.8f, 2.0f), false, 1.00f),
                new FurnitureSpec("overhead_light_array",vec2(0.8f, 0.8f),false, 1.00f),
                new FurnitureSpec("instrument_tray",    vec2(0.6f, 0.4f), true,  1.00f),
                new FurnitureSpec("anesthesia_machine", vec2(0.5f, 0.5f), false, 0.90f)
            );

        case PRISON_CELL:
            return Array.with(
                new FurnitureSpec("cell_bunk",          vec2(0.9f, 1.9f), true,  1.00f),
                new FurnitureSpec("cell_toilet",        vec2(0.5f, 0.5f), true,  0.90f),
                new FurnitureSpec("cell_desk",          vec2(0.7f, 0.4f), true,  0.60f)
            );

        case INTERROGATION_ROOM:
            return Array.with(
                new FurnitureSpec("metal_chair",        vec2(0.5f, 0.5f), false, 1.00f),
                new FurnitureSpec("interrogation_table",vec2(1.0f, 0.5f), false, 0.90f),
                new FurnitureSpec("one_way_mirror",     vec2(0.1f, 1.5f), true,  0.80f),
                new FurnitureSpec("overhead_lamp",      vec2(0.3f, 0.3f), false, 0.90f)
            );

        case FERMENTATION_ROOM:
            return Array.with(
                new FurnitureSpec("fermentation_vat",   vec2(2.0f, 2.0f), true,  1.00f),
                new FurnitureSpec("temperature_gauge",  vec2(0.2f, 0.6f), true,  0.90f),
                new FurnitureSpec("sample_bench",       vec2(1.0f, 0.5f), true,  0.70f)
            );

        case BATHHOUSE_POOL:
            return Array.with(
                new FurnitureSpec("stone_bench",        vec2(0.5f, 1.5f), true,  1.00f),
                new FurnitureSpec("pool_step",          vec2(1.5f, 0.5f), true,  0.90f),
                new FurnitureSpec("oil_lamp_wall",      vec2(0.2f, 0.2f), true,  0.80f)
            );

        case FIGHTING_PIT_FLOOR:
            return Array.with(
                new FurnitureSpec("weapon_rack_ringside",vec2(0.5f,1.5f), true,  0.70f),
                new FurnitureSpec("spectator_rail",     vec2(3.0f, 0.3f), true,  1.00f),
                new FurnitureSpec("sand_pit_marker",    vec2(4.0f, 4.0f), false, 1.00f)
            );

        case DIPLOMATIC_RECEPTION:
            return Array.with(
                new FurnitureSpec("formal_sofa",        vec2(2.0f, 0.8f), true,  0.90f * wealth),
                new FurnitureSpec("flag_stand",         vec2(0.2f, 0.3f), true,  1.00f),
                new FurnitureSpec("portrait_frame",     vec2(0.1f, 1.2f), true,  0.80f),
                new FurnitureSpec("side_table",         vec2(0.6f, 0.6f), true,  0.70f)
            );

        case SCRIPTORIUM:
            return Array.with(
                new FurnitureSpec("copying_desk",       vec2(1.2f, 0.6f), true,  1.00f),
                new FurnitureSpec("inkwell_stand",      vec2(0.3f, 0.3f), true,  0.90f),
                new FurnitureSpec("manuscript_shelf",   vec2(0.4f, 1.8f), true,  0.80f)
            );

        case CIPHER_ROOM:
            return Array.with(
                new FurnitureSpec("encryption_terminal",vec2(0.8f, 0.6f), true,  1.00f),
                new FurnitureSpec("signal_scrambler",   vec2(0.4f, 0.4f), true,  0.90f),
                new FurnitureSpec("paper_shredder",     vec2(0.4f, 0.4f), true,  0.70f)
            );

        case SERVER_ROOM:
            return Array.with(
                new FurnitureSpec("server_rack",        vec2(0.6f, 1.8f), true,  1.00f),
                new FurnitureSpec("raised_floor_tile",  vec2(4.0f, 4.0f), false, 1.00f),
                new FurnitureSpec("cooling_unit",       vec2(0.8f, 0.5f), true,  0.90f)
            );

        case ORACLE_CHAMBER_ROOM:
            return Array.with(
                new FurnitureSpec("oracle_throne",      vec2(1.2f, 1.2f), false, 1.00f),
                new FurnitureSpec("incense_brazier",    vec2(0.4f, 0.4f), false, 0.90f),
                new FurnitureSpec("carved_symbol_floor",vec2(4.0f, 4.0f), false, 0.80f),
                new FurnitureSpec("smoke_vent_cover",   vec2(0.6f, 0.6f), true,  1.00f)
            );

        case HIGH_ROLLER_LOUNGE:
            return Array.with(
                new FurnitureSpec("velvet_chair",       vec2(0.8f, 0.8f), false, 1.00f),
                new FurnitureSpec("private_card_table", vec2(1.2f, 1.2f), false, 0.90f),
                new FurnitureSpec("champagne_stand",    vec2(0.3f, 0.3f), true,  wealth > 0.6f ? 1.0f : 0.3f),
                new FurnitureSpec("security_guard_post",vec2(0.5f, 0.5f), true,  0.80f)
            );

        default:
            return genericFurniture(type, wealth);
    }
}
```

---

## NPC Spawn Points — Extended

```java
public void seed(InteriorRoom room, BuildingConfig cfg, Random rng) {
    switch (room.type) {

        case VAULT:
        case COUNTING_ROOM:
            room.npcSpawns.add(new NPCSpawn(NPCRole.GUARD,
                nearDoor(room), NPCBehaviour.PATROL_DOOR));
            break;

        case CASINO_FLOOR:
            int dealers = 2 + rng.nextInt(4);
            for (int i = 0; i < dealers; i++)
                room.npcSpawns.add(new NPCSpawn(NPCRole.DEALER,
                    atTable(room, i), NPCBehaviour.STATIONARY));
            int gamblers = 3 + rng.nextInt(6);
            for (int i = 0; i < gamblers; i++)
                room.npcSpawns.add(new NPCSpawn(NPCRole.CIVILIAN,
                    randomInteriorPos(room, rng), NPCBehaviour.SEATED));
            break;

        case INTERROGATION_ROOM:
            room.npcSpawns.add(new NPCSpawn(NPCRole.OFFICER,
                behindTable(room), NPCBehaviour.STATIONARY));
            if (rng.nextFloat() < 0.5f)
                room.npcSpawns.add(new NPCSpawn(NPCRole.PRISONER,
                    inChair(room), NPCBehaviour.RESTRAINED));
            break;

        case TRIAGE_WARD:
            room.npcSpawns.add(new NPCSpawn(NPCRole.MEDIC,
                nearCrashCart(room), NPCBehaviour.PATROL_ROOM));
            int patients = 1 + rng.nextInt(4);
            for (int i = 0; i < patients; i++)
                room.npcSpawns.add(new NPCSpawn(NPCRole.CIVILIAN,
                    atBed(room, i), NPCBehaviour.RESTING));
            break;

        case PRISON_CELL:
            int prisoners = 1 + rng.nextInt(2);
            for (int i = 0; i < prisoners; i++)
                room.npcSpawns.add(new NPCSpawn(NPCRole.PRISONER,
                    atBunk(room, i), NPCBehaviour.RESTING));
            break;

        case FIGHTING_PIT_FLOOR:
            // Fighters in pit; spectators at rail
            room.npcSpawns.add(new NPCSpawn(NPCRole.ENEMY,
                pitCentrePos(room), NPCBehaviour.PATROL_ROOM));
            int spectators = 2 + rng.nextInt(6);
            for (int i = 0; i < spectators; i++)
                room.npcSpawns.add(new NPCSpawn(NPCRole.CIVILIAN,
                    atRail(room, i), NPCBehaviour.WANDER_ROOM));
            break;

        case DIPLOMATIC_RECEPTION:
            room.npcSpawns.add(new NPCSpawn(NPCRole.OFFICIAL,
                headOfRoom(room), NPCBehaviour.STATIONARY));
            room.npcSpawns.add(new NPCSpawn(NPCRole.GUARD,
                nearDoor(room), NPCBehaviour.PATROL_DOOR));
            break;

        case FERMENTATION_ROOM:
            room.npcSpawns.add(new NPCSpawn(NPCRole.WORKER,
                atBench(room), NPCBehaviour.WORK_STATION));
            break;

        case ORACLE_CHAMBER_ROOM:
            room.npcSpawns.add(new NPCSpawn(NPCRole.ORACLE,
                atThrone(room), NPCBehaviour.STATIONARY));
            break;

        case INITIATION_CHAMBER:
            room.npcSpawns.add(new NPCSpawn(NPCRole.CULT_LEADER,
                centreFront(room), NPCBehaviour.STATIONARY));
            int acolytes = 3 + rng.nextInt(5);
            for (int i = 0; i < acolytes; i++)
                room.npcSpawns.add(new NPCSpawn(NPCRole.CULTIST,
                    inRing(room, i, acolytes), NPCBehaviour.RITUAL));
            break;

        case SERVER_ROOM:
        case RELAY_CONTROL_ROOM:
        case BROADCAST_CENTER:
            room.npcSpawns.add(new NPCSpawn(NPCRole.TECHNICIAN,
                atConsole(room), NPCBehaviour.WORK_STATION));
            break;

        case WAR_ROOM:
            room.npcSpawns.add(new NPCSpawn(NPCRole.OFFICER,
                headOfTable(room), NPCBehaviour.STATIONARY));
            int staff = 2 + rng.nextInt(4);
            for (int i = 0; i < staff; i++)
                room.npcSpawns.add(new NPCSpawn(NPCRole.SOLDIER,
                    atTableSeat(room, i), NPCBehaviour.SEATED));
            break;
    }
}
```

---

## Loot Tables — Extended

```java
private float lootValueFor(InteriorRoomType type, float wealth) {
    switch (type) {
        case VAULT:                  return 1000f + wealth * 3000f;
        case CIPHER_ROOM:            return  500f + wealth * 1000f; // data value
        case HIGH_ROLLER_LOUNGE:     return  400f + wealth * 600f;
        case ARMOURY:
        case ARMOURY_MILITARY:       return  300f + wealth * 500f;
        case INITIATION_CHAMBER:     return  250f + wealth * 500f; // cult artefacts
        case GENETICS_SUITE:         return  300f + wealth * 400f; // samples
        case SPECIMEN_ROOM:          return  200f + wealth * 400f;
        case SMUGGLERS_HOLD:         return  300f + wealth * 700f;
        case BACK_ROOM:              return   80f + wealth * 150f;
        case VAULT + "_BANK":        return 2000f + wealth * 5000f; // alias if needed
        case MEDICAL_STORAGE:        return   80f + wealth * 200f;
        case SERVER_ROOM:            return  150f + wealth * 300f; // data chips
        case DISTILLATION_COLUMN:    return   60f + wealth * 120f; // rare spirits
        case CRYPT:
        case CATACOMBS_PASSAGE:      return  200f + wealth * 400f;
        case ABBOT_QUARTERS:         return   80f + wealth * 150f;
        case STORAGE_CLOSET:         return   10f + wealth *  30f;
        default:                     return   20f + wealth *  80f;
    }
}
```

---

## Physics World Isolation

```java
// Every building interior runs in its own btDynamicsWorld — never in the main world.
// See CLAUDE.md architectural rule 6.

public class BuildingInteriorPhysics {

    private btDynamicsWorld interiorWorld;

    public void activate(BuildingEntity building) {
        if (interiorWorld != null) return;
        btBroadphaseInterface broadphase           = new btDbvtBroadphase();
        btDefaultCollisionConfiguration cfg        = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher           = new btCollisionDispatcher(cfg);
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        interiorWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, cfg);
        interiorWorld.setGravity(new Vector3(0, -9.81f, 0));
        for (InteriorRoom room : building.interior.rooms)
            addRoomCollision(interiorWorld, room, building.worldTransform);
    }

    public void deactivate() {
        if (interiorWorld == null) return;
        interiorWorld.dispose();
        interiorWorld = null;
    }

    public void step(float delta) {
        if (interiorWorld != null)
            interiorWorld.stepSimulation(delta, 5, 1f / 60f);
    }
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Same rooms on every floor | Use floor-level rules: ground=public/retail, upper=private/storage/specialist |
| Stairwells at different positions per floor | Align in the same grid column across all floors |
| Furniture placed without wall-hugging | Shelves, counters, beds snap to walls; only tables/chairs go in the centre |
| NPC spawns ignore room type | Dealers at card tables, medics at crash carts, guards at doors |
| Quest items hardcoded in loot tables | Quest system injects special items at runtime; never hardcode quest objects |
| Interior physics in the main world | btDynamicsWorld must be isolated per building (CLAUDE.md rule 6) |
| VAULT has no guard | Vaults always have at least one guard NPC at the door |
| PRISON_CELL without INTERROGATION_ROOM | Prisons with prisoners should have at least one interrogation room |
| VAULT loot value same as STORAGE_CLOSET | Loot value must scale with room security; vault > armoury > back room > closet |
| FIGHTING_PIT_FLOOR with no spectators | Pit always has spectator NPCs at the rail |
| No navmesh flag on interior | Always flag interior for navmesh rebuild on first entry |
