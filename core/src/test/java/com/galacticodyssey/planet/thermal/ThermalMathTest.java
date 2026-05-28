package com.galacticodyssey.planet.thermal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalMathTest {

    @Test
    void radiativeCoolingIsPositiveWhenHotterThanAmbient() {
        float w = ThermalMath.radiativeCooling(1000f, 300f, 2f, 0.9f);
        assertTrue(w > 0f, "hot body should radiate net heat out");
    }

    @Test
    void radiativeCoolingIsNegativeWhenColderThanAmbient() {
        // Net heat flows IN (negative "cooling") when the body is colder than ambient.
        float w = ThermalMath.radiativeCooling(200f, 300f, 2f, 0.9f);
        assertTrue(w < 0f);
    }

    @Test
    void conductionPullsTowardAmbient() {
        assertTrue(ThermalMath.conduction(400f, 300f, 2f) > 0f);  // hot -> loses heat
        assertTrue(ThermalMath.conduction(200f, 300f, 2f) < 0f);  // cold -> gains heat
    }
}
