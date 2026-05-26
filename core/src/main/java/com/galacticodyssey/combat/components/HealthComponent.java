package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;

public class HealthComponent implements Component {
    public float currentHP = 100f;
    public float maxHP = 100f;
    public boolean alive = true;
}
