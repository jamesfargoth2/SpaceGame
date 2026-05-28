package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.boarding.systems.ShipProjectileImpactSystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipProjectileImpactSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity ship;
    private final List<ShipDamageEvent> hits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new ShipProjectileImpactSystem(eventBus));

        ship = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(0, 0, 0);
        ship.add(tc);
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 500f; data.currentHullHp = 500f;
        ship.add(data);
        engine.addEntity(ship);

        eventBus.subscribe(ShipDamageEvent.class, hits::add);
    }

    private Entity projectileAt(float x, float y, float z, Entity owner) {
        Entity p = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(x, y, z);
        p.add(tc);
        ProjectileComponent pc = new ProjectileComponent();
        pc.damage = 25f;
        pc.damageType = DamageType.BALLISTIC;
        pc.owner = owner;
        p.add(pc);
        engine.addEntity(p);
        return p;
    }

    @Test
    void projectileTouchingShipPublishesDamageAndIsRemoved() {
        Entity p = projectileAt(0, 0, 1f, null); // within default impact radius
        int before = engine.getEntities().size();
        engine.update(0.016f);
        assertEquals(1, hits.size());
        assertEquals(25f, hits.get(0).damage, 0.01f);
        assertEquals(DamageType.BALLISTIC, hits.get(0).damageType);
        assertEquals(before - 1, engine.getEntities().size(), "projectile consumed on impact");
    }

    @Test
    void distantProjectileDoesNotHit() {
        projectileAt(0, 0, 500f, null);
        engine.update(0.016f);
        assertTrue(hits.isEmpty());
    }

    @Test
    void projectileDoesNotHitItsOwner() {
        projectileAt(0, 0, 1f, ship); // owner == ship
        engine.update(0.016f);
        assertTrue(hits.isEmpty());
    }
}
