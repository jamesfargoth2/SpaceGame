package com.galacticodyssey.player;

import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.PlayerStatQuery;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStatQueryPerkTest {

    @BeforeEach
    void setUp() {
        PlayerStatQuery.setPerkRegistry(PerkRegistry.fromClasspath("data/player/perk_trees.json"));
    }

    @AfterEach
    void tearDown() {
        PlayerStatQuery.setPerkRegistry(null);
    }

    @Test
    void tradePerkImprovesTradeModifier() {
        PlayerStatsComponent stats = new PlayerStatsComponent();
        float before = PlayerStatQuery.getTradeModifier(stats);
        stats.perks.add("trading_haggler"); // *0.97
        float after = PlayerStatQuery.getTradeModifier(stats);
        assertTrue(after < before, "trade modifier should drop (cheaper) with the perk");
        assertEquals(before * 0.97f, after, 0.0001f);
    }

    @Test
    void ballisticDamagePerkMultipliesOutgoing() {
        PlayerStatsComponent stats = new PlayerStatsComponent();
        assertEquals(1f, PlayerStatQuery.getOutgoingDamageMultiplier(stats, DamageType.BALLISTIC), 0.0001f);
        stats.perks.add("firearms_steady_hands"); // *1.05
        assertEquals(1.05f, PlayerStatQuery.getOutgoingDamageMultiplier(stats, DamageType.BALLISTIC), 0.0001f);
    }

    @Test
    void nullRegistryLeavesBaseMath() {
        PlayerStatQuery.setPerkRegistry(null);
        PlayerStatsComponent stats = new PlayerStatsComponent();
        // Capture pure skill-based result before adding a perk.
        float baseMath = PlayerStatQuery.getTradeModifier(stats);
        stats.perks.add("trading_haggler");
        // No registry => perks ignored; result equals the pure skill-based math unchanged.
        assertEquals(baseMath, PlayerStatQuery.getTradeModifier(stats), 0.0001f);
    }
}
