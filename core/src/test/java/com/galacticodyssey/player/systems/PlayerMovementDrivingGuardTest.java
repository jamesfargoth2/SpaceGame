package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerMovementDrivingGuardTest {
    @Test
    void drivingModeExists() {
        assertNotNull(PlayerMode.valueOf("DRIVING"));
    }

    @Test
    void stateHoldsCurrentVehicle() {
        PlayerStateComponent s = new PlayerStateComponent();
        Entity v = new Entity();
        s.currentVehicle = v;
        assertSame(v, s.currentVehicle);
    }
}
