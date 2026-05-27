package com.galacticodyssey.economy.procgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemEconomyGeneratorTest {

    @Test
    void deterministic() {
        long seed = 12345L;
        SystemEconomy a = SystemEconomyGenerator.generate(seed, "CORPORATE", 0.3f);
        SystemEconomy b = SystemEconomyGenerator.generate(seed, "CORPORATE", 0.3f);

        assertEquals(a.getEconomyType(), b.getEconomyType());
        assertEquals(a.getWealthLevel(), b.getWealthLevel());
        assertEquals(a.getTechLevel(), b.getTechLevel());
        assertEquals(a.getVolatility(), b.getVolatility());
        assertEquals(a.hasBlackMarket(), b.hasBlackMarket());
        assertEquals(a.getSupply(), b.getSupply());
        assertEquals(a.getDemand(), b.getDemand());
        assertEquals(a.getBasePrice(), b.getBasePrice());
        assertEquals(a.getContraband(), b.getContraband());
    }

    @Test
    void miningColonyProducesOre() {
        // Try many seeds to find one that generates a MINING_COLONY
        SystemEconomy mining = findEconomyOfType(EconomyType.MINING_COLONY, null, 0.9f);
        assertNotNull(mining, "Should find at least one MINING_COLONY in 1000 seeds");
        assertTrue(mining.getSupply().get("iron_ore") > 0.5f,
                "MINING_COLONY should have high iron_ore supply");
    }

    @Test
    void industrialDemandsRawMaterials() {
        SystemEconomy industrial = findEconomyOfType(EconomyType.INDUSTRIAL, "CORPORATE", 0.2f);
        assertNotNull(industrial, "Should find at least one INDUSTRIAL in 1000 seeds");
        assertTrue(industrial.getDemand().get("iron_ore") > 0.5f,
                "INDUSTRIAL should have high iron_ore demand");
    }

    @Test
    void pirateOutpostHasBlackMarket() {
        SystemEconomy pirate = findEconomyOfType(EconomyType.PIRATE_OUTPOST, "PIRATE_SYNDICATE", 0.5f);
        assertNotNull(pirate, "Should find at least one PIRATE_OUTPOST in 1000 seeds");
        assertTrue(pirate.hasBlackMarket(), "PIRATE_OUTPOST should always have a black market");
    }

    @Test
    void contrabandVariesByFaction() {
        // Generate with different faction ethos and check contraband differs
        SystemEconomy militarist = SystemEconomyGenerator.generate(100L, "MILITARIST", 0.5f);
        SystemEconomy corporate = SystemEconomyGenerator.generate(100L, "CORPORATE", 0.5f);
        SystemEconomy pirate = SystemEconomyGenerator.generate(100L, "PIRATE_SYNDICATE", 0.5f);

        // MILITARIST bans narcotics and salvaged_components
        assertTrue(militarist.getContraband().contains("narcotics"),
                "MILITARIST should ban narcotics");
        assertTrue(militarist.getContraband().contains("salvaged_components"),
                "MILITARIST should ban salvaged_components (stolen goods)");

        // CORPORATE bans salvaged_components but not narcotics
        assertTrue(corporate.getContraband().contains("salvaged_components"),
                "CORPORATE should ban salvaged_components");
        assertFalse(corporate.getContraband().contains("narcotics"),
                "CORPORATE should not ban narcotics");

        // PIRATE_SYNDICATE bans nothing
        assertTrue(pirate.getContraband().isEmpty(),
                "PIRATE_SYNDICATE should not ban anything");
    }

    @Test
    void pricesReflectSupplyDemand() {
        // Find two economies where one has high demand + low supply for a commodity
        // and the other has the reverse, then compare prices
        SystemEconomy mining = findEconomyOfType(EconomyType.MINING_COLONY, null, 0.5f);
        SystemEconomy industrial = findEconomyOfType(EconomyType.INDUSTRIAL, null, 0.5f);
        assertNotNull(mining);
        assertNotNull(industrial);

        // MINING has high iron_ore supply, INDUSTRIAL has high iron_ore demand
        // So iron_ore should be cheaper at mining than industrial
        float miningIronPrice = mining.getBasePrice().get("iron_ore");
        float industrialIronPrice = industrial.getBasePrice().get("iron_ore");
        assertTrue(miningIronPrice < industrialIronPrice,
                "Iron ore should be cheaper at MINING_COLONY (high supply) "
                + "than INDUSTRIAL (high demand): mining=" + miningIronPrice
                + " industrial=" + industrialIronPrice);
    }

    /** Searches up to 1000 seeds to find an economy of the given type. */
    private SystemEconomy findEconomyOfType(EconomyType type, String ethos, float dist) {
        for (long seed = 1; seed <= 1000; seed++) {
            SystemEconomy e = SystemEconomyGenerator.generate(seed, ethos, dist);
            if (e.getEconomyType() == type) return e;
        }
        return null;
    }
}
