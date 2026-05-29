package com.galacticodyssey.ship.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PilotArchetypeRegistryTest {

    private static final String JSON = "["
        + "{\"id\":\"rookie\",\"reactionTimeSec\":0.6,\"aimErrorDeg\":8.0,\"aggression\":0.4,"
        + "\"evadeHealthThreshold\":0.5,\"preferredEngageRange\":400.0,\"overshootExtendDist\":150.0,"
        + "\"throttleDiscipline\":0.4,\"usesMissiles\":false},"
        + "{\"id\":\"ace\",\"reactionTimeSec\":0.12,\"aimErrorDeg\":1.5,\"aggression\":0.9,"
        + "\"evadeHealthThreshold\":0.25,\"preferredEngageRange\":300.0,\"overshootExtendDist\":120.0,"
        + "\"throttleDiscipline\":0.9,\"usesMissiles\":true,\"aggroRange\":2500.0}"
        + "]";

    @Test
    void parsesArchetypesById() {
        PilotArchetypeRegistry reg = new PilotArchetypeRegistry();
        reg.parse(JSON);

        PilotArchetype rookie = reg.get("rookie");
        assertNotNull(rookie);
        assertEquals(8.0f, rookie.aimErrorDeg, 1e-4);
        assertFalse(rookie.usesMissiles);

        PilotArchetype ace = reg.get("ace");
        assertEquals(0.12f, ace.reactionTimeSec, 1e-4);
        assertTrue(ace.usesMissiles);
        assertTrue(ace.aimErrorDeg < rookie.aimErrorDeg);
        assertEquals(2500.0f, ace.aggroRange, 1e-4);
    }

    @Test
    void unknownIdReturnsNull() {
        PilotArchetypeRegistry reg = new PilotArchetypeRegistry();
        reg.parse(JSON);
        assertNull(reg.get("does_not_exist"));
    }
}
