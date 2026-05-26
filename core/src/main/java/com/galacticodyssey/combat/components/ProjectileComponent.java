package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.DamageType;

public class ProjectileComponent implements Component {
    public float speed;
    public float damage;
    public DamageType damageType;
    public Entity owner;
    public float lifetime;
    public float age;
    public float areaOfEffect;
    public String ammoTypeId;
}
