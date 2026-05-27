package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.components.GuidedProjectileComponent;
import com.galacticodyssey.ship.weapons.components.GuidedProjectileComponent.GuidancePhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuidedProjectileSystemTest {

    private static final Family GUIDED_FAMILY = Family.all(
        GuidedProjectileComponent.class, ProjectileComponent.class, TransformComponent.class
    ).get();

    private EventBus eventBus;
    private Engine engine;
    private final List<DetonationEvent> detonations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new GuidedProjectileSystem(eventBus));
        eventBus.subscribe(DetonationEvent.class, detonations::add);
    }

    private Entity spawnMissile(Vector3 pos, Vector3 velocity, Entity target,
                                float proximityFuse, float armingDist) {
        Entity missile = new Entity();

        TransformComponent tc = new TransformComponent();
        tc.position.set(pos);
        missile.add(tc);

        ProjectileComponent pc = new ProjectileComponent();
        pc.velocity.set(velocity);
        pc.speed = velocity.len();
        pc.damage = 100f;
        pc.damageType = DamageType.EXPLOSIVE;
        pc.lifetime = 30f;
        pc.areaOfEffect = 20f;
        pc.proximityFuseRadius = proximityFuse;
        missile.add(pc);

        GuidedProjectileComponent gpc = new GuidedProjectileComponent();
        gpc.targetEntity = target;
        gpc.armingDistance = armingDist;
        gpc.navigationGain = 3f;
        gpc.maxAcceleration = 50f;
        gpc.boostDuration = 1f;
        gpc.terminalRange = 100f;
        missile.add(gpc);

        engine.addEntity(missile);
        return missile;
    }

    private Entity spawnTarget(Vector3 pos) {
        Entity target = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(pos);
        target.add(tc);
        engine.addEntity(target);
        return target;
    }

    @Test
    void missileConvergesTowardTarget() {
        Entity target = spawnTarget(new Vector3(500, 0, 0));
        Entity missile = spawnMissile(
            new Vector3(0, 0, 0),
            new Vector3(100, 0, 0),
            target, 0f, 0f
        );

        TransformComponent missileTc = missile.getComponent(TransformComponent.class);
        TransformComponent targetTc = target.getComponent(TransformComponent.class);

        float distBefore = missileTc.position.dst(targetTc.position);

        for (int i = 0; i < 60; i++) {
            engine.update(1f / 60f);
        }

        float distAfter = missileTc.position.dst(targetTc.position);
        assertTrue(distAfter < distBefore,
            "Guided missile should close distance to target");
    }

    @Test
    void proximityFuseTriggersDetonation() {
        Entity target = spawnTarget(new Vector3(50, 0, 0));
        spawnMissile(
            new Vector3(0, 0, 0),
            new Vector3(200, 0, 0),
            target, 20f, 5f
        );

        for (int i = 0; i < 120 && detonations.isEmpty(); i++) {
            engine.update(1f / 60f);
        }

        assertFalse(detonations.isEmpty(), "Proximity fuse should trigger detonation");
        assertEquals(100f, detonations.get(0).damage, 0.01f);
        assertEquals(DamageType.EXPLOSIVE, detonations.get(0).damageType);
    }

    @Test
    void missileRemovedAfterDetonation() {
        Entity target = spawnTarget(new Vector3(30, 0, 0));
        spawnMissile(
            new Vector3(0, 0, 0),
            new Vector3(200, 0, 0),
            target, 15f, 0f
        );

        for (int i = 0; i < 120; i++) {
            engine.update(1f / 60f);
        }

        assertEquals(0, engine.getEntitiesFor(GUIDED_FAMILY).size(),
            "Missile should be removed after detonation");
    }

    @Test
    void phaseTransitionsFromBoostToCoast() {
        Entity target = spawnTarget(new Vector3(1000, 0, 0));
        Entity missile = spawnMissile(
            new Vector3(0, 0, 0),
            new Vector3(50, 0, 0),
            target, 0f, 0f
        );

        GuidedProjectileComponent gpc = missile.getComponent(GuidedProjectileComponent.class);
        gpc.boostDuration = 0.5f;

        assertEquals(GuidancePhase.BOOST, gpc.phase);

        for (int i = 0; i < 60; i++) {
            engine.update(1f / 60f);
        }

        assertEquals(GuidancePhase.COAST, gpc.phase,
            "Phase should transition to COAST after boost duration expires");
    }

    @Test
    void doNotSteerBeforeArmingDistance() {
        Entity target = spawnTarget(new Vector3(500, 100, 0));
        Entity missile = spawnMissile(
            new Vector3(0, 0, 0),
            new Vector3(100, 0, 0),
            target, 10f, 1000f
        );

        ProjectileComponent pc = missile.getComponent(ProjectileComponent.class);
        float vyBefore = pc.velocity.y;

        engine.update(1f / 60f);

        float vyAfter = pc.velocity.y;
        assertEquals(vyBefore, vyAfter, 0.001f,
            "Missile should not steer before arming distance");
    }
}
