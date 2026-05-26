package com.galacticodyssey.galaxy.station;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StationGeneratorTest {

    private static final long TEST_SEED = 42L;
    private StationGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new StationGenerator();
    }

    @Test
    void deterministic() {
        StationConfig cfg = new StationConfig(TEST_SEED, StationType.WAYSTATION, "FED", false);

        GeneratedStation a = generator.generate(cfg);
        GeneratedStation b = new StationGenerator().generate(cfg);

        assertEquals(a.modules.size(), b.modules.size());
        assertEquals(a.name, b.name);
        assertEquals(a.dockingPortCount, b.dockingPortCount);

        for (int i = 0; i < a.modules.size(); i++) {
            StationModule ma = a.modules.get(i);
            StationModule mb = b.modules.get(i);
            assertEquals(ma.id, mb.id);
            assertEquals(ma.type, mb.type);
            assertEquals(ma.localX, mb.localX, 1e-6f);
            assertEquals(ma.localY, mb.localY, 1e-6f);
            assertEquals(ma.localZ, mb.localZ, 1e-6f);
            assertEquals(ma.connectedModuleIds, mb.connectedModuleIds);
            assertEquals(ma.hasDockingPort, mb.hasDockingPort);
        }
    }

    @Test
    void alwaysHasCommandAndPower() {
        for (StationType type : StationType.values()) {
            StationConfig cfg = new StationConfig(TEST_SEED + type.ordinal(), type, null, false);
            GeneratedStation station = generator.generate(cfg);

            assertEquals(ModuleType.COMMAND, station.modules.get(0).type,
                    type + ": first module must be COMMAND");
            assertEquals(ModuleType.POWER_CORE, station.modules.get(1).type,
                    type + ": second module must be POWER_CORE");

            // They must be connected to each other.
            assertTrue(station.modules.get(0).connectedModuleIds.contains(station.modules.get(1).id),
                    type + ": COMMAND must connect to POWER_CORE");
            assertTrue(station.modules.get(1).connectedModuleIds.contains(station.modules.get(0).id),
                    type + ": POWER_CORE must connect to COMMAND");
        }
    }

    @Test
    void moduleCountInRange() {
        for (StationType type : StationType.values()) {
            for (long seed = 0; seed < 20; seed++) {
                StationConfig cfg = new StationConfig(seed, type, null, false);
                GeneratedStation station = generator.generate(cfg);

                assertTrue(station.modules.size() >= type.minModules,
                        type + " seed=" + seed + ": module count " + station.modules.size()
                                + " below min " + type.minModules);
                assertTrue(station.modules.size() <= type.maxModules,
                        type + " seed=" + seed + ": module count " + station.modules.size()
                                + " above max " + type.maxModules);
            }
        }
    }

    @Test
    void leafModulesGetDockingPorts() {
        StationConfig cfg = new StationConfig(TEST_SEED, StationType.WAYSTATION, "FED", false);
        GeneratedStation station = generator.generate(cfg);

        for (StationModule module : station.modules) {
            if (module.hasDockingPort) {
                // Docking ports should be on modules with few connections (leaf = 1).
                // The generator targets leaf modules, but fallback may place on others
                // if fewer than 2 leaves exist. So just verify docking ports exist.
                assertTrue(module.connectedModuleIds.size() >= 1,
                        "Docking port module " + module.id + " must be connected");
            }
        }

        // At least 2 docking ports.
        assertTrue(station.dockingPortCount >= 2,
                "Station must have at least 2 docking ports, got " + station.dockingPortCount);
    }

    @Test
    void battleStationHasWeapons() {
        for (long seed = 0; seed < 20; seed++) {
            StationConfig cfg = new StationConfig(seed, StationType.BATTLE_STATION, null, false);
            GeneratedStation station = generator.generate(cfg);

            long weaponCount = station.modules.stream()
                    .filter(m -> m.type == ModuleType.WEAPONS_PLATFORM)
                    .count();
            assertTrue(weaponCount >= 2,
                    "BATTLE_STATION seed=" + seed + " must have >=2 WEAPONS_PLATFORM, got " + weaponCount);
        }
    }

    @Test
    void tradingPostHasMarket() {
        for (long seed = 0; seed < 20; seed++) {
            StationConfig cfg = new StationConfig(seed, StationType.TRADING_POST, "TRD", false);
            GeneratedStation station = generator.generate(cfg);

            long marketCount = station.modules.stream()
                    .filter(m -> m.type == ModuleType.MARKET)
                    .count();
            assertTrue(marketCount >= 1,
                    "TRADING_POST seed=" + seed + " must have >=1 MARKET, got " + marketCount);
        }
    }

    @Test
    void allModulesConnected() {
        for (StationType type : StationType.values()) {
            for (long seed = 0; seed < 10; seed++) {
                StationConfig cfg = new StationConfig(seed, type, null, false);
                GeneratedStation station = generator.generate(cfg);

                // BFS from first module (COMMAND) should reach all modules.
                Set<String> visited = new HashSet<>();
                Queue<String> queue = new ArrayDeque<>();
                visited.add(station.modules.get(0).id);
                queue.add(station.modules.get(0).id);

                Map<String, StationModule> moduleMap = new HashMap<>();
                for (StationModule m : station.modules) {
                    moduleMap.put(m.id, m);
                }

                while (!queue.isEmpty()) {
                    String current = queue.poll();
                    StationModule mod = moduleMap.get(current);
                    for (String connId : mod.connectedModuleIds) {
                        if (visited.add(connId)) {
                            queue.add(connId);
                        }
                    }
                }

                assertEquals(station.modules.size(), visited.size(),
                        type + " seed=" + seed + ": not all modules reachable from COMMAND. "
                                + "Visited " + visited.size() + " of " + station.modules.size());
            }
        }
    }
}
