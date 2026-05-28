package com.galacticodyssey.ship.modules.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.ship.modules.ModuleSlotType;
import com.galacticodyssey.ship.modules.ShipModuleData;
import com.galacticodyssey.ship.modules.ShipModuleSlot;

import java.util.ArrayList;
import java.util.List;

public class ShipLoadoutComponent implements Component {
    public final List<ShipModuleSlot> moduleSlots = new ArrayList<>();
    public float maxMass = 30f;

    public ShipModuleSlot getSlot(String id) {
        for (ShipModuleSlot slot : moduleSlots) {
            if (slot.id.equals(id)) return slot;
        }
        return null;
    }

    public List<ShipModuleSlot> getSlotsOfType(ModuleSlotType type) {
        List<ShipModuleSlot> result = new ArrayList<>();
        for (ShipModuleSlot slot : moduleSlots) {
            if (slot.slotType == type) result.add(slot);
        }
        return result;
    }

    public float getTotalPowerGeneration() {
        float gen = 0f;
        for (ShipModuleSlot slot : moduleSlots) {
            if (slot.installedModule != null && slot.installedModule.powerDraw < 0) {
                gen += Math.abs(slot.installedModule.powerDraw);
            }
        }
        return gen;
    }

    public float getTotalPowerDraw() {
        float draw = 0f;
        for (ShipModuleSlot slot : moduleSlots) {
            if (slot.installedModule != null && slot.installedModule.powerDraw > 0) {
                draw += slot.installedModule.powerDraw;
            }
        }
        return draw;
    }

    public float getAvailablePower() {
        return getTotalPowerGeneration() - getTotalPowerDraw();
    }

    public float getTotalModuleMass() {
        float mass = 0f;
        for (ShipModuleSlot slot : moduleSlots) {
            if (slot.installedModule != null) {
                mass += slot.installedModule.mass;
            }
        }
        return mass;
    }
}
