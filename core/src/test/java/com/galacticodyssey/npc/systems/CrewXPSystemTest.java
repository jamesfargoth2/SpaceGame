package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRank;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.events.CrewPromotedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrewXPSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private CrewXPSystem xpSystem;
    private final List<CrewPromotedEvent> promotedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        xpSystem = new CrewXPSystem(eventBus);
        engine.addSystem(xpSystem);
        eventBus.subscribe(CrewPromotedEvent.class, promotedEvents::add);
    }

    @Test
    void awardXPIncreasesCrewXP() {
        Entity crew = createCrew(CrewRole.ENGINEER, CrewRank.RECRUIT, 0f);
        engine.addEntity(crew);

        xpSystem.awardXP(crew, 50f);

        assertEquals(50f, crew.getComponent(CrewMemberComponent.class).xp);
    }

    @Test
    void awardXPAccumulatesOverMultipleCalls() {
        Entity crew = createCrew(CrewRole.GUNNER, CrewRank.RECRUIT, 0f);
        engine.addEntity(crew);

        xpSystem.awardXP(crew, 30f);
        xpSystem.awardXP(crew, 25f);

        assertEquals(55f, crew.getComponent(CrewMemberComponent.class).xp);
    }

    @Test
    void promoteSetsNextRankAndResetsXP() {
        Entity crew = createCrew(CrewRole.PILOT, CrewRank.RECRUIT, 150f);
        engine.addEntity(crew);

        boolean promoted = xpSystem.promote(crew);

        assertTrue(promoted);
        CrewMemberComponent cm = crew.getComponent(CrewMemberComponent.class);
        assertEquals(CrewRank.CREWMAN, cm.rank);
        assertEquals(50f, cm.xp, 0.001f);
        assertEquals(CrewRank.CREWMAN.baseWage, cm.wage);
    }

    @Test
    void promotePublishesEvent() {
        Entity crew = createCrew(CrewRole.MEDIC, CrewRank.RECRUIT, 150f);
        engine.addEntity(crew);

        xpSystem.promote(crew);

        assertEquals(1, promotedEvents.size());
        assertEquals(CrewRank.RECRUIT, promotedEvents.get(0).oldRank);
        assertEquals(CrewRank.CREWMAN, promotedEvents.get(0).newRank);
    }

    @Test
    void promoteFailsWhenXPBelowThreshold() {
        Entity crew = createCrew(CrewRole.ENGINEER, CrewRank.RECRUIT, 50f);
        engine.addEntity(crew);

        boolean promoted = xpSystem.promote(crew);

        assertFalse(promoted);
        assertEquals(CrewRank.RECRUIT, crew.getComponent(CrewMemberComponent.class).rank);
        assertTrue(promotedEvents.isEmpty());
    }

    @Test
    void promoteFailsAtMaxRank() {
        Entity crew = createCrew(CrewRole.MARINE, CrewRank.COMMANDER, 9999f);
        engine.addEntity(crew);

        boolean promoted = xpSystem.promote(crew);

        assertFalse(promoted);
        assertEquals(CrewRank.COMMANDER, crew.getComponent(CrewMemberComponent.class).rank);
    }

    @Test
    void isPromotionEligibleReturnsCorrectly() {
        Entity eligible = createCrew(CrewRole.GUNNER, CrewRank.RECRUIT, 100f);
        Entity notEligible = createCrew(CrewRole.GUNNER, CrewRank.RECRUIT, 50f);
        engine.addEntity(eligible);
        engine.addEntity(notEligible);

        assertTrue(xpSystem.isPromotionEligible(eligible));
        assertFalse(xpSystem.isPromotionEligible(notEligible));
    }

    @Test
    void promoteUpdatesWageToNewRankBaseWage() {
        Entity crew = createCrew(CrewRole.ENGINEER, CrewRank.CREWMAN, 350f);
        crew.getComponent(CrewMemberComponent.class).wage = CrewRank.CREWMAN.baseWage;
        engine.addEntity(crew);

        xpSystem.promote(crew);

        assertEquals(CrewRank.SPECIALIST.baseWage,
            crew.getComponent(CrewMemberComponent.class).wage);
    }

    private Entity createCrew(CrewRole role, CrewRank rank, float xp) {
        Entity entity = new Entity();
        NpcStatsComponent stats = new NpcStatsComponent();
        entity.add(stats);

        CrewMemberComponent crew = new CrewMemberComponent();
        crew.role = role;
        crew.rank = rank;
        crew.xp = xp;
        crew.wage = rank.baseWage;
        entity.add(crew);

        return entity;
    }
}
