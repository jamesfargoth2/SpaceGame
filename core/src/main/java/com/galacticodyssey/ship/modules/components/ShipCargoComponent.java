package com.galacticodyssey.ship.modules.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.ship.modules.ShipModuleData;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;

import java.util.ArrayList;
import java.util.List;

public class ShipCargoComponent implements Component {
    public final List<ShipWeaponData> storedWeapons = new ArrayList<>();
    public final List<ShipModuleData> storedModules = new ArrayList<>();

    public void addWeapon(ShipWeaponData weapon) { storedWeapons.add(weapon); }
    public void addModule(ShipModuleData module) { storedModules.add(module); }

    public boolean removeWeapon(ShipWeaponData weapon) { return storedWeapons.remove(weapon); }
    public boolean removeModule(ShipModuleData module) { return storedModules.remove(module); }

    public float getStoredMass() {
        float mass = 0f;
        for (ShipModuleData mod : storedModules) mass += mod.mass;
        return mass;
    }
}
