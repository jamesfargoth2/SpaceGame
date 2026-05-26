package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;

public class CombatInputComponent implements Component {
    public boolean fireRequested;
    public boolean fireHeld;
    public final Vector3 aimDirection = new Vector3(0, 0, -1);
    public AttackDirection meleeAttackDirection;
    public boolean meleeAttackRequested;
    public boolean blockRequested;
    public boolean blockHeld;
    public AttackDirection blockDirection;
    public boolean reloadRequested;
    public int switchSlotRequested = -1;
    public boolean quickMeleeRequested;
}
