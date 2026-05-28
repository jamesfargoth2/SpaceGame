package com.galacticodyssey.integration;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.combat.events.RecoilEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.events.EquipmentChangedEvent;
import com.galacticodyssey.equipment.items.WeaponItem;
import com.galacticodyssey.equipment.systems.EquipmentSystem;
import com.galacticodyssey.player.components.*;
import com.galacticodyssey.player.systems.*;
import com.galacticodyssey.vfx.components.ParticlePoolComponent;
import com.galacticodyssey.vfx.data.ParticleEffectDefinition;
import com.galacticodyssey.vfx.data.VFXEventBindings;
import com.galacticodyssey.vfx.data.VFXRegistry;
import com.galacticodyssey.vfx.systems.ParticleSpawnSystem;
import com.galacticodyssey.vfx.systems.ParticleUpdateSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponsIntegrationTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity player;
    private ParticlePoolComponent particlePool;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();

        // Systems
        engine.addSystem(new EquipmentSystem(eventBus));
        engine.addSystem(new RecoilSystem(eventBus));
        engine.addSystem(new ADSSystem());
        engine.addSystem(new CrosshairSystem(eventBus));
        engine.addSystem(new ScreenShakeSystem());
        engine.addSystem(new WeaponSwaySystem());

        particlePool = new ParticlePoolComponent();
        Entity poolEntity = new Entity();
        poolEntity.add(particlePool);
        engine.addEntity(poolEntity);

        VFXRegistry vfxReg = new VFXRegistry();
        ParticleEffectDefinition flash = new ParticleEffectDefinition();
        flash.id = "muzzle_flash_ballistic";
        flash.burstCount = 6;
        flash.lifetimeMin = 0.05f;
        flash.lifetimeMax = 0.1f;
        flash.speedMin = 2f;
        flash.speedMax = 5f;
        flash.spread = 20f;
        vfxReg.register(flash);

        VFXEventBindings bindings = new VFXEventBindings();
        bindings.bind("WeaponFiredEvent", null, "muzzle_flash_ballistic");

        engine.addSystem(new ParticleSpawnSystem(eventBus, vfxReg, bindings, particlePool));
        engine.addSystem(new ParticleUpdateSystem(particlePool));

        // Player entity
        player = new Entity();
        player.add(new TransformComponent());
        player.add(new EquipmentSlotsComponent());
        player.add(new InventoryComponent(8, 6, 50f));
        player.add(new WeaponInventoryComponent());
        player.add(new RangedWeaponComponent());
        player.add(new MeleeWeaponComponent());
        player.add(new MeleeStateComponent());
        player.add(new ArmorComponent());
        player.add(new FPSCameraComponent());
        player.add(new MovementStateComponent());
        player.add(new CombatInputComponent());
        player.add(new RecoilComponent());
        player.add(new ADSComponent());
        player.add(new CrosshairComponent());
        player.add(new ScreenShakeComponent());

        RecoilComponent rc = player.getComponent(RecoilComponent.class);
        rc.pattern = new Vector2[]{new Vector2(1.5f, 0f)};

        engine.addEntity(player);
    }

    @Test
    void equipWeapon_thenFire_fullPipeline() {
        // Equip weapon via equipment system
        WeaponAssembly assembly = WeaponAssembly.ranged("pistol_standard", "standard_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem pistol = new WeaponItem("pistol_1", "Pistol", "A pistol",
            "pistol_icon", QualityTier.COMMON, 2, 1, 1.2f, assembly);

        EquipmentSystem equipSys = engine.getSystem(EquipmentSystem.class);
        assertTrue(equipSys.equip(player, EquipmentSlot.PRIMARY_WEAPON, pistol));

        // Verify weapon assembly synced to combat component
        WeaponInventoryComponent wic = player.getComponent(WeaponInventoryComponent.class);
        assertNotNull(wic.slots[0]);

        // Publish WeaponFiredEvent (triggers VFX particles) and RecoilEvent (triggers recoil system)
        eventBus.publish(new WeaponFiredEvent(player, new Vector3(0, 0, -1), true, new Vector3(0, 1.7f, -0.5f)));
        eventBus.publish(new RecoilEvent(player, new Vector2(1.5f, 0f)));
        engine.update(0.016f);

        // Verify recoil was applied — patternIndex advances after each RecoilEvent
        RecoilComponent rc = player.getComponent(RecoilComponent.class);
        assertEquals(1, rc.patternIndex);

        // Verify particles were spawned
        assertFalse(particlePool.active.isEmpty());
    }

    @Test
    void adsReducesSpread_andRecoilStillApplies() {
        CombatInputComponent input = player.getComponent(CombatInputComponent.class);
        ADSComponent ads = player.getComponent(ADSComponent.class);

        input.aimHeld = true;
        for (int i = 0; i < 20; i++) {
            engine.update(0.05f);
        }
        assertTrue(ads.adsProgress > 0.9f);

        // Publish both events: WeaponFiredEvent for VFX, RecoilEvent for recoil system
        eventBus.publish(new WeaponFiredEvent(player, new Vector3(0, 0, -1), true, new Vector3(0, 1.7f, -0.5f)));
        eventBus.publish(new RecoilEvent(player, new Vector2(1.5f, 0f)));
        engine.update(0.016f);

        RecoilComponent rc = player.getComponent(RecoilComponent.class);
        assertTrue(rc.currentPunch.len() > 0);
    }
}
