package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.CombatEnums.MeleeCategory;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.CombatEnums.WeaponCategory;
import com.galacticodyssey.combat.CombatEnums.WeaponSlot;
import com.galacticodyssey.combat.CombatEnums.WeightClass;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.MeleeWeaponComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.data.AmmoTypeData;
import com.galacticodyssey.combat.data.BarrelData;
import com.galacticodyssey.combat.data.MeleeFrameData;
import com.galacticodyssey.combat.data.QualityTierData;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.data.WeaponFrameData;
import com.galacticodyssey.combat.events.WeaponSwitchedEvent;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeaponSwitchSystemTest {

    private EventBus eventBus;
    private WeaponDataRegistry weaponData;
    private WeaponSwitchSystem switchSystem;
    private Engine engine;
    private Entity entity;

    private CombatInputComponent input;
    private WeaponInventoryComponent inventory;
    private RangedWeaponComponent ranged;
    private MeleeWeaponComponent melee;

    private final List<WeaponSwitchedEvent> switchedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        weaponData = new WeaponDataRegistry();

        // Register rifle frame (ranged)
        WeaponFrameData rifleFrame = new WeaponFrameData();
        rifleFrame.id = "rifle";
        rifleFrame.category = WeaponCategory.RIFLE;
        rifleFrame.baseDamage = 25f;
        rifleFrame.baseFireRate = 5f;
        rifleFrame.baseSpread = 0.5f;
        rifleFrame.baseRecoil = 0.3f;
        rifleFrame.magSize = 30;
        rifleFrame.weight = 3.5f;
        rifleFrame.firingMode = FiringMode.SEMI;
        rifleFrame.hitscan = true;
        rifleFrame.range = 100f;
        rifleFrame.reloadTime = 2.0f;
        weaponData.registerFrame(rifleFrame);

        // Register standard barrel
        BarrelData standardBarrel = new BarrelData();
        standardBarrel.id = "standard";
        standardBarrel.damageMultiplier = 1.0f;
        standardBarrel.rangeMultiplier = 1.0f;
        standardBarrel.spreadMultiplier = 1.0f;
        standardBarrel.recoilMultiplier = 1.0f;
        standardBarrel.weightAdd = 0f;
        weaponData.registerBarrel(standardBarrel);

        // Register standard ammo (BALLISTIC)
        AmmoTypeData standardAmmo = new AmmoTypeData();
        standardAmmo.id = "standard";
        standardAmmo.damageType = DamageType.BALLISTIC;
        standardAmmo.damageMultiplier = 1.0f;
        weaponData.registerAmmoType(standardAmmo);

        // Register COMMON quality (1.0x multiplier)
        QualityTierData commonQuality = new QualityTierData();
        commonQuality.tier = QualityTier.COMMON;
        commonQuality.globalMultiplier = 1.0f;
        commonQuality.durabilityMultiplier = 1.0f;
        weaponData.registerQuality(commonQuality);

        // Register blade melee frame
        MeleeFrameData bladeFrame = new MeleeFrameData();
        bladeFrame.id = "blade";
        bladeFrame.category = MeleeCategory.BLADE;
        bladeFrame.baseDamage = 35f;
        bladeFrame.baseSpeed = 1.2f;
        bladeFrame.baseReach = 1.5f;
        bladeFrame.baseBlockEfficiency = 0.7f;
        bladeFrame.weight = 1.5f;
        bladeFrame.damageType = DamageType.MELEE;
        bladeFrame.weightClass = WeightClass.LIGHT;
        weaponData.registerMeleeFrame(bladeFrame);

        // Build inventory
        inventory = new WeaponInventoryComponent();
        inventory.slots[0] = WeaponAssembly.ranged("rifle", "standard", "standard", null, QualityTier.COMMON);
        inventory.slots[1] = null; // empty
        inventory.slots[2] = WeaponAssembly.melee("blade", QualityTier.COMMON);
        inventory.activeSlotIndex = 0;
        inventory.switching = false;

        input = new CombatInputComponent();
        input.switchSlotRequested = -1;

        ranged = new RangedWeaponComponent();
        ranged.firingMode = FiringMode.SEMI;
        ranged.currentAmmo = 30;
        ranged.magSize = 30;
        ranged.fireRate = 5f;
        ranged.reloading = false;
        ranged.reloadTime = 2.0f;
        ranged.reloadTimer = 0f;
        ranged.fireTimer = 0f;
        ranged.hitscan = true;

        melee = new MeleeWeaponComponent();

        entity = new Entity();
        entity.add(input);
        entity.add(inventory);
        entity.add(ranged);
        entity.add(melee);
        engine = new Engine();

        switchSystem = new WeaponSwitchSystem(eventBus, weaponData);
        engine.addSystem(switchSystem);
        engine.addEntity(entity);

        eventBus.subscribe(WeaponSwitchedEvent.class, switchedEvents::add);
    }

    @Test
    void switchRequestStartsSwitching() {
        input.switchSlotRequested = 2; // request switch to melee slot

        engine.update(0.05f);

        assertTrue(inventory.switching, "Switching flag should be true after switch request");
        // Should not have completed yet (melee switch time = 0.2s, only 0.05s elapsed)
        assertEquals(0, switchedEvents.size(), "Switch should not be complete yet");
    }

    @Test
    void switchCompletesAfterTimer() {
        // Initiate switch to melee (slot 2, switchTime = 0.2s)
        input.switchSlotRequested = 2;
        engine.update(0.01f); // start switch, consume the request

        assertTrue(inventory.switching, "Should be switching after request");

        // Update past the switch time
        engine.update(0.25f);

        assertFalse(inventory.switching, "Switching should be complete");
        assertEquals(2, inventory.activeSlotIndex, "Active slot should now be 2 (MELEE)");
        assertEquals(1, switchedEvents.size(), "WeaponSwitchedEvent should have been published");
        assertEquals(WeaponSlot.MELEE, switchedEvents.get(0).newSlot, "New slot should be MELEE");
    }

    @Test
    void switchToSameSlotIgnored() {
        // Already at slot 0, request switch to slot 0
        input.switchSlotRequested = 0;

        engine.update(0.1f);

        assertFalse(inventory.switching, "Should not start switching to same slot");
        assertEquals(0, switchedEvents.size(), "No WeaponSwitchedEvent for same-slot switch");
    }

    @Test
    void switchCancelsReload() {
        ranged.reloading = true;
        ranged.reloadTimer = 1.5f;

        input.switchSlotRequested = 2; // switch to melee

        engine.update(0.05f);

        assertFalse(ranged.reloading, "Switch request should cancel active reload");
        assertTrue(inventory.switching, "Switch should have started");
    }
}
