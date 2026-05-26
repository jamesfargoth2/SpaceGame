package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory;
import com.galacticodyssey.ship.weapons.components.GuidedProjectileComponent;
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

        TransformComponent tc = new TransformComponent();
        tc.position.set(event.origin);
        projectile.add(tc);

        ProjectileComponent pc = new ProjectileComponent();
        pc.speed = event.weaponData.projectileSpeed;
        pc.damage = event.weaponData.damage;
        pc.damageType = event.weaponData.damageType;
        pc.owner = event.shipEntity;
        pc.lifetime = event.weaponData.range / event.weaponData.projectileSpeed;
        pc.age = 0f;
        pc.areaOfEffect = 0f;
        projectile.add(pc);

        if (event.weaponData.category == ShipWeaponCategory.MISSILE_LAUNCHER) {
            GuidedProjectileComponent gpc = new GuidedProjectileComponent();
            gpc.turnRate = 90f;
            gpc.armingDistance = 20f;
            projectile.add(gpc);
        }

        if (getEngine() != null) {
            getEngine().addEntity(projectile);
        }
    }
}
