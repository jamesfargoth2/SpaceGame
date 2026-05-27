package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory;
import com.galacticodyssey.ship.weapons.components.GuidedProjectileComponent;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;

import java.util.ArrayList;
import java.util.List;

public class ShipProjectileSystem extends EntitySystem {
    private static final int PRIORITY = 7;
    private final EventBus eventBus;
    private final List<ShipWeaponFiredEvent> pendingFires = new ArrayList<>();

    public ShipProjectileSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(ShipWeaponFiredEvent.class, pendingFires::add);
    }

    @Override
    public void update(float deltaTime) {
        for (ShipWeaponFiredEvent event : pendingFires) {
            spawnProjectile(event);
        }
        pendingFires.clear();
    }

    private void spawnProjectile(ShipWeaponFiredEvent event) {
        Entity projectile = new Entity();
        ShipWeaponData weapon = event.weaponData;

        TransformComponent tc = new TransformComponent();
        tc.position.set(event.origin);
        projectile.add(tc);

        Vector3 dir = Pools.obtain(Vector3.class).set(event.direction).nor();

        ProjectileComponent pc = new ProjectileComponent();
        pc.velocity.set(dir).scl(weapon.projectileSpeed);
        pc.speed = weapon.projectileSpeed;
        pc.damage = weapon.damage;
        pc.damageType = weapon.damageType;
        pc.owner = event.shipEntity;
        pc.lifetime = weapon.range / weapon.projectileSpeed;
        pc.age = 0f;
        pc.maxRange = weapon.range;

        configureBallisticsForCategory(pc, weapon);
        projectile.add(pc);

        Pools.free(dir);

        if (weapon.category == ShipWeaponCategory.MISSILE_LAUNCHER) {
            GuidedProjectileComponent gpc = new GuidedProjectileComponent();
            gpc.turnRate = 90f;
            gpc.armingDistance = 20f;
            pc.proximityFuseRadius = 15f;
            pc.areaOfEffect = 30f;
            projectile.add(gpc);
        }

        if (getEngine() != null) {
            getEngine().addEntity(projectile);
        }
    }

    private void configureBallisticsForCategory(ProjectileComponent pc, ShipWeaponData weapon) {
        switch (weapon.category) {
            case BALLISTIC_CANNON:
            case FLAK_CANNON:
                pc.mass = 5f;
                pc.affectedByGravity = true;
                pc.dragCoeff = 0.01f;
                pc.crossSection = 0.005f;
                break;
            case RAILGUN:
                pc.mass = 2f;
                pc.affectedByGravity = false;
                pc.dragCoeff = 0f;
                pc.crossSection = 0.001f;
                break;
            case PLASMA_TURRET:
            case EMP_PROJECTOR:
                pc.mass = 0.1f;
                pc.affectedByGravity = false;
                pc.dragCoeff = 0f;
                pc.crossSection = 0f;
                break;
            case MISSILE_LAUNCHER:
                pc.mass = 50f;
                pc.affectedByGravity = true;
                pc.dragCoeff = 0.3f;
                pc.crossSection = 0.05f;
                break;
            case POINT_DEFENSE:
                pc.mass = 0.5f;
                pc.affectedByGravity = false;
                pc.dragCoeff = 0f;
                pc.crossSection = 0f;
                break;
            default:
                pc.mass = 1f;
                pc.affectedByGravity = false;
                break;
        }
    }
}
