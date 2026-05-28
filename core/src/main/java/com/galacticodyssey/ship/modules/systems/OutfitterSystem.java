package com.galacticodyssey.ship.modules.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.modules.ShipModuleCategory;
import com.galacticodyssey.ship.modules.ShipModuleData;
import com.galacticodyssey.ship.modules.ShipModuleSlot;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.modules.events.ModuleInstalledEvent;
import com.galacticodyssey.ship.modules.events.ModuleUninstalledEvent;

/**
 * Recalculates ship stats (mass, thrust, turn rate, max speed) whenever a
 * module is installed or uninstalled. Subscribes to {@link ModuleInstalledEvent}
 * and {@link ModuleUninstalledEvent} via the event bus.
 *
 * <p>Base hull stats are latched the first time recalculate() runs, so
 * multipliers are always applied relative to the unmodified hull values.
 */
public class OutfitterSystem extends EntitySystem {

    private static final int PRIORITY = 3;
    private final EventBus eventBus;

    public OutfitterSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(ModuleInstalledEvent.class, e -> recalculate(e.shipEntity));
        eventBus.subscribe(ModuleUninstalledEvent.class, e -> recalculate(e.shipEntity));
    }

    /** No per-frame work; all logic is event-driven. */
    @Override
    public void update(float deltaTime) {}

    /**
     * Recalculates {@link ShipDataComponent} derived stats for the given entity.
     * Safe to call manually (e.g. after batch-loading a saved loadout).
     */
    public void recalculate(Entity shipEntity) {
        ShipDataComponent data = shipEntity.getComponent(ShipDataComponent.class);
        ShipLoadoutComponent loadout = shipEntity.getComponent(ShipLoadoutComponent.class);
        if (data == null || loadout == null) return;

        // Latch base hull mass on first call
        if (data.hullBaseMass <= 0f) data.hullBaseMass = data.mass;
        float baseMass = data.hullBaseMass;

        // Module mass — weapon mass not tracked (ShipWeaponData has no mass field)
        float totalModuleMass = loadout.getTotalModuleMass();
        data.mass = baseMass + totalModuleMass;

        // Latch base flight stats on first call
        if (data.baseMaxThrust <= 0f) data.baseMaxThrust = data.maxThrust;
        if (data.baseMaxTurnRate <= 0f) data.baseMaxTurnRate = data.maxTurnRate;
        if (data.baseMaxSpeed <= 0f) data.baseMaxSpeed = data.maxSpeed;

        float baseThrust   = data.baseMaxThrust;
        float baseTurnRate = data.baseMaxTurnRate;
        float baseMaxSpeed = data.baseMaxSpeed;

        // Accumulate engine-module multipliers/bonuses
        float thrustMul  = 1f;
        float turnMul    = 1f;
        float speedBonus = 0f;

        for (ShipModuleSlot slot : loadout.moduleSlots) {
            ShipModuleData mod = slot.installedModule;
            if (mod == null || mod.category != ShipModuleCategory.ENGINE) continue;
            thrustMul  *= mod.stats.getOrDefault("thrustMultiplier",  1f);
            turnMul    *= mod.stats.getOrDefault("turnRateMultiplier", 1f);
            speedBonus += mod.stats.getOrDefault("maxSpeedBonus",      0f);
        }

        data.maxThrust   = baseThrust   * thrustMul;
        data.maxTurnRate = baseTurnRate * turnMul;
        data.maxSpeed    = baseMaxSpeed + speedBonus;
    }
}
