package com.galacticodyssey.ship.modules;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipLoadoutComponentTest {

    private ShipLoadoutComponent loadout;

    @BeforeEach
    void setUp() {
        loadout = new ShipLoadoutComponent();
        loadout.maxMass = 30f;

        ShipModuleSlot reactor = new ShipModuleSlot("reactor_0", ModuleSlotType.REACTOR, HardpointSize.SMALL, true);
        ShipModuleSlot engine = new ShipModuleSlot("engine_0", ModuleSlotType.ENGINE, HardpointSize.SMALL, true);
        ShipModuleSlot internal = new ShipModuleSlot("internal_0", ModuleSlotType.INTERNAL, HardpointSize.SMALL, false);
        loadout.moduleSlots.add(reactor);
        loadout.moduleSlots.add(engine);
        loadout.moduleSlots.add(internal);
    }

    @Test
    void getSlot_returnsMatchingSlot() {
        assertNotNull(loadout.getSlot("reactor_0"));
        assertNull(loadout.getSlot("nonexistent"));
    }

    @Test
    void getSlotsOfType_filtersCorrectly() {
        assertEquals(1, loadout.getSlotsOfType(ModuleSlotType.REACTOR).size());
        assertEquals(1, loadout.getSlotsOfType(ModuleSlotType.INTERNAL).size());
    }

    @Test
    void powerBudget_sumsCorrectly() {
        ShipModuleData reactor = new ShipModuleData();
        reactor.id = "reactor_mk1";
        reactor.category = ShipModuleCategory.REACTOR;
        reactor.powerDraw = -80f;
        reactor.mass = 5f;
        loadout.getSlot("reactor_0").installedModule = reactor;

        ShipModuleData engine = new ShipModuleData();
        engine.id = "engine_mk1";
        engine.category = ShipModuleCategory.ENGINE;
        engine.powerDraw = 20f;
        engine.mass = 4f;
        loadout.getSlot("engine_0").installedModule = engine;

        ShipModuleData shield = new ShipModuleData();
        shield.id = "shield_mk1";
        shield.category = ShipModuleCategory.SHIELD_GENERATOR;
        shield.powerDraw = 15f;
        shield.mass = 3f;
        loadout.getSlot("internal_0").installedModule = shield;

        assertEquals(80f, loadout.getTotalPowerGeneration(), 0.01f);
        assertEquals(35f, loadout.getTotalPowerDraw(), 0.01f);
        assertEquals(45f, loadout.getAvailablePower(), 0.01f);
        assertEquals(12f, loadout.getTotalModuleMass(), 0.01f);
    }

    @Test
    void powerBudget_emptySlots_returnZero() {
        assertEquals(0f, loadout.getTotalPowerGeneration(), 0.01f);
        assertEquals(0f, loadout.getTotalPowerDraw(), 0.01f);
        assertEquals(0f, loadout.getAvailablePower(), 0.01f);
    }
}
