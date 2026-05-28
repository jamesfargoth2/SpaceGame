package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.NPCRole;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.crew.CrewRank;
import com.galacticodyssey.npc.data.*;
import com.galacticodyssey.npc.events.RecruitmentOpenedEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class CandidatePoolSystem extends EntitySystem {

    private final EventBus eventBus;
    private final NpcGenerator generator;
    private final RecruitmentDataRegistry recruitRegistry;
    private static final NPCRole[] CREW_ROLES;
    static {
        java.util.List<NPCRole> roles = new java.util.ArrayList<>();
        for (NPCRole r : NPCRole.values()) {
            if (r.isCrewRole()) roles.add(r);
        }
        CREW_ROLES = roles.toArray(new NPCRole[0]);
    }

    private final Set<String> activeStations = new HashSet<>();
    private Engine engine;

    public CandidatePoolSystem(EventBus eventBus, NpcGenerator generator,
                                RecruitmentDataRegistry recruitRegistry) {
        super(0);
        this.eventBus = eventBus;
        this.generator = generator;
        this.recruitRegistry = recruitRegistry;
        eventBus.subscribe(RecruitmentOpenedEvent.class, event -> onRecruitmentOpened(event));
    }

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
    }

    private void onRecruitmentOpened(RecruitmentOpenedEvent event) {
        if (activeStations.contains(event.stationId)) {
            return;
        }

        CantinaLayoutDefinition layout = recruitRegistry.getLayout(event.stationId);
        if (layout == null) {
            return;
        }

        activeStations.add(event.stationId);
        generateCandidates(event.stationId, layout);
    }

    private void generateCandidates(String stationId, CantinaLayoutDefinition layout) {
        int count = Math.min(layout.capacity, layout.seats.size());
        List<RecruitConditionDefinition> allConditions = recruitRegistry.getAllConditions();

        for (int i = 0; i < count; i++) {
            long seed = stationId.hashCode() ^ (long) i ^ System.nanoTime();
            Entity npc = generator.generate(engine, seed);

            NpcStatsComponent stats = npc.getComponent(NpcStatsComponent.class);
            NpcIdentityComponent identity = npc.getComponent(NpcIdentityComponent.class);

            Random roleRng = new Random(seed ^ 0xCAFE);
            NPCRole role = CREW_ROLES[roleRng.nextInt(CREW_ROLES.length)];
            identity.role = role;
            identity.recruitable = true;

            RecruitableComponent rc = new RecruitableComponent();
            float baseWage = CrewRank.RECRUIT.baseWage;
            float statAvg = (stats.accuracy + stats.repair + stats.medical +
                             stats.piloting + stats.science + stats.combat +
                             stats.persuasion + stats.stealth) / 8f;
            float qualityMod = 1f + (statAvg - 50f) / 100f;
            Random rng = new Random(seed);
            rc.askingWageMin = baseWage * qualityMod * (0.9f + rng.nextFloat() * 0.1f);
            rc.askingWageMax = rc.askingWageMin * (1.1f + rng.nextFloat() * 0.1f);
            rc.dialogTreeId = "recruitment_standard";
            rc.interactionState = RecruitInteractionState.UNMET;

            List<StatType> topStats = StatType.getTopN(stats, 2);
            rc.revealedStats.addAll(topStats);

            rc.hookLine = generateHookLine(identity, rng);

            rollConditions(rc, allConditions, rng);

            npc.add(rc);

            CantinaSeatComponent seat = new CantinaSeatComponent();
            CantinaSeatDefinition seatDef = layout.seats.get(i);
            seat.seatId = seatDef.id;
            seat.sceneX = seatDef.x;
            seat.sceneY = seatDef.y;
            npc.add(seat);
        }
    }

    private String generateHookLine(NpcIdentityComponent identity, Random rng) {
        String[] hooks = {
            "Looking for work. Got skills.",
            "Need a ship. You need crew. Simple.",
            "Heard you're hiring. I'm available.",
            "I can handle myself. Try me.",
            "Don't let appearances fool you."
        };
        return hooks[rng.nextInt(hooks.length)];
    }

    private void rollConditions(RecruitableComponent rc,
                                 List<RecruitConditionDefinition> allConditions, Random rng) {
        float roll = rng.nextFloat();
        int conditionCount;
        if (roll < 0.60f) conditionCount = 0;
        else if (roll < 0.90f) conditionCount = 1;
        else conditionCount = 2;

        for (int c = 0; c < conditionCount && !allConditions.isEmpty(); c++) {
            float totalWeight = 0f;
            for (RecruitConditionDefinition def : allConditions) {
                totalWeight += def.weight;
            }
            float pick = rng.nextFloat() * totalWeight;
            float acc = 0f;
            for (RecruitConditionDefinition def : allConditions) {
                acc += def.weight;
                if (acc >= pick) {
                    rc.conditions.add(new RecruitCondition(def.type, def.targetId, def.description));
                    break;
                }
            }
        }
    }

    public void clearStation(String stationId) {
        activeStations.remove(stationId);
        if (engine == null) return;

        ImmutableArray<Entity> candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());
        for (int i = candidates.size() - 1; i >= 0; i--) {
            engine.removeEntity(candidates.get(i));
        }
    }
}
