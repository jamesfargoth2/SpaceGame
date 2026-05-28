package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.components.GrenadeComponent;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.combat.systems.GrenadeSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private List<DetonationEvent> detonations;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new GrenadeSystem(eventBus));
        detonations = new ArrayList<>();
        eventBus.subscribe(DetonationEvent.class, detonations::add);
    }

    private Entity createGrenade(FuseType fuseType, float fuseTimer, Vector3 position) {
        Entity grenade = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(position);
        grenade.add(transform);

        ProjectileComponent projectile = new ProjectileComponent();
        projectile.velocity.set(5f, 0f, 0f);
        projectile.damage = 50f;
        projectile.damageType = DamageType.EXPLOSIVE;
        projectile.areaOfEffect = 8f;
        projectile.owner = new Entity();
        grenade.add(projectile);

        GrenadeComponent gc = new GrenadeComponent();
        gc.grenadeTypeId = "frag";
        gc.fuseType = fuseType;
        gc.fuseTimer = fuseTimer;
        gc.fuseDuration = 3.0f;
        gc.damage = 50f;
        gc.blastRadius = 8f;
        gc.blastFraction = 0.5f;
        gc.thermalFraction = 0.1f;
        gc.fragmentFraction = 0.4f;
        grenade.add(gc);

        return grenade;
    }

    @Test
    void timedFuseDetonatesWhenTimerExpires() {
        Entity grenade = createGrenade(FuseType.TIMED, 1.0f, new Vector3(10f, 0f, 0f));
        engine.addEntity(grenade);

        engine.update(0.5f);
        assertTrue(detonations.isEmpty());

        engine.update(0.6f);
        assertEquals(1, detonations.size());

        DetonationEvent det = detonations.get(0);
        assertEquals(10f, det.position.x, 0.01f);
        assertEquals(DamageType.EXPLOSIVE, det.damageType);
        assertEquals(8f, det.areaOfEffect, 0.01f);
        assertEquals(0.5f, det.blastFraction, 0.001f);
    }

    @Test
    void timedFuseGrenadeRemovedAfterDetonation() {
        Entity grenade = createGrenade(FuseType.TIMED, 0.1f, new Vector3(0, 0, 0));
        engine.addEntity(grenade);

        engine.update(0.2f);

        assertEquals(1, detonations.size());
        assertEquals(0, engine.getEntities().size());
    }

    @Test
    void grenadeDoesNotDoubleDetonate() {
        Entity grenade = createGrenade(FuseType.TIMED, 0.1f, new Vector3(0, 0, 0));
        engine.addEntity(grenade);

        engine.update(0.2f);
        engine.update(0.2f);

        assertEquals(1, detonations.size());
    }

    @Test
    void impactGrenadeDetonatesWhenFuseTimerSetToZero() {
        Entity grenade = createGrenade(FuseType.IMPACT, 5.0f, new Vector3(7f, 0f, 0f));
        engine.addEntity(grenade);

        GrenadeComponent gc = GrenadeComponent.MAPPER.get(grenade);
        gc.fuseTimer = 0f;

        engine.update(0.016f);

        assertEquals(1, detonations.size());
        assertEquals(7f, detonations.get(0).position.x, 0.01f);
    }

    @Test
    void impactGrenadeDoesNotDetonateWithoutCollision() {
        Entity grenade = createGrenade(FuseType.IMPACT, 5.0f, new Vector3(0, 0, 0));
        engine.addEntity(grenade);

        engine.update(0.016f);
        engine.update(0.016f);

        assertTrue(detonations.isEmpty());
    }
}
