---
name: procgen-building-generation
description: >
  Enforces procedural building exterior generation using archetype selection,
  footprint shaping, floor-stack extrusion, facade element tiling, roof form
  selection, window and door placement, faction aesthetic overrides, LOD
  collapse, and wear/damage layering for a libGDX 3D space game. Use this
  skill whenever writing or modifying: building mesh generation, facade tiling,
  building type classification, architectural style systems, roof geometry,
  window/door placement algorithms, building height variation, structural
  damage overlays, or any code that turns a 2D lot footprint into a 3D
  building exterior. Also triggers when adding any inhabited structure to a
  planet surface city — shops, residences, civic halls, factories, temples,
  barracks, hospitals, prisons, breweries, casinos, spaceport terminals,
  cult compounds, water towers, or any other purpose-built building type.
---

# Procedural Building Exterior Generation

## Building Archetypes — Core Set

```java
public enum BuildingArchetype {

    // ── RESIDENTIAL ────────────────────────────────────────────────────────
    HOVEL,               // 1–2 floors, crude, irregular footprint
    ROW_HOUSE,           // 2–4 floors, narrow, shared walls
    TOWNHOUSE,           // 3–5 floors, moderate width, distinct facade
    APARTMENT_BLOCK,     // 5–12 floors, wide rectangular
    MANSION,             // 3–5 floors, wide setback, ornate, large garden lot
    NOBLE_VILLA,         // 2–3 floors, colonnaded, courtyard plan
    SLUM_TENEMENT,       // 4–8 floors, over-dense, balcony clutter, poor upkeep
    DORMITORY,           // 3–6 floors, repetitive window rhythm, shared entrance
    DOME_HABITAT,        // 1–2 floors, geodesic dome shell, colony world
    PREFAB_MODULE,       // 1–2 floors, rectangular drop-shipped unit, seam lines
    UNDERGROUND_BUNKER,  // flush to ground, blast door entrance, vent stacks
    ROOFTOP_SHACK,       // 1 floor, built on top of existing structure, corrugated
    COMMUNAL_LONGHOUSE,  // 1–2 floors, very long narrow plan, tribal/communal
    CAVE_DWELLING,       // carved into cliff face, no separate roof form

    // ── COMMERCIAL / TRADE ─────────────────────────────────────────────────
    SHOP,                // 1–2 floors, wide storefront, sign space
    MARKET_STALL,        // single floor open structure, awning
    WAREHOUSE,           // 1–3 floors, large footprint, loading bay
    INN_TAVERN,          // 2–4 floors, overhanging upper floor, lamp posts
    CANTINA,             // 1–2 floors, neon/lantern signage, open doorway, sci-fi
    GENERAL_STORE,       // 1–2 floors, porch, mixed-goods signage
    WEAPONS_DEALER,      // 1–2 floors, barred windows, armour-plate door
    ARMOUR_SHOP,         // 1–2 floors, display rack exterior, reinforced entrance
    PHARMACY,            // 1–2 floors, mortar-and-pestle or caduceus signage
    PAWNSHOP,            // 1–2 floors, cluttered window display, bars on glass
    BANK,                // 2–4 floors, heavy columns, vault-style entrance
    AUCTION_HOUSE,       // 2–3 floors, wide arched entrance, podium niche
    TRADING_COMPANY,     // 3–5 floors, corporate facade, company crest relief
    MONEY_CHANGER,       // 1 floor, kiosk-scale, exchange rate board outside
    SECOND_HAND_EMPORIUM,// 1–2 floors, eclectic clutter in window display
    FENCE_SHOP,          // looks like a GENERAL_STORE; subtle tells: bars, back exit
    DATA_BROKER_DEN,     // 1–2 floors, dark facade, antenna cluster on roof
    BLACK_MARKET_FRONT,  // appears as GENERAL_STORE; hidden basement entrance
    REPAIR_SHOP,         // 1–2 floors, parts stacked outside, roll-up door
    CARTOGRAPHERS_OFFICE,// 1–2 floors, map-printed awning, sextant weathervane
    FOOD_STALL_ROW,      // 1 floor, multiple adjacent stalls under shared canopy

    // ── HOSPITALITY ────────────────────────────────────────────────────────
    CASINO,              // 2–4 floors, gaudy facade, bright signage, wide entrance
    GAMBLING_DEN,        // 1–2 floors, plain exterior, heavy curtains, bouncers
    BROTHEL,             // 2–3 floors, decorative balconies, low-key sign
    PLEASURE_DOME,       // 1–2 floors, curved walls, tinted windows, club lighting
    BATHHOUSE,           // 1–3 floors, arched windows, steam vents, tiled facade
    FIGHTING_PIT,        // 1 floor, thick walls, spectator gallery ring at roof level

    // ── MEDICAL / SCIENCE ──────────────────────────────────────────────────
    CLINIC,              // 1–2 floors, caduceus sign, access ramp, clean facade
    HOSPITAL,            // 3–8 floors, large footprint, ambulance bay, helipad
    RESEARCH_LAB,        // 2–5 floors, large windows, rooftop apparatus/antennae
    GENETICS_LAB,        // 2–4 floors, biohazard signage, airlock entrance
    XENOBIOLOGY_INSTITUTE,// 3–6 floors, specimen display in lobby window, sealed
    MORGUE,              // 1–2 floors, no windows on lower floor, cold-air vents
    QUARANTINE_FACILITY, // 2–4 floors, warning signage, sealed airlocks, fence
    PSYCHIATRIC_WARD,    // 2–4 floors, barred windows, courtyard wall
    PHARMACEUTICAL_PLANT,// 2–4 floors, pipes and vats on exterior, loading dock
    FIELD_SURGERY,       // 1 floor, tent-frame or prefab with red-cross marking

    // ── EDUCATION / CULTURE ────────────────────────────────────────────────
    SCHOOL,              // 2–3 floors, large windows, bell tower or flagpole
    ACADEMY,             // 3–6 floors, columned entrance, institutional stone
    TRAINING_HALL,       // 1–2 floors, large open floor, high ceiling, skylights
    LIBRARY_ARCHIVE,     // 2–5 floors, arched windows, carved lettering on frieze
    MUSEUM,              // 3–6 floors, grand entrance hall, display cases visible
    THEATER,             // 2–4 floors, marquee, ornate facade, wide lobby doors
    ARENA_SMALL,         // 1–2 floors, oval plan, tiered seating visible at roofline
    STORYTELLING_HALL,   // 1–2 floors, painted murals on exterior, open-air stage niche

    // ── ENTERTAINMENT / SPECTACLE ──────────────────────────────────────────
    ARENA_GRAND,         // massive oval, 3–5 floors of tiered seating, vomitoria
    HOLOTHEATER,         // 2–3 floors, curved facade, holographic projector cluster
    SPORTS_COMPLEX,      // large irregular footprint, track/field markers visible
    GLADIATORIAL_PIT,    // sunken below grade, spectator wall at street level

    // ── INDUSTRIAL ─────────────────────────────────────────────────────────
    FORGE,               // 1–2 floors, chimney stack, ventilation slats
    FACTORY,             // 2–5 floors, large windows, loading crane arm
    POWER_SUBSTATION,    // 1 floor, conduit towers, restricted access markers
    BREWERY,             // 2–4 floors, large fermentation vats visible on roof or side
    TEXTILE_MILL,        // 3–5 floors, many tall windows, loading dock at base
    LUMBER_YARD,         // 1 floor office + open yard with stacked timber
    FUEL_DEPOT_SURFACE,  // 1–2 floors, cylindrical tanks, pump station
    RECYCLING_PLANT,     // 2–3 floors, conveyor chute visible, sorting bins outside
    MINING_OFFICE,       // 1–2 floors, ore sample display, gear-motif signage
    GRANARY,             // 1–3 floors, wide cylindrical silos, hatch-ladder exterior
    SLAUGHTERHOUSE,      // 1–2 floors, no windows at ground, blood-gutter channel
    WEAPONS_FOUNDRY,     // 2–4 floors, heat vents, heavy crane rigging
    AMMUNITION_DEPOT,    // 1–2 floors, blast-wall surrounds, restricted signage
    COLD_STORAGE,        // 1–3 floors, insulated panel cladding, large refrigerant units
    GAS_STORAGE_TANK,    // spherical or cylindrical tank on legs, 1 small control hut
    PRINTING_WORKS,      // 2–4 floors, ink-stained walls, paper-roll loading dock
    CHEMICAL_PLANT,      // 2–5 floors, pipe manifolds, warning placards, flare stack
    WATER_TREATMENT,     // 2–3 floors, settlement tanks visible, filtration towers

    // ── MILITARY / SECURITY ────────────────────────────────────────────────
    BARRACKS,            // 2–4 floors, austere, inner courtyard
    WATCHTOWER,          // very narrow, very tall, observation platform at top
    GUARD_POST,          // 1 floor, barrier booth, sentry window, checkpoint arm
    PRISON,              // 3–6 floors, small barred windows, watchtower corners
    MILITARY_OUTPOST,    // 1–2 floors, fortified walls, gun slits, vehicle access
    SIGNAL_RELAY_TOWER,  // lattice tower + small equipment hut at base
    CHECKPOINT,          // 1 floor sprawl across road, barrier gates, scanner arch
    ARMOURED_VEHICLE_DEPOT,// large hangar, heavy roller doors, fuel point
    DETENTION_CENTER,    // 2–4 floors, plain concrete, fence perimeter, guard towers
    MILITARY_HOSPITAL,   // 3–5 floors, military insignia, armoured windows

    // ── GOVERNMENT / ADMINISTRATION ────────────────────────────────────────
    GUILD_HALL,          // 3–5 floors, ornate facade, large entrance arch
    COURTHOUSE,          // 3–6 floors, columns, raised plinth
    CITY_HALL,           // 4–8 floors, central dome or tower, symmetrical
    EMBASSY,             // 2–4 floors, foreign-nation flags, guarded perimeter
    CUSTOMS_HOUSE,       // 2–3 floors, inspection bay, weigh-bridge approach
    PROPAGANDA_MINISTRY, // 3–5 floors, large painted murals or screens on facade
    POSTAL_OFFICE,       // 1–2 floors, postbox cluster outside, sorting dock
    IMMIGRATION_OFFICE,  // 1–3 floors, queue barriers outside, number boards
    TAX_COLLECTION_OFFICE,// 2–3 floors, austere, strongbox motif on frieze
    CENSUS_BUREAU,       // 2–3 floors, data-scroll or registry motif on facade
    TRADE_REGISTRY,      // 2–4 floors, merchant seal above entrance

    // ── RELIGIOUS / CULTURAL ───────────────────────────────────────────────
    SHRINE,              // 1–2 floors, distinctive roof, icon niche
    TEMPLE,              // 3–8 floors, towers or spires, grand stairway
    MONUMENT,            // solid plinth + statue, no interior
    CULT_COMPOUND,       // walled courtyard, nondescript buildings inside, locked
    MAUSOLEUM,           // 1–3 floors, heavy stone, ornate carvings, sealed entrance
    ORACLE_CHAMBER,      // 1–2 floors, domed ceiling, smoke vents, carved symbols
    MONASTERY,           // 2–4 floors, cloister courtyard, bell tower
    RELIQUARY,           // 1–2 floors, glass-front display of sacred objects
    SACRED_GROVE_ENCLOSURE,// low wall enclosing a natural area, gate + shrine post
    HERETIC_HOLDING,     // looks like PRISON; unmarked, associated with religious authority

    // ── SPACEPORT / TRANSIT ────────────────────────────────────────────────
    TERMINAL,            // 2–4 floors, wide glazed facade, departure boards
    HANGAR,              // 1–2 floors, very large footprint, huge bay doors
    CONTROL_TOWER,       // narrow, very tall, observation ring near top
    CUSTOMS_WAREHOUSE,   // 2–3 floors, large loading dock, scanner gantry
    FUEL_DEPOT_SPACEPORT,// cylindrical tanks + pump gantry, blast-shielded
    STARSHIP_REPAIR_DOCK,// open frame dock, crane arms, scaffold clinging to hull
    TRANSIT_TERMINAL,    // 1–2 floors wide hub, platform canopy, departure boards

    // ── INFRASTRUCTURE ─────────────────────────────────────────────────────
    SEWAGE_PUMP_STATION, // 1 floor, grim, pipes in/out at grade, smell indicator
    COMMUNICATIONS_HUB,  // 1–3 floors, antenna farm on roof, satellite dishes
    WATER_TOWER,         // tall steel legs + spherical/cylindrical tank on top
    CARGO_LIFT_BASE,     // 1–2 floors, heavy crane/lift mechanism, freight platform
    AQUEDUCT_HEAD_HOUSE, // 1 floor, at head of aqueduct channel, arched outlet
    ELECTRICAL_HUB,      // 1–2 floors, transformer yard, high-voltage pylon approach
}
```

---

## BuildingConfig

```java
public class BuildingConfig {
    public long              seed;
    public BuildingArchetype archetype;
    public DistrictType      district;
    public FactionId         faction;
    public Rect              lotFootprint;
    public float             ageYears;          // 0 = new, 500 = ancient ruin
    public float             wealthLevel;       // 0–1
    public int               forcedFloors;      // –1 = auto-derive
    public boolean           cornerLot;
    public boolean           planetHasVegetation;
}
```

---

## Generation Pipeline

```
1. Footprint shaping    → set-backs, archetype rules (cruciform/portico/L-shape)
2. Floor stack          → extrude floors; vary heights; mechanical floors
3. Facade tiling        → tile window/door/blank panels per face
4. Roof form            → flat/pitched/dome/spire/sawtooth/pagoda/barrel
5. Feature attachment   → chimneys, antennae, balconies, signs, awnings, vats
6. Faction skin         → material palette and detail mesh set
7. Wear overlay         → age-driven grime, cracks, missing panels, overgrowth
8. LOD collapse         → LOD1 (merged) and LOD2 (impostor quad)
```

---

## Floor Count by Archetype

```java
private int rollFloorCount(BuildingArchetype a, Random rng) {
    switch (a) {
        // Residential
        case HOVEL:               return 1 + rng.nextInt(2);
        case ROW_HOUSE:           return 2 + rng.nextInt(3);
        case TOWNHOUSE:           return 3 + rng.nextInt(3);
        case APARTMENT_BLOCK:     return 5 + rng.nextInt(8);
        case MANSION:             return 2 + rng.nextInt(3);
        case NOBLE_VILLA:         return 2 + rng.nextInt(2);
        case SLUM_TENEMENT:       return 4 + rng.nextInt(5);
        case DORMITORY:           return 3 + rng.nextInt(4);
        case DOME_HABITAT:        return 1 + rng.nextInt(2);
        case PREFAB_MODULE:       return 1 + rng.nextInt(2);
        case UNDERGROUND_BUNKER:  return 0; // flush to grade; interior is underground
        case ROOFTOP_SHACK:       return 1;
        case COMMUNAL_LONGHOUSE:  return 1 + rng.nextInt(2);
        case CAVE_DWELLING:       return 0; // carved into surface
        // Commercial
        case SHOP:                return 1 + rng.nextInt(2);
        case CANTINA:             return 1 + rng.nextInt(2);
        case BANK:                return 2 + rng.nextInt(3);
        case AUCTION_HOUSE:       return 2 + rng.nextInt(2);
        case TRADING_COMPANY:     return 3 + rng.nextInt(3);
        case CASINO:              return 2 + rng.nextInt(3);
        case BROTHEL:             return 2 + rng.nextInt(2);
        case BATHHOUSE:           return 1 + rng.nextInt(3);
        // Medical/Science
        case CLINIC:              return 1 + rng.nextInt(2);
        case HOSPITAL:            return 3 + rng.nextInt(6);
        case RESEARCH_LAB:        return 2 + rng.nextInt(4);
        case GENETICS_LAB:        return 2 + rng.nextInt(3);
        case XENOBIOLOGY_INSTITUTE:return 3 + rng.nextInt(4);
        case PHARMACEUTICAL_PLANT: return 2 + rng.nextInt(3);
        // Education/Culture
        case SCHOOL:              return 2 + rng.nextInt(2);
        case ACADEMY:             return 3 + rng.nextInt(4);
        case LIBRARY_ARCHIVE:     return 2 + rng.nextInt(4);
        case MUSEUM:              return 3 + rng.nextInt(4);
        case THEATER:             return 2 + rng.nextInt(3);
        // Entertainment
        case ARENA_GRAND:         return 3 + rng.nextInt(3);
        case HOLOTHEATER:         return 2 + rng.nextInt(2);
        case FIGHTING_PIT:        return 1;
        // Industrial
        case BREWERY:             return 2 + rng.nextInt(3);
        case TEXTILE_MILL:        return 3 + rng.nextInt(3);
        case GRANARY:             return 1 + rng.nextInt(3);
        case WEAPONS_FOUNDRY:     return 2 + rng.nextInt(3);
        case CHEMICAL_PLANT:      return 2 + rng.nextInt(4);
        case PRINTING_WORKS:      return 2 + rng.nextInt(3);
        // Military/Security
        case BARRACKS:            return 2 + rng.nextInt(3);
        case WATCHTOWER:          return 5 + rng.nextInt(8);
        case PRISON:              return 3 + rng.nextInt(4);
        case SIGNAL_RELAY_TOWER:  return 1; // just the base hut; tower is structural mesh
        // Government
        case GUILD_HALL:          return 3 + rng.nextInt(3);
        case COURTHOUSE:          return 3 + rng.nextInt(4);
        case CITY_HALL:           return 4 + rng.nextInt(5);
        case EMBASSY:             return 2 + rng.nextInt(3);
        case PROPAGANDA_MINISTRY: return 3 + rng.nextInt(3);
        // Religious
        case TEMPLE:              return 3 + rng.nextInt(6);
        case MONASTERY:           return 2 + rng.nextInt(3);
        case CULT_COMPOUND:       return 1 + rng.nextInt(3);
        case MAUSOLEUM:           return 1 + rng.nextInt(3);
        // Spaceport
        case TERMINAL:            return 2 + rng.nextInt(3);
        case CONTROL_TOWER:       return 8 + rng.nextInt(12);
        case STARSHIP_REPAIR_DOCK:return 2 + rng.nextInt(2);
        // Infrastructure
        case WATER_TOWER:         return 1; // base only; tank is separate mesh
        case COMMUNICATIONS_HUB:  return 1 + rng.nextInt(3);
        case ELECTRICAL_HUB:      return 1 + rng.nextInt(2);
        default:                  return 2 + rng.nextInt(3);
    }
}
```

---

## Roof Forms by Archetype

```java
public RoofForm selectForm(BuildingArchetype a, FactionId faction, Random rng) {
    switch (a) {
        case TEMPLE:
        case ORACLE_CHAMBER:       return rng.nextBoolean() ? RoofForm.DOME : RoofForm.SPIRE;
        case CITY_HALL:
        case MAUSOLEUM:            return RoofForm.DOME;
        case CONTROL_TOWER:
        case WATCHTOWER:
        case GUARD_POST:           return RoofForm.FLAT;    // observation deck
        case FACTORY:
        case TEXTILE_MILL:
        case PRINTING_WORKS:       return RoofForm.SAW_TOOTH;
        case WAREHOUSE:
        case FUEL_DEPOT_SURFACE:
        case RECYCLING_PLANT:
        case AMMUNITION_DEPOT:     return RoofForm.FLAT;
        case HOVEL:
        case ROW_HOUSE:
        case INN_TAVERN:
        case COMMUNAL_LONGHOUSE:   return rng.nextBoolean() ? RoofForm.PITCHED_GABLE : RoofForm.PITCHED_HIP;
        case TOWNHOUSE:
        case MANSION:              return RoofForm.MANSARD;
        case DOME_HABITAT:         return RoofForm.DOME;
        case MONASTERY:
        case ACADEMY:              return RoofForm.PITCHED_HIP;
        case BREWERY:              return RoofForm.BARREL; // vaulted over fermentation hall
        case GRANARY:              return RoofForm.CONICAL; // silo tops
        case ARENA_GRAND:
        case ARENA_SMALL:          return RoofForm.FLAT;   // open-topped bowl
        case THEATER:
        case HOLOTHEATER:          return rng.nextBoolean() ? RoofForm.DOME : RoofForm.FLAT;
        default:                   return RoofForm.FLAT;
    }
}

public enum RoofForm {
    FLAT, PITCHED_GABLE, PITCHED_HIP, MANSARD,
    DOME, SPIRE, SAW_TOOTH, PAGODA,
    BARREL, CONICAL, VAULTED
}
```

---

## Facade Special Features by Archetype

```java
public void attachFeatures(BuildingMesh mesh, BuildingConfig cfg, Random rng) {
    switch (cfg.archetype) {
        case BREWERY:
            // Large cylindrical fermentation vats attached to exterior walls
            int vatCount = 2 + rng.nextInt(3);
            for (int i = 0; i < vatCount; i++)
                mesh.attachVat(randomExteriorWallPos(mesh, rng), VatSize.LARGE);
            break;

        case FACTORY:
        case WEAPONS_FOUNDRY:
        case CHEMICAL_PLANT:
            // Chimney stacks + crane arm
            mesh.attachStack(topCentrePos(mesh), 3f + rng.nextFloat() * 5f);
            if (rng.nextBoolean()) mesh.attachCraneArm(roofEdgePos(mesh, rng));
            break;

        case PRISON:
        case DETENTION_CENTER:
        case QUARANTINE_FACILITY:
            // Corner watchtowers at roof level
            for (Vector3 corner : mesh.roofCorners())
                mesh.attachWatchtowerPost(corner);
            break;

        case RESEARCH_LAB:
        case XENOBIOLOGY_INSTITUTE:
        case GENETICS_LAB:
            // Antenna clusters, satellite dishes, sensor pods
            int antennas = 2 + rng.nextInt(4);
            for (int i = 0; i < antennas; i++)
                mesh.attachAntenna(randomRoofPos(mesh, rng), AntennaType.random(rng));
            break;

        case COMMUNICATIONS_HUB:
            // Dense antenna farm
            for (int i = 0; i < 6 + rng.nextInt(6); i++)
                mesh.attachAntenna(randomRoofPos(mesh, rng), AntennaType.random(rng));
            break;

        case WATER_TOWER:
            // The tank is a separate large mesh placed above the leg structure
            mesh.attachTank(topPos(mesh), TankShape.SPHERICAL, 8f + rng.nextFloat() * 6f);
            break;

        case CANTINA:
        case BROTHEL:
        case CASINO:
        case PLEASURE_DOME:
            // Neon / holographic signage, decorative balconies
            mesh.attachSignage(facadeUpperPos(mesh), SignageType.NEON, cfg.faction);
            if (cfg.archetype == BuildingArchetype.BROTHEL || cfg.wealthLevel > 0.5f)
                mesh.attachBalconies(allUpperFloors(mesh));
            break;

        case GRANARY:
            // External ladder/hatch system on silo
            mesh.attachSiloLadder(siloSidePos(mesh));
            break;

        case MONASTERY:
            // Bell tower at one corner
            mesh.attachBellTower(cornerPos(mesh, rng), 6f + rng.nextFloat() * 4f);
            break;

        case PROPAGANDA_MINISTRY:
            // Giant painted mural or LED screen panel on largest facade face
            mesh.attachMural(largestFaceFacing(mesh, StreetDirection.PRIMARY),
                              cfg.faction);
            break;
    }
}
```

---

## Faction Aesthetic Skin

```java
public MaterialPalette selectPalette(FactionId faction, BuildingArchetype archetype,
                                      float wealthLevel) {
    MaterialPalette p = new MaterialPalette();
    switch (faction) {
        case TERRAN_REPUBLIC:
            p.wallMaterial  = wealthLevel > 0.5f ? "terran_plaster_painted" : "terran_brick";
            p.roofMaterial  = "terran_clay_tile";
            p.trimMaterial  = "terran_timber_dark";
            p.accentColour  = Color.valueOf("C8A870");
            break;
        case NEXARI_COLLECTIVE:
            p.wallMaterial  = "nexari_composite_panel";
            p.roofMaterial  = "nexari_membrane_flat";
            p.trimMaterial  = "nexari_alloy_strip";
            p.accentColour  = Color.valueOf("30C8A0");
            break;
        case VOID_SYNDICATE:
            p.wallMaterial  = "syndicate_armour_plate";
            p.roofMaterial  = "syndicate_mesh_deck";
            p.trimMaterial  = "syndicate_exposed_conduit";
            p.accentColour  = Color.valueOf("FF4020");
            break;
        case FRONTIER_INDEPENDENT:
            p.wallMaterial  = randomSalvageMaterial(archetype);
            p.roofMaterial  = "corrugated_metal";
            p.trimMaterial  = "raw_timber";
            p.accentColour  = Color.valueOf("A08060");
            break;
    }
    return p;
}
```

---

## Wear & Damage Overlay

```java
public void apply(BuildingMesh mesh, BuildingConfig cfg, Random rng) {
    float ageFactor   = MathUtils.clamp(cfg.ageYears / 300f, 0f, 1f);
    float grimeStrength = ageFactor * 0.5f
        + (cfg.district == DistrictType.INDUSTRIAL ? 0.3f : 0f);
    mesh.addDecal(DecalType.GRIME, grimeStrength, rng);

    if (ageFactor > 0.3f)
        mesh.addDecal(DecalType.CRACK_PATTERN, (ageFactor - 0.3f) * 1.4f, rng);

    if (ageFactor > 0.6f) {
        int missing = (int)(mesh.facadePanels.size * (ageFactor - 0.6f) * 0.4f);
        for (int i = 0; i < missing; i++)
            mesh.removeFacadePanel(rng.nextInt(mesh.facadePanels.size));
    }

    if (ageFactor > 0.2f && cfg.planetHasVegetation)
        mesh.addDecal(DecalType.OVERGROWTH, ageFactor * 0.6f, rng);

    // Industrial buildings accumulate soot regardless of age
    if (isIndustrial(cfg.archetype))
        mesh.addDecal(DecalType.SOOT, 0.2f + ageFactor * 0.4f, rng);
}
```

---

## LOD Generation

```java
public BuildingLOD[] buildLODs(BuildingMesh lod0, BuildingConfig cfg) {
    BuildingLOD lod1 = new BuildingLOD();
    lod1.mesh        = mergeWithoutDetails(lod0);  // strip balconies, chimneys, signs
    lod1.switchDist  = 80f;

    BuildingLOD lod2 = new BuildingLOD();
    lod2.mesh        = buildImpostorQuad(lod0);    // camera-facing quad with baked texture
    lod2.switchDist  = 250f;

    return new BuildingLOD[]{ lod1, lod2 };
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Buildings fill entire lot | Apply set-backs; shops flush to street, civic buildings have grand forecourts |
| Same floor height everywhere | Ground floor 4–5 m, upper floors 3–3.5 m, mechanical floors 2 m |
| Doors placed on any face | Main door on street-facing facade, centre bay; service doors on rear |
| No footprint scale on towers | Watchtowers and temples must step in as floors rise |
| Faction aesthetic ignored | Material palette set per faction; never use default grey concrete |
| No wear on old buildings | Age drives grime, cracks, missing panels; a 300-year building must look it |
| LOD0 at all distances | LOD1 at 80 m, impostor quad at 250 m; never full mesh at distance |
| Treating UNDERGROUND_BUNKER as normal | floor count = 0 aboveground; entrance is flush blast door, not a facade |
| SIGNAL_RELAY_TOWER as multi-floor | The lattice tower is a structural mesh, not stacked floors; base hut = 1 floor |
| Water tower as stacked floors | Tank is a separate attached mesh; base legs are structural, not rooms |
