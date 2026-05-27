package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import com.galacticodyssey.npc.crew.CrewAssignmentComponent;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRank;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.crew.MoraleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrewAssignmentSystemTest {

    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new CrewAssignmentSystem(eventBus));
    }

    @Test
    void unassignedStationHasZeroEffectiveness() {
        Entity station = createStation(CrewRole.ENGINEER);
        engine.addEntity(station);

        engine.update(1.1f);

        assertEquals(0f, station.getComponent(CrewAssignmentComponent.class).effectivenessMultiplier);
    }

    @Test
    void assignedCrewComputesEffectiveness() {
        Entity crew = createCrew(CrewRole.ENGINEER, CrewRank.RECRUIT, 80f, 75f);
        Entity station = createStation(CrewRole.ENGINEER);
        assignCrewToStation(crew, station);

        engine.addEntity(crew);
        engine.addEntity(station);
        engine.update(1.1f);

        CrewAssignmentComponent assignment = station.getComponent(CrewAssignmentComponent.class);
        // baseStat = 80/100 = 0.8, rankBonus = 0 * 0.05 = 0.0
        // moraleMod = GRUMBLING (75 morale) = 1.0
        // effectiveness = (0.8 + 0.0) * 1.0 = 0.8
        assertEquals(0.8f, assignment.effectivenessMultiplier, 0.001f);
    }

    @Test
    void rankBonusIncreasesEffectiveness() {
        Entity crew = createCrew(CrewRole.GUNNER, CrewRank.VETERAN, 60f, 75f);
        Entity station = createStation(CrewRole.GUNNER);
        assignCrewToStation(crew, station);

        engine.addEntity(crew);
        engine.addEntity(station);
        engine.update(1.1f);

        CrewAssignmentComponent assignment = station.getComponent(CrewAssignmentComponent.class);
        // baseStat = 60/100 = 0.6, rankBonus = 3 * 0.05 = 0.15
        // moraleMod = GRUMBLING = 1.0
        // effectiveness = (0.6 + 0.15) * 1.0 = 0.75
        assertEquals(0.75f, assignment.effectivenessMultiplier, 0.001f);
    }

    @Test
    void contentMoraleGivesBonus() {
        Entity crew = createCrew(CrewRole.MEDIC, CrewRank.RECRUIT, 50f, 90f);
        Entity station = createStation(CrewRole.MEDIC);
        assignCrewToStation(crew, station);

        engine.addEntity(crew);
        engine.addEntity(station);
        engine.update(1.1f);

        CrewAssignmentComponent assignment = station.getComponent(CrewAssignmentComponent.class);
        // baseStat = 0.5, rankBonus = 0, moraleMod = CONTENT = 1.1
        // effectiveness = 0.5 * 1.1 = 0.55
        assertEquals(0.55f, assignment.effectivenessMultiplier, 0.001f);
    }

    @Test
    void mutinousMoralePenalizesEffectiveness() {
        Entity crew = createCrew(CrewRole.PILOT, CrewRank.CREWMAN, 70f, 10f);
        Entity station = createStation(CrewRole.PILOT);
        assignCrewToStation(crew, station);

        engine.addEntity(crew);
        engine.addEntity(station);
        engine.update(1.1f);

        CrewAssignmentComponent assignment = station.getComponent(CrewAssignmentComponent.class);
        // baseStat = 0.7, rankBonus = 1 * 0.05 = 0.05, moraleMod = MUTINOUS = 0.7
        // effectiveness = (0.7 + 0.05) * 0.7 = 0.525
        assertEquals(0.525f, assignment.effectivenessMultiplier, 0.001f);
    }

    @Test
    void tickIntervalThrottlesUpdate() {
        Entity crew = createCrew(CrewRole.ENGINEER, CrewRank.RECRUIT, 80f, 75f);
        Entity station = createStation(CrewRole.ENGINEER);
        assignCrewToStation(crew, station);

        engine.addEntity(crew);
        engine.addEntity(station);

        // First update at 0.5s — under 1s threshold, should not compute yet
        engine.update(0.5f);
        assertEquals(0f, station.getComponent(CrewAssignmentComponent.class).effectivenessMultiplier);

        // Second update pushes past 1s
        engine.update(0.6f);
        assertTrue(station.getComponent(CrewAssignmentComponent.class).effectivenessMultiplier > 0f);
    }

    private Entity createCrew(CrewRole role, CrewRank rank, float relevantStat, float morale) {
        Entity entity = new Entity();
        NpcStatsComponent stats = new NpcStatsComponent();
        switch (role) {
            case PILOT:    stats.piloting = relevantStat; break;
            case GUNNER:   stats.accuracy = relevantStat; break;
            case ENGINEER: stats.repair = relevantStat; break;
            case MEDIC:    stats.medical = relevantStat; break;
            case MARINE:   stats.combat = relevantStat; break;
            case SCIENTIST: stats.science = relevantStat; break;
            case NAVIGATOR: stats.piloting = relevantStat; break;
        }
        entity.add(stats);

        CrewMemberComponent crew = new CrewMemberComponent();
        crew.role = role;
        crew.rank = rank;
        crew.morale = morale;
        crew.moraleState = MoraleState.fromMorale(morale);
        entity.add(crew);

        return entity;
    }

    private Entity createStation(CrewRole role) {
        Entity entity = new Entity();
        CrewAssignmentComponent assignment = new CrewAssignmentComponent();
        assignment.requiredRole = role;
        entity.add(assignment);
        return entity;
    }

    private void assignCrewToStation(Entity crew, Entity station) {
        crew.getComponent(CrewMemberComponent.class).assignedStation = station;
        station.getComponent(CrewAssignmentComponent.class).assignedCrew = crew;
    }
}
