package com.galacticodyssey.galaxy.anomaly;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AnomalyPlacerTest {

    private final AnomalyPlacer placer = new AnomalyPlacer();

    @Test
    void deterministic() {
        List<AnomalyData> a = placer.place(10000, 42L, new Random(42L));
        List<AnomalyData> b = placer.place(10000, 42L, new Random(42L));

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).type, b.get(i).type);
            assertEquals(a.get(i).posX, b.get(i).posX, 1e-6);
            assertEquals(a.get(i).posY, b.get(i).posY, 1e-6);
            assertEquals(a.get(i).id, b.get(i).id);
        }
    }

    @Test
    void frequencyDistribution() {
        List<AnomalyData> anomalies = placer.place(50000, 42L, new Random(42L));

        long jumpPoints = anomalies.stream().filter(a -> a.type == AnomalyType.JUMP_POINT).count();
        long wormholes = anomalies.stream().filter(a -> a.type == AnomalyType.WORMHOLE).count();

        assertTrue(jumpPoints > wormholes,
                "JUMP_POINTs (" + jumpPoints + ") should outnumber WORMHOLEs (" + wormholes + ")");
    }

    @Test
    void wormholesArePaired() {
        List<AnomalyData> anomalies = placer.place(10000, 42L, new Random(42L));

        List<AnomalyData> wormholes = anomalies.stream()
                .filter(a -> a.type == AnomalyType.WORMHOLE)
                .collect(java.util.stream.Collectors.toList());

        // Every wormhole should have a partner
        for (AnomalyData wh : wormholes) {
            assertTrue(wh.partnerAnomalyId > 0,
                    "Wormhole " + wh.id + " should have a partner");
            boolean partnerExists = wormholes.stream()
                    .anyMatch(other -> other.id == wh.partnerAnomalyId);
            assertTrue(partnerExists,
                    "Partner " + wh.partnerAnomalyId + " of wormhole " + wh.id + " not found");
        }

        // Wormholes should come in pairs (even count)
        assertEquals(0, wormholes.size() % 2,
                "Wormholes should come in pairs, but count is " + wormholes.size());
    }

    @Test
    void rareTypesAreScarce() {
        List<AnomalyData> anomalies = placer.place(50000, 42L, new Random(42L));

        long megastructures = anomalies.stream()
                .filter(a -> a.type == AnomalyType.MEGASTRUCTURE_REMNANT).count();
        long ionStorms = anomalies.stream()
                .filter(a -> a.type == AnomalyType.ION_STORM).count();

        assertTrue(megastructures < ionStorms,
                "MEGASTRUCTURE_REMNANT (" + megastructures + ") should be rarer than ION_STORM (" + ionStorms + ")");
    }
}
