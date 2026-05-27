package com.galacticodyssey.galaxy.derelict;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

/**
 * Procedurally generates derelict wrecks for exploration and salvage.
 * Pure data generator -- no libGDX dependencies.
 */
public final class DerelictGenerator {

    // Base sections shared by all hull classes.
    private static final String[] BASE_SECTIONS = {
        "bridge", "engineering", "cargo_hold", "crew_quarters",
        "medbay", "corridor_1", "corridor_2", "airlock"
    };

    // Extra sections added as hull class grows.
    private static final String[] EXTRA_SECTIONS = {
        "hangar", "weapons_bay", "science_lab", "secondary_cargo",
        "officer_quarters", "reactor_room", "brig", "observation_deck",
        "corridor_3", "corridor_4", "shuttle_bay", "armory"
    };

    // Damage states used when a section is not INTACT.
    private static final SectionState[] DAMAGE_STATES = {
        SectionState.BREACHED, SectionState.COLLAPSED, SectionState.FLOODED,
        SectionState.ON_FIRE, SectionState.IRRADIATED, SectionState.DESTROYED
    };

    public DerelictGenerator() {}

    /** Generate a derelict wreck from the given configuration. */
    public GeneratedDerelict generate(DerelictConfig cfg) {
        long domainSeed = SeedDeriver.domain(cfg.seed, SeedDeriver.STATION_DOMAIN);
        Random rng = new Random(domainSeed);

        DamageProfile damage = generateDamageProfile(rng, cfg);
        List<String> sectionIds = generateSectionIds(cfg.originalHullClass, rng);
        Map<String, SectionState> sectionStates = assignSectionStates(sectionIds, damage, cfg, rng);
        Map<String, List<SalvageItem>> salvage = placeSalvage(sectionIds, sectionStates, cfg, rng);
        DerelictNarrative narrative = generateNarrative(cfg, rng);
        List<String> enemySpawns = placeEnemySpawns(sectionIds, sectionStates, cfg, rng);

        return new GeneratedDerelict(damage, sectionStates, salvage, narrative, enemySpawns);
    }

    // -- Step 1: Damage profile ------------------------------------------------

    private DamageProfile generateDamageProfile(Random rng, DerelictConfig cfg) {
        int impacts;
        int explosions;
        float systemicDmg;
        boolean radiation;
        boolean atmosphereLost;
        float uniformDeg;
        int alienMods;

        switch (cfg.wreckType) {
            case BATTLE_CASUALTY:
                impacts = RngUtil.range(rng, 3, 12);
                explosions = RngUtil.range(rng, 1, 6);
                systemicDmg = RngUtil.range(rng, 0.4f, 0.9f);
                radiation = rng.nextFloat() < 0.3f;
                atmosphereLost = rng.nextFloat() < 0.7f;
                uniformDeg = RngUtil.range(rng, 0.0f, 0.2f);
                alienMods = 0;
                break;
            case DRIVE_FAILURE:
                impacts = RngUtil.range(rng, 0, 3);
                explosions = RngUtil.range(rng, 1, 4);
                systemicDmg = RngUtil.range(rng, 0.3f, 0.7f);
                radiation = true; // drive failures always leak
                atmosphereLost = rng.nextFloat() < 0.4f;
                uniformDeg = RngUtil.range(rng, 0.1f, 0.3f);
                alienMods = 0;
                break;
            case COLLISION:
                impacts = RngUtil.range(rng, 1, 5);
                explosions = RngUtil.range(rng, 0, 3);
                systemicDmg = RngUtil.range(rng, 0.5f, 0.8f);
                radiation = rng.nextFloat() < 0.2f;
                atmosphereLost = rng.nextFloat() < 0.6f;
                uniformDeg = RngUtil.range(rng, 0.0f, 0.15f);
                alienMods = 0;
                break;
            case STRUCTURAL_FAILURE:
                impacts = 0;
                explosions = RngUtil.range(rng, 0, 2);
                systemicDmg = RngUtil.range(rng, 0.6f, 1.0f);
                radiation = rng.nextFloat() < 0.15f;
                atmosphereLost = rng.nextFloat() < 0.5f;
                uniformDeg = RngUtil.range(rng, 0.3f, 0.6f);
                alienMods = 0;
                break;
            case ABANDONED:
                impacts = 0;
                explosions = 0;
                systemicDmg = RngUtil.range(rng, 0.0f, 0.2f);
                radiation = false;
                atmosphereLost = rng.nextFloat() < 0.3f;
                uniformDeg = RngUtil.range(rng, 0.1f, 0.4f);
                alienMods = 0;
                break;
            case ANCIENT:
                impacts = RngUtil.range(rng, 0, 4);
                explosions = RngUtil.range(rng, 0, 2);
                systemicDmg = RngUtil.range(rng, 0.3f, 0.6f);
                radiation = rng.nextFloat() < 0.1f;
                atmosphereLost = true;
                uniformDeg = RngUtil.range(rng, 0.6f, 1.0f);
                alienMods = RngUtil.range(rng, 1, 6);
                break;
            default:
                throw new IllegalArgumentException("Unknown wreck type: " + cfg.wreckType);
        }

        // Random primary damage direction (normalized).
        float dx = rng.nextFloat() * 2f - 1f;
        float dy = rng.nextFloat() * 2f - 1f;
        float dz = rng.nextFloat() * 2f - 1f;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) { dx = 1f; dy = 0f; dz = 0f; len = 1f; }
        dx /= len; dy /= len; dz /= len;

        return new DamageProfile(impacts, explosions, dx, dy, dz,
                systemicDmg, radiation, atmosphereLost, uniformDeg, alienMods);
    }

    // -- Step 2: Section IDs ---------------------------------------------------

    private List<String> generateSectionIds(HullClass hullClass, Random rng) {
        List<String> sections = new ArrayList<>(Arrays.asList(BASE_SECTIONS));
        int extraCount;
        switch (hullClass) {
            case SHUTTLE:    extraCount = 0; break;
            case CORVETTE:   extraCount = 2; break;
            case FRIGATE:    extraCount = 4; break;
            case CRUISER:    extraCount = 7; break;
            case CAPITAL:    extraCount = EXTRA_SECTIONS.length; break;
            default:         extraCount = 0; break;
        }
        // Shuffle extra sections and pick the first extraCount.
        List<String> extras = new ArrayList<>(Arrays.asList(EXTRA_SECTIONS));
        Collections.shuffle(extras, rng);
        for (int i = 0; i < Math.min(extraCount, extras.size()); i++) {
            sections.add(extras.get(i));
        }
        return sections;
    }

    // -- Step 3: Section states ------------------------------------------------

    private Map<String, SectionState> assignSectionStates(
            List<String> sectionIds, DamageProfile damage,
            DerelictConfig cfg, Random rng) {

        Map<String, SectionState> states = new LinkedHashMap<>();

        for (int i = 0; i < sectionIds.size(); i++) {
            String id = sectionIds.get(i);

            if (i == 0) {
                // Entry point is always BREACHED.
                states.put(id, SectionState.BREACHED);
                continue;
            }

            if (cfg.wreckType == WreckType.ABANDONED) {
                // 60% intact for abandoned wrecks.
                if (rng.nextFloat() < 0.6f) {
                    states.put(id, SectionState.INTACT);
                } else {
                    states.put(id, pickDamageState(rng));
                }
            } else {
                // Proximity-based damage: use section index as a rough spatial proxy.
                float proximity = (float) i / sectionIds.size();
                float damageChance = damage.systemicDamage * (1f - proximity * 0.5f);
                damageChance = Math.max(0f, Math.min(1f, damageChance));

                if (rng.nextFloat() < (1f - damageChance)) {
                    states.put(id, SectionState.INTACT);
                } else {
                    states.put(id, pickDamageState(rng));
                }
            }
        }
        return states;
    }

    private SectionState pickDamageState(Random rng) {
        return DAMAGE_STATES[rng.nextInt(DAMAGE_STATES.length)];
    }

    // -- Step 4: Salvage placement ---------------------------------------------

    private Map<String, List<SalvageItem>> placeSalvage(
            List<String> sectionIds, Map<String, SectionState> states,
            DerelictConfig cfg, Random rng) {

        Map<String, List<SalvageItem>> salvage = new LinkedHashMap<>();

        for (String sectionId : sectionIds) {
            SectionState state = states.get(sectionId);
            if (state == SectionState.DESTROYED) {
                continue; // nothing salvageable in a destroyed section
            }

            List<SalvageItem> items = new ArrayList<>();

            if (sectionId.equals("cargo_hold") || sectionId.equals("secondary_cargo")) {
                // Cargo holds get the most salvage: 2-5 commodity items.
                int count = RngUtil.range(rng, 2, 6);
                for (int i = 0; i < count; i++) {
                    items.add(new SalvageItem(SalvageType.COMMODITY, rng.nextFloat()));
                }
            }

            if (sectionId.equals("engineering") && state == SectionState.INTACT) {
                // 30% chance of ship component in intact engineering.
                if (rng.nextFloat() < 0.3f) {
                    items.add(new SalvageItem(SalvageType.SHIP_COMPONENT,
                            RngUtil.range(rng, 0.3f, 1.0f)));
                }
            }

            if (sectionId.equals("bridge")) {
                // Data cores in bridge.
                if (rng.nextFloat() < 0.5f) {
                    items.add(new SalvageItem(SalvageType.DATA_CORE,
                            RngUtil.range(rng, 0.4f, 1.0f)));
                }
            }

            if (sectionId.equals("crew_quarters") || sectionId.equals("officer_quarters")) {
                if (rng.nextFloat() < 0.4f) {
                    items.add(new SalvageItem(SalvageType.PERSONAL_EFFECTS,
                            RngUtil.range(rng, 0.1f, 0.6f)));
                }
            }

            if (sectionId.equals("armory") || sectionId.equals("weapons_bay")) {
                if (rng.nextFloat() < 0.4f) {
                    items.add(new SalvageItem(SalvageType.WEAPON,
                            RngUtil.range(rng, 0.3f, 0.9f)));
                }
            }

            // General random salvage for non-empty sections.
            if (items.isEmpty() && state == SectionState.INTACT && rng.nextFloat() < 0.2f) {
                items.add(new SalvageItem(SalvageType.COMMODITY, rng.nextFloat()));
            }

            if (!items.isEmpty()) {
                salvage.put(sectionId, items);
            }
        }
        return salvage;
    }

    // -- Step 5: Narrative generation ------------------------------------------

    private DerelictNarrative generateNarrative(DerelictConfig cfg, Random rng) {
        int remainsCount;
        if (cfg.wreckType == WreckType.ABANDONED) {
            remainsCount = 0;
        } else {
            // Scale remains with hull class crew capacity.
            int maxCrew = cfg.originalHullClass.maxCrew;
            remainsCount = RngUtil.range(rng, 0, Math.max(1, maxCrew / 4) + 1);
        }

        int logCount = RngUtil.range(rng, 1, 6);
        boolean logsRevealCause = rng.nextFloat() < 0.6f;
        boolean logsRevealDestination = rng.nextFloat() < 0.3f;
        boolean hasMissionHook = rng.nextFloat() < 0.2f;
        int lastEntryDaysAgo = RngUtil.range(rng, 1, (int) Math.max(2, cfg.ageYears * 365));

        // Black box for CORVETTE and larger.
        boolean hasBlackBox = cfg.originalHullClass.ordinal() >= HullClass.CORVETTE.ordinal();
        boolean blackBoxIntact = hasBlackBox && rng.nextFloat() < 0.7f;

        // Survivor: 10% chance if config allows it.
        boolean hasSurvivor = cfg.hasSurvivor && rng.nextFloat() < 0.1f;

        return new DerelictNarrative(remainsCount, logCount,
                logsRevealCause, logsRevealDestination,
                hasMissionHook, lastEntryDaysAgo,
                hasBlackBox, blackBoxIntact, hasSurvivor);
    }

    // -- Step 6: Enemy spawn placement -----------------------------------------

    private List<String> placeEnemySpawns(
            List<String> sectionIds, Map<String, SectionState> states,
            DerelictConfig cfg, Random rng) {

        List<String> spawns = new ArrayList<>();
        if (!cfg.hasScavengers) {
            return spawns;
        }
        for (String id : sectionIds) {
            SectionState state = states.get(id);
            if ((state == SectionState.INTACT || state == SectionState.BREACHED)
                    && rng.nextFloat() < 0.4f) {
                spawns.add(id);
            }
        }
        return spawns;
    }
}
