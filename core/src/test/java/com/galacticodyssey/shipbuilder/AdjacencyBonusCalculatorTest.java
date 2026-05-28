package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AdjacencyBonusCalculatorTest {
    private AdjacencyBonusCalculator calc;
    private ShipDesign design;

    @BeforeEach
    void setUp() {
        calc = new AdjacencyBonusCalculator();
        design = new ShipDesign(ShipSizeClass.MEDIUM);
    }

    @Test
    void medbayNextToCrewQuarters_grantsHealingBonus() {
        design.addRoom(new RoomDesign(RoomType.CREW_QUARTERS, 0, 0, 0, 3, 3, 3));
        design.addRoom(new RoomDesign(RoomType.MEDBAY, 3, 0, 0, 3, 3, 3));
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        assertTrue(bonuses.stream().anyMatch(b -> b.stat.equals("healingRate") && b.bonus == 0.15f));
    }

    @Test
    void medbayFarFromCrewQuarters_noHealingBonus() {
        design.addRoom(new RoomDesign(RoomType.CREW_QUARTERS, 0, 0, 0, 3, 3, 3));
        design.addRoom(new RoomDesign(RoomType.MEDBAY, 10, 0, 0, 3, 3, 3));
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        assertFalse(bonuses.stream().anyMatch(b -> b.stat.equals("healingRate")));
    }

    @Test
    void engineRoomAtStern_grantsFuelBonus() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 3, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 15, 0, 0, 4, 3, 3));
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        assertTrue(bonuses.stream().anyMatch(b -> b.stat.equals("fuelEfficiency")));
    }

    @Test
    void cockpitAtBow_grantsSensorBonus() {
        design.addRoom(new RoomDesign(RoomType.COCKPIT, 0, 0, 0, 3, 3, 3));
        design.addRoom(new RoomDesign(RoomType.ENGINE_ROOM, 15, 0, 0, 4, 3, 3));
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        assertTrue(bonuses.stream().anyMatch(b -> b.stat.equals("sensorRange")));
    }

    @Test
    void noBonuses_whenNoMatchingConditions() {
        design.addRoom(new RoomDesign(RoomType.CARGO_BAY, 5, 0, 0, 4, 3, 3));
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        assertTrue(bonuses.isEmpty());
    }
}
