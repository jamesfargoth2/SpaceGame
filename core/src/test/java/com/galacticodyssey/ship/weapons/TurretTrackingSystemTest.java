package com.galacticodyssey.ship.weapons;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.systems.TurretTrackingSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TurretTrackingSystemTest {
    private Engine engine;
    private Entity ship;
    private Entity target;
    private ShipHardpointComponent hardpointComp;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new TurretTrackingSystem());

        ship = new Entity();
        TransformComponent shipTc = new TransformComponent();
        shipTc.position.set(0, 0, 0);
        ship.add(shipTc);

        hardpointComp = new ShipHardpointComponent();
        Hardpoint turret = new Hardpoint("turret_1", HardpointType.TURRET, HardpointSize.MEDIUM, 0, 360);
        turret.position.set(0, 1, 0);
        ShipWeaponData weapon = new ShipWeaponData();
        weapon.trackingSpeed = 180f;
        turret.mountedWeapon = weapon;
        hardpointComp.hardpoints.add(turret);

        target = new Entity();
        TransformComponent targetTc = new TransformComponent();
        targetTc.position.set(100, 0, 0);
        target.add(targetTc);
        hardpointComp.currentTarget = target;

        ship.add(hardpointComp);
        engine.addEntity(ship);
        engine.addEntity(target);
    }

    @Test
    void turret_tracksTarget_setsTrackingState() {
        engine.update(1.0f);
        assertEquals(HardpointState.TRACKING, hardpointComp.hardpoints.get(0).currentState);
    }

    @Test
    void turret_noTarget_remainsIdle() {
        hardpointComp.currentTarget = null;
        engine.update(1.0f);
        assertEquals(HardpointState.IDLE, hardpointComp.hardpoints.get(0).currentState);
    }

    @Test
    void fixedHardpoint_targetOutOfArc_remainsIdle() {
        Hardpoint fixed = new Hardpoint("fixed_1", HardpointType.FIXED, HardpointSize.SMALL, 0, 15);
        ShipWeaponData weapon = new ShipWeaponData();
        fixed.mountedWeapon = weapon;
        hardpointComp.hardpoints.clear();
        hardpointComp.hardpoints.add(fixed);
        engine.update(1.0f);
        assertEquals(HardpointState.IDLE, fixed.currentState);
    }
}
