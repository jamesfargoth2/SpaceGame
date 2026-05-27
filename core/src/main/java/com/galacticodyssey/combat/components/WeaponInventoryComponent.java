package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.WeaponSlot;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.WeaponInventorySnapshot;

public class WeaponInventoryComponent implements Component, Snapshotable<WeaponInventorySnapshot> {
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

    @Override
    public WeaponInventorySnapshot takeSnapshot() {
        WeaponInventorySnapshot s = new WeaponInventorySnapshot();
        s.activeSlotIndex = activeSlotIndex;
        s.switching = switching;
        s.switchTimer = switchTimer;
        return s;
    }

    @Override
    public void restoreFromSnapshot(WeaponInventorySnapshot s) {
        activeSlotIndex = s.activeSlotIndex;
        switching = s.switching;
        switchTimer = s.switchTimer;
        // pendingSlotIndex and lowering are transient animation state; reset to defaults.
        pendingSlotIndex = s.activeSlotIndex;
        lowering = false;
    }
}
