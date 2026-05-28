package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.HitscanHitEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HitscanSystemTest {

    private EventBus eventBus;
    private Engine engine;

    private Entity shooter;
    private Entity target;

    private TransformComponent shooterTransform;
    private RangedWeaponComponent weapon;

    private TransformComponent targetTransform;
    private HitboxComponent hitbox;
    private HealthComponent health;

    private final List<HitscanHitEvent> hitEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();

        HitscanSystem hitscanSystem = new HitscanSystem(eventBus);
        engine.addSystem(hitscanSystem);

        // --- Shooter entity ---
        shooter = new Entity();
        shooterTransform = new TransformComponent();
        shooterTransform.position.set(0f, 1f, 0f);

        weapon = new RangedWeaponComponent();
        weapon.damage = 25f;
        weapon.range = 100f;
        weapon.spread = 0f;
        weapon.hitscan = true;
        weapon.damageType = DamageType.BALLISTIC;
        weapon.ammoTypeId = "standard";

        shooter.add(shooterTransform);
        shooter.add(weapon);
        engine.addEntity(shooter);

        // --- Target entity ---
        target = new Entity();
        targetTransform = new TransformComponent();
        hitbox = new HitboxComponent();
        health = new HealthComponent();

        target.add(targetTransform);
        target.add(hitbox);
        target.add(health);
        engine.addEntity(target);

        eventBus.subscribe(HitscanHitEvent.class, hitEvents::add);
    }

    /** Muzzle at (0,1.7,0), target at (0,1.7,-10), fire toward (0,0,-1) — should register a hit. */
    @Test
    void hitscanHitPublishedForTargetInRange() {
        targetTransform.position.set(0f, 1.7f, -10f);

        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0f, 0f, -1f), true, new Vector3(0f, 1.7f, 0f)));

        assertEquals(1, hitEvents.size(), "Should publish exactly one HitscanHitEvent");
        HitscanHitEvent event = hitEvents.get(0);
        assertEquals(shooter, event.shooter);
        assertEquals(target, event.target);
        assertEquals(25f, event.damage, 0.001f, "Damage should match weapon.damage");
        assertEquals(DamageType.BALLISTIC, event.damageType);
        assertEquals("standard", event.ammoTypeId);
    }

    /** Target at (0,1,-200) with weapon range=100 — no hit expected. */
    @Test
    void noHitWhenTargetOutOfRange() {
        targetTransform.position.set(0f, 1f, -200f);

        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0f, 0f, -1f), true, new Vector3(0f, 1.7f, -0.5f)));

        assertTrue(hitEvents.isEmpty(), "Should not hit a target beyond weapon range");
    }

    /** Firing a non-hitscan weapon must not trigger HitscanSystem. */
    @Test
    void projectileWeaponIgnoredByHitscan() {
        targetTransform.position.set(0f, 1f, -10f);
        weapon.hitscan = false;

        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0f, 0f, -1f), false, new Vector3(0f, 1.7f, -0.5f)));

        assertTrue(hitEvents.isEmpty(), "HitscanSystem must not process non-hitscan WeaponFiredEvents");
    }
}
