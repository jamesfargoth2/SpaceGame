package com.galacticodyssey.combat.fleet;

import com.galacticodyssey.combat.fleet.data.FleetDoctrine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetDoctrineTest {

    @Test
    void aggressiveHasHigherRetreatThreshold() {
        assertTrue(FleetDoctrine.AGGRESSIVE.retreatThreshold > FleetDoctrine.DEFENSIVE.retreatThreshold);
    }

    @Test
    void aggressiveDealsBonusDamage() {
        assertEquals(1.2f, FleetDoctrine.AGGRESSIVE.damageDealtModifier, 0.001f);
        assertEquals(1.2f, FleetDoctrine.AGGRESSIVE.damageTakenModifier, 0.001f);
    }

    @Test
    void defensiveReducesDamage() {
        assertEquals(0.8f, FleetDoctrine.DEFENSIVE.damageDealtModifier, 0.001f);
        assertEquals(0.8f, FleetDoctrine.DEFENSIVE.damageTakenModifier, 0.001f);
    }

    @Test
    void evasiveRequiresSuperiority() {
        assertTrue(FleetDoctrine.EVASIVE.engageStrengthRatio > 1.0f);
    }
}
