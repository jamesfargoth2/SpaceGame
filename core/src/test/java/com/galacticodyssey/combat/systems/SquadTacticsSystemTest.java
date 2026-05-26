package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.SquadRole;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.SquadComponent;
import com.galacticodyssey.combat.events.RetreatOrderEvent;
import com.galacticodyssey.combat.events.ThreatDetectedEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SquadTacticsSystemTest {

    private EventBus eventBus;
    private SquadTacticsSystem squadTacticsSystem;
    private Engine engine;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        squadTacticsSystem = new SquadTacticsSystem(eventBus);
        engine = new Engine();
        engine.addSystem(squadTacticsSystem);
    }

    /**
     * Creates a squad member entity with the required components.
     *
     * @param squadId  the squad this member belongs to
     * @param role     the role within the squad
     * @param alive    whether the member is alive
     * @return the configured entity (not yet added to the engine)
     */
    private Entity createSquadMember(int squadId, SquadRole role, boolean alive) {
        Entity entity = new Entity();

        SquadComponent squad = new SquadComponent();
        squad.squadId = squadId;
        squad.role = role;

        HealthComponent health = new HealthComponent();
        health.alive = alive;
        health.currentHP = alive ? 100f : 0f;
        health.maxHP = 100f;

        CombatAIComponent ai = new CombatAIComponent();

        entity.add(squad);
        entity.add(health);
        entity.add(new TransformComponent());
        entity.add(ai);

        return entity;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * With a squad of 4 where 2 are dead, the system should publish exactly one
     * RetreatOrderEvent carrying the squad's id (1).
     */
    @Test
    void retreatOrderWhenHalfSquadDead() {
        // Squad 1: 4 members, 2 alive, 2 dead
        Entity leader   = createSquadMember(1, SquadRole.LEADER,     true);
        Entity flanker  = createSquadMember(1, SquadRole.FLANKER,    true);
        Entity supp1    = createSquadMember(1, SquadRole.SUPPRESSOR, false);
        Entity supp2    = createSquadMember(1, SquadRole.SUPPRESSOR, false);

        engine.addEntity(leader);
        engine.addEntity(flanker);
        engine.addEntity(supp1);
        engine.addEntity(supp2);

        List<RetreatOrderEvent> captured = new ArrayList<>();
        eventBus.subscribe(RetreatOrderEvent.class, captured::add);

        engine.update(0.016f);

        assertEquals(1, captured.size(), "Exactly one RetreatOrderEvent should be published");
        assertEquals(1, captured.get(0).squadId, "RetreatOrderEvent must carry squadId = 1");
    }

    /**
     * When the leader detects a threat and a ThreatDetectedEvent is published for
     * squadId=1, all other alive squad members should have their currentTarget set
     * to the threat entity.
     */
    @Test
    void threatSharedWithSquad() {
        Entity leader  = createSquadMember(1, SquadRole.LEADER,  true);
        Entity flanker = createSquadMember(1, SquadRole.FLANKER, true);

        engine.addEntity(leader);
        engine.addEntity(flanker);

        // Trigger one engine tick so the system is fully registered
        engine.update(0.016f);

        // Simulate leader detecting a threat
        Entity threatEntity = new Entity();
        Vector3 threatPosition = new Vector3(5f, 0f, 10f);

        eventBus.publish(new ThreatDetectedEvent(leader, threatEntity, threatPosition, 1));

        CombatAIComponent flankerAI = flanker.getComponent(CombatAIComponent.class);

        assertSame(threatEntity, flankerAI.currentTarget,
            "Flanker's currentTarget should be set to the threat entity after leader detects it");
        assertEquals(5f, flankerAI.lastKnownTargetPosition.x, 0.001f,
            "Flanker's lastKnownTargetPosition.x should match threat position");
        assertEquals(10f, flankerAI.lastKnownTargetPosition.z, 0.001f,
            "Flanker's lastKnownTargetPosition.z should match threat position");

        // Leader's own AI should NOT be modified (detector is excluded)
        CombatAIComponent leaderAI = leader.getComponent(CombatAIComponent.class);
        assertNull(leaderAI.currentTarget,
            "Leader's currentTarget should not be overwritten by its own threat event");
    }
}
