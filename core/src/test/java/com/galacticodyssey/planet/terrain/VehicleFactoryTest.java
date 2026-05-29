package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.VehicleDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleFactoryTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;
    private btDiscreteDynamicsWorld world;

    @BeforeEach
    void setUpWorld() {
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
    }

    @AfterEach
    void tearDownWorld() {
        world.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
    }

    private VehicleDefinition def() {
        VehicleDefinition d = new VehicleDefinition();
        d.id = "rover_light";
        d.mass = 900f; d.maxHP = 200f; d.armorValue = 8f;
        d.modelPath = "models/box.g3db";
        d.weapon = new VehicleDefinition.VehicleWeaponStats();
        d.weapon.damage = 25f; d.weapon.magSize = 50; d.weapon.range = 100f;
        return d;
    }

    @Test
    void buildsEntityWithAllComponents() {
        Engine engine = new Engine();
        VehicleFactory factory = new VehicleFactory();

        Entity v = factory.create(engine, world, def(), new Vector3(10, 0, 5));

        assertNotNull(v.getComponent(VehicleTagComponent.class));
        assertEquals("rover_light", v.getComponent(VehicleTagComponent.class).definitionId);
        assertNotNull(v.getComponent(TransformComponent.class));
        assertNotNull(v.getComponent(GroundVehicleComponent.class));
        assertNotNull(v.getComponent(PhysicsBodyComponent.class));
        assertNotNull(v.getComponent(PhysicsBodyComponent.class).body);
        assertNotNull(v.getComponent(HealthComponent.class));
        assertEquals(200f, v.getComponent(HealthComponent.class).maxHP);
        assertNotNull(v.getComponent(HitboxComponent.class));
        assertNotNull(v.getComponent(RangedWeaponComponent.class));
        assertEquals(50, v.getComponent(RangedWeaponComponent.class).currentAmmo);
        assertNotNull(v.getComponent(CombatInputComponent.class));
        assertNotNull(v.getComponent(VehicleEntryPointComponent.class));
        assertEquals("models/box.g3db", v.getComponent(VehicleRenderComponent.class).modelPath);
        assertTrue(engine.getEntities().contains(v, true));
    }
}
