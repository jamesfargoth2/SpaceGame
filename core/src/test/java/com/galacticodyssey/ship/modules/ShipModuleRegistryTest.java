package com.galacticodyssey.ship.modules;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipModuleRegistryTest {

    @BeforeAll
    static void initGdx() {
        new HeadlessApplication(new ApplicationAdapter() {}, new HeadlessApplicationConfiguration());
    }

    private ShipModuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ShipModuleRegistry();
        registry.loadModules("data/modules/ship_modules.json");
        registry.loadSlotLayouts("data/modules/ship_module_slots.json");
    }

    @Test
    void loadModules_populatesRegistry() {
        assertNotNull(registry.getModule("reactor_compact_sm"));
        assertNotNull(registry.getModule("engine_ion_sm"));
        assertNotNull(registry.getModule("shield_gen_sm"));
    }

    @Test
    void getModule_returnsNullForUnknown() {
        assertNull(registry.getModule("nonexistent"));
    }

    @Test
    void createModuleInstance_returnsDeepCopy() {
        ShipModuleData original = registry.getModule("reactor_compact_sm");
        ShipModuleData copy = registry.createModuleInstance("reactor_compact_sm");
        assertNotNull(copy);
        assertNotSame(original, copy);
        assertEquals(original.id, copy.id);
        assertEquals(original.powerDraw, copy.powerDraw);
    }

    @Test
    void getModulesByCategory_filters() {
        List<ShipModuleData> reactors = registry.getModulesByCategory(ShipModuleCategory.REACTOR);
        assertTrue(reactors.size() >= 2);
        for (ShipModuleData mod : reactors) {
            assertEquals(ShipModuleCategory.REACTOR, mod.category);
        }
    }

    @Test
    void getModulesForSize_includesSmallerModules() {
        List<ShipModuleData> mediumFit = registry.getModulesForSize(HardpointSize.MEDIUM);
        boolean hasSmall = false;
        boolean hasMedium = false;
        for (ShipModuleData mod : mediumFit) {
            if (mod.size == HardpointSize.SMALL) hasSmall = true;
            if (mod.size == HardpointSize.MEDIUM) hasMedium = true;
        }
        assertTrue(hasSmall);
        assertTrue(hasMedium);
    }

    @Test
    void loadSlotLayouts_parsesCorvetteSlots() {
        ShipModuleRegistry.SlotLayout layout = registry.getSlotLayout("corvette_scout");
        assertNotNull(layout);
        assertEquals(30f, layout.maxMass, 0.01f);
        assertEquals(5, layout.slots.size());
        assertNotNull(layout.silhouettePoints);
        assertTrue(layout.silhouettePoints.length > 0);
    }
}
