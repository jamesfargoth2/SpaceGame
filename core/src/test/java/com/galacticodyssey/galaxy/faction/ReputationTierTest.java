package com.galacticodyssey.galaxy.faction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ReputationTierTest {

    @ParameterizedTest
    @CsvSource({
        "-100, HOSTILE",
        "-75,  HOSTILE",
        "-50.1, HOSTILE",
        "-50,  UNFRIENDLY",
        "-25,  UNFRIENDLY",
        "-0.1, UNFRIENDLY",
        "0,    NEUTRAL",
        "10,   NEUTRAL",
        "24.9, NEUTRAL",
        "25,   FRIENDLY",
        "49.9, FRIENDLY",
        "50,   ALLIED",
        "74.9, ALLIED",
        "75,   HONORED",
        "99.9, HONORED",
        "100,  EXALTED"
    })
    void fromStandingReturnsTier(float standing, ReputationTier expected) {
        assertEquals(expected, ReputationTier.fromStanding(standing));
    }

    @Test
    void hostilePriceMultipliers() {
        assertEquals(0f, ReputationTier.HOSTILE.buyMultiplier);
        assertEquals(0f, ReputationTier.HOSTILE.sellMultiplier);
    }

    @Test
    void neutralPriceMultipliers() {
        assertEquals(1.0f, ReputationTier.NEUTRAL.buyMultiplier);
        assertEquals(1.0f, ReputationTier.NEUTRAL.sellMultiplier);
    }

    @Test
    void friendlyPriceMultipliers() {
        assertEquals(0.95f, ReputationTier.FRIENDLY.buyMultiplier);
        assertEquals(1.05f, ReputationTier.FRIENDLY.sellMultiplier);
    }

    @Test
    void exaltedPriceMultipliers() {
        assertEquals(0.80f, ReputationTier.EXALTED.buyMultiplier);
        assertEquals(1.20f, ReputationTier.EXALTED.sellMultiplier);
    }

    @Test
    void clampedStandingAbove100() {
        assertEquals(ReputationTier.EXALTED, ReputationTier.fromStanding(150f));
    }

    @Test
    void clampedStandingBelow100() {
        assertEquals(ReputationTier.HOSTILE, ReputationTier.fromStanding(-200f));
    }
}
