package com.galacticodyssey.shipbuilder;

import java.util.*;

public class ShipStatsCalculator {

    public static class ShipStats {
        public float totalDps;
        public float maxWeaponRange;
        public float totalThrust;
        public float totalShieldHp;
        public float totalSensorRange;
        public float totalCargoCapacity;
        public float totalPowerDraw;
        public float totalWeight;
        public int crewCapacity;
    }

    private final Map<String, ModuleCatalogEntry> catalog = new LinkedHashMap<>();

    public void loadCatalog(List<ModuleCatalogEntry> entries) {
        for (ModuleCatalogEntry e : entries) catalog.put(e.moduleId, e);
    }

    public ModuleCatalogEntry getModule(String moduleId) {
        return catalog.get(moduleId);
    }

    public List<ModuleCatalogEntry> getModulesByType(ModuleCatalogEntry.HardpointType type) {
        List<ModuleCatalogEntry> result = new ArrayList<>();
        for (ModuleCatalogEntry e : catalog.values()) {
            if (e.hardpointType == type) result.add(e);
        }
        return result;
    }

    public ShipStats computeStats(ShipDesign design) {
        ShipStats stats = new ShipStats();
        for (ModuleAssignment assignment : design.modules.values()) {
            if (assignment == null) continue;
            ModuleCatalogEntry module = catalog.get(assignment.moduleId);
            if (module == null) continue;
            stats.totalDps += module.dps;
            stats.maxWeaponRange = Math.max(stats.maxWeaponRange, module.range);
            stats.totalThrust += module.thrust;
            stats.totalShieldHp += module.shieldHp;
            stats.totalSensorRange = Math.max(stats.totalSensorRange, module.sensorRange);
            stats.totalCargoCapacity += module.cargoCapacity;
            stats.totalPowerDraw += module.powerDraw;
            stats.totalWeight += module.weight;
        }
        for (RoomDesign room : design.rooms) {
            stats.totalWeight += room.volume() * 50;
        }
        return stats;
    }
}
