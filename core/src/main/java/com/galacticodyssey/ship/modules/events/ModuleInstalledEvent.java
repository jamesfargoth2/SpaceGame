package com.galacticodyssey.ship.modules.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.modules.ShipModuleData;

public final class ModuleInstalledEvent {
    public final Entity shipEntity;
    public final String slotId;
    public final ShipModuleData module;
    public final ShipModuleData previousModule;

    public ModuleInstalledEvent(Entity shipEntity, String slotId,
                                ShipModuleData module, ShipModuleData previousModule) {
        this.shipEntity = shipEntity;
        this.slotId = slotId;
        this.module = module;
        this.previousModule = previousModule;
    }
}
