---
name: procgen-creature-behavior
description: >
  Enforces procedural creature behavior and ecosystem generation (Cycle D)
  for the Galactic Odyssey fauna system, building on the Cycle A CreatureSpec.
  Covers diet classification (herbivore, carnivore, omnivore, detritivore,
  photovore, lithophage), temperament rolling (passive, territorial, aggressive,
  skittish, curious), social structure (solitary, pair-bonded, pack, herd,
  swarm, colonial), activity timing (diurnal, nocturnal, crepuscular, erratic),
  behavioral AI state machine definition (idle, foraging, fleeing, stalking,
  territorial display, mating), ecosystem role assignment (apex predator, prey,
  scavenger, decomposer, pollinator, symbiont), spawn density from biome and
  food chain position, and biome-weighted archetype selection hooks for a
  libGDX 3D space game. Use this skill whenever writing or modifying: creature
  AI behavior trees, creature spawn tables, ecosystem balance rules, creature
  threat assessment, prey/predator relationship seeding, creature patrol and
  foraging routines, territorial radius calculation, pack coordination logic,
  activity cycle timing, or any code that controls how creatures act in the world
  rather than what they look like. Reads CreatureSpec from Cycle A; writes
  CreatureBehaviorSpec which the AI system consumes.
---

# Procedural Creature Behavior & Ecosystem (Cycle D)

## Relationship to Other Cycles

```
Cycle A (procgen-creature-generation, DONE)
  → CreatureSpec {archetype, massKg, socketGraph, colorSeed, biome}

Cycle C (procgen-creature-appearance)
  → CreatureSpec.appearance {coverage, pattern, colours, bioluminescence}

Cycle D (THIS SKILL)
  → CreatureSpec.behavior {diet, temperament, social, activityCycle, aiStates}
  → EcosystemTable {spawnWeights, predatorPreyLinks, densityField}
```

---

## Core Enumerations

```java
public enum Diet {
    HERBIVORE,      // plants, seeds, fruits
    CARNIVORE,      // other animals
    OMNIVORE,       // both
    DETRITIVORE,    // dead organic matter / scavenger of decay
    PHOTOVORE,      // solar energy; no active feeding (alien biology)
    LITHOPHAGE,     // mineral feeding; rocky substrate
    FILTER_FEEDER,  // sieve suspended particles from water or air
    PARASITE        // feeds from host without killing
}

public enum Temperament {
    PASSIVE,        // ignores the player unless directly attacked
    SKITTISH,       // flees on player approach; wide detection radius
    CURIOUS,        // approaches cautiously; can be startled
    TERRITORIAL,    // charges if player enters radius; backs off at boundary
    AGGRESSIVE,     // attacks on sight at medium range
    AMBUSH,         // stationary/hidden until prey is within strike range
    PACK_AGGRESSIVE // individually non-threatening; attacks in groups
}

public enum SocialStructure {
    SOLITARY,       // single individual; aggressive to own species except mating
    PAIR_BONDED,    // always found as a bonded pair
    FAMILY_UNIT,    // 3–6 individuals; 1–2 adults + juveniles
    PACK,           // 4–12 individuals; coordinated hunting
    HERD,           // 10–100 individuals; loose aggregation; safety in numbers
    SWARM,          // 20–500+ individuals; emergent movement
    COLONIAL,       // fixed nest/burrow; individuals forage independently
    SOLITARY_NEST   // solitary except for defended nest site
}

public enum ActivityCycle {
    DIURNAL,        // active during daylight
    NOCTURNAL,      // active at night
    CREPUSCULAR,    // active at dawn and dusk
    CATHEMERAL,     // active at irregular intervals regardless of light
    TIDAL,          // tied to tidal rhythms (ocean-adjacent planets)
}
```

---

## CreatureBehaviorSpec

```java
public class CreatureBehaviorSpec {
    public Diet             diet;
    public Temperament      temperament;
    public SocialStructure  socialStructure;
    public ActivityCycle    activityCycle;

    // Derived numeric parameters
    public float    detectionRadiusM;    // range at which creature notices threats/prey
    public float    fleeRadiusM;         // skittish: distance at which flight triggers
    public float    territorialRadiusM;  // territorial: charge if player enters this
    public float    strikeRangeM;        // melee attack reach
    public int      packMinSize;         // if social == PACK, min individuals
    public int      packMaxSize;
    public float    foragingRangeM;      // how far from home range the creature wanders
    public float    restPeriodHours;     // hours per day spent inactive
    public boolean  isNocturnal;
    public boolean  isVenomous;
    public boolean  isToxic;
    public boolean  canFly;
    public boolean  canSwim;
    public boolean  isBurrowing;
    public EcosystemRole ecosystemRole;
}
```

---

## Behavior Spec Generator

```java
public class CreatureBehaviorGenerator {

    public CreatureBehaviorSpec generate(CreatureSpec spec, Biome biome, Random rng) {
        CreatureBehaviorSpec b = new CreatureBehaviorSpec();

        b.diet             = rollDiet(spec.archetype, biome, rng);
        b.temperament      = rollTemperament(b.diet, spec.massKg, biome, rng);
        b.socialStructure  = rollSocial(b.diet, b.temperament, spec.archetype, rng);
        b.activityCycle    = rollActivityCycle(biome, rng);
        b.ecosystemRole    = deriveEcosystemRole(b.diet, b.temperament, spec.massKg);

        // Derived metrics from the above
        b.detectionRadiusM = detectionRadius(b.temperament, spec.massKg);
        b.fleeRadiusM      = b.temperament == Temperament.SKITTISH
                           ? b.detectionRadiusM * 0.7f : 0f;
        b.territorialRadiusM = b.temperament == Temperament.TERRITORIAL
                             ? 8f + spec.massKg * 0.05f : 0f;
        b.strikeRangeM     = 0.5f + spec.massKg * 0.002f; // scales with body size
        b.foragingRangeM   = foragingRange(b.diet, b.socialStructure, spec.massKg);

        if (b.socialStructure == SocialStructure.PACK) {
            b.packMinSize  = 3 + rng.nextInt(3);
            b.packMaxSize  = b.packMinSize + 2 + rng.nextInt(5);
        }

        b.isVenomous  = rollVenomous(spec.archetype, biome, rng);
        b.isToxic     = !b.isVenomous && rng.nextFloat() < 0.08f;
        b.canFly      = spec.archetype == Archetype.BIPEDAL && rng.nextFloat() < 0.15f;
        b.canSwim     = (biome == Biome.SHALLOW_OCEAN || biome == Biome.MANGROVE)
                         || rng.nextFloat() < 0.20f;
        b.isBurrowing = (biome == Biome.HOT_DESERT || biome == Biome.TUNDRA)
                         && rng.nextFloat() < 0.30f;

        return b;
    }

    private Diet rollDiet(Archetype arch, Biome biome, Random rng) {
        // Alien biomes introduce non-standard diets
        if (biome == Biome.ALIEN_LAVA_FIELDS && rng.nextFloat() < 0.30f)
            return Diet.LITHOPHAGE;
        if (biome == Biome.ALIEN_CRYSTAL && rng.nextFloat() < 0.20f)
            return Diet.PHOTOVORE;

        // Serpentines are overwhelmingly carnivore
        if (arch == Archetype.SERPENTINE) {
            return rng.nextFloat() < 0.85f ? Diet.CARNIVORE : Diet.OMNIVORE;
        }
        // Hexapods diversify across all diets
        if (arch == Archetype.HEXAPOD) {
            float r = rng.nextFloat();
            if (r < 0.35f) return Diet.HERBIVORE;
            if (r < 0.55f) return Diet.DETRITIVORE;
            if (r < 0.70f) return Diet.FILTER_FEEDER;
            if (r < 0.80f) return Diet.PARASITE;
            return Diet.CARNIVORE;
        }
        // Quadrupeds: lean herbivore or carnivore
        if (arch == Archetype.QUADRUPED) {
            float r = rng.nextFloat();
            if (r < 0.45f) return Diet.HERBIVORE;
            if (r < 0.70f) return Diet.CARNIVORE;
            if (r < 0.85f) return Diet.OMNIVORE;
            return Diet.DETRITIVORE;
        }
        // Bipedal: wider distribution
        float r = rng.nextFloat();
        if (r < 0.30f) return Diet.OMNIVORE;
        if (r < 0.55f) return Diet.HERBIVORE;
        if (r < 0.75f) return Diet.CARNIVORE;
        return Diet.DETRITIVORE;
    }

    private Temperament rollTemperament(Diet diet, float massKg, Biome biome,
                                         Random rng) {
        // Herbivores generally flee; carnivores more aggressive
        // Large creatures more territorial; small ones more skittish
        float aggressionBase = diet == Diet.CARNIVORE ? 0.5f
                             : diet == Diet.OMNIVORE  ? 0.3f : 0.1f;
        aggressionBase += (massKg / 1000f) * 0.2f; // large = more territorial

        float r = rng.nextFloat();
        if (r < aggressionBase * 0.4f)          return Temperament.AGGRESSIVE;
        if (r < aggressionBase * 0.7f)          return Temperament.TERRITORIAL;
        if (diet == Diet.CARNIVORE && r < 0.75f) return Temperament.AMBUSH;
        if (diet == Diet.HERBIVORE && r < 0.60f) return Temperament.SKITTISH;
        if (r < 0.85f)                          return Temperament.PASSIVE;
        return Temperament.CURIOUS;
    }

    private SocialStructure rollSocial(Diet diet, Temperament temp,
                                        Archetype arch, Random rng) {
        // Pack hunters are carnivores with PACK_AGGRESSIVE temperament
        if (diet == Diet.CARNIVORE && rng.nextFloat() < 0.25f) {
            return SocialStructure.PACK;
        }
        // Hexapods strongly bias toward swarm or colonial
        if (arch == Archetype.HEXAPOD) {
            float r = rng.nextFloat();
            if (r < 0.40f) return SocialStructure.SWARM;
            if (r < 0.65f) return SocialStructure.COLONIAL;
            return SocialStructure.SOLITARY;
        }
        // Herbivores benefit from group safety
        if (diet == Diet.HERBIVORE) {
            float r = rng.nextFloat();
            if (r < 0.40f) return SocialStructure.HERD;
            if (r < 0.60f) return SocialStructure.FAMILY_UNIT;
            return SocialStructure.SOLITARY;
        }
        float r = rng.nextFloat();
        if (r < 0.50f) return SocialStructure.SOLITARY;
        if (r < 0.70f) return SocialStructure.PAIR_BONDED;
        if (r < 0.85f) return SocialStructure.FAMILY_UNIT;
        return SocialStructure.SOLITARY_NEST;
    }

    private float foragingRange(Diet diet, SocialStructure social, float massKg) {
        float base = switch (diet) {
            case CARNIVORE   -> 80f;
            case OMNIVORE    -> 50f;
            case HERBIVORE   -> 40f;
            case DETRITIVORE -> 20f;
            case PHOTOVORE   ->  5f; // barely moves
            default          -> 30f;
        };
        // Pack hunters range farther; herds move with the food supply
        if (social == SocialStructure.PACK)  base *= 1.4f;
        if (social == SocialStructure.HERD)  base *= 1.8f;
        base += massKg * 0.02f; // larger body = larger home range
        return base;
    }
}
```

---

## AI Behavior State Machine

```java
public enum CreatureAIState {
    IDLE,            // resting; minimal processing; no movement
    ROAMING,         // slow casual movement within foraging range
    FORAGING,        // actively searching for food; heading toward resource
    FEEDING,         // stationary; eating
    FLEEING,         // running away from threat at max speed
    HIDING,          // burrowed or motionless in cover after fleeing
    ALERT,           // threat detected; observing, not yet reacting
    STALKING,        // hunting: slow approach toward prey, low profile
    CHARGING,        // full-speed attack run toward target
    ATTACKING,       // melee range; performing strike animation
    TERRITORIAL_DISPLAY, // warning display: size inflation, colour flash
    CALLING,         // vocalisation to summon pack members
    SLEEPING,        // deep rest during inactive activity cycle phase
    MATING_DISPLAY,  // courtship behaviour
    NESTING,         // attending nest / brood
}

public class CreatureAIStateMachine {

    /**
     * Transition rules derived from CreatureBehaviorSpec.
     * The AI system ticks this; the outputs are target position + animation cues.
     */
    public CreatureAIState transition(CreatureAIState current,
                                       CreaturePerceptionResult perception,
                                       CreatureBehaviorSpec spec,
                                       float timeInState) {
        boolean threatVisible  = perception.nearestThreat != null
                               && perception.nearestThreat.distance < spec.detectionRadiusM;
        boolean preyVisible    = perception.nearestPrey != null
                               && perception.nearestPrey.distance < spec.detectionRadiusM;
        boolean inTerritory    = perception.playerDistance < spec.territorialRadiusM;

        switch (current) {
            case IDLE:
                if (!isActiveTime(spec)) return CreatureAIState.SLEEPING;
                if (threatVisible) return threatResponse(spec);
                if (preyVisible && spec.diet == Diet.CARNIVORE)
                    return CreatureAIState.ALERT;
                if (timeInState > 10f) return CreatureAIState.ROAMING;
                return CreatureAIState.IDLE;

            case ROAMING:
                if (!isActiveTime(spec)) return CreatureAIState.IDLE;
                if (threatVisible) return threatResponse(spec);
                if (preyVisible && spec.diet == Diet.CARNIVORE)
                    return CreatureAIState.STALKING;
                if (perception.foodSourceNearby) return CreatureAIState.FORAGING;
                if (timeInState > 30f) return CreatureAIState.IDLE;
                return CreatureAIState.ROAMING;

            case ALERT:
                if (timeInState > 3f && preyVisible)  return CreatureAIState.STALKING;
                if (threatVisible)                    return threatResponse(spec);
                if (!preyVisible && timeInState > 5f) return CreatureAIState.ROAMING;
                return CreatureAIState.ALERT;

            case STALKING:
                if (!preyVisible)                    return CreatureAIState.ROAMING;
                if (perception.nearestPrey.distance < spec.strikeRangeM * 2f)
                    return CreatureAIState.CHARGING;
                return CreatureAIState.STALKING;

            case FLEEING:
                if (!threatVisible && timeInState > 5f)
                    return spec.isBurrowing ? CreatureAIState.HIDING : CreatureAIState.IDLE;
                return CreatureAIState.FLEEING;

            case TERRITORIAL_DISPLAY:
                if (!inTerritory)    return CreatureAIState.IDLE;
                if (timeInState > 4f) return CreatureAIState.CHARGING;
                return CreatureAIState.TERRITORIAL_DISPLAY;

            default:
                return CreatureAIState.IDLE;
        }
    }

    private CreatureAIState threatResponse(CreatureBehaviorSpec spec) {
        return switch (spec.temperament) {
            case SKITTISH   -> CreatureAIState.FLEEING;
            case PASSIVE    -> CreatureAIState.FLEEING;
            case CURIOUS    -> CreatureAIState.ALERT;
            case TERRITORIAL-> CreatureAIState.TERRITORIAL_DISPLAY;
            case AGGRESSIVE -> CreatureAIState.CHARGING;
            case AMBUSH     -> CreatureAIState.ATTACKING; // already positioned
            case PACK_AGGRESSIVE -> CreatureAIState.CALLING; // summon pack first
        };
    }
}
```

---

## Ecosystem Table

```java
public enum EcosystemRole {
    APEX_PREDATOR,   // top of food chain; low population; large territory
    PREDATOR,        // hunts prey but has predators of its own
    PREY,            // primary food source; high population density
    SCAVENGER,       // cleans up dead material; follows apex predators
    DECOMPOSER,      // breaks down dead organic matter (fungal/detritivore)
    POLLINATOR,      // moves between plants; small, fast, non-threatening
    SYMBIONT,        // attached to or follows another creature type
    FILTER,          // filters environment; sessile or slow-moving
}

public class EcosystemTableGenerator {

    /**
     * Generate a biome's ecosystem table: which species appear, in what
     * ratios, and which predator-prey relationships exist.
     * Call once per planet biome zone; results feed the encounter spawner.
     */
    public EcosystemTable generate(Biome biome, Array<CreatureSpec> candidatePool,
                                    float planetFoodDensity, Random rng) {
        EcosystemTable table = new EcosystemTable();

        // Separate candidates by ecosystem role
        Array<CreatureSpec> apexes    = byRole(candidatePool, EcosystemRole.APEX_PREDATOR);
        Array<CreatureSpec> predators = byRole(candidatePool, EcosystemRole.PREDATOR);
        Array<CreatureSpec> prey      = byRole(candidatePool, EcosystemRole.PREY);

        // Spawn weight: prey most common, apex least common
        for (CreatureSpec spec : prey)      table.add(spec, 50f * planetFoodDensity);
        for (CreatureSpec spec : predators) table.add(spec, 15f);
        for (CreatureSpec spec : apexes)    table.add(spec,  3f);
        for (CreatureSpec spec : candidatePool) {
            if (spec.behavior.ecosystemRole == EcosystemRole.SCAVENGER)
                table.add(spec, 10f);
            if (spec.behavior.ecosystemRole == EcosystemRole.POLLINATOR)
                table.add(spec, 20f);
        }

        // Link predators to prey targets
        for (CreatureSpec pred : concat(apexes, predators)) {
            // Assign 1–3 prey species this predator hunts
            Array<CreatureSpec> targets = selectPreyTargets(pred, prey, rng);
            for (CreatureSpec target : targets) {
                table.addPreyLink(pred.id, target.id);
            }
        }

        // Scavengers follow apex predators
        for (CreatureSpec scav : byRole(candidatePool, EcosystemRole.SCAVENGER)) {
            if (!apexes.isEmpty())
                table.addFollowLink(scav.id, apexes.random().id);
        }

        return table;
    }

    private Array<CreatureSpec> selectPreyTargets(CreatureSpec predator,
                                                    Array<CreatureSpec> preyPool,
                                                    Random rng) {
        // Predators hunt prey significantly smaller than themselves
        Array<CreatureSpec> suitable = preyPool.select(
            p -> p.massKg < predator.massKg * 0.7f && p.massKg > predator.massKg * 0.05f);
        suitable.shuffle(rng);
        return suitable.subList(0, Math.min(3, suitable.size));
    }
}
```

---

## Spawn Density Field

```java
public class CreatureSpawnDensityField {

    /**
     * Compute per-cell spawn probability for each ecosystem role.
     * Density depends on biome food richness, role, activity cycle, and time of day.
     */
    public float spawnDensity(EcosystemRole role, Biome biome,
                               float timeOfDayNorm, // 0 = midnight, 0.5 = noon
                               ActivityCycle activity) {
        float biomeFoodFactor = biomeFoodRichness(biome);
        float timeFactor      = activityTimeFactor(activity, timeOfDayNorm);

        float baseWeight = switch (role) {
            case APEX_PREDATOR -> 0.05f;
            case PREDATOR      -> 0.15f;
            case PREY          -> 0.50f * biomeFoodFactor;
            case SCAVENGER     -> 0.10f;
            case DECOMPOSER    -> 0.20f;
            case POLLINATOR    -> 0.30f * biomeFoodFactor;
            case SYMBIONT      -> 0.08f;
            case FILTER        -> 0.15f;
        };
        return baseWeight * timeFactor;
    }

    private float biomeFoodRichness(Biome b) {
        return switch (b) {
            case TROPICAL_RAINFOREST -> 1.0f;
            case TROPICAL_SEASONAL   -> 0.85f;
            case TEMPERATE_RAINFOREST-> 0.80f;
            case TEMPERATE_DECIDUOUS -> 0.70f;
            case SAVANNA, GRASSLAND  -> 0.65f;
            case BOREAL_FOREST       -> 0.50f;
            case SHRUBLAND           -> 0.40f;
            case TUNDRA              -> 0.25f;
            case HOT_DESERT, COLD_DESERT -> 0.10f;
            case ALIEN_FUNGAL        -> 0.60f;
            case ALIEN_CRYSTAL       -> 0.15f;
            case ALIEN_LAVA_FIELDS   -> 0.05f;
            default                  -> 0.30f;
        };
    }

    private float activityTimeFactor(ActivityCycle cycle, float timeNorm) {
        return switch (cycle) {
            case DIURNAL     -> timeNorm > 0.2f && timeNorm < 0.8f ? 1.0f : 0.05f;
            case NOCTURNAL   -> timeNorm < 0.2f || timeNorm > 0.8f ? 1.0f : 0.05f;
            case CREPUSCULAR -> (Math.abs(timeNorm - 0.2f) < 0.1f ||
                                 Math.abs(timeNorm - 0.8f) < 0.1f) ? 1.0f : 0.15f;
            case CATHEMERAL  -> 0.6f; // always somewhat active
            default          -> 0.5f;
        };
    }
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| All creatures have identical temperament | Roll temperament from diet + mass + biome; heavy carnivores territorial, small herbivores skittish |
| Apex predators spawning at prey density | Apex spawns at 0.05× prey density; never let them saturate the ecosystem |
| Photovore / lithophage diet in normal biomes | Alien diets only in `ALIEN_*` biomes; don't assign them to temperate forests |
| Pack creatures spawning as individuals | When `socialStructure == PACK`, always spawn `packMinSize` to `packMaxSize` at once |
| Activity cycle not checked at spawn time | Nocturnal creatures spawn only at `timeOfDayNorm < 0.2 || > 0.8`; check before spawning |
| Predator-prey links missing | Every carnivore must have ≥1 prey species in the ecosystem table or it has nothing to hunt |
| AI state machine runs every frame for distant creatures | LOD the AI: full state machine < 80 m, simplified at distance, sleep > 400 m |
| Warning colour on non-venomous creature (from Cycle C) | `isVenomous` is set here in Cycle D; Cycle C reads it — generate behavior first |
