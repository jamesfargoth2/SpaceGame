package com.galacticodyssey.ship.weapons;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.components.ShipWeaponHeatComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;
import com.galacticodyssey.ship.weapons.systems.ShipWeaponSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ShipWeaponSystemTest {
    private Engine engine;
    private EventBus eventBus;
    private Entity ship;
    private Hardpoint turret;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new ShipWeaponSystem(eventBus));

        ship = new Entity();
        ship.add(new TransformComponent());

        ShipHardpointComponent hpc = new ShipHardpointComponent();
        turret = new Hardpoint("turret_1", HardpointType.TURRET, HardpointSize.MEDIUM, 0, 360);
        ShipWeaponData weapon = new ShipWeaponData();
        weapon.id = "test_cannon";
        weapon.damage = 50f;
        weapon.damageType = DamageType.BALLISTIC;
        weapon.fireRate = 2f;
        weapon.projectileSpeed = 200f;
        weapon.range = 500f;
        weapon.energyCost = 0f;
        weapon.heatPerShot = 0.1f;
        weapon.ammoCapacity = 100;
        weapon.currentAmmo = 100;
        turret.mountedWeapon = weapon;
        turret.currentState = HardpointState.TRACKING;
        hpc.hardpoints.add(turret);
        ship.add(hpc);
        ship.add(new ShipWeaponHeatComponent());
        engine.addEntity(ship);
    }

    @Test
    void fireHardpoint_publishesEvent() {
        AtomicReference<ShipWeaponFiredEvent> received = new AtomicReference<>();
        eventBus.subscribe(ShipWeaponFiredEvent.class, received::set);
        engine.getSystem(ShipWeaponSystem.class).fireHardpoint(ship, "turret_1");
        assertNotNull(received.get());
        assertEquals("turret_1", received.get().hardpointId);
    }

    @Test
    void fireHardpoint_consumesAmmo() {
        engine.getSystem(ShipWeaponSystem.class).fireHardpoint(ship, "turret_1");
        assertEquals(99, turret.mountedWeapon.currentAmmo);
    }

    @Test
    void fireHardpoint_addsHeat() {
        engine.getSystem(ShipWeaponSystem.class).fireHardpoint(ship, "turret_1");
        ShipWeaponHeatComponent heat = ship.getComponent(ShipWeaponHeatComponent.class);
        assertEquals(0.1f, heat.getHeat("turret_1"), 0.01f);
    }

    @Test
    void fireHardpoint_overheated_blocked() {
        ship.getComponent(ShipWeaponHeatComponent.class).overheatedHardpoints.add("turret_1");
        AtomicReference<ShipWeaponFiredEvent> received = new AtomicReference<>();
        eventBus.subscribe(ShipWeaponFiredEvent.class, received::set);
        engine.getSystem(ShipWeaponSystem.class).fireHardpoint(ship, "turret_1");
        assertNull(received.get());
    }
}
