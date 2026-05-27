package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileBallisticsTest {

    private static final Family PROJECTILE_FAMILY =
        Family.all(ProjectileComponent.class, TransformComponent.class).get();

    private EventBus eventBus;
    private Engine engine;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new ProjectileSystem(eventBus));
    }

    private Entity spawnProjectile(Vector3 pos, Vector3 velocity, boolean gravity,
                                   float dragCoeff, float crossSection, float mass,
                                   float maxRange) {
        Entity entity = new Entity();

        TransformComponent tc = new TransformComponent();
        tc.position.set(pos);
        entity.add(tc);

        ProjectileComponent pc = new ProjectileComponent();
        pc.velocity.set(velocity);
        pc.speed = velocity.len();
        pc.damage = 10f;
        pc.damageType = DamageType.BALLISTIC;
        pc.lifetime = 10f;
        pc.age = 0f;
        pc.affectedByGravity = gravity;
        pc.dragCoeff = dragCoeff;
        pc.crossSection = crossSection;
        pc.mass = mass;
        pc.maxRange = maxRange;
        entity.add(pc);

        engine.addEntity(entity);
        return entity;
    }

    @Test
    void gravityAffectedProjectileDrops() {
        Entity proj = spawnProjectile(
            new Vector3(0, 100, 0),
            new Vector3(50, 0, 0),
            true, 0f, 0f, 1f, Float.MAX_VALUE
        );

        TransformComponent tc = proj.getComponent(TransformComponent.class);
        float yBefore = tc.position.y;

        for (int i = 0; i < 10; i++) {
            engine.update(1f / 60f);
        }

        float yAfter = tc.position.y;
        assertTrue(yAfter < yBefore,
            "Gravity-affected projectile must drop; was " + yBefore + ", now " + yAfter);
    }

    @Test
    void nonGravityProjectileDoesNotDrop() {
        Entity proj = spawnProjectile(
            new Vector3(0, 100, 0),
            new Vector3(50, 0, 0),
            false, 0f, 0f, 1f, Float.MAX_VALUE
        );

        TransformComponent tc = proj.getComponent(TransformComponent.class);

        for (int i = 0; i < 10; i++) {
            engine.update(1f / 60f);
        }

        assertEquals(100f, tc.position.y, 0.001f,
            "Non-gravity projectile Y should remain unchanged");
    }

    @Test
    void dragSlowsProjectile() {
        Entity proj = spawnProjectile(
            new Vector3(0, 0, 0),
            new Vector3(100, 0, 0),
            false, 0.5f, 0.01f, 1f, Float.MAX_VALUE
        );

        ProjectileComponent pc = proj.getComponent(ProjectileComponent.class);
        float speedBefore = pc.velocity.len();

        for (int i = 0; i < 60; i++) {
            engine.update(1f / 60f);
        }

        float speedAfter = pc.velocity.len();
        assertTrue(speedAfter < speedBefore,
            "Drag should slow projectile; was " + speedBefore + ", now " + speedAfter);
    }

    @Test
    void zeroDragDoesNotSlowProjectile() {
        Entity proj = spawnProjectile(
            new Vector3(0, 0, 0),
            new Vector3(100, 0, 0),
            false, 0f, 0f, 1f, Float.MAX_VALUE
        );

        ProjectileComponent pc = proj.getComponent(ProjectileComponent.class);
        float speedBefore = pc.velocity.len();

        for (int i = 0; i < 60; i++) {
            engine.update(1f / 60f);
        }

        float speedAfter = pc.velocity.len();
        assertEquals(speedBefore, speedAfter, 0.01f,
            "Zero-drag projectile speed should remain constant");
    }

    @Test
    void projectileDespawnsAtMaxRange() {
        Entity proj = spawnProjectile(
            new Vector3(0, 0, 0),
            new Vector3(100, 0, 0),
            false, 0f, 0f, 1f, 50f
        );

        assertEquals(1, engine.getEntitiesFor(PROJECTILE_FAMILY).size());

        for (int i = 0; i < 60; i++) {
            engine.update(1f / 60f);
        }

        assertEquals(0, engine.getEntitiesFor(PROJECTILE_FAMILY).size(),
            "Projectile should be removed after travelling past maxRange");
    }

    @Test
    void distanceTravelledAccumulates() {
        Entity proj = spawnProjectile(
            new Vector3(0, 0, 0),
            new Vector3(60, 0, 0),
            false, 0f, 0f, 1f, Float.MAX_VALUE
        );

        ProjectileComponent pc = proj.getComponent(ProjectileComponent.class);
        assertEquals(0f, pc.distanceTravelled, 0.001f);

        engine.update(1f);

        assertTrue(pc.distanceTravelled > 0f,
            "distanceTravelled must accumulate over time");
        assertEquals(60f, pc.distanceTravelled, 1f);
    }

    @Test
    void poolableResetClearsAllFields() {
        ProjectileComponent pc = new ProjectileComponent();
        pc.velocity.set(1, 2, 3);
        pc.mass = 50f;
        pc.affectedByGravity = true;
        pc.distanceTravelled = 999f;
        pc.dragCoeff = 0.5f;

        pc.reset();

        assertTrue(pc.velocity.isZero());
        assertEquals(1f, pc.mass);
        assertFalse(pc.affectedByGravity);
        assertEquals(0f, pc.distanceTravelled);
        assertEquals(0f, pc.dragCoeff);
    }
}
