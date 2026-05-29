package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointType;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory;
import com.galacticodyssey.ship.weapons.components.GuidedProjectileComponent;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MissileTargetBindingTest {

    @Test
    void firedMissileBindsToShipCurrentTarget() {
        EventBus bus = new EventBus();
        Engine engine = new Engine();
        ShipProjectileSystem sys = new ShipProjectileSystem(bus);
        engine.addSystem(sys);

        Entity target = new Entity();
        TransformComponent tt = new TransformComponent();
        tt.position.set(0, 0, -500);
        target.add(tt);
        engine.addEntity(target);

        Entity shooter = new Entity();
        TransformComponent st = new TransformComponent();
        shooter.add(st);
        ShipHardpointComponent hpc = new ShipHardpointComponent();
        hpc.currentTarget = target;
        Hardpoint hp = new Hardpoint("m0", HardpointType.MISSILE_BAY, HardpointSize.MEDIUM, 0, 120);
        ShipWeaponData missile = new ShipWeaponData();
        missile.id = "m";
        missile.category = ShipWeaponCategory.MISSILE_LAUNCHER;
        missile.damage = 100f;
        missile.damageType = DamageType.EXPLOSIVE;
        missile.fireRate = 1f;
        missile.projectileSpeed = 80f;
        missile.range = 2000f;
        hp.mountedWeapon = missile;
        hpc.hardpoints.add(hp);
        shooter.add(hpc);
        engine.addEntity(shooter);

        bus.publish(new ShipWeaponFiredEvent(shooter, "m0",
            new Vector3(st.position), new Vector3(0, 0, -1), missile));

        // Spawn is deferred to update() via the pendingFires queue
        engine.update(1f / 60f);

        GuidedProjectileComponent found = null;
        for (Entity e : engine.getEntities()) {
            GuidedProjectileComponent g = e.getComponent(GuidedProjectileComponent.class);
            if (g != null) {
                found = g;
                break;
            }
        }
        assertNotNull(found, "a guided projectile should have spawned");
        assertSame(target, found.targetEntity, "missile should be bound to the ship's current target");
    }
}
