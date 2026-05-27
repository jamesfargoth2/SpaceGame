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

        for (StationModuleType mandatory : mandatoryModulesForType(type)) {
            modules.add(new StationModule(mandatory, tier, modules.size()));
        }

        while (modules.size() < totalModules) {
            StationModuleType moduleType = OPTIONAL_MODULES[rng.nextInt(OPTIONAL_MODULES.length)];
            int level = RngUtil.range(rng, 1, tier + 1);
            modules.add(new StationModule(moduleType, level, modules.size()));
        }

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
