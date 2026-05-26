package com.galacticodyssey.ship.weapons;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.events.PointDefenseEngagedEvent;
import com.galacticodyssey.ship.weapons.systems.PointDefenseSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PointDefenseSystemTest {
    private Engine engine;
    private EventBus eventBus;
    private Entity ship;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new PointDefenseSystem(eventBus));

        ship = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(0, 0, 0);
        ship.add(tc);

        ShipHardpointComponent hpc = new ShipHardpointComponent();
        Hardpoint pd = new Hardpoint("pd_1", HardpointType.POINT_DEFENSE, HardpointSize.SMALL, 0, 360);
        ShipWeaponData weapon = new ShipWeaponData();
        weapon.id = "pd";
        weapon.damage = 8f;
        weapon.damageType = DamageType.BALLISTIC;
        weapon.fireRate = 20f;
        weapon.range = 400f;
        pd.mountedWeapon = weapon;
        hpc.hardpoints.add(pd);
        ship.add(hpc);

        engine.addEntity(ship);
    }

    @Test
    void incomingProjectile_inRange_engages() {
        AtomicReference<PointDefenseEngagedEvent> received = new AtomicReference<>();
        eventBus.subscribe(PointDefenseEngagedEvent.class, received::set);

        Entity missile = new Entity();
        TransformComponent mtc = new TransformComponent();
        mtc.position.set(100, 0, 0);
        missile.add(mtc);
        ProjectileComponent pc = new ProjectileComponent();
        pc.owner = new Entity();
        pc.speed = 80f;
        pc.damage = 120f;
        pc.damageType = DamageType.EXPLOSIVE;
        pc.lifetime = 10f;
        missile.add(pc);
        engine.addEntity(missile);

        engine.update(0.1f);
        assertNotNull(received.get());
        assertSame(ship, received.get().shipEntity);
    }

    @Test
    void projectile_outOfRange_ignored() {
        AtomicReference<PointDefenseEngagedEvent> received = new AtomicReference<>();
        eventBus.subscribe(PointDefenseEngagedEvent.class, received::set);

        Entity missile = new Entity();
        TransformComponent mtc = new TransformComponent();
        mtc.position.set(1000, 0, 0);
        missile.add(mtc);
        ProjectileComponent pc = new ProjectileComponent();
        pc.owner = new Entity();
        pc.speed = 80f;
        missile.add(pc);
        engine.addEntity(missile);

        engine.update(0.1f);
        assertNull(received.get());
    }
}
