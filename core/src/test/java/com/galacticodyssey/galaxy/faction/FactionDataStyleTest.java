package com.galacticodyssey.galaxy.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FactionDataStyleTest {

    @Test
    void legacyConstructorLeavesStyleIdNull() {
        FactionData f = new FactionData("f1", "N", 0, 0, 0, 0.5f, 0.5f,
                FactionEthos.FEDERATION, 0.5f, 0.5f, 0.5f, 300f, "HUMAN_COLONY");
        assertNull(f.styleId);
    }

    @Test
    void styleConstructorStoresStyleId() {
        FactionData f = new FactionData("f1", "N", 0, 0, 0, 0.5f, 0.5f,
                FactionEthos.MILITARIST, 0.5f, 0.5f, 0.5f, 300f, "HUMAN_COLONY", "vaun");
        assertEquals("vaun", f.styleId);
    }
}
