package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.NPCRole;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentScreenSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private List<CrewMemberHiredEvent> hiredEvents;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        hiredEvents = new ArrayList<>();
        eventBus.subscribe(CrewMemberHiredEvent.class, hiredEvents::add);
    }

    @Test
    void hiringSequenceAttachesCrewComponent() {
        Entity npc = new Entity();
        NpcIdentityComponent identity = new NpcIdentityComponent();
        identity.name = "Threx-Ka";
        identity.species = "veloxi";
        identity.role = NPCRole.ENGINEER;
        npc.add(identity);

        NpcStatsComponent stats = new NpcStatsComponent();
        stats.repair = 85f;
        npc.add(stats);

        RecruitableComponent rc = new RecruitableComponent();
        rc.askingWageMin = 500f;
        rc.askingWageMax = 650f;
        rc.negotiatedWage = 580f;
        npc.add(rc);

        CantinaSeatComponent seat = new CantinaSeatComponent();
        seat.seatId = "test";
        npc.add(seat);

        engine.addEntity(npc);

        RecruitmentScreenSystem.executeHire(npc, eventBus);

        assertNotNull(npc.getComponent(CrewMemberComponent.class));
        CrewMemberComponent crew = npc.getComponent(CrewMemberComponent.class);
        assertEquals(580f, crew.wage);
        assertEquals(75f, crew.morale);
        assertEquals(50f, crew.loyalty);

        assertNull(npc.getComponent(RecruitableComponent.class));
        assertNull(npc.getComponent(CantinaSeatComponent.class));

        assertEquals(1, hiredEvents.size());
        assertSame(npc, hiredEvents.get(0).npc);
    }

    @Test
    void hiringUsesMaxWageWhenNotNegotiated() {
        Entity npc = new Entity();
        NpcIdentityComponent identity = new NpcIdentityComponent();
        identity.name = "Test";
        identity.role = NPCRole.GUNNER;
        npc.add(identity);
        npc.add(new NpcStatsComponent());

        RecruitableComponent rc = new RecruitableComponent();
        rc.askingWageMin = 400f;
        rc.askingWageMax = 500f;
        rc.negotiatedWage = -1f;
        npc.add(rc);
        npc.add(new CantinaSeatComponent());

        engine.addEntity(npc);

        RecruitmentScreenSystem.executeHire(npc, eventBus);

        CrewMemberComponent crew = npc.getComponent(CrewMemberComponent.class);
        assertEquals(500f, crew.wage);
    }
}
