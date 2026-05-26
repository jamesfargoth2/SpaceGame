package com.galacticodyssey.ship;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.BulletPhysicsSystem;
import com.galacticodyssey.ship.components.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipFactoryTest {

    private EventBus eventBus;
    private BulletPhysicsSystem physics;
    private Engine engine;
    private ShipFactory factory;

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        physics = new BulletPhysicsSystem(eventBus);
        physics.initialize();
        engine = new Engine();
        factory = new ShipFactory(engine, physics);
    }

    @AfterEach
    void tearDown() {
        factory.dispose();
        physics.dispose();
    }

    @Test
    void createsEntityWithAllComponents() {
        Entity ship = factory.createShip(42L, ShipSizeClass.SMALL, 0, 5, 0);
        assertNotNull(ship.getComponent(TransformComponent.class));
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class));
        assertNotNull(ship.getComponent(ShipDataComponent.class));
        assertNotNull(ship.getComponent(ShipMeshComponent.class));
        assertNotNull(ship.getComponent(ShipInteriorComponent.class));
        assertNotNull(ship.getComponent(ShipFlightComponent.class));
        assertNotNull(ship.getComponent(PilotSeatComponent.class));
        assertNotNull(ship.getComponent(ShipEntryPointComponent.class));
    }

    @Test
    void shipHasInteriorPhysicsWorld() {
        Entity ship = factory.createShip(42L, ShipSizeClass.SMALL, 0, 5, 0);
        ShipInteriorComponent interior = ship.getComponent(ShipInteriorComponent.class);
        assertNotNull(interior.interiorWorld);
    }

    @Test
    void shipIsAddedToEngine() {
        Entity ship = factory.createShip(42L, ShipSizeClass.SMALL, 0, 5, 0);
        assertTrue(engine.getEntities().size() > 0);
    }

    @Test
    void differentSizesHaveDifferentMass() {
        Entity small = factory.createShip(42L, ShipSizeClass.SMALL, 0, 5, 0);
        Entity large = factory.createShip(43L, ShipSizeClass.LARGE, 50, 5, 50);
        float smallMass = small.getComponent(ShipDataComponent.class).mass;
        float largeMass = large.getComponent(ShipDataComponent.class).mass;
        assertTrue(largeMass > smallMass);
    }
}
