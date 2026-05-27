package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.events.PlayerStartPilotingEvent;
import com.galacticodyssey.core.events.PlayerStopPilotingEvent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ui.events.CockpitHUDShowEvent;
import com.galacticodyssey.ui.events.CockpitHUDHideEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PilotTransitionSystemTest {

    @Test
    void enterPilotingAddsFlightInputComponent() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        PilotTransitionSystem system = new PilotTransitionSystem(eventBus);
        engine.addSystem(system);

        Entity player = createPlayer();
        Entity ship = new Entity();
        engine.addEntity(player);
        engine.addEntity(ship);

        assertNull(player.getComponent(ShipFlightInputComponent.class));

        eventBus.publish(new PlayerStartPilotingEvent(player, ship));

        assertNotNull(player.getComponent(ShipFlightInputComponent.class));
    }

    @Test
    void enterPilotingPublishesCockpitHUDShow() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        PilotTransitionSystem system = new PilotTransitionSystem(eventBus);
        engine.addSystem(system);

        Entity player = createPlayer();
        Entity ship = new Entity();
        engine.addEntity(player);

        AtomicBoolean hudShown = new AtomicBoolean(false);
        eventBus.subscribe(CockpitHUDShowEvent.class, e -> hudShown.set(true));

        eventBus.publish(new PlayerStartPilotingEvent(player, ship));

        assertTrue(hudShown.get());
    }

    @Test
    void exitPilotingRemovesFlightInputComponent() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        PilotTransitionSystem system = new PilotTransitionSystem(eventBus);
        engine.addSystem(system);

        Entity player = createPlayer();
        Entity ship = new Entity();
        engine.addEntity(player);

        eventBus.publish(new PlayerStartPilotingEvent(player, ship));
        assertNotNull(player.getComponent(ShipFlightInputComponent.class));

        eventBus.publish(new PlayerStopPilotingEvent(player, ship));
        assertNull(player.getComponent(ShipFlightInputComponent.class));
    }

    @Test
    void exitPilotingPublishesCockpitHUDHide() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        PilotTransitionSystem system = new PilotTransitionSystem(eventBus);
        engine.addSystem(system);

        Entity player = createPlayer();
        Entity ship = new Entity();
        engine.addEntity(player);

        eventBus.publish(new PlayerStartPilotingEvent(player, ship));

        AtomicBoolean hudHidden = new AtomicBoolean(false);
        eventBus.subscribe(CockpitHUDHideEvent.class, e -> hudHidden.set(true));

        eventBus.publish(new PlayerStopPilotingEvent(player, ship));

        assertTrue(hudHidden.get());
    }

    private Entity createPlayer() {
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new PlayerInputComponent());
        player.add(new PlayerStateComponent());
        return player;
    }
}
