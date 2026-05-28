package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipStatsCalculatorTest {
    private ShipStatsCalculator calc;
    private ShipDesign design;

    @BeforeEach
    void setUp() {
        calc = new ShipStatsCalculator();

        ModuleCatalogEntry laser = new ModuleCatalogEntry();
        laser.moduleId = "laser_mk1";
        laser.hardpointType = ModuleCatalogEntry.HardpointType.WEAPON;
        laser.dps = 25; laser.range = 500; laser.powerDraw = 8; laser.weight = 200;

        ModuleCatalogEntry thruster = new ModuleCatalogEntry();
        thruster.moduleId = "thruster_basic";
        thruster.hardpointType = ModuleCatalogEntry.HardpointType.ENGINE;
        thruster.thrust = 15000; thruster.powerDraw = 5; thruster.weight = 300;

        calc.loadCatalog(Arrays.asList(laser, thruster));

        design = new ShipDesign(ShipSizeClass.SMALL);
        design.setModule("WPN-1", new ModuleAssignment("laser_mk1", "bp_laser_mk1"));
        design.setModule("ENG-1", new ModuleAssignment("thruster_basic", "bp_thruster_basic"));
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 3, 3, 3));
    }

    @Test
    void computeStats_sumsDps() {
        ShipStatsCalculator.ShipStats stats = calc.computeStats(design);
        assertEquals(25f, stats.totalDps, 0.01f);
    }

    @Test
    void computeStats_sumsThrust() {
        ShipStatsCalculator.ShipStats stats = calc.computeStats(design);
        assertEquals(15000f, stats.totalThrust, 0.01f);
    }

    @Test
    void computeStats_sumsPowerDraw() {
        ShipStatsCalculator.ShipStats stats = calc.computeStats(design);
        assertEquals(13f, stats.totalPowerDraw, 0.01f);
    }

    @Test
    void computeStats_includesRoomWeight() {
        ShipStatsCalculator.ShipStats stats = calc.computeStats(design);
        float moduleWeight = 200 + 300;
        float roomWeight = 27 * 50;
        assertEquals(moduleWeight + roomWeight, stats.totalWeight, 0.01f);
    }

    @Test
    void getModulesByType_filters() {
        List<ModuleCatalogEntry> weapons = calc.getModulesByType(ModuleCatalogEntry.HardpointType.WEAPON);
        assertEquals(1, weapons.size());
        assertEquals("laser_mk1", weapons.get(0).moduleId);
    }
}
