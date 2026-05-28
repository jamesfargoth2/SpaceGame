package com.galacticodyssey.ship.modules;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;

public final class OutfitterValidator {

    private OutfitterValidator() {}

    public static class Result {
        public final boolean allowed;
        public final String reason;

        private Result(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public static Result ok() { return new Result(true, ""); }
        public static Result deny(String reason) { return new Result(false, reason); }
    }

    public static Result canInstallModule(Entity ship, String slotId, ShipModuleData module) {
        ShipLoadoutComponent loadout = ship.getComponent(ShipLoadoutComponent.class);
        if (loadout == null) return Result.deny("Ship has no loadout");

        ShipModuleSlot slot = loadout.getSlot(slotId);
        if (slot == null) return Result.deny("Slot not found: " + slotId);

        if (!slot.accepts(module)) {
            if (module.size.ordinal() > slot.size.ordinal()) {
                return Result.deny("Module too large for this slot (size mismatch)");
            }
            return Result.deny("Module category not compatible with this slot");
        }

        float currentDraw = loadout.getTotalPowerDraw();
        float currentGen = loadout.getTotalPowerGeneration();

        float newDraw = currentDraw;
        float newGen = currentGen;

        if (slot.installedModule != null) {
            if (slot.installedModule.powerDraw > 0) newDraw -= slot.installedModule.powerDraw;
            if (slot.installedModule.powerDraw < 0) newGen -= Math.abs(slot.installedModule.powerDraw);
        }

        if (module.powerDraw > 0) newDraw += module.powerDraw;
        if (module.powerDraw < 0) newGen += Math.abs(module.powerDraw);

        if (newDraw > newGen) {
            return Result.deny("Insufficient power budget (" + newDraw + " MW draw > " + newGen + " MW generation)");
        }

        float currentMass = loadout.getTotalModuleMass();
        float newMass = currentMass;
        if (slot.installedModule != null) newMass -= slot.installedModule.mass;
        newMass += module.mass;

        if (newMass > loadout.maxMass) {
            return Result.deny("Exceeds mass budget (" + newMass + "t > " + loadout.maxMass + "t)");
        }

        return Result.ok();
    }

    public static Result canUninstallModule(Entity ship, String slotId) {
        ShipLoadoutComponent loadout = ship.getComponent(ShipLoadoutComponent.class);
        if (loadout == null) return Result.deny("Ship has no loadout");

        ShipModuleSlot slot = loadout.getSlot(slotId);
        if (slot == null) return Result.deny("Slot not found: " + slotId);

        if (slot.isEmpty()) return Result.deny("Slot is already empty");

        if (slot.mandatory) {
            return Result.deny("Cannot uninstall from mandatory slot without replacement");
        }

        return Result.ok();
    }
}
