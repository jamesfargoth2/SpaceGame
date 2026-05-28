package com.galacticodyssey.ship.modules;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.modules.components.ShipCargoComponent;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutfitterValidationTest {

    private Entity ship;
    private ShipLoadoutComponent loadout;
    private ShipCargoComponent cargo;

    @BeforeEach
    void setUp() {
        ship = new Entity();
        loadout = new ShipLoadoutComponent();
        loadout.maxMass = 30f;
        loadout.moduleSlots.add(new ShipModuleSlot("reactor_0", ModuleSlotType.REACTOR, HardpointSize.SMALL, true));
        loadout.moduleSlots.add(new ShipModuleSlot("engine_0", ModuleSlotType.ENGINE, HardpointSize.SMALL, true));
        loadout.moduleSlots.add(new ShipModuleSlot("internal_0", ModuleSlotType.INTERNAL, HardpointSize.SMALL, false));
        ship.add(loadout);

        cargo = new ShipCargoComponent();
        ship.add(cargo);

        ShipModuleData reactor = new ShipModuleData();
        reactor.id = "reactor_sm";
        reactor.category = ShipModuleCategory.REACTOR;
        reactor.powerDraw = -60f;
        reactor.mass = 4f;
        reactor.size = HardpointSize.SMALL;
        loadout.getSlot("reactor_0").installedModule = reactor;

        ShipModuleData engine = new ShipModuleData();
        engine.id = "engine_sm";
        engine.category = ShipModuleCategory.ENGINE;
        engine.powerDraw = 15f;
        engine.mass = 3f;
        engine.size = HardpointSize.SMALL;
        loadout.getSlot("engine_0").installedModule = engine;
    }

    @Test
    void canInstall_validModule_returnsOk() {
        ShipModuleData shield = new ShipModuleData();
        shield.id = "shield_sm";
        shield.category = ShipModuleCategory.SHIELD_GENERATOR;
        shield.size = HardpointSize.SMALL;
        shield.powerDraw = 10f;
        shield.mass = 2f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "internal_0", shield);
        assertTrue(result.allowed);
    }

    @Test
    void canInstall_wrongCategory_rejected() {
        ShipModuleData reactor2 = new ShipModuleData();
        reactor2.id = "reactor2";
        reactor2.category = ShipModuleCategory.REACTOR;
        reactor2.size = HardpointSize.SMALL;
        reactor2.powerDraw = -80f;
        reactor2.mass = 5f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "internal_0", reactor2);
        assertFalse(result.allowed);
    }

    @Test
    void canInstall_overPowerBudget_rejected() {
        ShipModuleData hog = new ShipModuleData();
        hog.id = "power_hog";
        hog.category = ShipModuleCategory.MINING_LASER;
        hog.size = HardpointSize.SMALL;
        hog.powerDraw = 50f;
        hog.mass = 1f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "internal_0", hog);
        assertFalse(result.allowed);
        assertTrue(result.reason.contains("power"));
    }

    @Test
    void canInstall_overMassBudget_rejected() {
        ShipModuleData heavy = new ShipModuleData();
        heavy.id = "heavy_mod";
        heavy.category = ShipModuleCategory.ARMOR_PLATING;
        heavy.size = HardpointSize.SMALL;
        heavy.powerDraw = 0f;
        heavy.mass = 25f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "internal_0", heavy);
        assertFalse(result.allowed);
        assertTrue(result.reason.contains("mass"));
    }

    @Test
    void canInstall_tooLargeForSlot_rejected() {
        ShipModuleData big = new ShipModuleData();
        big.id = "big_shield";
        big.category = ShipModuleCategory.SHIELD_GENERATOR;
        big.size = HardpointSize.LARGE;
        big.powerDraw = 10f;
        big.mass = 2f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "internal_0", big);
        assertFalse(result.allowed);
        assertTrue(result.reason.contains("size"));
    }

    @Test
    void canUninstall_mandatorySlot_rejected() {
        OutfitterValidator.Result result = OutfitterValidator.canUninstallModule(ship, "reactor_0");
        assertFalse(result.allowed);
        assertTrue(result.reason.contains("mandatory"));
    }

    @Test
    void canUninstall_optionalSlot_allowed() {
        ShipModuleData shield = new ShipModuleData();
        shield.id = "shield_sm";
        shield.category = ShipModuleCategory.SHIELD_GENERATOR;
        shield.size = HardpointSize.SMALL;
        shield.powerDraw = 10f;
        shield.mass = 2f;
        loadout.getSlot("internal_0").installedModule = shield;

        OutfitterValidator.Result result = OutfitterValidator.canUninstallModule(ship, "internal_0");
        assertTrue(result.allowed);
    }

    @Test
    void canInstall_reactorDowngrade_wouldExceedPower_rejected() {
        ShipModuleData weakReactor = new ShipModuleData();
        weakReactor.id = "weak_reactor";
        weakReactor.category = ShipModuleCategory.REACTOR;
        weakReactor.size = HardpointSize.SMALL;
        weakReactor.powerDraw = -10f;
        weakReactor.mass = 2f;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(ship, "reactor_0", weakReactor);
        assertFalse(result.allowed);
        assertTrue(result.reason.contains("power"));
    }
}
