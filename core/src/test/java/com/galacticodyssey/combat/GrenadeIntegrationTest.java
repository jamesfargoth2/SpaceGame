package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.GrenadeData;
import com.galacticodyssey.combat.data.GrenadeDataRegistry;
import com.galacticodyssey.combat.events.*;
import com.galacticodyssey.combat.systems.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeIntegrationTest {

    private Engine engine;
    private EventBus eventBus;
    private GrenadeDataRegistry grenadeRegistry;
    private float worldTime;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        worldTime = 0f;

        grenadeRegistry = new GrenadeDataRegistry();
        GrenadeData frag = new GrenadeData();
        frag.id = "frag";
        frag.fuseType = FuseType.TIMED;
        frag.fuseDuration = 3.0f;
        frag.cookable = true;
        frag.throwForce = 18.0f;
        frag.mass = 0.4f;
        frag.drag = 0.05f;
        frag.gravity = true;
        frag.damage = 50.0f;
        frag.blastRadius = 8.0f;
        frag.blastFraction = 0.5f;
        frag.thermalFraction = 0.1f;
        frag.fragmentFraction = 0.4f;
        frag.bounceRestitution = 0.3f;
        frag.maxBounces = 5;
        frag.maxCarry = 4;
        grenadeRegistry.register(frag);

        // Add all systems in priority order
        engine.addSystem(new GrenadeThrowSystem(eventBus, grenadeRegistry, () -> worldTime));
        ProjectileSystem projSystem = new ProjectileSystem(eventBus);
        projSystem.setGrenadeDataRegistry(grenadeRegistry);
        engine.addSystem(projSystem);
        engine.addSystem(new GrenadeSystem(eventBus));
        engine.addSystem(new ExplosionSystem(eventBus));
    }

    @Test
    void fullGrenadeLifecycle_throwCookExplodeDamage() {
        // Create player
        Entity player = new Entity();
        TransformComponent playerTransform = new TransformComponent();
        playerTransform.position.set(0, 1.5f, 0);
        player.add(playerTransform);

        CombatInputComponent input = new CombatInputComponent();
        input.aimDirection.set(1f, 0f, 0f);
        player.add(input);

        GrenadeInventoryComponent inv = new GrenadeInventoryComponent();
        inv.grenades.put("frag", 3);
        inv.selectedGrenadeType = "frag";
        player.add(inv);

        engine.addEntity(player);

        // Create target at distance 5 from expected landing zone
        Entity target = new Entity();
        TransformComponent targetTransform = new TransformComponent();
        targetTransform.position.set(5f, 1f, 0f);
        target.add(targetTransform);
        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        target.add(health);
        target.add(new ExplosionAffectedComponent());
        engine.addEntity(target);

        // Track events
        List<GrenadeThrowEvent> throws_ = new ArrayList<>();
        List<DetonationEvent> detonations = new ArrayList<>();
        List<BlastDamageEvent> blastDamages = new ArrayList<>();
        eventBus.subscribe(GrenadeThrowEvent.class, throws_::add);
        eventBus.subscribe(DetonationEvent.class, detonations::add);
        eventBus.subscribe(BlastDamageEvent.class, blastDamages::add);

        // Step 1: Press throw button (start cooking)
        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        worldTime = 0f;
        engine.update(0.016f);
        assertEquals(ThrowState.COOKING, inv.throwState);

        // Step 2: Release after 1s cook
        worldTime = 1.0f;
        input.grenadeThrowHeld = false;
        input.grenadeThrowRequested = false;
        engine.update(0.016f);

        assertEquals(1, throws_.size(), "Should publish GrenadeThrowEvent");
        assertEquals(2, inv.grenades.get("frag"), "Inventory should decrement");

        // Grenade entity should exist
        Family grenadeFamily = Family.all(GrenadeComponent.class, ProjectileComponent.class).get();
        assertEquals(1, engine.getEntitiesFor(grenadeFamily).size());

        // Verify fuse timer accounts for cook time
        Entity grenade = engine.getEntitiesFor(grenadeFamily).first();
        GrenadeComponent gc = GrenadeComponent.MAPPER.get(grenade);
        assertEquals(2.0f, gc.fuseTimer, 0.1f);

        // Step 3: Advance time until fuse expires (2.0s remaining)
        for (int i = 0; i < 130; i++) {
            engine.update(0.016f);
        }

        // Grenade should have detonated
        assertEquals(1, detonations.size(), "Grenade should detonate after fuse timer expires");
        assertEquals(0, engine.getEntitiesFor(grenadeFamily).size(), "Grenade entity should be removed");

        // Verify detonation event properties
        DetonationEvent det = detonations.get(0);
        assertEquals(DamageType.EXPLOSIVE, det.damageType);
        assertEquals(8.0f, det.areaOfEffect, 0.01f);
        assertEquals(0.5f, det.blastFraction, 0.001f);
    }
}
