package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.DamageDealtEvent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.CrosshairComponent;
import com.galacticodyssey.player.systems.CrosshairSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrosshairSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity player;
    private CrosshairComponent crosshair;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new CrosshairSystem(eventBus));

        player = new Entity();
        crosshair = new CrosshairComponent();
        crosshair.bloomDecayRate = 5f;
        player.add(crosshair);

        RangedWeaponComponent rwc = new RangedWeaponComponent();
        rwc.spread = 2f;
        player.add(rwc);

        engine.addEntity(player);
    }

    @Test
    void bloom_decaysOverTime() {
        crosshair.currentBloom = 5f;
        engine.update(0.5f);
        assertTrue(crosshair.currentBloom < 5f);
    }

    @Test
    void bloom_doesNotGoBelowZero() {
        crosshair.currentBloom = 0.1f;
        engine.update(1.0f);
        assertEquals(0f, crosshair.currentBloom, 0.01f);
    }

    @Test
    void damageDealt_triggersHitMarker() {
        Entity target = new Entity();
        eventBus.publish(new DamageDealtEvent(target, player, 20f, DamageType.BALLISTIC, HitRegion.TORSO));
        engine.update(0.016f);

        assertTrue(crosshair.hitMarkerTimer > 0);
    }

    @Test
    void entityKilled_triggersKillConfirm() {
        Entity target = new Entity();
        eventBus.publish(new EntityKilledEvent(target, player));
        engine.update(0.016f);

        assertTrue(crosshair.killConfirmTimer > 0);
    }
}
