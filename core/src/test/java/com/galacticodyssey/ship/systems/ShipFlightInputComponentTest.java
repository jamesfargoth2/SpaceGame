package com.galacticodyssey.ship.systems;

import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipFlightInputComponentTest {

    @Test
    void defaultStateIsNeutral() {
        ShipFlightInputComponent input = new ShipFlightInputComponent();
        assertEquals(0f, input.throttle);
        assertEquals(0f, input.strafe);
        assertEquals(0f, input.verticalThrust);
        assertEquals(0f, input.pitchInput);
        assertEquals(0f, input.yawInput);
        assertEquals(0f, input.rollInput);
        assertFalse(input.fireGroup[0]);
        assertFalse(input.fireHeld[0]);
        assertFalse(input.targetLockPressed);
        assertFalse(input.nextTargetPressed);
        assertFalse(input.prevTargetPressed);
        assertFalse(input.cameraTogglePressed);
        assertEquals(0f, input.scrollDelta);
    }

    @Test
    void fireGroupsHaveFourSlots() {
        ShipFlightInputComponent input = new ShipFlightInputComponent();
        assertEquals(4, input.fireGroup.length);
        assertEquals(4, input.fireHeld.length);
    }
}
