package com.galacticodyssey.planet.terrain.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.events.PlayerEnterVehicleEvent;
import com.galacticodyssey.core.events.PlayerExitVehicleEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleEventsTest {
    @Test
    void eventsCarryReferences() {
        Entity ship = new Entity();
        Entity vehicle = new Entity();
        Entity player = new Entity();

        VehicleDeployedEvent dep = new VehicleDeployedEvent(vehicle, ship);
        assertSame(vehicle, dep.vehicle);
        assertSame(ship, dep.ship);

        VehicleRetrievedEvent ret = new VehicleRetrievedEvent("rover_light", ship);
        assertEquals("rover_light", ret.vehicleDefinitionId);
        assertSame(ship, ret.ship);

        PlayerEnterVehicleEvent enter = new PlayerEnterVehicleEvent(player, vehicle);
        assertSame(player, enter.player);
        assertSame(vehicle, enter.vehicle);

        PlayerExitVehicleEvent exit = new PlayerExitVehicleEvent(player, vehicle);
        assertSame(player, exit.player);
        assertSame(vehicle, exit.vehicle);
    }
}
