package com.galacticodyssey.ship.weapons;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.components.GuidedProjectileComponent;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory;
import com.galacticodyssey.ship.weapons.systems.ShipProjectileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipProjectileSystemTest {
    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new ShipProjectileSystem(eventBus));
    }

    @Test
    void shipWeaponFired_spawnsProjectile() {
        Entity ship = new Entity();
        ShipWeaponData weapon = new ShipWeaponData();
        weapon.id = "cannon";
        weapon.damage = 50f;
        weapon.damageType = DamageType.BALLISTIC;
        weapon.projectileSpeed = 200f;
        weapon.category = ShipWeaponCategory.BALLISTIC_CANNON;

        eventBus.publish(new ShipWeaponFiredEvent(ship, "turret_1",
            new Vector3(0, 0, 0), new Vector3(0, 0, 1), weapon));
        engine.update(0.016f);

        boolean foundProjectile = false;
        for (Entity e : engine.getEntities()) {
            if (e.getComponent(ProjectileComponent.class) != null) {
                foundProjectile = true;
                ProjectileComponent pc = e.getComponent(ProjectileComponent.class);
                assertEquals(50f, pc.damage, 0.01f);
                assertEquals(DamageType.BALLISTIC, pc.damageType);
            }
        }
        assertTrue(foundProjectile);
    }

    @Test
    void missileWeaponFired_spawnsGuidedProjectile() {
        Entity ship = new Entity();
        Entity target = new Entity();
        target.add(new TransformComponent());

        ShipWeaponData weapon = new ShipWeaponData();
        weapon.id = "missile";
        weapon.damage = 120f;
        weapon.damageType = DamageType.EXPLOSIVE;
        weapon.projectileSpeed = 80f;
        weapon.category = ShipWeaponCategory.MISSILE_LAUNCHER;

        eventBus.publish(new ShipWeaponFiredEvent(ship, "missile_bay",
            new Vector3(0, 0, 0), new Vector3(0, 0, 1), weapon));
        engine.update(0.016f);

        boolean foundGuided = false;
        for (Entity e : engine.getEntities()) {
            if (e.getComponent(GuidedProjectileComponent.class) != null) {
                foundGuided = true;
            }
        }
        assertTrue(foundGuided);
    }
}
