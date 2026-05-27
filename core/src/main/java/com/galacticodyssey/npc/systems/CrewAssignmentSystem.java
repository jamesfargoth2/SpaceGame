package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import com.galacticodyssey.npc.crew.CrewAssignmentComponent;
import com.galacticodyssey.npc.crew.CrewMemberComponent;

public class CrewAssignmentSystem extends EntitySystem {

    public static final int PRIORITY = 21;
    private static final float TICK_INTERVAL = 1.0f;

    private static final Family STATION_FAMILY =
        Family.all(CrewAssignmentComponent.class).get();
    private static final ComponentMapper<CrewAssignmentComponent> ASSIGN_M =
        ComponentMapper.getFor(CrewAssignmentComponent.class);
    private static final ComponentMapper<CrewMemberComponent> CREW_M =
        ComponentMapper.getFor(CrewMemberComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> stations;
    private float timeSinceLastTick;

    public CrewAssignmentSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        stations = engine.getEntitiesFor(STATION_FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        stations = null;
    }

    @Override
    public void update(float deltaTime) {
        timeSinceLastTick += deltaTime;
        if (timeSinceLastTick < TICK_INTERVAL) return;
        timeSinceLastTick = 0f;

        if (stations == null) return;

        for (int i = 0, n = stations.size(); i < n; i++) {
            Entity stationEntity = stations.get(i);
            CrewAssignmentComponent assignment = ASSIGN_M.get(stationEntity);

            Entity crewEntity = assignment.assignedCrew;
            if (crewEntity == null) {
                assignment.effectivenessMultiplier = 0f;
                continue;
            }

            CrewMemberComponent crew = CREW_M.get(crewEntity);
            NpcStatsComponent stats = STATS_M.get(crewEntity);
            if (crew == null || stats == null) {
                assignment.effectivenessMultiplier = 0f;
                continue;
            }

            float baseStat = crew.role.getRelevantStat(stats) / 100f;
            float rankBonus = crew.rank.ordinal() * 0.05f;
            float moraleMod = crew.moraleState.effectivenessModifier();
            assignment.effectivenessMultiplier = (baseStat + rankBonus) * moraleMod;
        }
    }
}
