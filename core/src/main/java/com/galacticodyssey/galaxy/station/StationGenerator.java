package com.galacticodyssey.galaxy.station;

import com.galacticodyssey.data.names.SpaceNameGenerator;
import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

/**
 * Procedurally generates space station module graphs.
 * Builds a spine-and-branch layout: a central spine grows along the Y axis
 * with radial branches extending from junction modules.
 * Pure data generator -- no libGDX dependencies.
 */
public final class StationGenerator {

    /** Module spacing along the Y axis for spine segments. */
    private static final float SPINE_SPACING = 20f;

    /** Radial offset for branch modules extending from junctions. */
    private static final float BRANCH_SPACING = 15f;

    /** Weighted branch module types: type + cumulative weight threshold. */
    private static final ModuleType[] BRANCH_TYPES = {
        ModuleType.HABITATION,       // 20%
        ModuleType.CARGO_BAY,        // 15%
        ModuleType.FUEL_DEPOT,       // 15%
        ModuleType.RESEARCH_LAB,     // 15%
        ModuleType.FACTORY_FLOOR,    // 15%
        ModuleType.CORRIDOR_STRAIGHT // 20%
    };
    private static final float[] BRANCH_WEIGHTS = { 0.20f, 0.35f, 0.50f, 0.65f, 0.80f, 1.00f };

    private final SpaceNameGenerator nameGenerator;

    public StationGenerator() {
        this.nameGenerator = new SpaceNameGenerator();
    }

    public StationGenerator(SpaceNameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    /**
     * Generate a space station from the given configuration.
     */
    public GeneratedStation generate(StationConfig cfg) {
        long domainSeed = SeedDeriver.domain(cfg.seed, SeedDeriver.STATION_DOMAIN);
        Random rng = new Random(domainSeed);

        int moduleCount = RngUtil.range(rng, cfg.stationType.minModules,
                cfg.stationType.maxModules + 1);

        // Mutable module builder list; modules track their connections mutably
        // and are finalized into immutable StationModule objects at the end.
        List<ModuleBuilder> builders = new ArrayList<>();

        // Step 1 & 2: Command + Power Core at center, connected.
        ModuleBuilder command = addModule(builders, ModuleType.COMMAND, 0f, 0f, 0f);
        ModuleBuilder power = addModule(builders, ModuleType.POWER_CORE, 0f, SPINE_SPACING * 0.5f, 0f);
        connect(command, power);

        // Step 3: Build spine along Y axis.
        int spineLength = Math.max(1, moduleCount / 3);
        List<ModuleBuilder> junctions = new ArrayList<>();
        ModuleBuilder prevSpine = power;
        float spineY = SPINE_SPACING;

        for (int i = 0; i < spineLength && builders.size() < moduleCount; i++) {
            boolean isJunction = (i % 3 == 2); // every 3rd module
            ModuleType spineType = isJunction ? ModuleType.CORRIDOR_JUNCTION : ModuleType.SPINE_SECTION;
            ModuleBuilder seg = addModule(builders, spineType, 0f, spineY, 0f);
            connect(prevSpine, seg);
            if (isJunction) {
                junctions.add(seg);
            }
            prevSpine = seg;
            spineY += SPINE_SPACING;
        }

        // Step 4: Branch from junctions.
        for (ModuleBuilder junction : junctions) {
            if (builders.size() >= moduleCount) break;
            int branchCount = RngUtil.range(rng, 1, 4); // 1-3 branches
            for (int b = 0; b < branchCount && builders.size() < moduleCount; b++) {
                int branchLength = RngUtil.range(rng, 1, 5); // 1-4 modules
                // Determine radial direction for this branch.
                float angle = (float) (2.0 * Math.PI * b / branchCount) + rng.nextFloat() * 0.5f;
                float dirX = (float) Math.cos(angle);
                float dirZ = (float) Math.sin(angle);

                ModuleBuilder prevBranch = junction;
                for (int m = 0; m < branchLength && builders.size() < moduleCount; m++) {
                    ModuleType branchType = rollBranchType(rng);
                    float bx = junction.localX + dirX * BRANCH_SPACING * (m + 1);
                    float bz = junction.localZ + dirZ * BRANCH_SPACING * (m + 1);
                    ModuleBuilder branchModule = addModule(builders, branchType, bx, junction.localY, bz);
                    connect(prevBranch, branchModule);
                    prevBranch = branchModule;
                }
            }
        }

        // Fill remaining slots with branch modules off the last spine segment.
        while (builders.size() < moduleCount) {
            float angle = rng.nextFloat() * (float) (2.0 * Math.PI);
            float dirX = (float) Math.cos(angle);
            float dirZ = (float) Math.sin(angle);
            ModuleType branchType = rollBranchType(rng);
            float bx = prevSpine.localX + dirX * BRANCH_SPACING;
            float bz = prevSpine.localZ + dirZ * BRANCH_SPACING;
            ModuleBuilder extra = addModule(builders, branchType, bx, prevSpine.localY, bz);
            connect(prevSpine, extra);
        }

        // Step 5: Ensure required modules.
        ensureRequired(builders, ModuleType.LIFE_SUPPORT, 1, rng);

        if (moduleCount > 10) {
            ensureRequired(builders, ModuleType.MEDICAL, 1, rng);
        }

        if (cfg.stationType == StationType.TRADING_POST || cfg.stationType == StationType.STARPORT) {
            ensureRequired(builders, ModuleType.MARKET, 1, rng);
        }

        if (cfg.stationType == StationType.BATTLE_STATION) {
            ensureRequired(builders, ModuleType.WEAPONS_PLATFORM, 2, rng);
        }

        // Step 6: Place docking ports on leaf modules.
        int maxDockingPorts = maxDockingPorts(cfg.stationType);
        int dockingCount = 0;
        for (ModuleBuilder mb : builders) {
            if (mb.connections.size() == 1 && dockingCount < maxDockingPorts) {
                mb.hasDockingPort = true;
                // Docking port faces away from the single connection.
                ModuleBuilder neighbor = mb.connections.get(0);
                float dx = mb.localX - neighbor.localX;
                float dy = mb.localY - neighbor.localY;
                float dz = mb.localZ - neighbor.localZ;
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len > 1e-6f) {
                    mb.dockFacingX = dx / len;
                    mb.dockFacingY = dy / len;
                    mb.dockFacingZ = dz / len;
                } else {
                    mb.dockFacingX = 1f;
                    mb.dockFacingY = 0f;
                    mb.dockFacingZ = 0f;
                }
                dockingCount++;
            }
        }

        // Ensure minimum of 2 docking ports.
        if (dockingCount < 2) {
            for (ModuleBuilder mb : builders) {
                if (!mb.hasDockingPort && dockingCount < 2) {
                    mb.hasDockingPort = true;
                    mb.dockFacingX = 1f;
                    mb.dockFacingY = 0f;
                    mb.dockFacingZ = 0f;
                    dockingCount++;
                }
            }
        }

        // Step 7: Generate station name.
        String factionPrefix = cfg.factionId != null ? cfg.factionId : "IND";
        String stationName = nameGenerator.stationName(factionPrefix, rng);

        // Convert builders to immutable StationModule objects.
        List<StationModule> modules = new ArrayList<>();
        for (ModuleBuilder mb : builders) {
            List<String> connIds = new ArrayList<>();
            for (ModuleBuilder conn : mb.connections) {
                connIds.add(conn.id);
            }
            modules.add(new StationModule(
                    mb.id, mb.type, mb.localX, mb.localY, mb.localZ,
                    connIds, mb.hasDockingPort,
                    mb.dockFacingX, mb.dockFacingY, mb.dockFacingZ));
        }

        return new GeneratedStation(modules, dockingCount, stationName);
    }

    // -- Internal helpers ---------------------------------------------------------

    private ModuleBuilder addModule(List<ModuleBuilder> builders, ModuleType type,
                                    float x, float y, float z) {
        String id = "mod_" + builders.size();
        ModuleBuilder mb = new ModuleBuilder(id, type, x, y, z);
        builders.add(mb);
        return mb;
    }

    private void connect(ModuleBuilder a, ModuleBuilder b) {
        if (!a.connections.contains(b)) {
            a.connections.add(b);
        }
        if (!b.connections.contains(a)) {
            b.connections.add(a);
        }
    }

    private ModuleType rollBranchType(Random rng) {
        float roll = rng.nextFloat();
        for (int i = 0; i < BRANCH_WEIGHTS.length; i++) {
            if (roll < BRANCH_WEIGHTS[i]) {
                return BRANCH_TYPES[i];
            }
        }
        return BRANCH_TYPES[BRANCH_TYPES.length - 1];
    }

    /**
     * Ensures at least {@code requiredCount} modules of the given type exist.
     * Replaces generic branch modules when the requirement is not met.
     * Iterates from the end to prefer replacing leaf/branch modules over
     * structural spine modules.
     */
    private void ensureRequired(List<ModuleBuilder> builders, ModuleType required,
                                int requiredCount, Random rng) {
        long existing = builders.stream().filter(m -> m.type == required).count();
        int deficit = requiredCount - (int) existing;
        if (deficit <= 0) return;

        // First pass: replace easily replaceable branch modules (iterate from end).
        for (int i = builders.size() - 1; i >= 0 && deficit > 0; i--) {
            ModuleBuilder mb = builders.get(i);
            if (isReplaceable(mb.type)) {
                mb.type = required;
                deficit--;
            }
        }

        // Second pass: replace any non-critical module if still short.
        for (int i = builders.size() - 1; i >= 0 && deficit > 0; i--) {
            ModuleBuilder mb = builders.get(i);
            if (isSecondaryReplaceable(mb.type)) {
                mb.type = required;
                deficit--;
            }
        }
    }

    private boolean isReplaceable(ModuleType type) {
        return type == ModuleType.CORRIDOR_STRAIGHT
                || type == ModuleType.HABITATION
                || type == ModuleType.FACTORY_FLOOR
                || type == ModuleType.CARGO_BAY
                || type == ModuleType.FUEL_DEPOT
                || type == ModuleType.RESEARCH_LAB;
    }

    /** Fallback: replace spine/junction modules if no branch modules available. */
    private boolean isSecondaryReplaceable(ModuleType type) {
        return type == ModuleType.SPINE_SECTION
                || type == ModuleType.CORRIDOR_JUNCTION;
    }

    private int maxDockingPorts(StationType type) {
        switch (type) {
            case OUTPOST:           return 2;
            case TRADING_POST:      return 4;
            case WAYSTATION:        return 8;
            case STARPORT:          return 16;
            case SHIPYARD:          return 6;
            case RESEARCH_STATION:  return 3;
            case BATTLE_STATION:    return 4;
            default:                return 2;
        }
    }

    // -- Mutable builder used during generation -----------------------------------

    private static final class ModuleBuilder {
        final String id;
        ModuleType type; // mutable for ensureRequired replacements
        final float localX;
        final float localY;
        final float localZ;
        final List<ModuleBuilder> connections = new ArrayList<>();
        boolean hasDockingPort;
        float dockFacingX;
        float dockFacingY;
        float dockFacingZ;

        ModuleBuilder(String id, ModuleType type, float x, float y, float z) {
            this.id = id;
            this.type = type;
            this.localX = x;
            this.localY = y;
            this.localZ = z;
        }
    }
}
