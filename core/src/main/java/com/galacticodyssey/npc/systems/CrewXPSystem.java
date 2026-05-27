package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRank;
import com.galacticodyssey.npc.events.CrewPromotedEvent;

public class CrewXPSystem extends EntitySystem {

    public static final int PRIORITY = 24;

    private static final ComponentMapper<CrewMemberComponent> CREW_M =
        ComponentMapper.getFor(CrewMemberComponent.class);

    private final EventBus eventBus;

    public CrewXPSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    public void awardXP(Entity crewEntity, float amount) {
        CrewMemberComponent crew = CREW_M.get(crewEntity);
        if (crew == null) return;
        crew.xp += amount;
    }

    public boolean isPromotionEligible(Entity crewEntity) {
        CrewMemberComponent crew = CREW_M.get(crewEntity);
        if (crew == null) return false;
        CrewRank next = crew.rank.nextRank();
        if (next == null) return false;
        return crew.xp >= next.xpThreshold;
    }

    public boolean promote(Entity crewEntity) {
        CrewMemberComponent crew = CREW_M.get(crewEntity);
        if (crew == null) return false;

        CrewRank next = crew.rank.nextRank();
        if (next == null) return false;
        if (crew.xp < next.xpThreshold) return false;

        CrewRank oldRank = crew.rank;
        crew.xp -= next.xpThreshold;
        crew.rank = next;
        crew.wage = next.baseWage;

        eventBus.publish(new CrewPromotedEvent(crewEntity, oldRank, next));
        return true;
    }

    @Override
    public void update(float deltaTime) {
        // XP awards and promotions are event-driven via public methods.
        // Future phases will subscribe to combat/mission events here.
    }
}
