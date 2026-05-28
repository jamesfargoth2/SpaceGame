package com.galacticodyssey.hacking;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.hacking.events.*;
import com.galacticodyssey.hacking.systems.HackingSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HackingSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private HackingSystem hackingSystem;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        hackingSystem = new HackingSystem(0, eventBus);
        engine = new Engine();
        engine.addSystem(hackingSystem);
    }

    private Entity entityWithHackable(int difficulty, HackEffect effect) {
        Entity e = new Entity();
        HackableComponent h = new HackableComponent();
        h.difficulty = difficulty;
        h.effect = effect;
        h.lockoutDuration = 45f;
        e.add(h);
        engine.addEntity(e);
        return e;
    }

    @Test
    void lockoutTimerCountsDownEachFrame() {
        Entity target = entityWithHackable(1, HackEffect.ACCESS_DATA);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        hackable.lockoutTimer = 10f;

        engine.update(1f);

        assertEquals(9f, hackable.lockoutTimer, 0.01f);
    }

    @Test
    void lockoutTimerDoesNotGoBelowZero() {
        Entity target = entityWithHackable(1, HackEffect.ACCESS_DATA);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        hackable.lockoutTimer = 0.5f;

        engine.update(2f);

        assertEquals(0f, hackable.lockoutTimer, 0.001f);
    }

    @Test
    void onHackFailedSetsLockoutTimer() {
        Entity target = entityWithHackable(1, HackEffect.ACCESS_DATA);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        assertEquals(0f, hackable.lockoutTimer);

        eventBus.publish(new HackFailedEvent(new Entity(), target));

        assertEquals(45f, hackable.lockoutTimer, 0.001f);
    }

    @Test
    void onHackSucceededUnlockSetsUnlockedFlag() {
        Entity target = entityWithHackable(1, HackEffect.UNLOCK);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        assertFalse(hackable.unlocked);

        eventBus.publish(new HackSucceededEvent(new Entity(), target, HackEffect.UNLOCK));

        assertTrue(hackable.unlocked);
    }

    @Test
    void onHackSucceededDisableTurretSetsEffectTimer() {
        Entity target = entityWithHackable(2, HackEffect.DISABLE_TURRET);
        HackableComponent hackable = target.getComponent(HackableComponent.class);

        eventBus.publish(new HackSucceededEvent(new Entity(), target, HackEffect.DISABLE_TURRET));

        assertEquals(45f, hackable.effectTimer, 0.001f);
    }

    @Test
    void effectTimerExpiryPublishesHackEffectExpiredEvent() {
        Entity target = entityWithHackable(2, HackEffect.DISABLE_CAMERA);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        hackable.effectTimer = 0.5f;

        HackEffectExpiredEvent[] received = {null};
        eventBus.subscribe(HackEffectExpiredEvent.class, e -> received[0] = e);

        engine.update(1f);

        assertNotNull(received[0]);
        assertSame(target, received[0].target);
        assertEquals(HackEffect.DISABLE_CAMERA, received[0].effect);
        assertEquals(0f, hackable.effectTimer, 0.001f);
    }

    @Test
    void onHackSucceededAccessDataPublishesDataAccessedEvent() {
        Entity player = new Entity();
        Entity target = entityWithHackable(3, HackEffect.ACCESS_DATA);
        HackableComponent hackable = target.getComponent(HackableComponent.class);
        hackable.terminalId = "vault_alpha";

        DataAccessedEvent[] received = {null};
        eventBus.subscribe(DataAccessedEvent.class, e -> received[0] = e);

        eventBus.publish(new HackSucceededEvent(player, target, HackEffect.ACCESS_DATA));

        assertNotNull(received[0]);
        assertEquals("vault_alpha", received[0].terminalId);
        assertSame(player, received[0].player);
    }
}
