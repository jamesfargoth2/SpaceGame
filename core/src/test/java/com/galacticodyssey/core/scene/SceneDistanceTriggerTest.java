package com.galacticodyssey.core.scene;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneDistanceTriggerTest {

    // enterRadius 1000 < exitRadius 1500 (hysteresis band)
    private final SceneDistanceTrigger trigger = new SceneDistanceTrigger(1000f, 1500f);

    @Test
    void entersOnlyWhenInsideEnterRadius() {
        // currently outside, distance still within the band -> must NOT enter yet
        assertFalse(trigger.shouldBeInside(false, 1200f));
        // inside enter radius -> enter
        assertTrue(trigger.shouldBeInside(false, 900f));
    }

    @Test
    void staysInsideThroughHysteresisBand() {
        // currently inside, distance in the band -> stay inside (no thrash)
        assertTrue(trigger.shouldBeInside(true, 1200f));
        // only leaves once beyond exit radius
        assertFalse(trigger.shouldBeInside(true, 1600f));
    }

    @Test
    void rejectsNonHysteresisRadii() {
        assertThrows(IllegalArgumentException.class, () -> new SceneDistanceTrigger(1500f, 1000f));
    }
}
