package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.data.*;
import com.galacticodyssey.npc.events.RecruitmentOpenedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CandidatePoolSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private NpcDataRegistry npcRegistry;
    private RecruitmentDataRegistry recruitRegistry;
    private CandidatePoolSystem system;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        npcRegistry = new NpcDataRegistry();
        recruitRegistry = new RecruitmentDataRegistry();

        // Register minimal species/background/names data
        SpeciesDefinition human = new SpeciesDefinition();
        human.id = "human";
        human.name = "Human";
        human.portraitIds.add("portrait_human_01");
        npcRegistry.registerSpecies(human);

        BackgroundDefinition military = new BackgroundDefinition();
        military.id = "military";
        military.name = "Military";
        npcRegistry.registerBackground(military);

        npcRegistry.registerNames("human",
            java.util.List.of("Kira", "Orin", "Garak"),
            java.util.List.of("Voss", "Mael", "Durn"));

        // Register a cantina layout
        CantinaLayoutDefinition layout = new CantinaLayoutDefinition();
        layout.backgroundKey = "cantina_test";
        layout.capacity = 3;
        for (int i = 0; i < 3; i++) {
            CantinaSeatDefinition seat = new CantinaSeatDefinition();
            seat.id = "seat_" + i;
            seat.x = 0.2f + i * 0.3f;
            seat.y = 0.4f;
            layout.seats.add(seat);
        }
        layout.hiringBoardX = 0.5f;
        layout.hiringBoardY = 0.2f;
        recruitRegistry.registerLayout("test_station", layout);

        NpcGenerator generator = new NpcGenerator(npcRegistry);
        system = new CandidatePoolSystem(eventBus, generator, recruitRegistry);
        engine.addSystem(system);
    }

    @Test
    void generatesCorrectNumberOfCandidates() {
        eventBus.publish(new RecruitmentOpenedEvent("test_station"));

        var candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());

        assertEquals(3, candidates.size());
    }

    @Test
    void candidatesHaveRequiredComponents() {
        eventBus.publish(new RecruitmentOpenedEvent("test_station"));

        var candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());

        for (Entity e : candidates) {
            assertNotNull(e.getComponent(NpcIdentityComponent.class));
            assertNotNull(e.getComponent(NpcStatsComponent.class));
            assertNotNull(e.getComponent(RecruitableComponent.class));
            assertNotNull(e.getComponent(CantinaSeatComponent.class));

            RecruitableComponent rc = e.getComponent(RecruitableComponent.class);
            assertEquals(RecruitInteractionState.UNMET, rc.interactionState);
            assertTrue(rc.askingWageMin > 0);
            assertTrue(rc.askingWageMax >= rc.askingWageMin);
            assertNotNull(rc.hookLine);
            assertFalse(rc.revealedStats.isEmpty());
        }
    }

    @Test
    void doesNotRegenerateIfPoolAlreadyExists() {
        eventBus.publish(new RecruitmentOpenedEvent("test_station"));
        eventBus.publish(new RecruitmentOpenedEvent("test_station"));

        var candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());

        assertEquals(3, candidates.size());
    }

    @Test
    void noOpForUnknownStation() {
        eventBus.publish(new RecruitmentOpenedEvent("nonexistent"));

        var candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());

        assertEquals(0, candidates.size());
    }
}
