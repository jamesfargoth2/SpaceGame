package com.galacticodyssey.player.systems;

import com.galacticodyssey.core.events.PlayerPostureChangedEvent;
import com.galacticodyssey.player.PostureType;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.persistence.snapshots.MovementStateSnapshot;
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

    @Test
    void isProneDefaultsFalse() {
        MovementStateComponent state = new MovementStateComponent();
        assertFalse(state.isProne);
    }

    @Test
    void snapshotRoundTripsIsProne() {
        MovementStateComponent state = new MovementStateComponent();
        state.isProne = true;
        MovementStateSnapshot snap = state.takeSnapshot();
        assertTrue(snap.isProne);

        MovementStateComponent restored = new MovementStateComponent();
        restored.restoreFromSnapshot(snap);
        assertTrue(restored.isProne);
    }
}
