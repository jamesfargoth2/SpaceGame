package com.galacticodyssey.ship.modules;

import com.galacticodyssey.ship.modules.components.ShipCargoComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipCargoComponentTest {

    private ShipCargoComponent cargo;

    @BeforeEach
    void setUp() {
        cargo = new ShipCargoComponent();
    }

    @Test
    void addAndRemoveModule() {
        ShipModuleData shield = new ShipModuleData();
        shield.id = "shield_mk1";
        shield.mass = 3f;

        cargo.addModule(shield);
        assertEquals(1, cargo.storedModules.size());
        assertEquals(3f, cargo.getStoredMass(), 0.01f);

        assertTrue(cargo.removeModule(shield));
        assertEquals(0, cargo.storedModules.size());
        assertEquals(0f, cargo.getStoredMass(), 0.01f);
    }

    @Test
    void addAndRemoveWeapon() {
        ShipWeaponData weapon = new ShipWeaponData();
        weapon.id = "autocannon_sm";

        cargo.addWeapon(weapon);
        assertEquals(1, cargo.storedWeapons.size());

        assertTrue(cargo.removeWeapon(weapon));
        assertEquals(0, cargo.storedWeapons.size());
    }

    @Test
    void removeNonexistent_returnsFalse() {
        ShipModuleData mod = new ShipModuleData();
        mod.id = "nothing";
        assertFalse(cargo.removeModule(mod));
    }
}
