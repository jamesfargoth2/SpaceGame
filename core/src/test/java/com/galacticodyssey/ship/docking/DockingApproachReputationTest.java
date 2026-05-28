package com.galacticodyssey.ship.docking;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.galaxy.faction.ReputationConfigData;
import com.galacticodyssey.galaxy.faction.ReputationManager;
import com.galacticodyssey.galaxy.faction.ReputationTier;
import com.galacticodyssey.player.components.PlayerReputationComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.ship.docking.DockingStateComponent.DockingPhase;
import com.galacticodyssey.ship.docking.events.DockingDeniedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DockingApproachReputationTest {

    private EventBus eventBus;
    private DockingApproachSystem system;
    private ReputationManager reputationManager;
    private Engine engine;
    private PlayerReputationComponent repComp;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        system = new DockingApproachSystem(eventBus);

        reputationManager = new ReputationManager(eventBus, new ReputationConfigData(), new HashMap<>());
        Entity player = new Entity();
        repComp = new PlayerReputationComponent();
        player.add(repComp);
        player.add(new PlayerStatsComponent());
        reputationManager.setPlayerEntity(player);

        system.setReputationManager(reputationManager);

        engine = new Engine();
        engine.addSystem(system);
    }

    private Entity createChaser(Entity target, DockingPhase phase) {
        Entity chaser = new Entity();
        DockingStateComponent state = new DockingStateComponent();
        state.dockingPhase = phase;
        state.targetEntity = target;
        chaser.add(state);

        DockingPortComponent port = new DockingPortComponent();
        chaser.add(port);

        TransformComponent transform = new TransformComponent();
        transform.position.set(0, 0, 10);
        chaser.add(transform);

        return chaser;
    }

    private Entity createFactionStation(String stationId, String factionId) {
        Entity station = new Entity();
        TransformComponent transform = new TransformComponent();
        station.add(transform);
        DockingPortComponent port = new DockingPortComponent();
        station.add(port);
        MarketComponent market = new MarketComponent();
        market.stationId = stationId;
        market.ownerFactionId = factionId;
        station.add(market);
        return station;
    }

    @Test
    void hostileFactionDeniedDocking() {
        Entity station = createFactionStation("station_1", "fed");
        engine.addEntity(station);

        Entity chaser = createChaser(station, DockingPhase.FAR_APPROACH);
        engine.addEntity(chaser);

        // Standing below -50 → HOSTILE
        repComp.standings.put("fed", -75f);
        assertEquals(ReputationTier.HOSTILE, reputationManager.getTier("fed"));

        List<DockingDeniedEvent> events = new ArrayList<>();
        eventBus.subscribe(DockingDeniedEvent.class, events::add);

        engine.update(0.016f);

        DockingStateComponent state = chaser.getComponent(DockingStateComponent.class);
        assertEquals(DockingPhase.NONE, state.dockingPhase);
        assertEquals(1, events.size());
        assertEquals("station_1", events.get(0).stationId);
        assertEquals("fed", events.get(0).factionId);
    }

    @Test
    void friendlyFactionAllowedDocking() {
        Entity station = createFactionStation("station_1", "fed");
        engine.addEntity(station);

        Entity chaser = createChaser(station, DockingPhase.FAR_APPROACH);
        engine.addEntity(chaser);

        // Standing of 30 → FRIENDLY
        repComp.standings.put("fed", 30f);
        assertEquals(ReputationTier.FRIENDLY, reputationManager.getTier("fed"));

        List<DockingDeniedEvent> events = new ArrayList<>();
        eventBus.subscribe(DockingDeniedEvent.class, events::add);

        engine.update(0.016f);

        DockingStateComponent state = chaser.getComponent(DockingStateComponent.class);
        assertEquals(DockingPhase.FAR_APPROACH, state.dockingPhase);
        assertTrue(events.isEmpty());
    }

    @Test
    void nonFactionStationAlwaysAllowed() {
        Entity station = new Entity();
        station.add(new TransformComponent());
        station.add(new DockingPortComponent());
        engine.addEntity(station);

        Entity chaser = createChaser(station, DockingPhase.FAR_APPROACH);
        engine.addEntity(chaser);

        List<DockingDeniedEvent> events = new ArrayList<>();
        eventBus.subscribe(DockingDeniedEvent.class, events::add);

        engine.update(0.016f);

        DockingStateComponent state = chaser.getComponent(DockingStateComponent.class);
        assertEquals(DockingPhase.FAR_APPROACH, state.dockingPhase);
        assertTrue(events.isEmpty());
    }
}
