package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.MeleeState;

import java.util.HashSet;
import java.util.Set;

public class MeleeStateComponent implements Component {
    public MeleeState currentState = MeleeState.IDLE;
    public AttackDirection attackDirection = AttackDirection.LEFT;
    public AttackDirection blockDirection = AttackDirection.LEFT;
    public float stateTimer;
    public int comboCounter;
    public boolean canCombo;
    public AttackDirection queuedDirection;
    public final Set<Entity> hitThisSwing = new HashSet<>();
}
