package com.galacticodyssey.ship;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.systems.BulletPhysicsSystem;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.ship.ai.PilotArchetypeRegistry;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipFactoryNpcCombatTest {

    @BeforeAll static void initBullet() { Bullet.init(); }

    private EventBus bus;
    private BulletPhysicsSystem physics;
    private Engine engine;
    private ShipFactory factory;
    private PilotArchetypeRegistry archetypes;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        physics = new BulletPhysicsSystem(bus);
        physics.initialize();
        engine = new Engine();
        factory = new ShipFactory(engine, physics);
        archetypes = new PilotArchetypeRegistry();
        archetypes.parse("[{\"id\":\"veteran\",\"usesMissiles\":true}]");
        factory.setPilotArchetypes(archetypes);
    }

    @AfterEach
    void tearDown() { factory.dispose(); physics.dispose(); }

    @Test
    void buildsFlyableAiCombatShip() {
        Entity ship = factory.createNpcCombatShip(12345L, ShipSizeClass.SMALL, "veteran", 0, 0, 0);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class));
        assertNotNull(ship.getComponent(ShipFlightInputComponent.class), "NPC ship owns its input");
        assertNotNull(ship.getComponent(HealthComponent.class));
        assertNotNull(ship.getComponent(ShipHardpointComponent.class));
        ShipPilotAIComponent ai = ship.getComponent(ShipPilotAIComponent.class);
        assertNotNull(ai);
        assertNotNull(ai.behaviorTree, "tree built");
        assertEquals("veteran", ai.archetypeId);
        assertFalse(ship.getComponent(ShipHardpointComponent.class).hardpoints.isEmpty(), "has a weapon");
    }
}
