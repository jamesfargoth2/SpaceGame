package com.galacticodyssey.fauna;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.fauna.components.CreatureComponent;
import com.galacticodyssey.fauna.components.CreatureRenderComponent;

/** Builds the logical creature entity from a {@link CreatureSpec}. Model attached by GL layer. */
public final class CreatureFactory {

    public Entity create(Engine engine, CreatureSpec spec, Vector3 spawnPos) {
        Entity e = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(spawnPos);
        e.add(transform);

        CreatureComponent cc = new CreatureComponent();
        cc.spec = spec;
        cc.archetypeId = spec.archetypeId;
        cc.moveSpeed = spec.moveSpeed;
        cc.meleeDamage = spec.meleeDamage;
        e.add(cc);

        HealthComponent health = new HealthComponent();
        health.maxHP = spec.maxHP;
        health.currentHP = spec.maxHP;
        health.alive = true;
        e.add(health);

        e.add(new CreatureRenderComponent());

        engine.addEntity(e);
        return e;
    }
}
