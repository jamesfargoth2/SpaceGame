package com.galacticodyssey.combat.fleet;

import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class FleetCompositionRegistryTest {

    @Test
    void registryLoadsTemplatesFromData() {
        FleetCompositionRegistry registry = new FleetCompositionRegistry();
        registry.registerDefaults();
        FleetCompositionData data = registry.getForEthos(FactionEthos.MILITARIST);
        assertNotNull(data);
        assertFalse(data.slots.isEmpty());
    }

    @Test
    void generateCompositionRespectsStrength() {
        FleetCompositionRegistry registry = new FleetCompositionRegistry();
        registry.registerDefaults();
        FleetCompositionData data = registry.getForEthos(FactionEthos.MILITARIST);

        List<FleetShipEntry> weak = data.generate(0.2f, new Random(1));
        List<FleetShipEntry> strong = data.generate(0.9f, new Random(1));

        int weakTotal = weak.stream().mapToInt(e -> e.count).sum();
        int strongTotal = strong.stream().mapToInt(e -> e.count).sum();
        assertTrue(strongTotal > weakTotal);
    }

    @Test
    void allEthosHaveCompositions() {
        FleetCompositionRegistry registry = new FleetCompositionRegistry();
        registry.registerDefaults();
        for (FactionEthos ethos : FactionEthos.values()) {
            assertNotNull(registry.getForEthos(ethos), "Missing composition for " + ethos);
        }
    }
}
