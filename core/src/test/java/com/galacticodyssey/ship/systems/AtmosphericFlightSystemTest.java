package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.AtmosphereZoneComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipAerodynamicsComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipThermalComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphericFlightSystemTest {

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    @Test
    void dragReducesVelocityInAtmosphere() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        AtmosphericFlightSystem system = new AtmosphericFlightSystem(eventBus);
        engine.addSystem(system);

        Entity planet = createPlanet(1000f, 1200f);
        engine.addEntity(planet);

        btDiscreteDynamicsWorld world = createBulletWorld();
        Entity ship = createShipInAtmosphere(world, planet, 1050f);
        engine.addEntity(ship);

        Entity player = createPilotingPlayer(ship);
        engine.addEntity(player);

        PhysicsBodyComponent physics = ship.getComponent(PhysicsBodyComponent.class);
        physics.body.setLinearVelocity(new Vector3(0, 0, -100f));
        float initialSpeed = physics.body.getLinearVelocity().len();

        system.update(1f / 60f);
        world.stepSimulation(1f / 60f, 1, 1f / 60f);

        float newSpeed = physics.body.getLinearVelocity().len();
        assertTrue(newSpeed < initialSpeed, "Drag should reduce speed in atmosphere");

        cleanup(physics, world);
    }

    @Test
    void noForcesAboveAtmosphere() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        AtmosphericFlightSystem system = new AtmosphericFlightSystem(eventBus);
        engine.addSystem(system);

        Entity planet = createPlanet(1000f, 1200f);
        engine.addEntity(planet);

        btDiscreteDynamicsWorld world = createBulletWorld();
        Entity ship = createShipInAtmosphere(world, planet, 1500f); // above atmosphere
        engine.addEntity(ship);

        Entity player = createPilotingPlayer(ship);
        engine.addEntity(player);

        PhysicsBodyComponent physics = ship.getComponent(PhysicsBodyComponent.class);
        physics.body.setLinearVelocity(new Vector3(0, 0, -100f));
        float initialSpeed = physics.body.getLinearVelocity().len();

        system.update(1f / 60f);

        float afterSpeed = physics.body.getLinearVelocity().len();
        assertEquals(initialSpeed, afterSpeed, 0.01f, "No aero forces above atmosphere");

        cleanup(physics, world);
    }

    @Test
    void heatingAccumulatesAboveMachThreshold() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        AtmosphericFlightSystem system = new AtmosphericFlightSystem(eventBus);
        engine.addSystem(system);

        Entity planet = createPlanet(1000f, 1200f);
        AtmosphereZoneComponent atmo = planet.getComponent(AtmosphereZoneComponent.class);
        atmo.speedOfSound = 343f;
        atmo.machThreshold = 3f;
        engine.addEntity(planet);

        btDiscreteDynamicsWorld world = createBulletWorld();
        Entity ship = createShipInAtmosphere(world, planet, 1050f);
        ShipThermalComponent thermal = new ShipThermalComponent();
        ship.add(thermal);
        engine.addEntity(ship);

        Entity player = createPilotingPlayer(ship);
        engine.addEntity(player);

        PhysicsBodyComponent physics = ship.getComponent(PhysicsBodyComponent.class);
        physics.body.setLinearVelocity(new Vector3(0, 0, -1500f)); // well above Mach 3

        system.update(1f);

        assertTrue(thermal.currentHeat > 0, "Heat should accumulate above Mach threshold");

        cleanup(physics, world);
    }

    private Entity createPlanet(float surfaceRadius, float atmosphereRadius) {
        Entity planet = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(0, 0, 0);
        planet.add(t);
        AtmosphereZoneComponent atmo = new AtmosphereZoneComponent();
        atmo.surfaceRadius = surfaceRadius;
        atmo.atmosphereRadius = atmosphereRadius;
        atmo.surfaceDensity = 1.225f;
        atmo.scaleHeight = 200f;
        atmo.transitionAltitude = 150f;
        atmo.speedOfSound = 343f;
        atmo.machThreshold = 3f;
        planet.add(atmo);
        return planet;
    }

    private Entity createShipInAtmosphere(btDiscreteDynamicsWorld world, Entity planet, float altitude) {
        Entity ship = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(0, altitude, 0);
        ship.add(t);

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(1, 1, 1));
        float mass = 10000f;
        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(0, altitude, 0));
        physics.mass = mass;
        info.dispose();
        ship.add(physics);
        world.addRigidBody(physics.body);

        ShipFlightComponent flight = new ShipFlightComponent();
        flight.linearThrust = 50000;
        ship.add(flight);

        ShipAerodynamicsComponent aero = new ShipAerodynamicsComponent();
        aero.wingArea = 25f;
        aero.dragCoefficient = 0.35f;
        aero.crossSectionArea = 12f;
        aero.stallAngle = 18f;
        aero.maxLiftCoefficient = 1.4f;
        aero.controlSurfaceAuthority = 0.8f;
        aero.liftCurve = new float[]{0f, 0.2f, 0.5f, 0.9f, 1.2f, 1.4f, 1.3f, 0.8f, 0.3f, 0.1f};
        ship.add(aero);

        return ship;
    }

    private Entity createPilotingPlayer(Entity ship) {
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = ship;
        player.add(state);
        return player;
    }

    private btDiscreteDynamicsWorld createBulletWorld() {
        btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
        btDbvtBroadphase broadphase = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        btDiscreteDynamicsWorld world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, 0, 0));
        return world;
    }

    private void cleanup(PhysicsBodyComponent physics, btDiscreteDynamicsWorld world) {
        world.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
        world.dispose();
    }
}
