package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.IntMap;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.SquadComponent;
import com.galacticodyssey.combat.events.RetreatOrderEvent;
import com.galacticodyssey.combat.events.ThreatDetectedEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles squad-level tactics: sharing threat information across squad members
 * and issuing retreat orders when a squad suffers heavy casualties.
 *
 * <p>Priority 11 — runs after {@link CombatAISystem} (priority 10).</p>
 */
public class SquadTacticsSystem extends EntitySystem {

    public static final int PRIORITY = 11;

    private static final ComponentMapper<SquadComponent> SQUAD_M =
        ComponentMapper.getFor(SquadComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private static final Family SQUAD_FAMILY = Family.all(
        SquadComponent.class,
        HealthComponent.class,
        TransformComponent.class
    ).get();

    /** Tracks which squadIds have already had a RetreatOrderEvent published this engagement. */
    private final IntMap<Boolean> retreatPublished = new IntMap<>();

    private final EventBus eventBus;

    /** Engine reference — populated in addedToEngine, used to iterate entities. */
    private Engine engine;

    public SquadTacticsSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(ThreatDetectedEvent.class, this::onThreatDetected);
    }

    // -------------------------------------------------------------------------
    // Ashley lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /**
     * When any squad member detects a threat, share the target and last-known
     * position with all other alive members of the same squad.
     */
    private void onThreatDetected(ThreatDetectedEvent event) {
        if (engine == null) return;

        for (Entity member : engine.getEntitiesFor(SQUAD_FAMILY)) {
            SquadComponent squad = SQUAD_M.get(member);
            if (squad.squadId != event.squadId) continue;
            if (member == event.detector) continue;

            HealthComponent health = HEALTH_M.get(member);
            if (health == null || !health.alive) continue;

            CombatAIComponent ai = AI_M.get(member);
            if (ai == null) continue;

            ai.currentTarget = event.threat;
            ai.lastKnownTargetPosition.set(event.position);
            ai.hasLastKnownPosition = true;
        }
    }

    // -------------------------------------------------------------------------
    // Per-frame update
    // -------------------------------------------------------------------------

    @Override
    public void update(float deltaTime) {
        if (engine == null) return;

        // Group all squad entities by squadId
        Map<Integer, List<Entity>> bySquad = new HashMap<>();
        for (Entity entity : engine.getEntitiesFor(SQUAD_FAMILY)) {
            SquadComponent squad = SQUAD_M.get(entity);
            bySquad.computeIfAbsent(squad.squadId, k -> new ArrayList<>()).add(entity);
        }

        for (Map.Entry<Integer, List<Entity>> entry : bySquad.entrySet()) {
            int squadId = entry.getKey();
            List<Entity> members = entry.getValue();

            // Skip squads that already had a retreat order issued
            if (retreatPublished.containsKey(squadId)) continue;

            int total = members.size();
            int alive = 0;
            for (Entity member : members) {
                HealthComponent health = HEALTH_M.get(member);
                if (health != null && health.alive) alive++;
            }

            // Retreat when alive count is at or below half (integer division)
            if (alive <= total / 2) {
                retreatPublished.put(squadId, Boolean.TRUE);
                eventBus.publish(new RetreatOrderEvent(squadId, new Vector3(0f, 0f, 0f)));
            }
        }
    }
}
