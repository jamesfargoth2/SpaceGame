package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.combat.CombatEnums.DamageType;

public class ProjectileComponent implements Component, Pool.Poolable {
    public final Vector3 velocity = new Vector3();
    public float speed;
    public float damage;
    public DamageType damageType;
    public Entity owner;
    public float lifetime;
    public float age;
    public float areaOfEffect;
    public String ammoTypeId;

    public float mass = 1f;
    public float dragCoeff;
    public float crossSection;
    public float maxRange = Float.MAX_VALUE;
    public float distanceTravelled;
    public boolean affectedByGravity;
    public float proximityFuseRadius;

    @Override
    public void reset() {
        velocity.setZero();
        speed = 0f;
        damage = 0f;
        damageType = null;
        owner = null;
        lifetime = 0f;
        age = 0f;
        areaOfEffect = 0f;
        ammoTypeId = null;
        mass = 1f;
        dragCoeff = 0f;
        crossSection = 0f;
        maxRange = Float.MAX_VALUE;
        distanceTravelled = 0f;
        affectedByGravity = false;
        proximityFuseRadius = 0f;
    }
}
