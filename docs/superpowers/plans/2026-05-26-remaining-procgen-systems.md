# Remaining Procedural Generation Systems — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 11 missing procgen generators (Name, Station, Derelict, Cave, Dungeon, Asteroid Shape, Encounter Table, Trade Economy, Faction Territory, Crater Impact, Nebula Volumetric) following established seeded-RNG patterns.

**Architecture:** Each generator is a stateless `final` class with a `generate(long seed, ...)` method. Output is an immutable data model. Seed derivation uses `SeedDeriver.domain()` + `SeedDeriver.forId()`. Template-driven generators load JSON data files at runtime via libGDX `Gdx.files`.

**Tech Stack:** Java 17+, libGDX (for math/Color types), existing `SeedDeriver`/`RngUtil` infrastructure, JSON data files.

---

## Task 0: SeedDeriver Domain Constants

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java`

- [ ] **Step 1: Add missing domain constants**

Add these constants after the existing `NEBULA_DOMAIN` line:

```java
public static final long DERELICT_DOMAIN       = 0x2E8BA2E8BA2E8BA3L;
public static final long CAVE_DOMAIN           = 0x7A6D76E9E6237015L;
public static final long DUNGEON_DOMAIN        = 0x5D19E57F4F22A935L;
public static final long ASTEROID_SHAPE_DOMAIN = 0x1B4E81B4E81B4E82L;
public static final long ENCOUNTER_DOMAIN      = 0x9C49FBD688E6BF6DL;
public static final long ECONOMY_DOMAIN        = 0x3F56B0C4FCA1AF8BL;
public static final long CRATER_DOMAIN         = 0xAB54A98CEB1C3F47L;
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java
git commit -m "feat(procgen): add domain constants for remaining generators"
```

---

## Task 1: Name Generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GeneratedName.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/NameGenerator.java`
- Create: `core/src/main/resources/data/names/syllables.json`
- Create: `core/src/main/resources/data/names/prefixes.json`

- [ ] **Step 1: Create GeneratedName data model**

```java
package com.galacticodyssey.galaxy;

public final class GeneratedName {
    public final String prefix;
    public final String root;
    public final String suffix;
    public final String full;

    public GeneratedName(String prefix, String root, String suffix) {
        this.prefix = prefix;
        this.root = root;
        this.suffix = suffix;
        this.full = (prefix.isEmpty() ? "" : prefix + " ") + root + (suffix.isEmpty() ? "" : " " + suffix);
    }
}
```

- [ ] **Step 2: Create syllables.json**

```json
{
  "starOnsets": ["Al", "Be", "Cen", "Del", "Ep", "Fo", "Gal", "Hy", "Ion", "Kep", "Lyr", "Mir", "Neb", "Or", "Pol", "Rig", "Sir", "Tau", "Vel", "Zet"],
  "starCodas": ["pha", "ri", "tus", "nar", "ion", "ux", "ari", "eon", "is", "os", "um", "ax", "el", "on", "us"],
  "greekLetters": ["Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", "Iota", "Kappa", "Lambda", "Mu", "Nu", "Xi", "Omicron", "Pi", "Rho", "Sigma", "Tau", "Upsilon"],
  "planetRoots": ["Tera", "Nova", "Kry", "Val", "Sol", "Zeph", "Ard", "Cael", "Dra", "Fen", "Gol", "Hex", "Ith", "Jor", "Lum", "Myr", "Nyx", "Obl", "Pyr", "Qua", "Rex", "Syl", "Tor", "Umb", "Vor", "Wyr", "Xan", "Yth", "Zel"],
  "planetSuffixes": ["is", "us", "on", "ar", "ix", "a", "um", "os", "ium", "eas"],
  "stationAdjectives": ["Outer", "Deep", "High", "Far", "New", "Old", "Prime", "Grand", "Iron", "Silver"],
  "stationNouns": ["Haven", "Gate", "Reach", "Point", "Harbor", "Watch", "Forge", "Spire", "Ring", "Dock"],
  "factionAdjectives": ["Iron", "Crimson", "Solar", "Void", "Crystal", "Shadow", "Golden", "Silver", "Obsidian", "Azure"],
  "factionNouns": ["Collective", "Dominion", "Republic", "Alliance", "Syndicate", "Consortium", "Empire", "Federation", "Covenant", "Order"],
  "factionPrefixes": ["The", "United", "Free", "Sovereign"],
  "shipClasses": ["Vanguard", "Tempest", "Falcon", "Sentinel", "Wraith", "Corsair", "Nomad", "Harbinger", "Eclipse", "Zenith"],
  "shipNames": ["Defiance", "Horizon", "Resolve", "Fortune", "Perseverance", "Wanderer", "Valiant", "Serenity", "Reckoning", "Providence"],
  "personOnsets": ["Kal", "Zar", "Ven", "Tai", "Kor", "Mira", "Dex", "Syl", "Ren", "Nyx", "Ash", "Bri", "Cae", "Dri", "Eli", "Fen", "Gri", "Hel", "Isa", "Jax"],
  "personCodas": ["en", "ax", "is", "or", "an", "el", "ia", "us", "ra", "on", "ik", "os", "ara", "ius", "ene"]
}
```

- [ ] **Step 3: Create prefixes.json**

```json
{
  "stationTypePrefixes": {
    "RING": ["Ring", "Orbital", "Halo"],
    "HUB_SPOKE": ["Hub", "Central", "Nexus"],
    "ORBITAL_PLATFORM": ["Platform", "Station", "Port"],
    "OUTPOST": ["Outpost", "Post", "Waypoint"]
  },
  "romanNumerals": ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"]
}
```

- [ ] **Step 4: Create NameGenerator class**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import java.util.Random;

public final class NameGenerator {

    private String[] starOnsets;
    private String[] starCodas;
    private String[] greekLetters;
    private String[] planetRoots;
    private String[] planetSuffixes;
    private String[] stationAdjectives;
    private String[] stationNouns;
    private String[] factionAdjectives;
    private String[] factionNouns;
    private String[] factionPrefixes;
    private String[] shipClasses;
    private String[] shipNames;
    private String[] personOnsets;
    private String[] personCodas;
    private String[] romanNumerals;

    public NameGenerator() {
        loadData();
    }

    private void loadData() {
        Json json = new Json();
        JsonValue syllables = json.fromJson(null, Gdx.files.internal("data/names/syllables.json"));
        starOnsets = toArray(syllables.get("starOnsets"));
        starCodas = toArray(syllables.get("starCodas"));
        greekLetters = toArray(syllables.get("greekLetters"));
        planetRoots = toArray(syllables.get("planetRoots"));
        planetSuffixes = toArray(syllables.get("planetSuffixes"));
        stationAdjectives = toArray(syllables.get("stationAdjectives"));
        stationNouns = toArray(syllables.get("stationNouns"));
        factionAdjectives = toArray(syllables.get("factionAdjectives"));
        factionNouns = toArray(syllables.get("factionNouns"));
        factionPrefixes = toArray(syllables.get("factionPrefixes"));
        shipClasses = toArray(syllables.get("shipClasses"));
        shipNames = toArray(syllables.get("shipNames"));
        personOnsets = toArray(syllables.get("personOnsets"));
        personCodas = toArray(syllables.get("personCodas"));

        JsonValue prefixes = json.fromJson(null, Gdx.files.internal("data/names/prefixes.json"));
        romanNumerals = toArray(prefixes.get("romanNumerals"));
    }

    private String[] toArray(JsonValue arrayNode) {
        String[] result = new String[arrayNode.size];
        for (int i = 0; i < arrayNode.size; i++) {
            result[i] = arrayNode.getString(i);
        }
        return result;
    }

    public GeneratedName generateStarName(long seed) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 0);
        Random rng = new Random(nameSeed);

        String onset = pick(rng, starOnsets);
        String coda = pick(rng, starCodas);
        String root = onset + coda;

        String prefix = "";
        if (rng.nextFloat() < 0.3f) {
            prefix = pick(rng, greekLetters);
        }

        return new GeneratedName(prefix, root, "");
    }

    public GeneratedName generatePlanetName(long seed) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 1);
        Random rng = new Random(nameSeed);

        String root = pick(rng, planetRoots) + pick(rng, planetSuffixes);
        String suffix = "";
        if (rng.nextFloat() < 0.4f) {
            suffix = pick(rng, romanNumerals);
        }

        return new GeneratedName("", root, suffix);
    }

    public GeneratedName generateStationName(long seed, String stationType) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 2);
        Random rng = new Random(nameSeed);

        String adj = pick(rng, stationAdjectives);
        String noun = pick(rng, stationNouns);

        return new GeneratedName(adj, noun, "");
    }

    public GeneratedName generateFactionName(long seed) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 3);
        Random rng = new Random(nameSeed);

        String prefix = pick(rng, factionPrefixes);
        String adj = pick(rng, factionAdjectives);
        String noun = pick(rng, factionNouns);

        return new GeneratedName(prefix, adj, noun);
    }

    public GeneratedName generateShipName(long seed) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 4);
        Random rng = new Random(nameSeed);

        String className = pick(rng, shipClasses);
        String name = pick(rng, shipNames);

        return new GeneratedName(className + "-class", name, "");
    }

    public GeneratedName generatePersonName(long seed) {
        long nameSeed = SeedDeriver.forId(SeedDeriver.domain(seed, SeedDeriver.NAME_DOMAIN), 5);
        Random rng = new Random(nameSeed);

        String first = pick(rng, personOnsets) + pick(rng, personCodas);
        String last = pick(rng, personOnsets) + pick(rng, personCodas);

        return new GeneratedName("", first, last);
    }

    private String pick(Random rng, String[] array) {
        return array[rng.nextInt(array.length)];
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/GeneratedName.java
git add core/src/main/java/com/galacticodyssey/galaxy/NameGenerator.java
git add core/src/main/resources/data/names/syllables.json
git add core/src/main/resources/data/names/prefixes.json
git commit -m "feat(procgen): add NameGenerator with syllable-based name generation"
```

---

## Task 2: Space Station Generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/StationType.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/StationModuleType.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/StationModule.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/StationLayout.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/SpaceStationGenerator.java`
- Create: `core/src/main/resources/data/stations/module_templates.json`
- Create: `core/src/main/resources/data/stations/station_archetypes.json`

- [ ] **Step 1: Create StationType enum**

```java
package com.galacticodyssey.galaxy;

public enum StationType {
    RING,
    HUB_SPOKE,
    ORBITAL_PLATFORM,
    OUTPOST
}
```

- [ ] **Step 2: Create StationModuleType enum**

```java
package com.galacticodyssey.galaxy;

public enum StationModuleType {
    DOCK,
    MARKET,
    REFINERY,
    HABITAT,
    COMMAND,
    DEFENSE,
    STORAGE,
    MEDBAY,
    ENGINEERING,
    COMMUNICATIONS
}
```

- [ ] **Step 3: Create StationModule data class**

```java
package com.galacticodyssey.galaxy;

public final class StationModule {
    public final StationModuleType type;
    public final int level;
    public final int sectorIndex;

    public StationModule(StationModuleType type, int level, int sectorIndex) {
        this.type = type;
        this.level = level;
        this.sectorIndex = sectorIndex;
    }
}
```

- [ ] **Step 4: Create StationLayout data class**

```java
package com.galacticodyssey.galaxy;

import java.util.List;

public final class StationLayout {
    public final long seed;
    public final StationType type;
    public final int tier;
    public final List<StationModule> modules;
    public final int dockingPorts;
    public final int populationCapacity;
    public final float defenseRating;
    public final String factionId;

    public StationLayout(long seed, StationType type, int tier, List<StationModule> modules,
                         int dockingPorts, int populationCapacity, float defenseRating, String factionId) {
        this.seed = seed;
        this.type = type;
        this.tier = tier;
        this.modules = List.copyOf(modules);
        this.dockingPorts = dockingPorts;
        this.populationCapacity = populationCapacity;
        this.defenseRating = defenseRating;
        this.factionId = factionId;
    }
}
```

- [ ] **Step 5: Create station_archetypes.json**

```json
[
  {
    "type": "RING",
    "baseDocks": 4,
    "baseModules": 6,
    "basePopulation": 5000,
    "modulesPerTier": 3,
    "populationPerTier": 3000,
    "mandatoryModules": ["COMMAND", "DOCK", "HABITAT"]
  },
  {
    "type": "HUB_SPOKE",
    "baseDocks": 6,
    "baseModules": 8,
    "basePopulation": 8000,
    "modulesPerTier": 4,
    "populationPerTier": 5000,
    "mandatoryModules": ["COMMAND", "DOCK", "HABITAT", "ENGINEERING"]
  },
  {
    "type": "ORBITAL_PLATFORM",
    "baseDocks": 3,
    "baseModules": 5,
    "basePopulation": 3000,
    "modulesPerTier": 2,
    "populationPerTier": 2000,
    "mandatoryModules": ["COMMAND", "DOCK"]
  },
  {
    "type": "OUTPOST",
    "baseDocks": 1,
    "baseModules": 3,
    "basePopulation": 500,
    "modulesPerTier": 1,
    "populationPerTier": 500,
    "mandatoryModules": ["COMMAND"]
  }
]
```

- [ ] **Step 6: Create module_templates.json**

```json
[
  {"type": "DOCK", "defenseBonus": 0.0, "populationBonus": 0},
  {"type": "MARKET", "defenseBonus": 0.0, "populationBonus": 200},
  {"type": "REFINERY", "defenseBonus": 0.0, "populationBonus": 100},
  {"type": "HABITAT", "defenseBonus": 0.0, "populationBonus": 1000},
  {"type": "COMMAND", "defenseBonus": 0.1, "populationBonus": 50},
  {"type": "DEFENSE", "defenseBonus": 0.25, "populationBonus": 50},
  {"type": "STORAGE", "defenseBonus": 0.0, "populationBonus": 0},
  {"type": "MEDBAY", "defenseBonus": 0.0, "populationBonus": 100},
  {"type": "ENGINEERING", "defenseBonus": 0.05, "populationBonus": 80},
  {"type": "COMMUNICATIONS", "defenseBonus": 0.0, "populationBonus": 30}
]
```

- [ ] **Step 7: Create SpaceStationGenerator**

```java
package com.galacticodyssey.galaxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SpaceStationGenerator {

    private static final StationModuleType[] OPTIONAL_MODULES = {
        StationModuleType.MARKET, StationModuleType.REFINERY, StationModuleType.HABITAT,
        StationModuleType.DEFENSE, StationModuleType.STORAGE, StationModuleType.MEDBAY,
        StationModuleType.ENGINEERING, StationModuleType.COMMUNICATIONS
    };

    public StationLayout generate(long seed, StationType type, int tier, String factionId) {
        long stationSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.STATION_DOMAIN), 0);
        Random rng = new Random(stationSeed);

        int baseDocks = baseDocksForType(type);
        int baseModuleCount = baseModuleCountForType(type);
        int basePopulation = basePopulationForType(type);

        int totalModules = baseModuleCount + (tier - 1) * modulesPerTierForType(type);
        int dockingPorts = baseDocks + (tier - 1);
        int populationCapacity = basePopulation + (tier - 1) * populationPerTierForType(type);

        List<StationModule> modules = new ArrayList<>();

        // Mandatory modules
        for (StationModuleType mandatory : mandatoryModulesForType(type)) {
            modules.add(new StationModule(mandatory, tier, modules.size()));
        }

        // Fill remaining slots with weighted random selection
        while (modules.size() < totalModules) {
            StationModuleType moduleType = OPTIONAL_MODULES[rng.nextInt(OPTIONAL_MODULES.length)];
            int level = RngUtil.range(rng, 1, tier + 1);
            modules.add(new StationModule(moduleType, level, modules.size()));
        }

        // Calculate defense rating from modules
        float defenseRating = 0f;
        for (StationModule module : modules) {
            if (module.type == StationModuleType.DEFENSE) {
                defenseRating += 0.25f * module.level;
            } else if (module.type == StationModuleType.COMMAND) {
                defenseRating += 0.1f * module.level;
            } else if (module.type == StationModuleType.ENGINEERING) {
                defenseRating += 0.05f * module.level;
            }
        }

        return new StationLayout(stationSeed, type, tier, modules, dockingPorts,
            populationCapacity, defenseRating, factionId);
    }

    private int baseDocksForType(StationType type) {
        return switch (type) {
            case RING -> 4;
            case HUB_SPOKE -> 6;
            case ORBITAL_PLATFORM -> 3;
            case OUTPOST -> 1;
        };
    }

    private int baseModuleCountForType(StationType type) {
        return switch (type) {
            case RING -> 6;
            case HUB_SPOKE -> 8;
            case ORBITAL_PLATFORM -> 5;
            case OUTPOST -> 3;
        };
    }

    private int basePopulationForType(StationType type) {
        return switch (type) {
            case RING -> 5000;
            case HUB_SPOKE -> 8000;
            case ORBITAL_PLATFORM -> 3000;
            case OUTPOST -> 500;
        };
    }

    private int modulesPerTierForType(StationType type) {
        return switch (type) {
            case RING -> 3;
            case HUB_SPOKE -> 4;
            case ORBITAL_PLATFORM -> 2;
            case OUTPOST -> 1;
        };
    }

    private int populationPerTierForType(StationType type) {
        return switch (type) {
            case RING -> 3000;
            case HUB_SPOKE -> 5000;
            case ORBITAL_PLATFORM -> 2000;
            case OUTPOST -> 500;
        };
    }

    private StationModuleType[] mandatoryModulesForType(StationType type) {
        return switch (type) {
            case RING -> new StationModuleType[]{StationModuleType.COMMAND, StationModuleType.DOCK, StationModuleType.HABITAT};
            case HUB_SPOKE -> new StationModuleType[]{StationModuleType.COMMAND, StationModuleType.DOCK, StationModuleType.HABITAT, StationModuleType.ENGINEERING};
            case ORBITAL_PLATFORM -> new StationModuleType[]{StationModuleType.COMMAND, StationModuleType.DOCK};
            case OUTPOST -> new StationModuleType[]{StationModuleType.COMMAND};
        };
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/StationType.java
git add core/src/main/java/com/galacticodyssey/galaxy/StationModuleType.java
git add core/src/main/java/com/galacticodyssey/galaxy/StationModule.java
git add core/src/main/java/com/galacticodyssey/galaxy/StationLayout.java
git add core/src/main/java/com/galacticodyssey/galaxy/SpaceStationGenerator.java
git add core/src/main/resources/data/stations/module_templates.json
git add core/src/main/resources/data/stations/station_archetypes.json
git commit -m "feat(procgen): add SpaceStationGenerator with tiered module layout"
```

---

## Task 3: Derelict/Wreck Generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/WreckHazard.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/DerelictCause.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/DerelictWreck.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/DerelictGenerator.java`
- Create: `core/src/main/resources/data/derelicts/log_fragments.json`
- Create: `core/src/main/resources/data/derelicts/hazard_profiles.json`

- [ ] **Step 1: Create WreckHazard enum**

```java
package com.galacticodyssey.galaxy;

public enum WreckHazard {
    RADIATION,
    VACUUM_BREACH,
    HOSTILE_FAUNA,
    UNSTABLE_REACTOR,
    AUTOMATED_DEFENSES,
    TOXIC_ATMOSPHERE,
    STRUCTURAL_COLLAPSE
}
```

- [ ] **Step 2: Create DerelictCause enum**

```java
package com.galacticodyssey.galaxy;

public enum DerelictCause {
    PIRATE_ATTACK,
    REACTOR_FAILURE,
    ALIEN_ENCOUNTER,
    MUTINY,
    COLLISION,
    PLAGUE,
    UNKNOWN
}
```

- [ ] **Step 3: Create DerelictWreck data class**

```java
package com.galacticodyssey.galaxy;

import java.util.EnumSet;
import java.util.List;

public final class DerelictWreck {
    public final long seed;
    public final int hullClass;
    public final float damageLevel;
    public final List<String> remainingModules;
    public final EnumSet<WreckHazard> hazards;
    public final int lootTier;
    public final List<String> logEntries;
    public final DerelictCause cause;

    public DerelictWreck(long seed, int hullClass, float damageLevel, List<String> remainingModules,
                         EnumSet<WreckHazard> hazards, int lootTier, List<String> logEntries,
                         DerelictCause cause) {
        this.seed = seed;
        this.hullClass = hullClass;
        this.damageLevel = damageLevel;
        this.remainingModules = List.copyOf(remainingModules);
        this.hazards = hazards;
        this.lootTier = lootTier;
        this.logEntries = List.copyOf(logEntries);
        this.cause = cause;
    }
}
```

- [ ] **Step 4: Create log_fragments.json**

```json
{
  "PIRATE_ATTACK": [
    "Mayday! Multiple hostiles on approach vector—",
    "Hull breach on deck {deck}. Weapons offline.",
    "Captain's log: We tried to outrun them near {location}. We couldn't.",
    "Last entry. If anyone finds this... the pirates came from the {direction} belt."
  ],
  "REACTOR_FAILURE": [
    "Engineering report: containment field fluctuation in reactor {number}.",
    "Emergency shutdown failed. Radiation levels critical on decks {deck} through {deck2}.",
    "All hands abandon ship. Repeat: abandon ship. Reactor cascade imminent.",
    "Final log: coolant system failed at {time}. Crew evacuation at {percent}%."
  ],
  "ALIEN_ENCOUNTER": [
    "Contact! Unknown vessel. No transponder. No known configuration.",
    "They're inside the ship. Deck {deck} is... changing.",
    "It doesn't respond to any frequency. Crew reporting hallucinations.",
    "I don't think they wanted to hurt us. I think they just didn't notice us."
  ],
  "MUTINY": [
    "Security alert: unauthorized access to armory on deck {deck}.",
    "First Mate's log: The captain has gone too far. We have no choice.",
    "Gunfire in the corridors. Both sides have sealed their sections.",
    "It's over. Half the crew is gone. The other half won't last without supplies."
  ],
  "COLLISION": [
    "Navigation error. Object on collision course. Brace for—",
    "Sensors didn't pick it up. Too small for the array, too big to survive.",
    "Structural integrity at {percent}%. Main spine fractured in three places.",
    "Emergency beacon activated. We are adrift. Life support on backup."
  ],
  "PLAGUE": [
    "Medical log: Unidentified pathogen. Quarantine on deck {deck}.",
    "Day {number}: Infection rate now {percent}%. No treatment effective.",
    "The medbay is overrun. We're sealing the ship and transmitting a warning.",
    "If you're reading this, do NOT board. The pathogen is airborne."
  ],
  "UNKNOWN": [
    "Systems nominal. All readings green. Crew complement: 0.",
    "There's no damage. No struggle. They're just... gone.",
    "Automated systems running. Ship in perfect condition. Date stamp: {years} years ago.",
    "Every personal item is in place. Every meal half-eaten. No bodies. No explanation."
  ]
}
```

- [ ] **Step 5: Create hazard_profiles.json**

```json
{
  "PIRATE_ATTACK": {"VACUUM_BREACH": 0.8, "STRUCTURAL_COLLAPSE": 0.4, "AUTOMATED_DEFENSES": 0.2},
  "REACTOR_FAILURE": {"RADIATION": 0.9, "TOXIC_ATMOSPHERE": 0.5, "STRUCTURAL_COLLAPSE": 0.3},
  "ALIEN_ENCOUNTER": {"HOSTILE_FAUNA": 0.6, "RADIATION": 0.3, "TOXIC_ATMOSPHERE": 0.4},
  "MUTINY": {"AUTOMATED_DEFENSES": 0.5, "VACUUM_BREACH": 0.3, "STRUCTURAL_COLLAPSE": 0.2},
  "COLLISION": {"VACUUM_BREACH": 0.9, "STRUCTURAL_COLLAPSE": 0.7, "RADIATION": 0.2},
  "PLAGUE": {"TOXIC_ATMOSPHERE": 0.8, "HOSTILE_FAUNA": 0.3},
  "UNKNOWN": {"RADIATION": 0.2, "AUTOMATED_DEFENSES": 0.3}
}
```

- [ ] **Step 6: Create DerelictGenerator**

```java
package com.galacticodyssey.galaxy;

import java.util.*;

public final class DerelictGenerator {

    private static final String[] ALL_MODULES = {
        "bridge", "engine_room", "cargo_bay", "crew_quarters",
        "medbay", "armory", "engineering", "life_support"
    };

    private static final DerelictCause[] CAUSES = DerelictCause.values();

    public DerelictWreck generate(long seed, int hullClass) {
        long derelictSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.DERELICT_DOMAIN), 0);
        Random rng = new Random(derelictSeed);

        DerelictCause cause = CAUSES[rng.nextInt(CAUSES.length)];
        float damageLevel = RngUtil.range(rng, 0.3f, 1.0f);

        // Remaining modules based on damage
        List<String> remainingModules = new ArrayList<>();
        int moduleCount = Math.max(1, (int) (ALL_MODULES.length * (1f - damageLevel * 0.8f)));
        List<String> pool = new ArrayList<>(Arrays.asList(ALL_MODULES));
        Collections.shuffle(pool, rng);
        for (int i = 0; i < moduleCount && i < pool.size(); i++) {
            remainingModules.add(pool.get(i));
        }

        // Hazards based on cause
        EnumSet<WreckHazard> hazards = rollHazards(cause, rng);

        // Loot tier inversely proportional to damage (more intact = better loot)
        int lootTier = Math.max(1, hullClass - (int) (damageLevel * 3));

        // Log entries
        List<String> logEntries = generateLogs(cause, rng);

        return new DerelictWreck(derelictSeed, hullClass, damageLevel,
            remainingModules, hazards, lootTier, logEntries, cause);
    }

    private EnumSet<WreckHazard> rollHazards(DerelictCause cause, Random rng) {
        EnumSet<WreckHazard> hazards = EnumSet.noneOf(WreckHazard.class);
        for (WreckHazard hazard : WreckHazard.values()) {
            float chance = getHazardChance(cause, hazard);
            if (rng.nextFloat() < chance) {
                hazards.add(hazard);
            }
        }
        if (hazards.isEmpty()) {
            hazards.add(WreckHazard.VACUUM_BREACH);
        }
        return hazards;
    }

    private float getHazardChance(DerelictCause cause, WreckHazard hazard) {
        return switch (cause) {
            case PIRATE_ATTACK -> switch (hazard) {
                case VACUUM_BREACH -> 0.8f;
                case STRUCTURAL_COLLAPSE -> 0.4f;
                case AUTOMATED_DEFENSES -> 0.2f;
                default -> 0.05f;
            };
            case REACTOR_FAILURE -> switch (hazard) {
                case RADIATION -> 0.9f;
                case TOXIC_ATMOSPHERE -> 0.5f;
                case STRUCTURAL_COLLAPSE -> 0.3f;
                default -> 0.05f;
            };
            case ALIEN_ENCOUNTER -> switch (hazard) {
                case HOSTILE_FAUNA -> 0.6f;
                case RADIATION -> 0.3f;
                case TOXIC_ATMOSPHERE -> 0.4f;
                default -> 0.1f;
            };
            case MUTINY -> switch (hazard) {
                case AUTOMATED_DEFENSES -> 0.5f;
                case VACUUM_BREACH -> 0.3f;
                case STRUCTURAL_COLLAPSE -> 0.2f;
                default -> 0.05f;
            };
            case COLLISION -> switch (hazard) {
                case VACUUM_BREACH -> 0.9f;
                case STRUCTURAL_COLLAPSE -> 0.7f;
                case RADIATION -> 0.2f;
                default -> 0.05f;
            };
            case PLAGUE -> switch (hazard) {
                case TOXIC_ATMOSPHERE -> 0.8f;
                case HOSTILE_FAUNA -> 0.3f;
                default -> 0.05f;
            };
            case UNKNOWN -> switch (hazard) {
                case RADIATION -> 0.2f;
                case AUTOMATED_DEFENSES -> 0.3f;
                default -> 0.1f;
            };
        };
    }

    private List<String> generateLogs(DerelictCause cause, Random rng) {
        int logCount = RngUtil.range(rng, 2, 5);
        List<String> logs = new ArrayList<>();
        for (int i = 0; i < logCount; i++) {
            logs.add(generateLogEntry(cause, rng));
        }
        return logs;
    }

    private String generateLogEntry(DerelictCause cause, Random rng) {
        String template = getTemplate(cause, rng);
        template = template.replace("{deck}", String.valueOf(RngUtil.range(rng, 1, 8)));
        template = template.replace("{deck2}", String.valueOf(RngUtil.range(rng, 3, 12)));
        template = template.replace("{number}", String.valueOf(RngUtil.range(rng, 1, 5)));
        template = template.replace("{percent}", String.valueOf(RngUtil.range(rng, 10, 90)));
        template = template.replace("{years}", String.valueOf(RngUtil.range(rng, 5, 500)));
        template = template.replace("{time}", String.format("%02d:%02d", RngUtil.range(rng, 0, 24), RngUtil.range(rng, 0, 60)));
        template = template.replace("{location}", pickLocation(rng));
        template = template.replace("{direction}", pickDirection(rng));
        return template;
    }

    private String getTemplate(DerelictCause cause, Random rng) {
        String[] templates = getTemplatesForCause(cause);
        return templates[rng.nextInt(templates.length)];
    }

    private String[] getTemplatesForCause(DerelictCause cause) {
        return switch (cause) {
            case PIRATE_ATTACK -> new String[]{
                "Mayday! Multiple hostiles on approach vector—",
                "Hull breach on deck {deck}. Weapons offline.",
                "Captain's log: We tried to outrun them near {location}. We couldn't.",
                "Last entry. If anyone finds this... the pirates came from the {direction} belt."
            };
            case REACTOR_FAILURE -> new String[]{
                "Engineering report: containment field fluctuation in reactor {number}.",
                "Emergency shutdown failed. Radiation levels critical on decks {deck} through {deck2}.",
                "All hands abandon ship. Repeat: abandon ship. Reactor cascade imminent.",
                "Final log: coolant system failed at {time}. Crew evacuation at {percent}%."
            };
            case ALIEN_ENCOUNTER -> new String[]{
                "Contact! Unknown vessel. No transponder. No known configuration.",
                "They're inside the ship. Deck {deck} is... changing.",
                "It doesn't respond to any frequency. Crew reporting hallucinations.",
                "I don't think they wanted to hurt us. I think they just didn't notice us."
            };
            case MUTINY -> new String[]{
                "Security alert: unauthorized access to armory on deck {deck}.",
                "First Mate's log: The captain has gone too far. We have no choice.",
                "Gunfire in the corridors. Both sides have sealed their sections.",
                "It's over. Half the crew is gone. The other half won't last without supplies."
            };
            case COLLISION -> new String[]{
                "Navigation error. Object on collision course. Brace for—",
                "Sensors didn't pick it up. Too small for the array, too big to survive.",
                "Structural integrity at {percent}%. Main spine fractured in three places.",
                "Emergency beacon activated. We are adrift. Life support on backup."
            };
            case PLAGUE -> new String[]{
                "Medical log: Unidentified pathogen. Quarantine on deck {deck}.",
                "Day {number}: Infection rate now {percent}%. No treatment effective.",
                "The medbay is overrun. We're sealing the ship and transmitting a warning.",
                "If you're reading this, do NOT board. The pathogen is airborne."
            };
            case UNKNOWN -> new String[]{
                "Systems nominal. All readings green. Crew complement: 0.",
                "There's no damage. No struggle. They're just... gone.",
                "Automated systems running. Ship in perfect condition. Date stamp: {years} years ago.",
                "Every personal item is in place. Every meal half-eaten. No bodies."
            };
        };
    }

    private String pickLocation(Random rng) {
        String[] locations = {"the outer ring", "sector seven", "the trade lanes", "deep space", "the nebula edge"};
        return locations[rng.nextInt(locations.length)];
    }

    private String pickDirection(Random rng) {
        String[] directions = {"northern", "southern", "eastern", "western", "inner", "outer"};
        return directions[rng.nextInt(directions.length)];
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/WreckHazard.java
git add core/src/main/java/com/galacticodyssey/galaxy/DerelictCause.java
git add core/src/main/java/com/galacticodyssey/galaxy/DerelictWreck.java
git add core/src/main/java/com/galacticodyssey/galaxy/DerelictGenerator.java
git add core/src/main/resources/data/derelicts/log_fragments.json
git add core/src/main/resources/data/derelicts/hazard_profiles.json
git commit -m "feat(procgen): add DerelictGenerator with cause-driven hazards and log entries"
```

---

## Task 4: Cave System Generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/CaveBiome.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/CaveRoomType.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/CaveRoom.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/CaveTunnel.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/CaveSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/CaveSystemGenerator.java`

- [ ] **Step 1: Create CaveBiome enum**

```java
package com.galacticodyssey.planet;

public enum CaveBiome {
    ICE,
    VOLCANIC,
    FUNGAL,
    CRYSTAL,
    BARREN
}
```

- [ ] **Step 2: Create CaveRoomType enum**

```java
package com.galacticodyssey.planet;

public enum CaveRoomType {
    CHAMBER,
    GALLERY,
    SINKHOLE,
    CRYSTAL_CAVE,
    LAVA_TUBE,
    UNDERGROUND_LAKE,
    NARROW_PASSAGE
}
```

- [ ] **Step 3: Create CaveRoom data class**

```java
package com.galacticodyssey.planet;

import com.badlogic.gdx.math.Vector3;

public final class CaveRoom {
    public final int id;
    public final Vector3 position;
    public final float radius;
    public final float height;
    public final CaveRoomType type;
    public final int depthLayer;

    public CaveRoom(int id, Vector3 position, float radius, float height, CaveRoomType type, int depthLayer) {
        this.id = id;
        this.position = new Vector3(position);
        this.radius = radius;
        this.height = height;
        this.type = type;
        this.depthLayer = depthLayer;
    }
}
```

- [ ] **Step 4: Create CaveTunnel data class**

```java
package com.galacticodyssey.planet;

public final class CaveTunnel {
    public final int roomA;
    public final int roomB;
    public final float width;
    public final float slope;
    public final boolean hasHazard;

    public CaveTunnel(int roomA, int roomB, float width, float slope, boolean hasHazard) {
        this.roomA = roomA;
        this.roomB = roomB;
        this.width = width;
        this.slope = slope;
        this.hasHazard = hasHazard;
    }
}
```

- [ ] **Step 5: Create CaveSystem data class**

```java
package com.galacticodyssey.planet;

import com.badlogic.gdx.math.Vector3;
import java.util.List;

public final class CaveSystem {
    public final long seed;
    public final CaveBiome biome;
    public final List<CaveRoom> rooms;
    public final List<CaveTunnel> tunnels;
    public final int depth;
    public final List<Vector3> entrances;

    public CaveSystem(long seed, CaveBiome biome, List<CaveRoom> rooms,
                      List<CaveTunnel> tunnels, int depth, List<Vector3> entrances) {
        this.seed = seed;
        this.biome = biome;
        this.rooms = List.copyOf(rooms);
        this.tunnels = List.copyOf(tunnels);
        this.depth = depth;
        this.entrances = List.copyOf(entrances);
    }
}
```

- [ ] **Step 6: Create CaveSystemGenerator**

```java
package com.galacticodyssey.planet;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

public final class CaveSystemGenerator {

    private static final CaveRoomType[] ROOM_TYPES = CaveRoomType.values();

    public CaveSystem generate(long seed, CaveBiome biome, int complexity) {
        long caveSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.CAVE_DOMAIN), 0);
        Random rng = new Random(caveSeed);

        int roomCount = complexity * 3 + RngUtil.range(rng, 2, 5);
        int depthLayers = Math.max(2, complexity);
        float layerSpacing = RngUtil.range(rng, 15f, 30f);

        // Generate rooms via random walk per layer
        List<CaveRoom> rooms = new ArrayList<>();
        for (int layer = 0; layer < depthLayers; layer++) {
            int roomsInLayer = roomCount / depthLayers + (layer == 0 ? roomCount % depthLayers : 0);
            float baseY = -layer * layerSpacing;

            for (int i = 0; i < roomsInLayer; i++) {
                float angle = rng.nextFloat() * MathUtils.PI2;
                float dist = RngUtil.range(rng, 10f, 50f) * (1 + layer * 0.5f);
                float x = dist * MathUtils.cos(angle);
                float z = dist * MathUtils.sin(angle);
                float y = baseY + RngUtil.range(rng, -5f, 5f);

                float radius = RngUtil.range(rng, 5f, 20f);
                float height = RngUtil.range(rng, 3f, radius * 1.5f);
                CaveRoomType type = pickRoomType(biome, rng);

                rooms.add(new CaveRoom(rooms.size(), new Vector3(x, y, z), radius, height, type, layer));
            }
        }

        // Connect rooms: spanning tree + extra cycles
        List<CaveTunnel> tunnels = connectRooms(rooms, rng, complexity);

        // Entrances: rooms on layer 0
        List<Vector3> entrances = new ArrayList<>();
        for (CaveRoom room : rooms) {
            if (room.depthLayer == 0 && rng.nextFloat() < 0.3f) {
                entrances.add(new Vector3(room.position.x, 0f, room.position.z));
            }
        }
        if (entrances.isEmpty() && !rooms.isEmpty()) {
            CaveRoom first = rooms.get(0);
            entrances.add(new Vector3(first.position.x, 0f, first.position.z));
        }

        return new CaveSystem(caveSeed, biome, rooms, tunnels, depthLayers, entrances);
    }

    private List<CaveTunnel> connectRooms(List<CaveRoom> rooms, Random rng, int complexity) {
        List<CaveTunnel> tunnels = new ArrayList<>();
        if (rooms.size() < 2) return tunnels;

        // Build MST using Prim's algorithm on distance
        boolean[] connected = new boolean[rooms.size()];
        connected[0] = true;
        int connectedCount = 1;

        while (connectedCount < rooms.size()) {
            float bestDist = Float.MAX_VALUE;
            int bestFrom = -1, bestTo = -1;

            for (int i = 0; i < rooms.size(); i++) {
                if (!connected[i]) continue;
                for (int j = 0; j < rooms.size(); j++) {
                    if (connected[j]) continue;
                    float dist = rooms.get(i).position.dst(rooms.get(j).position);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestFrom = i;
                        bestTo = j;
                    }
                }
            }

            if (bestFrom >= 0) {
                connected[bestTo] = true;
                connectedCount++;
                float width = RngUtil.range(rng, 2f, 6f);
                float dy = rooms.get(bestTo).position.y - rooms.get(bestFrom).position.y;
                float slope = dy / Math.max(1f, bestDist);
                boolean hazard = rng.nextFloat() < 0.2f;
                tunnels.add(new CaveTunnel(bestFrom, bestTo, width, slope, hazard));
            }
        }

        // Add extra cycles for interesting navigation
        int extras = complexity;
        for (int i = 0; i < extras; i++) {
            int a = rng.nextInt(rooms.size());
            int b = rng.nextInt(rooms.size());
            if (a != b) {
                float dist = rooms.get(a).position.dst(rooms.get(b).position);
                float width = RngUtil.range(rng, 1.5f, 4f);
                float dy = rooms.get(b).position.y - rooms.get(a).position.y;
                float slope = dy / Math.max(1f, dist);
                tunnels.add(new CaveTunnel(a, b, width, slope, rng.nextFloat() < 0.3f));
            }
        }

        return tunnels;
    }

    private CaveRoomType pickRoomType(CaveBiome biome, Random rng) {
        return switch (biome) {
            case CRYSTAL -> rng.nextFloat() < 0.4f ? CaveRoomType.CRYSTAL_CAVE : ROOM_TYPES[rng.nextInt(ROOM_TYPES.length)];
            case VOLCANIC -> rng.nextFloat() < 0.4f ? CaveRoomType.LAVA_TUBE : ROOM_TYPES[rng.nextInt(ROOM_TYPES.length)];
            case ICE -> rng.nextFloat() < 0.3f ? CaveRoomType.GALLERY : ROOM_TYPES[rng.nextInt(ROOM_TYPES.length)];
            default -> ROOM_TYPES[rng.nextInt(ROOM_TYPES.length)];
        };
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/CaveBiome.java
git add core/src/main/java/com/galacticodyssey/planet/CaveRoomType.java
git add core/src/main/java/com/galacticodyssey/planet/CaveRoom.java
git add core/src/main/java/com/galacticodyssey/planet/CaveTunnel.java
git add core/src/main/java/com/galacticodyssey/planet/CaveSystem.java
git add core/src/main/java/com/galacticodyssey/planet/CaveSystemGenerator.java
git commit -m "feat(procgen): add CaveSystemGenerator with graph-based room connectivity"
```

---

## Task 5: Dungeon Interior Generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/DungeonTheme.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/DungeonRoomType.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/DungeonRoom.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/DungeonConnection.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/EncounterSlot.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/DungeonLayout.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/DungeonGenerator.java`
- Create: `core/src/main/resources/data/dungeons/themes.json`

- [ ] **Step 1: Create DungeonTheme enum**

```java
package com.galacticodyssey.planet;

public enum DungeonTheme {
    ALIEN_RUIN,
    MILITARY_BUNKER,
    PIRATE_HIDEOUT,
    ANCIENT_TEMPLE
}
```

- [ ] **Step 2: Create DungeonRoomType enum**

```java
package com.galacticodyssey.planet;

public enum DungeonRoomType {
    ENTRY,
    CORRIDOR,
    LOOT_ROOM,
    BOSS_ARENA,
    PUZZLE,
    TRAP,
    GUARD_POST,
    STORAGE
}
```

- [ ] **Step 3: Create DungeonRoom data class**

```java
package com.galacticodyssey.planet;

public final class DungeonRoom {
    public final int id;
    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public final DungeonRoomType type;

    public DungeonRoom(int id, int x, int y, int width, int height, DungeonRoomType type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;
    }

    public int centerX() { return x + width / 2; }
    public int centerY() { return y + height / 2; }
    public int area() { return width * height; }
}
```

- [ ] **Step 4: Create DungeonConnection and EncounterSlot**

```java
package com.galacticodyssey.planet;

public final class DungeonConnection {
    public final int roomA;
    public final int roomB;
    public final int doorX;
    public final int doorY;
    public final boolean locked;

    public DungeonConnection(int roomA, int roomB, int doorX, int doorY, boolean locked) {
        this.roomA = roomA;
        this.roomB = roomB;
        this.doorX = doorX;
        this.doorY = doorY;
        this.locked = locked;
    }
}
```

```java
package com.galacticodyssey.planet;

public final class EncounterSlot {
    public final int roomId;
    public final int x;
    public final int y;
    public final int difficulty;

    public EncounterSlot(int roomId, int x, int y, int difficulty) {
        this.roomId = roomId;
        this.x = x;
        this.y = y;
        this.difficulty = difficulty;
    }
}
```

- [ ] **Step 5: Create DungeonLayout data class**

```java
package com.galacticodyssey.planet;

import java.util.List;

public final class DungeonLayout {
    public final long seed;
    public final DungeonTheme theme;
    public final List<DungeonRoom> rooms;
    public final List<DungeonConnection> connections;
    public final List<EncounterSlot> encounterSlots;
    public final int totalArea;

    public DungeonLayout(long seed, DungeonTheme theme, List<DungeonRoom> rooms,
                         List<DungeonConnection> connections, List<EncounterSlot> encounterSlots) {
        this.seed = seed;
        this.theme = theme;
        this.rooms = List.copyOf(rooms);
        this.connections = List.copyOf(connections);
        this.encounterSlots = List.copyOf(encounterSlots);
        this.totalArea = rooms.stream().mapToInt(DungeonRoom::area).sum();
    }
}
```

- [ ] **Step 6: Create themes.json**

```json
[
  {
    "theme": "ALIEN_RUIN",
    "minRoomSize": 5,
    "maxRoomSize": 15,
    "bossChance": 0.15,
    "trapChance": 0.25,
    "lootChance": 0.3,
    "puzzleChance": 0.2
  },
  {
    "theme": "MILITARY_BUNKER",
    "minRoomSize": 4,
    "maxRoomSize": 10,
    "bossChance": 0.1,
    "trapChance": 0.15,
    "lootChance": 0.25,
    "puzzleChance": 0.1
  },
  {
    "theme": "PIRATE_HIDEOUT",
    "minRoomSize": 3,
    "maxRoomSize": 12,
    "bossChance": 0.2,
    "trapChance": 0.3,
    "lootChance": 0.35,
    "puzzleChance": 0.05
  },
  {
    "theme": "ANCIENT_TEMPLE",
    "minRoomSize": 6,
    "maxRoomSize": 20,
    "bossChance": 0.2,
    "trapChance": 0.2,
    "lootChance": 0.2,
    "puzzleChance": 0.35
  }
]
```

- [ ] **Step 7: Create DungeonGenerator**

```java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

public final class DungeonGenerator {

    public DungeonLayout generate(long seed, DungeonTheme theme, int roomCount) {
        long dungeonSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.DUNGEON_DOMAIN), 0);
        Random rng = new Random(dungeonSeed);

        int minSize = minRoomSizeForTheme(theme);
        int maxSize = maxRoomSizeForTheme(theme);
        int gridSize = (int) Math.ceil(Math.sqrt(roomCount)) * maxSize * 2;

        // BSP-style room placement
        List<DungeonRoom> rooms = new ArrayList<>();
        List<int[]> candidates = new ArrayList<>();
        candidates.add(new int[]{0, 0, gridSize, gridSize});

        while (rooms.size() < roomCount && !candidates.isEmpty()) {
            int idx = rng.nextInt(candidates.size());
            int[] region = candidates.remove(idx);
            int rx = region[0], ry = region[1], rw = region[2], rh = region[3];

            if (rw < minSize * 2 || rh < minSize * 2) continue;

            // Try to split
            if (rw > maxSize * 2 || rh > maxSize * 2) {
                if (rng.nextBoolean() && rw > minSize * 2) {
                    int split = rx + RngUtil.range(rng, minSize, rw - minSize);
                    candidates.add(new int[]{rx, ry, split - rx, rh});
                    candidates.add(new int[]{split, ry, rx + rw - split, rh});
                } else if (rh > minSize * 2) {
                    int split = ry + RngUtil.range(rng, minSize, rh - minSize);
                    candidates.add(new int[]{rx, ry, rw, split - ry});
                    candidates.add(new int[]{rx, split, rw, ry + rh - split});
                }
                continue;
            }

            // Carve room within region
            int roomW = RngUtil.range(rng, minSize, Math.min(maxSize, rw - 2) + 1);
            int roomH = RngUtil.range(rng, minSize, Math.min(maxSize, rh - 2) + 1);
            int roomX = rx + RngUtil.range(rng, 1, Math.max(2, rw - roomW));
            int roomY = ry + RngUtil.range(rng, 1, Math.max(2, rh - roomH));

            DungeonRoomType type = assignRoomType(rooms.size(), roomCount, theme, rng);
            rooms.add(new DungeonRoom(rooms.size(), roomX, roomY, roomW, roomH, type));
        }

        // Connect rooms with L-shaped corridors
        List<DungeonConnection> connections = connectRooms(rooms, rng);

        // Place encounter slots
        List<EncounterSlot> encounters = placeEncounters(rooms, rng, theme);

        return new DungeonLayout(dungeonSeed, theme, rooms, connections, encounters);
    }

    private List<DungeonConnection> connectRooms(List<DungeonRoom> rooms, Random rng) {
        List<DungeonConnection> connections = new ArrayList<>();
        for (int i = 0; i < rooms.size() - 1; i++) {
            DungeonRoom a = rooms.get(i);
            DungeonRoom b = rooms.get(i + 1);
            int doorX = (a.centerX() + b.centerX()) / 2;
            int doorY = (a.centerY() + b.centerY()) / 2;
            boolean locked = rng.nextFloat() < 0.15f;
            connections.add(new DungeonConnection(i, i + 1, doorX, doorY, locked));
        }
        // Extra connections for loops
        for (int i = 0; i < rooms.size() / 4; i++) {
            int a = rng.nextInt(rooms.size());
            int b = rng.nextInt(rooms.size());
            if (a != b && Math.abs(a - b) > 1) {
                DungeonRoom ra = rooms.get(a);
                DungeonRoom rb = rooms.get(b);
                int doorX = (ra.centerX() + rb.centerX()) / 2;
                int doorY = (ra.centerY() + rb.centerY()) / 2;
                connections.add(new DungeonConnection(a, b, doorX, doorY, rng.nextFloat() < 0.3f));
            }
        }
        return connections;
    }

    private List<EncounterSlot> placeEncounters(List<DungeonRoom> rooms, Random rng, DungeonTheme theme) {
        List<EncounterSlot> slots = new ArrayList<>();
        for (DungeonRoom room : rooms) {
            if (room.type == DungeonRoomType.ENTRY || room.type == DungeonRoomType.CORRIDOR) continue;
            int difficulty = room.type == DungeonRoomType.BOSS_ARENA ? 10 :
                             room.type == DungeonRoomType.TRAP ? 7 : RngUtil.range(rng, 1, 8);
            slots.add(new EncounterSlot(room.id, room.centerX(), room.centerY(), difficulty));
        }
        return slots;
    }

    private DungeonRoomType assignRoomType(int index, int totalRooms, DungeonTheme theme, Random rng) {
        if (index == 0) return DungeonRoomType.ENTRY;
        if (index == totalRooms - 1) return DungeonRoomType.BOSS_ARENA;

        float roll = rng.nextFloat();
        float trapChance = trapChanceForTheme(theme);
        float lootChance = lootChanceForTheme(theme);
        float puzzleChance = puzzleChanceForTheme(theme);

        if (roll < trapChance) return DungeonRoomType.TRAP;
        if (roll < trapChance + lootChance) return DungeonRoomType.LOOT_ROOM;
        if (roll < trapChance + lootChance + puzzleChance) return DungeonRoomType.PUZZLE;
        if (rng.nextFloat() < 0.3f) return DungeonRoomType.GUARD_POST;
        return DungeonRoomType.STORAGE;
    }

    private int minRoomSizeForTheme(DungeonTheme theme) {
        return switch (theme) { case ALIEN_RUIN -> 5; case MILITARY_BUNKER -> 4; case PIRATE_HIDEOUT -> 3; case ANCIENT_TEMPLE -> 6; };
    }

    private int maxRoomSizeForTheme(DungeonTheme theme) {
        return switch (theme) { case ALIEN_RUIN -> 15; case MILITARY_BUNKER -> 10; case PIRATE_HIDEOUT -> 12; case ANCIENT_TEMPLE -> 20; };
    }

    private float trapChanceForTheme(DungeonTheme theme) {
        return switch (theme) { case ALIEN_RUIN -> 0.25f; case MILITARY_BUNKER -> 0.15f; case PIRATE_HIDEOUT -> 0.3f; case ANCIENT_TEMPLE -> 0.2f; };
    }

    private float lootChanceForTheme(DungeonTheme theme) {
        return switch (theme) { case ALIEN_RUIN -> 0.3f; case MILITARY_BUNKER -> 0.25f; case PIRATE_HIDEOUT -> 0.35f; case ANCIENT_TEMPLE -> 0.2f; };
    }

    private float puzzleChanceForTheme(DungeonTheme theme) {
        return switch (theme) { case ALIEN_RUIN -> 0.2f; case MILITARY_BUNKER -> 0.1f; case PIRATE_HIDEOUT -> 0.05f; case ANCIENT_TEMPLE -> 0.35f; };
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/DungeonTheme.java
git add core/src/main/java/com/galacticodyssey/planet/DungeonRoomType.java
git add core/src/main/java/com/galacticodyssey/planet/DungeonRoom.java
git add core/src/main/java/com/galacticodyssey/planet/DungeonConnection.java
git add core/src/main/java/com/galacticodyssey/planet/EncounterSlot.java
git add core/src/main/java/com/galacticodyssey/planet/DungeonLayout.java
git add core/src/main/java/com/galacticodyssey/planet/DungeonGenerator.java
git add core/src/main/resources/data/dungeons/themes.json
git commit -m "feat(procgen): add DungeonGenerator with BSP room carving and L-path corridors"
```

---

## Task 6: Asteroid Shape Generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/AsteroidComposition.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/AsteroidShape.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/AsteroidShapeGenerator.java`

- [ ] **Step 1: Create AsteroidComposition enum**

```java
package com.galacticodyssey.planet;

public enum AsteroidComposition {
    CARBONACEOUS(0.6f, 0.3f),
    SILICATE(0.8f, 0.5f),
    METALLIC(0.4f, 0.2f),
    ICE(0.7f, 0.6f);

    public final float roughness;
    public final float craterDensity;

    AsteroidComposition(float roughness, float craterDensity) {
        this.roughness = roughness;
        this.craterDensity = craterDensity;
    }
}
```

- [ ] **Step 2: Create AsteroidShape data class**

```java
package com.galacticodyssey.planet;

public final class AsteroidShape {
    public final long seed;
    public final float[] vertices;
    public final short[] indices;
    public final float[] normals;
    public final float boundingRadius;
    public final AsteroidComposition composition;

    public AsteroidShape(long seed, float[] vertices, short[] indices, float[] normals,
                         float boundingRadius, AsteroidComposition composition) {
        this.seed = seed;
        this.vertices = vertices;
        this.indices = indices;
        this.normals = normals;
        this.boundingRadius = boundingRadius;
        this.composition = composition;
    }
}
```

- [ ] **Step 3: Create AsteroidShapeGenerator**

```java
package com.galacticodyssey.planet;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.galaxy.SimplexNoise;

import java.util.Random;

public final class AsteroidShapeGenerator {

    private static final int SUBDIVISIONS = 2;

    public AsteroidShape generate(long seed, float baseRadius, AsteroidComposition composition) {
        long shapeSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.ASTEROID_SHAPE_DOMAIN), 0);
        Random rng = new Random(shapeSeed);

        // Generate icosphere
        float[][] baseVerts = generateIcosphere(SUBDIVISIONS);
        int vertCount = baseVerts.length;

        // Apply noise displacement
        float[] vertices = new float[vertCount * 3];
        float[] normals = new float[vertCount * 3];
        float maxRadius = 0f;

        float offsetX = rng.nextFloat() * 1000f;
        float offsetY = rng.nextFloat() * 1000f;
        float offsetZ = rng.nextFloat() * 1000f;

        for (int i = 0; i < vertCount; i++) {
            float nx = baseVerts[i][0];
            float ny = baseVerts[i][1];
            float nz = baseVerts[i][2];

            // FBM noise displacement
            float displacement = 0f;
            float amplitude = composition.roughness * baseRadius * 0.4f;
            float frequency = 1.0f;
            for (int octave = 0; octave < 4; octave++) {
                float sampleX = (nx + offsetX) * frequency;
                float sampleY = (ny + offsetY) * frequency;
                float sampleZ = (nz + offsetZ) * frequency;
                displacement += (float) SimplexNoise.noise(sampleX, sampleY, sampleZ) * amplitude;
                amplitude *= 0.5f;
                frequency *= 2.0f;
            }

            float r = baseRadius + displacement;
            vertices[i * 3]     = nx * r;
            vertices[i * 3 + 1] = ny * r;
            vertices[i * 3 + 2] = nz * r;

            maxRadius = Math.max(maxRadius, r);
        }

        // Generate indices from icosphere topology
        short[] indices = generateIcosphereIndices(SUBDIVISIONS);

        // Compute normals
        computeNormals(vertices, indices, normals);

        return new AsteroidShape(shapeSeed, vertices, indices, normals, maxRadius, composition);
    }

    private float[][] generateIcosphere(int subdivisions) {
        // Start with icosahedron vertices
        float t = (1f + MathUtils.sqrt(5f)) / 2f;
        float[][] icoVerts = {
            {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0},
            {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
            {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}
        };
        int[][] icoFaces = {
            {0,11,5},{0,5,1},{0,1,7},{0,7,10},{0,10,11},
            {1,5,9},{5,11,4},{11,10,2},{10,7,6},{7,1,8},
            {3,9,4},{3,4,2},{3,2,6},{3,6,8},{3,8,9},
            {4,9,5},{2,4,11},{6,2,10},{8,6,7},{9,8,1}
        };

        // Normalize icosahedron vertices
        for (float[] v : icoVerts) {
            float len = MathUtils.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
            v[0] /= len; v[1] /= len; v[2] /= len;
        }

        // Subdivide
        java.util.List<float[]> verts = new java.util.ArrayList<>(java.util.Arrays.asList(icoVerts));
        java.util.List<int[]> faces = new java.util.ArrayList<>(java.util.Arrays.asList(icoFaces));

        for (int s = 0; s < subdivisions; s++) {
            java.util.List<int[]> newFaces = new java.util.ArrayList<>();
            java.util.Map<Long, Integer> midpointCache = new java.util.HashMap<>();

            for (int[] face : faces) {
                int a = getMidpoint(face[0], face[1], verts, midpointCache);
                int b = getMidpoint(face[1], face[2], verts, midpointCache);
                int c = getMidpoint(face[2], face[0], verts, midpointCache);
                newFaces.add(new int[]{face[0], a, c});
                newFaces.add(new int[]{face[1], b, a});
                newFaces.add(new int[]{face[2], c, b});
                newFaces.add(new int[]{a, b, c});
            }
            faces = newFaces;
        }

        return verts.toArray(new float[0][]);
    }

    private int getMidpoint(int i1, int i2, java.util.List<float[]> verts, java.util.Map<Long, Integer> cache) {
        long key = Math.min(i1, i2) * 100000L + Math.max(i1, i2);
        Integer cached = cache.get(key);
        if (cached != null) return cached;

        float[] v1 = verts.get(i1);
        float[] v2 = verts.get(i2);
        float[] mid = {(v1[0]+v2[0])/2f, (v1[1]+v2[1])/2f, (v1[2]+v2[2])/2f};
        float len = MathUtils.sqrt(mid[0]*mid[0] + mid[1]*mid[1] + mid[2]*mid[2]);
        mid[0] /= len; mid[1] /= len; mid[2] /= len;

        int idx = verts.size();
        verts.add(mid);
        cache.put(key, idx);
        return idx;
    }

    private short[] generateIcosphereIndices(int subdivisions) {
        int faceCount = 20 * (int) Math.pow(4, subdivisions);
        short[] indices = new short[faceCount * 3];

        // Regenerate faces to match vertex order
        int[][] icoFaces = {
            {0,11,5},{0,5,1},{0,1,7},{0,7,10},{0,10,11},
            {1,5,9},{5,11,4},{11,10,2},{10,7,6},{7,1,8},
            {3,9,4},{3,4,2},{3,2,6},{3,6,8},{3,8,9},
            {4,9,5},{2,4,11},{6,2,10},{8,6,7},{9,8,1}
        };

        java.util.List<int[]> faces = new java.util.ArrayList<>(java.util.Arrays.asList(icoFaces));
        java.util.List<float[]> dummyVerts = new java.util.ArrayList<>();
        // Rebuild vertex list for index generation
        float t2 = (1f + MathUtils.sqrt(5f)) / 2f;
        float[][] iv = {{-1,t2,0},{1,t2,0},{-1,-t2,0},{1,-t2,0},{0,-1,t2},{0,1,t2},{0,-1,-t2},{0,1,-t2},{t2,0,-1},{t2,0,1},{-t2,0,-1},{-t2,0,1}};
        for (float[] v : iv) { float l = MathUtils.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]); v[0]/=l;v[1]/=l;v[2]/=l; }
        dummyVerts.addAll(java.util.Arrays.asList(iv));

        java.util.Map<Long, Integer> midCache = new java.util.HashMap<>();
        for (int s = 0; s < subdivisions; s++) {
            java.util.List<int[]> newFaces = new java.util.ArrayList<>();
            for (int[] face : faces) {
                int a = getMidpoint(face[0], face[1], dummyVerts, midCache);
                int b = getMidpoint(face[1], face[2], dummyVerts, midCache);
                int c = getMidpoint(face[2], face[0], dummyVerts, midCache);
                newFaces.add(new int[]{face[0], a, c});
                newFaces.add(new int[]{face[1], b, a});
                newFaces.add(new int[]{face[2], c, b});
                newFaces.add(new int[]{a, b, c});
            }
            faces = newFaces;
        }

        for (int i = 0; i < faces.size(); i++) {
            indices[i*3]   = (short) faces.get(i)[0];
            indices[i*3+1] = (short) faces.get(i)[1];
            indices[i*3+2] = (short) faces.get(i)[2];
        }
        return indices;
    }

    private void computeNormals(float[] vertices, short[] indices, float[] normals) {
        Vector3 v0 = new Vector3(), v1 = new Vector3(), v2 = new Vector3();
        Vector3 edge1 = new Vector3(), edge2 = new Vector3(), faceNormal = new Vector3();

        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i] & 0xFFFF, i1 = indices[i+1] & 0xFFFF, i2 = indices[i+2] & 0xFFFF;
            v0.set(vertices[i0*3], vertices[i0*3+1], vertices[i0*3+2]);
            v1.set(vertices[i1*3], vertices[i1*3+1], vertices[i1*3+2]);
            v2.set(vertices[i2*3], vertices[i2*3+1], vertices[i2*3+2]);

            edge1.set(v1).sub(v0);
            edge2.set(v2).sub(v0);
            faceNormal.set(edge1).crs(edge2);

            normals[i0*3]   += faceNormal.x; normals[i0*3+1] += faceNormal.y; normals[i0*3+2] += faceNormal.z;
            normals[i1*3]   += faceNormal.x; normals[i1*3+1] += faceNormal.y; normals[i1*3+2] += faceNormal.z;
            normals[i2*3]   += faceNormal.x; normals[i2*3+1] += faceNormal.y; normals[i2*3+2] += faceNormal.z;
        }

        // Normalize
        for (int i = 0; i < normals.length; i += 3) {
            float len = MathUtils.sqrt(normals[i]*normals[i] + normals[i+1]*normals[i+1] + normals[i+2]*normals[i+2]);
            if (len > 0) { normals[i] /= len; normals[i+1] /= len; normals[i+2] /= len; }
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/AsteroidComposition.java
git add core/src/main/java/com/galacticodyssey/planet/AsteroidShape.java
git add core/src/main/java/com/galacticodyssey/planet/AsteroidShapeGenerator.java
git commit -m "feat(procgen): add AsteroidShapeGenerator with FBM noise-displaced icosphere"
```

---

## Task 7: Encounter Table Generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/RegionType.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/EncounterType.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/EncounterEntry.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/EncounterTable.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/EncounterTableGenerator.java`
- Create: `core/src/main/resources/data/encounters/base_weights.json`
- Create: `core/src/main/resources/data/encounters/faction_modifiers.json`

- [ ] **Step 1: Create RegionType and EncounterType enums**

```java
package com.galacticodyssey.combat;

public enum RegionType {
    CORE_WORLD,
    INNER_RIM,
    OUTER_RIM,
    FRONTIER,
    LAWLESS,
    NEBULA,
    ASTEROID_FIELD
}
```

```java
package com.galacticodyssey.combat;

public enum EncounterType {
    PATROL,
    PIRATE_AMBUSH,
    TRADER_CONVOY,
    ASTEROID_HAZARD,
    DERELICT_DISCOVERY,
    ANOMALY,
    DISTRESS_SIGNAL,
    NOTHING
}
```

- [ ] **Step 2: Create EncounterEntry and EncounterTable**

```java
package com.galacticodyssey.combat;

public final class EncounterEntry {
    public final EncounterType type;
    public final float weight;
    public final int minDifficulty;
    public final int maxDifficulty;

    public EncounterEntry(EncounterType type, float weight, int minDifficulty, int maxDifficulty) {
        this.type = type;
        this.weight = weight;
        this.minDifficulty = minDifficulty;
        this.maxDifficulty = maxDifficulty;
    }
}
```

```java
package com.galacticodyssey.combat;

import java.util.List;
import java.util.Random;

public final class EncounterTable {
    public final long seed;
    public final RegionType regionType;
    public final int dangerLevel;
    public final List<EncounterEntry> entries;

    public EncounterTable(long seed, RegionType regionType, int dangerLevel, List<EncounterEntry> entries) {
        this.seed = seed;
        this.regionType = regionType;
        this.dangerLevel = dangerLevel;
        this.entries = List.copyOf(entries);
    }

    public EncounterEntry roll(Random rng) {
        float totalWeight = 0f;
        for (EncounterEntry e : entries) totalWeight += e.weight;
        float roll = rng.nextFloat() * totalWeight;
        float cumulative = 0f;
        for (EncounterEntry e : entries) {
            cumulative += e.weight;
            if (roll <= cumulative) return e;
        }
        return entries.get(entries.size() - 1);
    }
}
```

- [ ] **Step 3: Create base_weights.json**

```json
{
  "CORE_WORLD":      {"PATROL": 30, "PIRATE_AMBUSH": 5, "TRADER_CONVOY": 25, "ASTEROID_HAZARD": 5, "DERELICT_DISCOVERY": 5, "ANOMALY": 5, "DISTRESS_SIGNAL": 10, "NOTHING": 15},
  "INNER_RIM":       {"PATROL": 20, "PIRATE_AMBUSH": 10, "TRADER_CONVOY": 20, "ASTEROID_HAZARD": 10, "DERELICT_DISCOVERY": 10, "ANOMALY": 5, "DISTRESS_SIGNAL": 10, "NOTHING": 15},
  "OUTER_RIM":       {"PATROL": 10, "PIRATE_AMBUSH": 20, "TRADER_CONVOY": 15, "ASTEROID_HAZARD": 15, "DERELICT_DISCOVERY": 15, "ANOMALY": 5, "DISTRESS_SIGNAL": 10, "NOTHING": 10},
  "FRONTIER":        {"PATROL": 5, "PIRATE_AMBUSH": 25, "TRADER_CONVOY": 10, "ASTEROID_HAZARD": 15, "DERELICT_DISCOVERY": 15, "ANOMALY": 10, "DISTRESS_SIGNAL": 10, "NOTHING": 10},
  "LAWLESS":         {"PATROL": 2, "PIRATE_AMBUSH": 35, "TRADER_CONVOY": 5, "ASTEROID_HAZARD": 10, "DERELICT_DISCOVERY": 20, "ANOMALY": 8, "DISTRESS_SIGNAL": 15, "NOTHING": 5},
  "NEBULA":          {"PATROL": 5, "PIRATE_AMBUSH": 10, "TRADER_CONVOY": 5, "ASTEROID_HAZARD": 5, "DERELICT_DISCOVERY": 15, "ANOMALY": 30, "DISTRESS_SIGNAL": 15, "NOTHING": 15},
  "ASTEROID_FIELD":  {"PATROL": 5, "PIRATE_AMBUSH": 15, "TRADER_CONVOY": 10, "ASTEROID_HAZARD": 30, "DERELICT_DISCOVERY": 15, "ANOMALY": 5, "DISTRESS_SIGNAL": 10, "NOTHING": 10}
}
```

- [ ] **Step 4: Create faction_modifiers.json**

```json
{
  "pirate_heavy": {"PIRATE_AMBUSH": 2.0, "PATROL": 0.3, "TRADER_CONVOY": 0.5},
  "military_presence": {"PATROL": 2.5, "PIRATE_AMBUSH": 0.4, "DISTRESS_SIGNAL": 0.7},
  "trade_hub": {"TRADER_CONVOY": 2.0, "PIRATE_AMBUSH": 0.8, "NOTHING": 0.5},
  "abandoned": {"DERELICT_DISCOVERY": 2.5, "PATROL": 0.1, "TRADER_CONVOY": 0.2, "ANOMALY": 1.5},
  "contested": {"PIRATE_AMBUSH": 1.5, "PATROL": 1.5, "DISTRESS_SIGNAL": 1.8, "NOTHING": 0.3}
}
```

- [ ] **Step 5: Create EncounterTableGenerator**

```java
package com.galacticodyssey.combat;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

public final class EncounterTableGenerator {

    public EncounterTable generate(long seed, RegionType region, int dangerLevel, String factionPresence) {
        long encounterSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.ENCOUNTER_DOMAIN), 0);
        Random rng = new Random(encounterSeed);

        Map<EncounterType, Float> weights = getBaseWeights(region);

        // Apply faction modifier
        applyFactionModifier(weights, factionPresence);

        // Scale hostile encounters by danger level
        float dangerScale = 1f + (dangerLevel - 5) * 0.1f;
        weights.computeIfPresent(EncounterType.PIRATE_AMBUSH, (k, v) -> v * dangerScale);
        weights.computeIfPresent(EncounterType.ASTEROID_HAZARD, (k, v) -> v * dangerScale);

        // Add randomness via per-entry jitter
        for (EncounterType type : EncounterType.values()) {
            weights.computeIfPresent(type, (k, v) -> v * RngUtil.range(rng, 0.8f, 1.2f));
        }

        // Build entries with difficulty ranges based on danger level
        List<EncounterEntry> entries = new ArrayList<>();
        for (Map.Entry<EncounterType, Float> entry : weights.entrySet()) {
            if (entry.getValue() <= 0f) continue;
            int minDiff = Math.max(1, dangerLevel - 2);
            int maxDiff = Math.min(10, dangerLevel + 2);
            entries.add(new EncounterEntry(entry.getKey(), entry.getValue(), minDiff, maxDiff));
        }

        return new EncounterTable(encounterSeed, region, dangerLevel, entries);
    }

    private Map<EncounterType, Float> getBaseWeights(RegionType region) {
        Map<EncounterType, Float> weights = new EnumMap<>(EncounterType.class);
        switch (region) {
            case CORE_WORLD -> { weights.put(EncounterType.PATROL, 30f); weights.put(EncounterType.PIRATE_AMBUSH, 5f); weights.put(EncounterType.TRADER_CONVOY, 25f); weights.put(EncounterType.ASTEROID_HAZARD, 5f); weights.put(EncounterType.DERELICT_DISCOVERY, 5f); weights.put(EncounterType.ANOMALY, 5f); weights.put(EncounterType.DISTRESS_SIGNAL, 10f); weights.put(EncounterType.NOTHING, 15f); }
            case INNER_RIM -> { weights.put(EncounterType.PATROL, 20f); weights.put(EncounterType.PIRATE_AMBUSH, 10f); weights.put(EncounterType.TRADER_CONVOY, 20f); weights.put(EncounterType.ASTEROID_HAZARD, 10f); weights.put(EncounterType.DERELICT_DISCOVERY, 10f); weights.put(EncounterType.ANOMALY, 5f); weights.put(EncounterType.DISTRESS_SIGNAL, 10f); weights.put(EncounterType.NOTHING, 15f); }
            case OUTER_RIM -> { weights.put(EncounterType.PATROL, 10f); weights.put(EncounterType.PIRATE_AMBUSH, 20f); weights.put(EncounterType.TRADER_CONVOY, 15f); weights.put(EncounterType.ASTEROID_HAZARD, 15f); weights.put(EncounterType.DERELICT_DISCOVERY, 15f); weights.put(EncounterType.ANOMALY, 5f); weights.put(EncounterType.DISTRESS_SIGNAL, 10f); weights.put(EncounterType.NOTHING, 10f); }
            case FRONTIER -> { weights.put(EncounterType.PATROL, 5f); weights.put(EncounterType.PIRATE_AMBUSH, 25f); weights.put(EncounterType.TRADER_CONVOY, 10f); weights.put(EncounterType.ASTEROID_HAZARD, 15f); weights.put(EncounterType.DERELICT_DISCOVERY, 15f); weights.put(EncounterType.ANOMALY, 10f); weights.put(EncounterType.DISTRESS_SIGNAL, 10f); weights.put(EncounterType.NOTHING, 10f); }
            case LAWLESS -> { weights.put(EncounterType.PATROL, 2f); weights.put(EncounterType.PIRATE_AMBUSH, 35f); weights.put(EncounterType.TRADER_CONVOY, 5f); weights.put(EncounterType.ASTEROID_HAZARD, 10f); weights.put(EncounterType.DERELICT_DISCOVERY, 20f); weights.put(EncounterType.ANOMALY, 8f); weights.put(EncounterType.DISTRESS_SIGNAL, 15f); weights.put(EncounterType.NOTHING, 5f); }
            case NEBULA -> { weights.put(EncounterType.PATROL, 5f); weights.put(EncounterType.PIRATE_AMBUSH, 10f); weights.put(EncounterType.TRADER_CONVOY, 5f); weights.put(EncounterType.ASTEROID_HAZARD, 5f); weights.put(EncounterType.DERELICT_DISCOVERY, 15f); weights.put(EncounterType.ANOMALY, 30f); weights.put(EncounterType.DISTRESS_SIGNAL, 15f); weights.put(EncounterType.NOTHING, 15f); }
            case ASTEROID_FIELD -> { weights.put(EncounterType.PATROL, 5f); weights.put(EncounterType.PIRATE_AMBUSH, 15f); weights.put(EncounterType.TRADER_CONVOY, 10f); weights.put(EncounterType.ASTEROID_HAZARD, 30f); weights.put(EncounterType.DERELICT_DISCOVERY, 15f); weights.put(EncounterType.ANOMALY, 5f); weights.put(EncounterType.DISTRESS_SIGNAL, 10f); weights.put(EncounterType.NOTHING, 10f); }
        }
        return weights;
    }

    private void applyFactionModifier(Map<EncounterType, Float> weights, String factionPresence) {
        if (factionPresence == null) return;
        Map<EncounterType, Float> modifiers = getFactionModifiers(factionPresence);
        for (Map.Entry<EncounterType, Float> mod : modifiers.entrySet()) {
            weights.computeIfPresent(mod.getKey(), (k, v) -> v * mod.getValue());
        }
    }

    private Map<EncounterType, Float> getFactionModifiers(String presence) {
        Map<EncounterType, Float> mods = new EnumMap<>(EncounterType.class);
        switch (presence) {
            case "pirate_heavy" -> { mods.put(EncounterType.PIRATE_AMBUSH, 2.0f); mods.put(EncounterType.PATROL, 0.3f); mods.put(EncounterType.TRADER_CONVOY, 0.5f); }
            case "military_presence" -> { mods.put(EncounterType.PATROL, 2.5f); mods.put(EncounterType.PIRATE_AMBUSH, 0.4f); mods.put(EncounterType.DISTRESS_SIGNAL, 0.7f); }
            case "trade_hub" -> { mods.put(EncounterType.TRADER_CONVOY, 2.0f); mods.put(EncounterType.PIRATE_AMBUSH, 0.8f); mods.put(EncounterType.NOTHING, 0.5f); }
            case "abandoned" -> { mods.put(EncounterType.DERELICT_DISCOVERY, 2.5f); mods.put(EncounterType.PATROL, 0.1f); mods.put(EncounterType.TRADER_CONVOY, 0.2f); mods.put(EncounterType.ANOMALY, 1.5f); }
            case "contested" -> { mods.put(EncounterType.PIRATE_AMBUSH, 1.5f); mods.put(EncounterType.PATROL, 1.5f); mods.put(EncounterType.DISTRESS_SIGNAL, 1.8f); mods.put(EncounterType.NOTHING, 0.3f); }
        }
        return mods;
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/RegionType.java
git add core/src/main/java/com/galacticodyssey/combat/EncounterType.java
git add core/src/main/java/com/galacticodyssey/combat/EncounterEntry.java
git add core/src/main/java/com/galacticodyssey/combat/EncounterTable.java
git add core/src/main/java/com/galacticodyssey/combat/EncounterTableGenerator.java
git add core/src/main/resources/data/encounters/base_weights.json
git add core/src/main/resources/data/encounters/faction_modifiers.json
git commit -m "feat(procgen): add EncounterTableGenerator with region and faction weighting"
```

---

## Task 8: Trade Economy Generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/economy/data/SupplyLevel.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/data/LocalEconomy.java`
- Create: `core/src/main/java/com/galacticodyssey/economy/TradeEconomyGenerator.java`

- [ ] **Step 1: Create SupplyLevel enum**

```java
package com.galacticodyssey.economy.data;

public enum SupplyLevel {
    SURPLUS,
    NORMAL,
    SCARCE,
    UNAVAILABLE
}
```

- [ ] **Step 2: Create LocalEconomy data class**

```java
package com.galacticodyssey.economy.data;

import java.util.List;
import java.util.Map;

public final class LocalEconomy {
    public final long seed;
    public final Map<String, Float> priceModifiers;
    public final Map<String, SupplyLevel> supplyLevels;
    public final List<String> specializations;
    public final boolean blackMarketAvailable;
    public final float taxRate;

    public LocalEconomy(long seed, Map<String, Float> priceModifiers, Map<String, SupplyLevel> supplyLevels,
                        List<String> specializations, boolean blackMarketAvailable, float taxRate) {
        this.seed = seed;
        this.priceModifiers = Map.copyOf(priceModifiers);
        this.supplyLevels = Map.copyOf(supplyLevels);
        this.specializations = List.copyOf(specializations);
        this.blackMarketAvailable = blackMarketAvailable;
        this.taxRate = taxRate;
    }
}
```

- [ ] **Step 3: Create TradeEconomyGenerator**

```java
package com.galacticodyssey.economy;

import com.galacticodyssey.economy.data.*;
import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

public final class TradeEconomyGenerator {

    private static final String[] COMMON_COMMODITIES = {
        "iron_ore", "copper", "silicon", "carbon", "water", "food_rations"
    };
    private static final String[] UNCOMMON_COMMODITIES = {
        "titanium", "lithium_cells", "tungsten_alloy", "medical_supplies", "synthetic_textiles", "manufactured_parts"
    };
    private static final String[] RARE_COMMODITIES = {
        "iridium_ingots", "neutronium", "dark_crystals", "military_electronics", "luxury_goods"
    };
    private static final String[] EXOTIC_COMMODITIES = {
        "zero_point_cells", "quantum_foam", "void_essence", "salvaged_components"
    };

    public LocalEconomy generate(long seed, IndustryType industry, int population, int techLevel) {
        long econSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.ECONOMY_DOMAIN), 0);
        Random rng = new Random(econSeed);

        Map<String, Float> priceModifiers = new HashMap<>();
        Map<String, SupplyLevel> supplyLevels = new HashMap<>();
        List<String> specializations = new ArrayList<>();

        // Base prices and supply for all commodities
        applyIndustryPricing(industry, rng, priceModifiers, supplyLevels, specializations);

        // Tech level gates exotic goods
        if (techLevel < 3) {
            for (String exotic : EXOTIC_COMMODITIES) {
                supplyLevels.put(exotic, SupplyLevel.UNAVAILABLE);
            }
        }
        if (techLevel < 2) {
            for (String rare : RARE_COMMODITIES) {
                supplyLevels.put(rare, SupplyLevel.SCARCE);
                priceModifiers.put(rare, priceModifiers.getOrDefault(rare, 1f) * 1.5f);
            }
        }

        // Population scales variety
        if (population < 1000) {
            for (String uncommon : UNCOMMON_COMMODITIES) {
                if (rng.nextFloat() < 0.4f) {
                    supplyLevels.put(uncommon, SupplyLevel.UNAVAILABLE);
                }
            }
        }

        // Black market more likely in low-pop, low-tech, or military/outpost areas
        boolean blackMarket = rng.nextFloat() < blackMarketChance(industry, population, techLevel);

        // Tax rate based on industry
        float taxRate = baseTaxForIndustry(industry) + RngUtil.range(rng, -0.02f, 0.03f);
        taxRate = Math.max(0f, Math.min(0.3f, taxRate));

        return new LocalEconomy(econSeed, priceModifiers, supplyLevels, specializations, blackMarket, taxRate);
    }

    private void applyIndustryPricing(IndustryType industry, Random rng,
                                       Map<String, Float> prices, Map<String, SupplyLevel> supply,
                                       List<String> specializations) {
        switch (industry) {
            case MINING -> {
                specializations.addAll(List.of("iron_ore", "copper", "titanium"));
                for (String s : specializations) { prices.put(s, RngUtil.range(rng, 0.5f, 0.7f)); supply.put(s, SupplyLevel.SURPLUS); }
                prices.put("food_rations", RngUtil.range(rng, 1.2f, 1.5f)); supply.put("food_rations", SupplyLevel.SCARCE);
                prices.put("medical_supplies", RngUtil.range(rng, 1.3f, 1.6f)); supply.put("medical_supplies", SupplyLevel.SCARCE);
            }
            case AGRICULTURAL -> {
                specializations.addAll(List.of("food_rations", "water"));
                for (String s : specializations) { prices.put(s, RngUtil.range(rng, 0.4f, 0.6f)); supply.put(s, SupplyLevel.SURPLUS); }
                prices.put("manufactured_parts", RngUtil.range(rng, 1.3f, 1.5f)); supply.put("manufactured_parts", SupplyLevel.SCARCE);
                prices.put("military_electronics", RngUtil.range(rng, 1.5f, 2.0f));
            }
            case INDUSTRIAL -> {
                specializations.addAll(List.of("manufactured_parts", "tungsten_alloy", "synthetic_textiles"));
                for (String s : specializations) { prices.put(s, RngUtil.range(rng, 0.6f, 0.8f)); supply.put(s, SupplyLevel.SURPLUS); }
                prices.put("iron_ore", RngUtil.range(rng, 1.2f, 1.4f)); supply.put("iron_ore", SupplyLevel.SCARCE);
            }
            case HIGH_TECH -> {
                specializations.addAll(List.of("military_electronics", "lithium_cells", "zero_point_cells"));
                for (String s : specializations) { prices.put(s, RngUtil.range(rng, 0.6f, 0.8f)); supply.put(s, SupplyLevel.NORMAL); }
                prices.put("food_rations", RngUtil.range(rng, 1.1f, 1.3f));
            }
            case MILITARY -> {
                specializations.addAll(List.of("military_electronics", "tungsten_alloy"));
                for (String s : specializations) { prices.put(s, RngUtil.range(rng, 0.7f, 0.9f)); supply.put(s, SupplyLevel.NORMAL); }
                prices.put("luxury_goods", RngUtil.range(rng, 1.4f, 1.8f)); supply.put("luxury_goods", SupplyLevel.SCARCE);
            }
            case RESORT -> {
                specializations.addAll(List.of("luxury_goods", "food_rations"));
                prices.put("luxury_goods", RngUtil.range(rng, 0.7f, 0.9f)); supply.put("luxury_goods", SupplyLevel.SURPLUS);
                prices.put("manufactured_parts", RngUtil.range(rng, 1.3f, 1.6f)); supply.put("manufactured_parts", SupplyLevel.SCARCE);
            }
            case OUTPOST -> {
                prices.put("food_rations", RngUtil.range(rng, 1.3f, 1.8f)); supply.put("food_rations", SupplyLevel.SCARCE);
                prices.put("water", RngUtil.range(rng, 1.2f, 1.6f)); supply.put("water", SupplyLevel.SCARCE);
                prices.put("medical_supplies", RngUtil.range(rng, 1.4f, 2.0f)); supply.put("medical_supplies", SupplyLevel.SCARCE);
            }
        }

        // Fill remaining commodities with normal supply and small jitter
        for (String c : COMMON_COMMODITIES) { prices.putIfAbsent(c, RngUtil.range(rng, 0.9f, 1.1f)); supply.putIfAbsent(c, SupplyLevel.NORMAL); }
        for (String c : UNCOMMON_COMMODITIES) { prices.putIfAbsent(c, RngUtil.range(rng, 0.9f, 1.15f)); supply.putIfAbsent(c, SupplyLevel.NORMAL); }
        for (String c : RARE_COMMODITIES) { prices.putIfAbsent(c, RngUtil.range(rng, 0.95f, 1.2f)); supply.putIfAbsent(c, SupplyLevel.NORMAL); }
        for (String c : EXOTIC_COMMODITIES) { prices.putIfAbsent(c, RngUtil.range(rng, 0.9f, 1.3f)); supply.putIfAbsent(c, SupplyLevel.SCARCE); }
    }

    private float blackMarketChance(IndustryType industry, int population, int techLevel) {
        float base = switch (industry) {
            case OUTPOST -> 0.6f;
            case MILITARY -> 0.3f;
            case RESORT -> 0.2f;
            default -> 0.4f;
        };
        if (population < 5000) base += 0.1f;
        if (techLevel < 2) base += 0.1f;
        return Math.min(0.9f, base);
    }

    private float baseTaxForIndustry(IndustryType industry) {
        return switch (industry) {
            case OUTPOST -> 0.02f;
            case MILITARY -> 0.15f;
            case HIGH_TECH -> 0.12f;
            case RESORT -> 0.1f;
            case INDUSTRIAL -> 0.08f;
            default -> 0.05f;
        };
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/economy/data/SupplyLevel.java
git add core/src/main/java/com/galacticodyssey/economy/data/LocalEconomy.java
git add core/src/main/java/com/galacticodyssey/economy/TradeEconomyGenerator.java
git commit -m "feat(procgen): add TradeEconomyGenerator with industry-driven pricing and supply"
```

---

## Task 9: Faction Territory Generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/FactionSeed.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/FactionTerritory.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/FactionTerritoryGenerator.java`
- Create: `core/src/main/resources/data/factions/faction_seeds.json`

- [ ] **Step 1: Create FactionSeed data class**

```java
package com.galacticodyssey.galaxy;

public final class FactionSeed {
    public final String factionId;
    public final double startX;
    public final double startY;
    public final float strength;
    public final float aggressiveness;

    public FactionSeed(String factionId, double startX, double startY, float strength, float aggressiveness) {
        this.factionId = factionId;
        this.startX = startX;
        this.startY = startY;
        this.strength = strength;
        this.aggressiveness = aggressiveness;
    }
}
```

- [ ] **Step 2: Create FactionTerritory data class**

```java
package com.galacticodyssey.galaxy;

import java.util.List;

public final class FactionTerritory {
    public final String factionId;
    public final double capitalX;
    public final double capitalY;
    public final List<Long> controlledSystems;
    public final List<Long> borderSystems;
    public final float influence;
    public final double expansionBiasX;
    public final double expansionBiasY;

    public FactionTerritory(String factionId, double capitalX, double capitalY,
                            List<Long> controlledSystems, List<Long> borderSystems,
                            float influence, double expansionBiasX, double expansionBiasY) {
        this.factionId = factionId;
        this.capitalX = capitalX;
        this.capitalY = capitalY;
        this.controlledSystems = List.copyOf(controlledSystems);
        this.borderSystems = List.copyOf(borderSystems);
        this.influence = influence;
        this.expansionBiasX = expansionBiasX;
        this.expansionBiasY = expansionBiasY;
    }
}
```

- [ ] **Step 3: Create faction_seeds.json**

```json
[
  {"factionId": "terran_federation", "startX": 0.0, "startY": 0.0, "strength": 1.0, "aggressiveness": 0.3},
  {"factionId": "iron_collective", "startX": 150.0, "startY": 80.0, "strength": 0.8, "aggressiveness": 0.7},
  {"factionId": "void_syndicate", "startX": -120.0, "startY": 60.0, "strength": 0.6, "aggressiveness": 0.9},
  {"factionId": "solar_covenant", "startX": -50.0, "startY": -130.0, "strength": 0.7, "aggressiveness": 0.4},
  {"factionId": "free_traders_guild", "startX": 80.0, "startY": -90.0, "strength": 0.5, "aggressiveness": 0.2},
  {"factionId": "crystal_dominion", "startX": -180.0, "startY": -40.0, "strength": 0.9, "aggressiveness": 0.6}
]
```

- [ ] **Step 4: Create FactionTerritoryGenerator**

```java
package com.galacticodyssey.galaxy;

import java.util.*;

public final class FactionTerritoryGenerator {

    public List<FactionTerritory> generate(long galaxySeed, List<FactionSeed> factions, int systemCount) {
        long factionSeed = SeedDeriver.domain(galaxySeed, SeedDeriver.FACTION_DOMAIN);
        Random rng = new Random(factionSeed);

        // Generate system positions
        double[][] systemPositions = new double[systemCount][2];
        long[] systemIds = new long[systemCount];
        for (int i = 0; i < systemCount; i++) {
            systemPositions[i][0] = (rng.nextDouble() - 0.5) * 400.0;
            systemPositions[i][1] = (rng.nextDouble() - 0.5) * 400.0;
            systemIds[i] = SeedDeriver.forId(factionSeed, i);
        }

        // Assign systems to factions via weighted Voronoi (strength-biased distance)
        int[] ownership = new int[systemCount];
        Arrays.fill(ownership, -1);

        for (int i = 0; i < systemCount; i++) {
            float bestScore = Float.MAX_VALUE;
            int bestFaction = -1;
            for (int f = 0; f < factions.size(); f++) {
                FactionSeed fs = factions.get(f);
                double dx = systemPositions[i][0] - fs.startX;
                double dy = systemPositions[i][1] - fs.startY;
                double dist = Math.sqrt(dx * dx + dy * dy);
                float score = (float) (dist / fs.strength);
                if (score < bestScore) {
                    bestScore = score;
                    bestFaction = f;
                }
            }
            ownership[i] = bestFaction;
        }

        // Build territory objects
        List<FactionTerritory> territories = new ArrayList<>();
        for (int f = 0; f < factions.size(); f++) {
            FactionSeed fs = factions.get(f);
            List<Long> controlled = new ArrayList<>();
            List<Long> border = new ArrayList<>();

            for (int i = 0; i < systemCount; i++) {
                if (ownership[i] == f) {
                    controlled.add(systemIds[i]);

                    // Check if border (any neighbor belongs to different faction)
                    if (isBorderSystem(i, f, systemPositions, ownership, systemCount)) {
                        border.add(systemIds[i]);
                    }
                }
            }

            // Expansion bias: average direction from capital to controlled systems
            double biasX = 0, biasY = 0;
            for (Long sysId : controlled) {
                int idx = findSystemIndex(systemIds, sysId);
                if (idx >= 0) {
                    biasX += systemPositions[idx][0] - fs.startX;
                    biasY += systemPositions[idx][1] - fs.startY;
                }
            }
            if (!controlled.isEmpty()) {
                biasX /= controlled.size();
                biasY /= controlled.size();
            }

            float influence = (float) controlled.size() / systemCount;

            territories.add(new FactionTerritory(fs.factionId, fs.startX, fs.startY,
                controlled, border, influence, biasX, biasY));
        }

        return territories;
    }

    private boolean isBorderSystem(int sysIdx, int faction, double[][] positions, int[] ownership, int total) {
        double threshold = 30.0;
        for (int j = 0; j < total; j++) {
            if (j == sysIdx || ownership[j] == faction) continue;
            double dx = positions[sysIdx][0] - positions[j][0];
            double dy = positions[sysIdx][1] - positions[j][1];
            if (dx * dx + dy * dy < threshold * threshold) {
                return true;
            }
        }
        return false;
    }

    private int findSystemIndex(long[] systemIds, long id) {
        for (int i = 0; i < systemIds.length; i++) {
            if (systemIds[i] == id) return i;
        }
        return -1;
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/FactionSeed.java
git add core/src/main/java/com/galacticodyssey/galaxy/FactionTerritory.java
git add core/src/main/java/com/galacticodyssey/galaxy/FactionTerritoryGenerator.java
git add core/src/main/resources/data/factions/faction_seeds.json
git commit -m "feat(procgen): add FactionTerritoryGenerator with weighted Voronoi expansion"
```

---

## Task 10: Crater Impact Generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/CraterProfile.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/CraterGenerator.java`

- [ ] **Step 1: Create CraterProfile data class**

```java
package com.galacticodyssey.planet.terrain;

public final class CraterProfile {
    public final long seed;
    public final float centerX;
    public final float centerZ;
    public final float radius;
    public final float depth;
    public final float rimHeight;
    public final float centralPeakHeight;
    public final float ejectaRadius;
    public final float age;

    public CraterProfile(long seed, float centerX, float centerZ, float radius, float depth,
                         float rimHeight, float centralPeakHeight, float ejectaRadius, float age) {
        this.seed = seed;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.depth = depth;
        this.rimHeight = rimHeight;
        this.centralPeakHeight = centralPeakHeight;
        this.ejectaRadius = ejectaRadius;
        this.age = age;
    }
}
```

- [ ] **Step 2: Create CraterGenerator**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.Random;

public final class CraterGenerator {

    public CraterProfile generate(long seed, float baseRadius, float terrainScale) {
        long craterSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.CRATER_DOMAIN), 0);
        Random rng = new Random(craterSeed);

        float radius = baseRadius * RngUtil.range(rng, 0.7f, 1.3f);
        float depth = radius * RngUtil.range(rng, 0.1f, 0.3f);
        float rimHeight = depth * RngUtil.range(rng, 0.2f, 0.5f);
        float age = rng.nextFloat();

        // Central peak only for larger craters
        float centralPeakHeight = 0f;
        if (radius > terrainScale * 0.1f) {
            centralPeakHeight = depth * RngUtil.range(rng, 0.1f, 0.4f);
        }

        float ejectaRadius = radius * RngUtil.range(rng, 1.5f, 2.5f);

        // Age softens all features
        depth *= (1f - age * 0.6f);
        rimHeight *= (1f - age * 0.7f);
        centralPeakHeight *= (1f - age * 0.5f);

        float cx = RngUtil.range(rng, -terrainScale * 0.4f, terrainScale * 0.4f);
        float cz = RngUtil.range(rng, -terrainScale * 0.4f, terrainScale * 0.4f);

        return new CraterProfile(craterSeed, cx, cz, radius, depth, rimHeight,
            centralPeakHeight, ejectaRadius, age);
    }

    public void stampOnHeightmap(CraterProfile crater, float[] heightmap, int resolution, float mapScale) {
        float cellSize = mapScale / resolution;

        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                float worldX = (x - resolution / 2f) * cellSize;
                float worldZ = (z - resolution / 2f) * cellSize;

                float dx = worldX - crater.centerX;
                float dz = worldZ - crater.centerZ;
                float dist = MathUtils.sqrt(dx * dx + dz * dz);

                float influence = getRadialProfile(dist, crater);
                if (influence != 0f) {
                    heightmap[z * resolution + x] += influence;
                }
            }
        }
    }

    private float getRadialProfile(float dist, CraterProfile crater) {
        float r = crater.radius;

        if (dist > crater.ejectaRadius) return 0f;

        if (dist < r * 0.2f && crater.centralPeakHeight > 0f) {
            // Central peak: Gaussian bump
            float t = dist / (r * 0.2f);
            return crater.centralPeakHeight * MathUtils.exp(-t * t * 2f);
        }

        if (dist < r) {
            // Bowl interior: cosine profile
            float t = dist / r;
            return -crater.depth * MathUtils.cos(t * MathUtils.PI * 0.5f);
        }

        if (dist < r * 1.2f) {
            // Rim: raised ring
            float t = (dist - r) / (r * 0.2f);
            return crater.rimHeight * (1f - t) * (1f - t);
        }

        // Ejecta: exponential falloff
        float t = (dist - r * 1.2f) / (crater.ejectaRadius - r * 1.2f);
        return crater.rimHeight * 0.3f * MathUtils.exp(-t * 3f);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/CraterProfile.java
git add core/src/main/java/com/galacticodyssey/planet/terrain/CraterGenerator.java
git commit -m "feat(procgen): add CraterGenerator with radial profile and heightmap stamping"
```

---

## Task 11: Nebula Volumetric Generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/NebulaVolume.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/NebulaVolumetricGenerator.java`

- [ ] **Step 1: Create NebulaVolume data class**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;

public final class NebulaVolume {
    public final long seed;
    public final float[] densityField;
    public final float[] colorField;
    public final int resolution;
    public final float boundingRadius;
    public final NebulaType type;
    public final Color dominantColor;

    public NebulaVolume(long seed, float[] densityField, float[] colorField, int resolution,
                        float boundingRadius, NebulaType type, Color dominantColor) {
        this.seed = seed;
        this.densityField = densityField;
        this.colorField = colorField;
        this.resolution = resolution;
        this.boundingRadius = boundingRadius;
        this.type = type;
        this.dominantColor = new Color(dominantColor);
    }

    public float getDensity(int x, int y, int z) {
        return densityField[z * resolution * resolution + y * resolution + x];
    }

    public void getColor(int x, int y, int z, Color out) {
        int idx = (z * resolution * resolution + y * resolution + x) * 3;
        out.r = colorField[idx];
        out.g = colorField[idx + 1];
        out.b = colorField[idx + 2];
        out.a = 1f;
    }
}
```

- [ ] **Step 2: Create NebulaVolumetricGenerator**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

import java.util.Random;

public final class NebulaVolumetricGenerator {

    public NebulaVolume generate(long seed, NebulaType type, float radius, int resolution) {
        long nebSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.NEBULA_DOMAIN), 0);
        Random rng = new Random(nebSeed);

        float[] densityField = new float[resolution * resolution * resolution];
        float[] colorField = new float[resolution * resolution * resolution * 3];

        Color dominantColor = getDominantColor(type, rng);
        Color secondaryColor = getSecondaryColor(type, rng);

        float offsetX = rng.nextFloat() * 100f;
        float offsetY = rng.nextFloat() * 100f;
        float offsetZ = rng.nextFloat() * 100f;

        float cellSize = (radius * 2f) / resolution;

        for (int z = 0; z < resolution; z++) {
            for (int y = 0; y < resolution; y++) {
                for (int x = 0; x < resolution; x++) {
                    float wx = (x - resolution / 2f) * cellSize;
                    float wy = (y - resolution / 2f) * cellSize;
                    float wz = (z - resolution / 2f) * cellSize;

                    float distFromCenter = MathUtils.sqrt(wx*wx + wy*wy + wz*wz);
                    float radialFalloff = Math.max(0f, 1f - distFromCenter / radius);

                    // FBM density
                    float density = 0f;
                    float amplitude = 1f;
                    float frequency = 1f / radius;
                    for (int octave = 0; octave < 5; octave++) {
                        float nx = (wx + offsetX) * frequency;
                        float ny = (wy + offsetY) * frequency;
                        float nz = (wz + offsetZ) * frequency;
                        density += (float) SimplexNoise.noise(nx, ny, nz) * amplitude;
                        amplitude *= 0.5f;
                        frequency *= 2.0f;
                    }

                    density = (density + 1f) * 0.5f;
                    density *= radialFalloff * radialFalloff;

                    // Type-specific density modifications
                    density = applyTypeModification(type, density, distFromCenter, radius);

                    int idx = z * resolution * resolution + y * resolution + x;
                    densityField[idx] = Math.max(0f, Math.min(1f, density));

                    // Color: lerp between dominant and secondary based on density and position
                    float colorT = density * 0.7f + (distFromCenter / radius) * 0.3f;
                    colorT = Math.max(0f, Math.min(1f, colorT));
                    int cIdx = idx * 3;
                    colorField[cIdx]     = MathUtils.lerp(dominantColor.r, secondaryColor.r, colorT);
                    colorField[cIdx + 1] = MathUtils.lerp(dominantColor.g, secondaryColor.g, colorT);
                    colorField[cIdx + 2] = MathUtils.lerp(dominantColor.b, secondaryColor.b, colorT);
                }
            }
        }

        return new NebulaVolume(nebSeed, densityField, colorField, resolution, radius, type, dominantColor);
    }

    private float applyTypeModification(NebulaType type, float density, float dist, float radius) {
        return switch (type) {
            case EMISSION -> {
                // Hot core, brighter center
                float coreBoost = Math.max(0f, 1f - dist / (radius * 0.3f));
                yield density + coreBoost * 0.4f;
            }
            case DARK -> {
                // Invert: dark absorption regions
                yield density > 0.4f ? density * 1.5f : density * 0.3f;
            }
            case REFLECTION -> {
                // Uniform with slight edge brightening
                float edgeBright = Math.max(0f, (dist / radius) - 0.6f) * 0.5f;
                yield density + edgeBright;
            }
            case PLANETARY -> {
                // Shell structure: dense ring at mid-radius
                float shellDist = Math.abs(dist - radius * 0.5f) / (radius * 0.2f);
                float shell = Math.max(0f, 1f - shellDist);
                yield density * 0.5f + shell * 0.5f;
            }
        };
    }

    private Color getDominantColor(NebulaType type, Random rng) {
        return switch (type) {
            case EMISSION -> new Color(
                RngUtil.range(rng, 0.8f, 1.0f),
                RngUtil.range(rng, 0.1f, 0.4f),
                RngUtil.range(rng, 0.2f, 0.5f), 1f);
            case REFLECTION -> new Color(
                RngUtil.range(rng, 0.3f, 0.6f),
                RngUtil.range(rng, 0.4f, 0.7f),
                RngUtil.range(rng, 0.7f, 1.0f), 1f);
            case DARK -> new Color(
                RngUtil.range(rng, 0.05f, 0.15f),
                RngUtil.range(rng, 0.02f, 0.1f),
                RngUtil.range(rng, 0.05f, 0.15f), 1f);
            case PLANETARY -> new Color(
                RngUtil.range(rng, 0.2f, 0.5f),
                RngUtil.range(rng, 0.6f, 0.9f),
                RngUtil.range(rng, 0.3f, 0.6f), 1f);
        };
    }

    private Color getSecondaryColor(NebulaType type, Random rng) {
        return switch (type) {
            case EMISSION -> new Color(
                RngUtil.range(rng, 0.9f, 1.0f),
                RngUtil.range(rng, 0.6f, 0.9f),
                RngUtil.range(rng, 0.1f, 0.3f), 1f);
            case REFLECTION -> new Color(
                RngUtil.range(rng, 0.6f, 0.9f),
                RngUtil.range(rng, 0.7f, 1.0f),
                RngUtil.range(rng, 0.9f, 1.0f), 1f);
            case DARK -> new Color(
                RngUtil.range(rng, 0.1f, 0.2f),
                RngUtil.range(rng, 0.05f, 0.15f),
                RngUtil.range(rng, 0.1f, 0.25f), 1f);
            case PLANETARY -> new Color(
                RngUtil.range(rng, 0.1f, 0.3f),
                RngUtil.range(rng, 0.8f, 1.0f),
                RngUtil.range(rng, 0.6f, 0.9f), 1f);
        };
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/NebulaVolume.java
git add core/src/main/java/com/galacticodyssey/galaxy/NebulaVolumetricGenerator.java
git commit -m "feat(procgen): add NebulaVolumetricGenerator with type-driven 3D density fields"
```

---

## Execution Order

1. **Task 0** (SeedDeriver) — must go first, all others depend on it
2. **Task 1** (Name Generator) — other generators may use it for naming
3. **Tasks 2-11** (all remaining) — fully independent, can be executed in parallel

Total new files: ~40 Java classes + ~10 JSON data files
