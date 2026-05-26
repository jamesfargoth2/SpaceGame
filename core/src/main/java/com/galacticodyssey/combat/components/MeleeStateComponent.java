package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.MeleeState;

public class MeleeStateComponent implements Component {
    public MeleeState currentState = MeleeState.IDLE;
    public AttackDirection attackDirection = AttackDirection.LEFT;
    public AttackDirection blockDirection = AttackDirection.LEFT;
    public float stateTimer;
    public int comboCounter;
    public boolean canCombo;
    public AttackDirection queuedDirection;
}
