package com.galacticodyssey.ship.modules;

import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;

public class ShipModuleSlot {
    public final String id;
    public final ModuleSlotType slotType;
    public final HardpointSize size;
    public final Vector2 position = new Vector2();
    public final boolean mandatory;
    public ShipModuleData installedModule;

    public ShipModuleSlot(String id, ModuleSlotType slotType, HardpointSize size, boolean mandatory) {
        this.id = id;
        this.slotType = slotType;
        this.size = size;
        this.mandatory = mandatory;
    }

    public boolean isEmpty() { return installedModule == null; }

    public boolean accepts(ShipModuleData module) {
        if (module == null) return false;
        if (module.size.ordinal() > size.ordinal()) return false;
        switch (slotType) {
            case REACTOR:  return module.category == ShipModuleCategory.REACTOR;
            case ENGINE:   return module.category == ShipModuleCategory.ENGINE;
            case INTERNAL: return module.category != ShipModuleCategory.REACTOR
                                && module.category != ShipModuleCategory.ENGINE;
            default:       return false;
        }
    }
}
