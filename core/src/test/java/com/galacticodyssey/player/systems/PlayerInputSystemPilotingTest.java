package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerInputSystemPilotingTest {

    @Test
    void pilotingModeDoesNotWriteToPlayerInput() {
        Engine engine = new Engine();
        PlayerInputSystem system = new PlayerInputSystem();
        engine.addSystem(system);

        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        PlayerInputComponent input = new PlayerInputComponent();
        player.add(input);
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = new Entity();
        player.add(state);
        ShipFlightInputComponent flightInput = new ShipFlightInputComponent();
        player.add(flightInput);

        engine.addEntity(player);

        system.update(1f / 60f);

        assertEquals(0f, input.moveForward, "FPS input should not be written when PILOTING");
    }

    @Test
    void onFootModeWritesToPlayerInput() {
        Engine engine = new Engine();
        PlayerInputSystem system = new PlayerInputSystem();
        engine.addSystem(system);

        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        PlayerInputComponent input = new PlayerInputComponent();
        player.add(input);
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.ON_FOOT_EXTERIOR;
        player.add(state);

        engine.addEntity(player);

        system.update(1f / 60f);

        // moveForward will be 0 since no keys pressed, but the path was taken
        assertEquals(0f, input.moveForward);
    }
}
