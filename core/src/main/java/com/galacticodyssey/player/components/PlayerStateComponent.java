package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class PlayerStateComponent implements Component {
    public enum PlayerMode {
        ON_FOOT_EXTERIOR,
        ON_FOOT_INTERIOR,
        PILOTING
    }

    public PlayerMode currentMode = PlayerMode.ON_FOOT_EXTERIOR;
    public Entity currentShip;
    public Entity interactionTarget;
}
