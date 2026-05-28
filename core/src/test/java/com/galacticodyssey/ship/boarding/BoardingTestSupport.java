package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;

/** Shared headless fixtures for boarding tests. */
public final class BoardingTestSupport {
    private BoardingTestSupport() {}

    /** A player entity piloting {@code ship}, with the given board-input state. */
    public static Entity pilotingPlayer(Engine engine, Entity ship, boolean boardPressed) {
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = ship;
        player.add(state);
        PlayerInputComponent input = new PlayerInputComponent();
        input.boardPressed = boardPressed;
        player.add(input);
        engine.addEntity(player);
        return player;
    }
}
