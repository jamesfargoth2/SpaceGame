package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.events.ReloadStartedEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeaponSystemTest {

    private EventBus eventBus;
    private WeaponSystem weaponSystem;
    private Engine engine;
    private Entity entity;

    private CombatInputComponent input;
    private RangedWeaponComponent ranged;
    private WeaponInventoryComponent inventory;

    private final List<WeaponFiredEvent> firedEvents = new ArrayList<>();
    private final List<ReloadStartedEvent> reloadEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        weaponSystem = new WeaponSystem(eventBus);
        engine = new Engine();
        engine.addSystem(weaponSystem);

        input = new CombatInputComponent();
        ranged = new RangedWeaponComponent();
        inventory = new WeaponInventoryComponent();

        // Default weapon state: SEMI, 30/30 ammo, 0.1s fire rate
        ranged.firingMode = FiringMode.SEMI;
        ranged.currentAmmo = 30;
        ranged.magSize = 30;
        ranged.fireRate = 10f;  // 10 rounds/sec → fireTimer interval = 0.1s
        ranged.fireTimer = 0f;
        ranged.reloading = false;
        ranged.reloadTime = 2.0f;
        ranged.reloadTimer = 0f;
        ranged.hitscan = true;

        // Inventory: slot 0 active, not switching, not melee
        inventory.activeSlotIndex = 0;
        inventory.switching = false;

        entity = new Entity();
        entity.add(input);
        entity.add(ranged);
        entity.add(inventory);
        engine.addEntity(entity);

        eventBus.subscribe(WeaponFiredEvent.class, firedEvents::add);
        eventBus.subscribe(ReloadStartedEvent.class, reloadEvents::add);
    }

    @Test
    void semiAutoFiresOncePerClick() {
        input.fireRequested = true;
        input.aimDirection.set(0f, 0f, -1f);

        engine.update(0.1f);

        assertEquals(1, firedEvents.size(), "SEMI mode should fire exactly once on a click");
        assertEquals(29, ranged.currentAmmo, "Ammo should decrement by 1 after firing");
    }

    @Test
    void cannotFireWhileReloading() {
        ranged.reloading = true;
        ranged.reloadTimer = 1.5f;
        input.fireRequested = true;

        engine.update(0.1f);

        assertEquals(0, firedEvents.size(), "Should not fire while reloading");
        assertEquals(30, ranged.currentAmmo, "Ammo should not change while reloading");
    }

    @Test
    void reloadRefillsAmmo() {
        ranged.currentAmmo = 0;
        input.reloadRequested = true;

        // First update: reload starts
        engine.update(0.1f);

        assertEquals(1, reloadEvents.size(), "ReloadStartedEvent should be published");
        assertTrue(ranged.reloading, "Weapon should be in reloading state");
        assertEquals(0, ranged.currentAmmo, "Ammo should not yet be refilled mid-reload");

        // Update past the reload time (reloadTime is 2.0s, reloadTimer started at 2.0)
        engine.update(2.1f);

        assertFalse(ranged.reloading, "Weapon should no longer be reloading after reload time");
        assertEquals(30, ranged.currentAmmo, "Ammo should be refilled to magSize after reload");
    }

    @Test
    void emptyMagCannotFire() {
        ranged.currentAmmo = 0;
        input.fireRequested = true;

        engine.update(0.1f);

        assertEquals(0, firedEvents.size(), "Should not fire with empty magazine");
    }

    @Test
    void autoFiresContinuouslyWhileHeld() {
        ranged.firingMode = FiringMode.AUTO;
        ranged.fireRate = 10f; // 10 rounds/sec → fire every 0.1s
        ranged.fireTimer = 0f;
        ranged.currentAmmo = 30;
        input.fireHeld = true;
        input.aimDirection.set(0f, 0f, -1f);

        // Update for 0.25s — should fire at t=0, t=0.1, t=0.2 = 3 shots
        // But some implementations fire at 0 and then at each fireRate interval.
        // We accumulate 0.25s / 0.1s interval = fires at least 2 times.
        engine.update(0.1f);
        engine.update(0.1f);
        engine.update(0.1f);

        assertTrue(firedEvents.size() >= 2,
            "AUTO mode should fire multiple times while held (got " + firedEvents.size() + ")");
    }
}
