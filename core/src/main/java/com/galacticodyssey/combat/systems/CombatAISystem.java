package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Drives per-NPC behavior trees each frame. Clears currentTarget when the target
 * dies, updates lastKnownTargetPosition while the target is visible, then steps
 * the BehaviorTree. Dead NPCs are skipped.
 *
 * <p>Priority 10 — runs after input systems and before movement systems.</p>
 */
public class CombatAISystem extends IteratingSystem {

    public static final int PRIORITY = 10;

    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    @SuppressWarnings("unused")
    private final EventBus eventBus;

    public CombatAISystem(EventBus eventBus) {
        super(Family.all(
            CombatAIComponent.class,
            TransformComponent.class,
            HealthComponent.class,
            CombatInputComponent.class
        ).get(), PRIORITY);

        this.eventBus = eventBus;
        eventBus.subscribe(EntityKilledEvent.class, this::onEntityKilled);
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    private void onEntityKilled(EntityKilledEvent event) {
        // Any AI that was targeting the killed entity must clear its reference
        for (Entity entity : getEntities()) {
            CombatAIComponent ai = AI_M.get(entity);
            if (ai != null && ai.currentTarget == event.target) {
                ai.currentTarget = null;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-entity update
    // -------------------------------------------------------------------------

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CombatAIComponent ai = AI_M.get(entity);
        HealthComponent health = HEALTH_M.get(entity);

        // Skip dead NPCs
        if (health == null || !health.alive) return;
        if (ai == null) return;

        // Update last-known position while target is alive and reachable
        updateTargetTracking(ai, deltaTime);

        // Step the behavior tree if one is attached
        if (ai.behaviorTree != null) {
            ai.behaviorTree.step();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void updateTargetTracking(CombatAIComponent ai, float deltaTime) {
        if (ai.currentTarget == null) return;

        HealthComponent targetHealth = HEALTH_M.get(ai.currentTarget);
        if (targetHealth == null || !targetHealth.alive) {
            // Target died without an EntityKilledEvent reaching us yet — clear anyway
            ai.currentTarget = null;
            return;
        }

        TransformComponent targetTransform = TRANSFORM_M.get(ai.currentTarget);
        if (targetTransform != null) {
            ai.lastKnownTargetPosition.set(targetTransform.position);
            ai.hasLastKnownPosition = true;
        }

        // Tick search timer down if we're currently searching (no target)
        // (here target is non-null so reset the timer to full)
        ai.searchTimer = ai.searchDuration;
    }
}
