package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.ProjectileHitEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProjectileSystem}.
 *
 * <p>All tests use a real {@link Engine} (no GL context needed) and drive the system manually
 * via {@link Engine#update(float)}.
 */
class ProjectileSystemTest {

    private static final Family PROJECTILE_FAMILY =
        Family.all(ProjectileComponent.class, TransformComponent.class).get();

    private EventBus eventBus;
    private Engine engine;

    /** Shooter entity present in every test. */
    private Entity shooter;

    private final List<ProjectileHitEvent> hitEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();

        ProjectileSystem system = new ProjectileSystem(eventBus);
        engine.addSystem(system);

        // --- Shooter entity ---
        shooter = new Entity();

        TransformComponent shooterTransform = new TransformComponent();
        shooterTransform.position.set(0f, 1f, 0f);

        RangedWeaponComponent weapon = new RangedWeaponComponent();
        weapon.damage = 35f;
        weapon.range = 80f;
        weapon.hitscan = false;
        weapon.damageType = DamageType.PLASMA;
        weapon.projectileSpeed = 40f;
        weapon.ammoTypeId = "plasma_cell";
        weapon.firingMode = FiringMode.SEMI;

        shooter.add(shooterTransform);
        shooter.add(weapon);
        engine.addEntity(shooter);

        eventBus.subscribe(ProjectileHitEvent.class, hitEvents::add);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private int projectileCount() {
        return engine.getEntitiesFor(PROJECTILE_FAMILY).size();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Firing a non-hitscan weapon must create exactly one projectile entity in the engine.
     */
    @Test
    void projectileEntityCreatedOnFire() {
        int before = projectileCount();

        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0f, 0f, -1f), false));
        // The system adds the entity directly on receiving the event (before any update)
        engine.update(0f);

        assertEquals(before + 1, projectileCount(),
            "Firing a non-hitscan weapon must add exactly one projectile entity");
    }

    /**
     * After firing toward (0,0,-1) and updating 0.1 s, the projectile's Z coordinate must
     * have decreased (moved forward).
     */
    @Test
    void projectileMovesForward() {
        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0f, 0f, -1f), false));
        engine.update(0f); // let entity settle

        // Capture initial Z position of the projectile
        Entity projectile = engine.getEntitiesFor(PROJECTILE_FAMILY).get(0);
        TransformComponent transform =
            projectile.getComponent(TransformComponent.class);
        float zBefore = transform.position.z;

        engine.update(0.1f);

        float zAfter = transform.position.z;
        assertTrue(zAfter < zBefore,
            "Projectile fired toward (0,0,-1) must have a smaller Z after 0.1 s; was "
                + zBefore + ", now " + zAfter);
    }

    /**
     * Lifetime = range / speed = 80 / 40 = 2 s. After 2.1 s the projectile must be removed.
     */
    @Test
    void projectileExpiresAfterLifetime() {
        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0f, 0f, -1f), false));
        engine.update(0f); // entity added

        assertEquals(1, projectileCount(), "Projectile should exist before expiry");

        // Advance past the lifetime (2.1 s in one step to keep it simple)
        engine.update(2.1f);

        assertEquals(0, projectileCount(),
            "Projectile must be removed from the engine after its lifetime (range/speed = 2 s) elapses");
    }

    /**
     * A target placed at (0,1,-5) with a hitbox and health component must be hit by a projectile
     * fired toward (0,0,-1) at speed 40. Distance from shooter (0,1,0) = 5 m, so it takes 0.125 s
     * to arrive. After enough discrete updates the system should have published a
     * {@link ProjectileHitEvent}.
     */
    @Test
    void projectileHitsTarget() {
        // --- Target entity ---
        Entity target = new Entity();

        TransformComponent targetTransform = new TransformComponent();
        targetTransform.position.set(0f, 1f, -5f);

        HitboxComponent hitbox = new HitboxComponent();

        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        health.alive = true;

        target.add(targetTransform);
        target.add(hitbox);
        target.add(health);
        engine.addEntity(target);

        // Fire toward the target
        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0f, 0f, -1f), false));
        engine.update(0f); // spawn projectile

        // Step in small increments until hit or timeout (well within 2 s lifetime)
        final float dt = 0.05f;
        final int maxSteps = 60; // 3 s worth — more than enough
        for (int step = 0; step < maxSteps && hitEvents.isEmpty(); step++) {
            engine.update(dt);
        }

        assertFalse(hitEvents.isEmpty(),
            "ProjectileHitEvent must be published when the projectile reaches the target");

        ProjectileHitEvent event = hitEvents.get(0);
        assertEquals(shooter, event.shooter, "Event.shooter must be the shooter entity");
        assertEquals(target, event.target, "Event.target must be the target entity");
        assertEquals(35f, event.damage, 0.001f, "Event.damage must match weapon.damage");
        assertEquals(DamageType.PLASMA, event.damageType, "Event.damageType must match weapon.damageType");
        assertEquals("plasma_cell", event.ammoTypeId, "Event.ammoTypeId must match weapon.ammoTypeId");
    }
}
