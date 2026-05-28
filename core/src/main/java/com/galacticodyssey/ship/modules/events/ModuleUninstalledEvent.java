package com.galacticodyssey.ship.modules.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.modules.ShipModuleData;

public final class ModuleUninstalledEvent {
    public final Entity shipEntity;
    public final String slotId;
    public final ShipModuleData module;

    public ModuleUninstalledEvent(Entity shipEntity, String slotId, ShipModuleData module) {
        this.shipEntity = shipEntity;
        this.slotId = slotId;
        this.module = module;
    }
}
