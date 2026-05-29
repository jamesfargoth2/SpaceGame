package com.galacticodyssey.player.systems;

import com.galacticodyssey.core.events.PlayerPostureChangedEvent;
import com.galacticodyssey.player.PostureType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProneStateTest {

    @Test
    void eventHoldsPostures() {
        PlayerPostureChangedEvent event =
            new PlayerPostureChangedEvent(PostureType.STANDING, PostureType.PRONE);
        assertEquals(PostureType.STANDING, event.previous);
        assertEquals(PostureType.PRONE, event.next);
    }
}
