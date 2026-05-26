package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.WeaponSlot;
import com.galacticodyssey.combat.data.WeaponAssembly;

public class WeaponInventoryComponent implements Component {
    public final WeaponAssembly[] slots = new WeaponAssembly[3];
    public int activeSlotIndex = 0;
    public int pendingSlotIndex = 0;
    public float switchTimer = 0f;
    public boolean switching = false;
    public boolean lowering = true;

    public WeaponAssembly getActiveAssembly() {
        return slots[activeSlotIndex];
    }

    public WeaponSlot getActiveSlot() {
        return WeaponSlot.values()[activeSlotIndex];
    }

    public boolean isActiveSlotMelee() {
        return activeSlotIndex == WeaponSlot.MELEE.index;
    }
}
