package com.galacticodyssey.npc;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import com.galacticodyssey.npc.components.NpcScheduleComponent;
import com.galacticodyssey.npc.components.ScheduleEntry;
import com.galacticodyssey.npc.crew.CrewAssignmentComponent;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRank;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.crew.MoraleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcComponentsTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        engine = new Engine();
    }

    @Test
    void createNpcEntityWithIdentityAndStats() {
        Entity npc = new Entity();
        NpcIdentityComponent identity = new NpcIdentityComponent();
        identity.npcId = "npc_001";
        identity.name = "Test NPC";
        identity.species = "human";
        identity.recruitable = true;
        npc.add(identity);

        NpcStatsComponent stats = new NpcStatsComponent();
        stats.accuracy = 60f;
        stats.repair = 40f;
        npc.add(stats);

        engine.addEntity(npc);

        var entities = engine.getEntitiesFor(
            Family.all(NpcIdentityComponent.class, NpcStatsComponent.class).get());
        assertEquals(1, entities.size());

        ComponentMapper<NpcIdentityComponent> idMapper =
            ComponentMapper.getFor(NpcIdentityComponent.class);
        assertEquals("npc_001", idMapper.get(npc).npcId);
        assertTrue(idMapper.get(npc).recruitable);
    }

    @Test
    void crewMemberDefaultValues() {
        CrewMemberComponent crew = new CrewMemberComponent();
        assertEquals(CrewRank.RECRUIT, crew.rank);
        assertEquals(75f, crew.morale);
        assertEquals(50f, crew.loyalty);
        assertEquals(MoraleState.GRUMBLING, crew.moraleState);
        assertNull(crew.assignedStation);
        assertTrue(crew.perkIds.isEmpty());
    }

    @Test
    void moraleStateFromMorale() {
        assertEquals(MoraleState.CONTENT, MoraleState.fromMorale(90f));
        assertEquals(MoraleState.CONTENT, MoraleState.fromMorale(80f));
        assertEquals(MoraleState.GRUMBLING, MoraleState.fromMorale(79f));
        assertEquals(MoraleState.GRUMBLING, MoraleState.fromMorale(50f));
        assertEquals(MoraleState.DISGRUNTLED, MoraleState.fromMorale(49f));
        assertEquals(MoraleState.DISGRUNTLED, MoraleState.fromMorale(25f));
        assertEquals(MoraleState.MUTINOUS, MoraleState.fromMorale(24f));
        assertEquals(MoraleState.MUTINOUS, MoraleState.fromMorale(0f));
    }

    @Test
    void crewRankNextRank() {
        assertEquals(CrewRank.CREWMAN, CrewRank.RECRUIT.nextRank());
        assertEquals(CrewRank.SPECIALIST, CrewRank.CREWMAN.nextRank());
        assertNull(CrewRank.COMMANDER.nextRank());
    }

    @Test
    void crewRoleRelevantStat() {
        NpcStatsComponent stats = new NpcStatsComponent();
        stats.accuracy = 80f;
        stats.repair = 60f;
        stats.piloting = 70f;

        assertEquals(70f, CrewRole.PILOT.getRelevantStat(stats));
        assertEquals(80f, CrewRole.GUNNER.getRelevantStat(stats));
        assertEquals(60f, CrewRole.ENGINEER.getRelevantStat(stats));
    }

    @Test
    void scheduleComponentHoldsEntries() {
        NpcScheduleComponent schedule = new NpcScheduleComponent();
        schedule.entries.add(new ScheduleEntry(8f, "market_01", "TRADE"));
        schedule.entries.add(new ScheduleEntry(20f, "home_01", "REST"));
        assertEquals(2, schedule.entries.size());
        assertEquals(8f, schedule.entries.get(0).hourOfDay);
    }

    @Test
    void crewAssignmentComponentDefaults() {
        CrewAssignmentComponent assignment = new CrewAssignmentComponent();
        assertNull(assignment.requiredRole);
        assertNull(assignment.assignedCrew);
        assertEquals(0f, assignment.effectivenessMultiplier);
    }

    @Test
    void moraleStateEffectivenessModifiers() {
        assertEquals(1.1f, MoraleState.CONTENT.effectivenessModifier());
        assertEquals(1.0f, MoraleState.GRUMBLING.effectivenessModifier());
        assertEquals(0.85f, MoraleState.DISGRUNTLED.effectivenessModifier());
        assertEquals(0.7f, MoraleState.MUTINOUS.effectivenessModifier());
    }
}
