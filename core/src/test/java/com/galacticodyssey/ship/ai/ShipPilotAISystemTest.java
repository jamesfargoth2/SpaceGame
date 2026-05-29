package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ship.systems.ShipFlightSystem;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointType;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.systems.ShipWeaponSystem;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ShipPilotAISystemTest {

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    private btDefaultCollisionConfiguration config;
    private btCollisionDispatcher dispatcher;
    private btDbvtBroadphase broadphase;
    private btSequentialImpulseConstraintSolver solver;
    private btDiscreteDynamicsWorld world;
    private final List<btRigidBody> bodies = new ArrayList<>();
    private final List<btCollisionShape> shapes = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (world != null) {
            for (btRigidBody b : bodies) world.removeRigidBody(b);
        }
        for (btRigidBody b : bodies) b.dispose();
        for (btCollisionShape s : shapes) s.dispose();
        bodies.clear();
        shapes.clear();
        if (world != null) { world.dispose(); world = null; }
        if (solver != null) { solver.dispose(); solver = null; }
        if (broadphase != null) { broadphase.dispose(); broadphase = null; }
        if (dispatcher != null) { dispatcher.dispose(); dispatcher = null; }
        if (config != null) { config.dispose(); config = null; }
    }

    private void initWorld() {
        config = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(config);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, 0, 0));
    }

    private btRigidBody box(float mass, btCollisionShape shape, Vector3 pos) {
        Vector3 inertia = new Vector3();
        shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, shape, inertia);
        btRigidBody b = new btRigidBody(info);
        b.setWorldTransform(new Matrix4().setToTranslation(pos));
        info.dispose();
        return b;
    }

    @Test
    void attackerClosesAlignsAndFires() {
        initWorld();

        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(ShipWeaponFiredEvent.class, e -> shots.incrementAndGet());

        Engine engine = new Engine();
        ShipWeaponSystem weapons = new ShipWeaponSystem(bus);
        engine.addSystem(weapons);
        ShipPilotAISystem ai = new ShipPilotAISystem(bus, weapons);
        engine.addSystem(ai);
        ShipFlightSystem flight = new ShipFlightSystem();
        engine.addSystem(flight);

        Entity target = new Entity();
        btBoxShape tShape = new btBoxShape(new Vector3(2, 2, 2));
        shapes.add(tShape);
        btRigidBody tBody = box(20000f, tShape, new Vector3(0, 0, -800));
        bodies.add(tBody);
        target.add(physicsOf(tBody, tShape, 20000f));
        TransformComponent tT = new TransformComponent();
        tT.position.set(0, 0, -800);
        target.add(tT);
        HealthComponent tH = new HealthComponent();
        target.add(tH);
        engine.addEntity(target);
        world.addRigidBody(tBody);

        Entity attacker = new Entity();
        btBoxShape aShape = new btBoxShape(new Vector3(1, 1, 1));
        shapes.add(aShape);
        btRigidBody aBody = box(10000f, aShape, new Vector3(0, 0, 0));
        bodies.add(aBody);
        attacker.add(physicsOf(aBody, aShape, 10000f));
        TransformComponent aT = new TransformComponent();
        attacker.add(aT);
        ShipFlightComponent f = new ShipFlightComponent();
        f.linearThrust = 50000; f.pitchYawTorque = 30000; f.rollTorque = 15000;
        f.strafeThrustFraction = 0.6f; f.verticalThrustFraction = 0.6f;
        f.linearDrag = 0.05f; f.angularDrag = 3.0f;
        attacker.add(f);
        attacker.add(new ShipFlightInputComponent());

        ShipHardpointComponent hpc = new ShipHardpointComponent();
        Hardpoint hp = new Hardpoint("gun_0", HardpointType.FIXED, HardpointSize.SMALL, 0, 30);
        ShipWeaponData gun = new ShipWeaponData();
        gun.id = "ai_gun"; gun.damage = 10f; gun.damageType = DamageType.BALLISTIC;
        gun.fireRate = 4f; gun.projectileSpeed = 600f; gun.range = 1200f;
        gun.energyCost = 0f; gun.heatPerShot = 0f;
        hp.mountedWeapon = gun;
        hpc.hardpoints.add(hp);
        attacker.add(hpc);

        ShipPilotAIComponent pilot = new ShipPilotAIComponent();
        pilot.archetype = new PilotArchetype();
        pilot.archetype.aimErrorDeg = 0f;
        pilot.archetype.reactionTimeSec = 0f;
        pilot.decisionInterval = 0f;
        pilot.currentTarget = target;
        pilot.behaviorTree = DogfightTreeFactory.build(attacker, gun.range);
        attacker.add(pilot);

        engine.addEntity(attacker);
        world.addRigidBody(aBody);

        float startRange = aT.position.dst(tT.position);
        float dt = 1f / 60f;
        for (int i = 0; i < 1800; i++) {
            engine.update(dt);
            world.stepSimulation(dt, 2, dt / 2f);
            syncTransform(attacker, aT);
            syncTransform(target, tT);
        }

        float endRange = aT.position.dst(tT.position);
        assertTrue(endRange < startRange, "attacker should close range (" + startRange + "->" + endRange + ")");
        assertTrue(shots.get() > 0, "attacker should have fired at least once");
    }

    @Test
    void acquiresPlayerTargetWhenNoneAssigned() {
        initWorld();

        EventBus bus = new EventBus();
        Engine engine = new Engine();
        ShipWeaponSystem weapons = new ShipWeaponSystem(bus);
        engine.addSystem(weapons);
        engine.addSystem(new ShipPilotAISystem(bus, weapons));

        Entity player = new Entity();
        com.galacticodyssey.core.components.PlayerTagComponent tag =
            new com.galacticodyssey.core.components.PlayerTagComponent();
        player.add(tag);
        TransformComponent pT = new TransformComponent();
        pT.position.set(0, 0, -500);
        player.add(pT);
        engine.addEntity(player);

        Entity attacker = new Entity();
        btBoxShape aShape = new btBoxShape(new Vector3(1, 1, 1));
        shapes.add(aShape);
        btRigidBody aBody = box(10000f, aShape, new Vector3(0, 0, 0));
        bodies.add(aBody);
        attacker.add(physicsOf(aBody, aShape, 10000f));
        attacker.add(new TransformComponent());
        ShipFlightComponent f = new ShipFlightComponent();
        f.linearThrust = 50000; f.pitchYawTorque = 30000; f.rollTorque = 15000;
        f.linearDrag = 0.05f; f.angularDrag = 3.0f;
        attacker.add(f);
        attacker.add(new ShipFlightInputComponent());
        ShipPilotAIComponent pilot = new ShipPilotAIComponent();
        pilot.archetype = new PilotArchetype();
        pilot.decisionInterval = 0f;
        pilot.behaviorTree = DogfightTreeFactory.build(attacker, 1000f);
        attacker.add(pilot);
        engine.addEntity(attacker);
        world.addRigidBody(aBody);

        engine.update(1f / 60f);

        assertSame(player, pilot.currentTarget, "AI should acquire the nearby player ship");
    }

    private static PhysicsBodyComponent physicsOf(btRigidBody body, btCollisionShape shape, float mass) {
        PhysicsBodyComponent p = new PhysicsBodyComponent();
        p.body = body; p.shape = shape; p.mass = mass;
        return p;
    }

    private static void syncTransform(Entity e, TransformComponent t) {
        Matrix4 m = new Matrix4();
        e.getComponent(PhysicsBodyComponent.class).body.getWorldTransform(m);
        m.getTranslation(t.position);
        m.getRotation(t.rotation);
    }
}
