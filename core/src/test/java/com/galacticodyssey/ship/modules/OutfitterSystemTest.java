package com.galacticodyssey.ship.modules;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.modules.events.ModuleInstalledEvent;
import com.galacticodyssey.ship.modules.systems.OutfitterSystem;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutfitterSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private OutfitterSystem system;
    private Entity ship;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        system = new OutfitterSystem(eventBus);
        engine.addSystem(system);

        ship = new Entity();
        ShipDataComponent data = new ShipDataComponent();
        data.mass = 8000f;
        data.maxThrust = 50000f;
        data.maxTurnRate = 90f;
        data.maxSpeed = 150f;
        ship.add(data);

        ShipLoadoutComponent loadout = new ShipLoadoutComponent();
        loadout.maxMass = 30f;
        loadout.moduleSlots.add(new ShipModuleSlot("reactor_0", ModuleSlotType.REACTOR, HardpointSize.SMALL, true));
        loadout.moduleSlots.add(new ShipModuleSlot("engine_0", ModuleSlotType.ENGINE, HardpointSize.SMALL, true));
        loadout.moduleSlots.add(new ShipModuleSlot("internal_0", ModuleSlotType.INTERNAL, HardpointSize.SMALL, false));
        ship.add(loadout);

        engine.addEntity(ship);
    }

    @Test
    void moduleInstalled_recalculatesShipMass() {
        ShipLoadoutComponent loadout = ship.getComponent(ShipLoadoutComponent.class);
        ShipModuleData reactor = new ShipModuleData();
        reactor.id = "reactor_mk1";
        reactor.category = ShipModuleCategory.REACTOR;
        reactor.powerDraw = -60f;
        reactor.mass = 4f;
        loadout.getSlot("reactor_0").installedModule = reactor;

        eventBus.publish(new ModuleInstalledEvent(ship, "reactor_0", reactor, null));

        ShipDataComponent data = ship.getComponent(ShipDataComponent.class);
        assertEquals(8004f, data.mass, 0.01f);
    }

    @Test
    void engineModule_appliesStatMultipliers() {
        ShipLoadoutComponent loadout = ship.getComponent(ShipLoadoutComponent.class);
        ShipDataComponent data = ship.getComponent(ShipDataComponent.class);

        float baseThrust = data.maxThrust;
        float baseTurnRate = data.maxTurnRate;
        float baseMaxSpeed = data.maxSpeed;

        ShipModuleData eng = new ShipModuleData();
        eng.id = "engine_mk1";
        eng.category = ShipModuleCategory.ENGINE;
        eng.powerDraw = 15f;
        eng.mass = 3f;
        eng.stats.put("thrustMultiplier", 1.3f);
        eng.stats.put("turnRateMultiplier", 1.1f);
        eng.stats.put("maxSpeedBonus", 15f);
        loadout.getSlot("engine_0").installedModule = eng;

        eventBus.publish(new ModuleInstalledEvent(ship, "engine_0", eng, null));

        assertEquals(baseThrust * 1.3f, data.maxThrust, 0.1f);
        assertEquals(baseTurnRate * 1.1f, data.maxTurnRate, 0.1f);
        assertEquals(baseMaxSpeed + 15f, data.maxSpeed, 0.1f);
    }
}
