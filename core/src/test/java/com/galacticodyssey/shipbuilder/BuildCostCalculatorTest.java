package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildCostCalculatorTest {
    private BuildCostCalculator calc;

    @BeforeEach
    void setUp() {
        calc = new BuildCostCalculator();
    }

    @Test
    void roomConstructionCost_baseAndVolumeScaling() {
        RoomDesign cockpit = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        // base=5000 + perM3=200 * volume=36 = 5000 + 7200 = 12200
        assertEquals(12200, calc.roomConstructionCost(cockpit));
    }

    @Test
    void roomRefund_is70Percent() {
        RoomDesign cockpit = new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3);
        int cost = calc.roomConstructionCost(cockpit);
        assertEquals((int)(cost * 0.7f), calc.roomRefund(cockpit));
    }

    @Test
    void hullCost_scalesBySizeClass() {
        assertEquals(10000, calc.hullCost(ShipSizeClass.SMALL, 10f));
        assertEquals(25000, calc.hullCost(ShipSizeClass.MEDIUM, 10f));
        assertEquals(50000, calc.hullCost(ShipSizeClass.LARGE, 10f));
    }

    @Test
    void totalDesignCost_sumsAllComponents() {
        ShipDesign design = ShipDesign.fromSeed(42L, ShipSizeClass.SMALL);
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 4, 3, 3));
        design.setModule("WPN-1", new ModuleAssignment("laser_mk1", "bp_laser_mk1"));
        int total = calc.totalDesignCost(design);
        assertTrue(total > 0);
        int expectedHull = calc.hullCost(ShipSizeClass.SMALL, design.hull.estimateSpineLength());
        int expectedRoom = calc.roomConstructionCost(design.rooms.get(0));
        int expectedModule = calc.moduleInstallFee();
        assertEquals(expectedHull + expectedRoom + expectedModule, total);
    }
}
