package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatAISystemTest {

    private EventBus eventBus;
    private CombatAISystem combatAISystem;
    private Engine engine;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        combatAISystem = new CombatAISystem(eventBus);
        engine = new Engine();
        engine.addSystem(combatAISystem);
    }

    /** Creates a minimal alive NPC entity with all required components. */
    private Entity makeNpc() {
        Entity npc = new Entity();
        CombatAIComponent ai = new CombatAIComponent();
        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        health.alive = true;
        npc.add(ai);
        npc.add(health);
        npc.add(new TransformComponent());
        npc.add(new CombatInputComponent());
        return npc;
    }

    /** Creates a minimal alive target entity. */
    private Entity makeTarget() {
        Entity target = new Entity();
        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        health.alive = true;
        target.add(health);
        target.add(new TransformComponent());
        return target;
    }

    @Test
    void clearsTargetWhenKilled() {
        Entity npc = makeNpc();
        Entity target = makeTarget();

        CombatAIComponent ai = npc.getComponent(CombatAIComponent.class);
        ai.currentTarget = target;

        engine.addEntity(npc);
        engine.update(0.016f); // initial tick

        // Sanity: target is set
        assertSame(target, ai.currentTarget, "Precondition: target should be set");

        // Publish kill event for the target
        eventBus.publish(new EntityKilledEvent(target, null));

        assertNull(ai.currentTarget, "AI should clear currentTarget when target is killed");
    }

    @Test
    void updatesLastKnownPosition() {
        Entity npc = makeNpc();
        Entity target = makeTarget();

        // Place target at a known position
        TransformComponent targetTransform = target.getComponent(TransformComponent.class);
        targetTransform.position.set(10f, 0f, -5f);

        CombatAIComponent ai = npc.getComponent(CombatAIComponent.class);
        ai.currentTarget = target;

        engine.addEntity(npc);
        engine.update(0.016f);

        assertEquals(10f, ai.lastKnownTargetPosition.x, 0.001f,
            "lastKnownTargetPosition.x should match target x after update");
        assertEquals(0f, ai.lastKnownTargetPosition.y, 0.001f,
            "lastKnownTargetPosition.y should match target y after update");
        assertEquals(-5f, ai.lastKnownTargetPosition.z, 0.001f,
            "lastKnownTargetPosition.z should match target z after update");
        assertTrue(ai.hasLastKnownPosition, "hasLastKnownPosition should be true after tracking");
    }

    @Test
    void deadNPCSkipped() {
        Entity npc = makeNpc();
        HealthComponent health = npc.getComponent(HealthComponent.class);
        health.alive = false;
        health.currentHP = 0f;

        CombatAIComponent ai = npc.getComponent(CombatAIComponent.class);
        // Give it a target so any incorrect processing would overwrite lastKnownTargetPosition
        Entity target = makeTarget();
        ai.currentTarget = target;
        ai.lastKnownTargetPosition.set(99f, 99f, 99f); // sentinel

        engine.addEntity(npc);
        // Should not throw and should not update lastKnownTargetPosition (NPC is dead)
        assertDoesNotThrow(() -> engine.update(0.016f),
            "CombatAISystem must not crash when processing a dead NPC");

        // Dead NPC skipped — lastKnownTargetPosition should remain at sentinel value
        assertEquals(99f, ai.lastKnownTargetPosition.x, 0.001f,
            "Dead NPC's lastKnownTargetPosition must not be updated");
    }
}
