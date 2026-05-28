package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.CombatEnums.ThrowState;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.GrenadeData;
import com.galacticodyssey.combat.data.GrenadeDataRegistry;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.combat.events.GrenadeThrowEvent;
import com.galacticodyssey.combat.systems.GrenadeThrowSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeThrowSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private GrenadeDataRegistry registry;
    private Entity player;
    private CombatInputComponent input;
    private GrenadeInventoryComponent inventory;
    private List<GrenadeThrowEvent> throwEvents;
    private List<DetonationEvent> detonations;
    private float worldTime;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        worldTime = 0f;

        registry = new GrenadeDataRegistry();
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
        registry.register(frag);

        GrenadeThrowSystem system = new GrenadeThrowSystem(eventBus, registry, () -> worldTime);
        engine.addSystem(system);

        player = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(0, 1.5f, 0);
        player.add(transform);

        input = new CombatInputComponent();
        input.aimDirection.set(0, 0, -1);
        player.add(input);

        inventory = new GrenadeInventoryComponent();
        inventory.grenades.put("frag", 3);
        inventory.selectedGrenadeType = "frag";
        player.add(inventory);

        engine.addEntity(player);

        throwEvents = new ArrayList<>();
        detonations = new ArrayList<>();
        eventBus.subscribe(GrenadeThrowEvent.class, throwEvents::add);
        eventBus.subscribe(DetonationEvent.class, detonations::add);
    }

    @Test
    void throwButtonStartsCooking() {
        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        engine.update(0.016f);

        assertEquals(ThrowState.COOKING, inventory.throwState);
    }

    @Test
    void releaseThrowButtonSpawnsGrenade() {
        // Press
        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        engine.update(0.016f);
        worldTime += 0.5f;

        // Release
        input.grenadeThrowRequested = false;
        input.grenadeThrowHeld = false;
        engine.update(0.5f);

        assertEquals(ThrowState.IDLE, inventory.throwState);
        assertEquals(2, inventory.grenades.get("frag"));
        assertEquals(1, throwEvents.size());

        // Grenade entity should exist in engine (player + grenade)
        int grenadeCount = engine.getEntitiesFor(
                Family.all(GrenadeComponent.class, ProjectileComponent.class).get()).size();
        assertEquals(1, grenadeCount);
    }

    @Test
    void cookingReducesFuseTimer() {
        // Press and cook for 1 second
        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        worldTime = 10.0f;
        engine.update(0.016f);
        worldTime = 11.0f;

        // Release after 1s cook
        input.grenadeThrowHeld = false;
        engine.update(1.0f);

        // Find spawned grenade
        Entity grenade = engine.getEntitiesFor(
                Family.all(GrenadeComponent.class).get()).first();
        GrenadeComponent gc = GrenadeComponent.MAPPER.get(grenade);

        // Fuse should be fuseDuration(3.0) - cookTime(1.0) = 2.0
        assertEquals(2.0f, gc.fuseTimer, 0.1f);
    }

    @Test
    void cannotThrowWithEmptyInventory() {
        inventory.grenades.put("frag", 0);

        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        engine.update(0.016f);

        assertEquals(ThrowState.IDLE, inventory.throwState);
        assertTrue(throwEvents.isEmpty());
    }

    @Test
    void overcookDetonatesInHand() {
        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        worldTime = 0f;
        engine.update(0.016f);

        // Cook for longer than fuseDuration (3.0s)
        worldTime = 3.1f;
        engine.update(3.1f);

        assertEquals(1, detonations.size());
        assertEquals(ThrowState.IDLE, inventory.throwState);
        assertEquals(2, inventory.grenades.get("frag"));
    }
}
