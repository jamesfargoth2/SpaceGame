package com.galacticodyssey.equipment.systems;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.equipment.items.ComponentItem;
import com.galacticodyssey.equipment.items.ModItem;
import com.galacticodyssey.equipment.items.WeaponItem;

import java.util.List;

public class WeaponAssemblySystem {

    public WeaponItem assemble(ComponentItem frame, ComponentItem barrel,
                               ComponentItem ammoType, List<ModItem> mods,
                               QualityTier quality) {
        if (frame == null || !"frame".equals(frame.componentType)) return null;
        if (barrel == null || !"barrel".equals(barrel.componentType)) return null;
        if (ammoType == null || !"ammo_type".equals(ammoType.componentType)) return null;

        String[] modIds = mods.stream()
            .map(m -> m.weaponModId)
            .toArray(String[]::new);

        WeaponAssembly assembly = WeaponAssembly.ranged(
            frame.componentId, barrel.componentId, ammoType.componentId, modIds, quality);

        float totalWeight = frame.weight + barrel.weight + ammoType.weight
            + (float) mods.stream().mapToDouble(m -> m.weight).sum();
        int gridW = Math.max(frame.gridWidth, barrel.gridWidth);
        int gridH = frame.gridHeight;

        String name = frame.name + " (" + barrel.name + ")";

        return new WeaponItem(
            "assembled_" + frame.componentId + "_" + barrel.componentId,
            name, "Assembled weapon", frame.icon, quality,
            gridW, gridH, totalWeight, assembly
        );
    }
}
