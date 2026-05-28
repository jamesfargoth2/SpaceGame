package com.galacticodyssey.combat.fleet;

import com.galacticodyssey.combat.fleet.data.FleetShipClass;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetShipClassTest {

    @Test
    void eachClassMapsToSizeClass() {
        assertEquals(ShipSizeClass.SMALL, FleetShipClass.FIGHTER.sizeClass);
        assertEquals(ShipSizeClass.SMALL, FleetShipClass.BOMBER.sizeClass);
        assertEquals(ShipSizeClass.SMALL, FleetShipClass.CORVETTE.sizeClass);
        assertEquals(ShipSizeClass.MEDIUM, FleetShipClass.FRIGATE.sizeClass);
        assertEquals(ShipSizeClass.MEDIUM, FleetShipClass.DESTROYER.sizeClass);
        assertEquals(ShipSizeClass.MEDIUM, FleetShipClass.CRUISER.sizeClass);
        assertEquals(ShipSizeClass.LARGE, FleetShipClass.BATTLECRUISER.sizeClass);
        assertEquals(ShipSizeClass.LARGE, FleetShipClass.BATTLESHIP.sizeClass);
        assertEquals(ShipSizeClass.LARGE, FleetShipClass.CARRIER.sizeClass);
        assertEquals(ShipSizeClass.LARGE, FleetShipClass.DREADNOUGHT.sizeClass);
    }

    @Test
    void expendableClassesAreSmallShips() {
        assertTrue(FleetShipClass.FIGHTER.expendable);
        assertTrue(FleetShipClass.BOMBER.expendable);
        assertTrue(FleetShipClass.CORVETTE.expendable);
        assertFalse(FleetShipClass.FRIGATE.expendable);
        assertFalse(FleetShipClass.DREADNOUGHT.expendable);
    }

    @Test
    void firepowerWeightIncreasesWithSize() {
        assertTrue(FleetShipClass.FIGHTER.firepowerWeight < FleetShipClass.FRIGATE.firepowerWeight);
        assertTrue(FleetShipClass.FRIGATE.firepowerWeight < FleetShipClass.CRUISER.firepowerWeight);
        assertTrue(FleetShipClass.CRUISER.firepowerWeight < FleetShipClass.BATTLESHIP.firepowerWeight);
        assertTrue(FleetShipClass.BATTLESHIP.firepowerWeight < FleetShipClass.DREADNOUGHT.firepowerWeight);
    }
}
