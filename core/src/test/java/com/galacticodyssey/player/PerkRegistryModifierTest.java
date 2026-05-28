package com.galacticodyssey.player;

import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.PerkTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerkRegistryModifierTest {

    private PerkRegistry reg() { return PerkRegistry.fromClasspath("data/player/perk_trees.json"); }

    @Test
    void noPerksLeavesBaseUnchanged() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        assertEquals(100f, reg.applyModifiers(stats, PerkTarget.DAMAGE_BALLISTIC, 100f), 0.001f);
    }

    @Test
    void multiplicativeModifiersStack() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.perks.add("firearms_steady_hands"); // *1.05
        stats.perks.add("firearms_marksman");     // *1.10
        // 100 * 1.05 * 1.10 = 115.5
        assertEquals(115.5f, reg.applyModifiers(stats, PerkTarget.DAMAGE_BALLISTIC, 100f), 0.01f);
    }

    @Test
    void additiveModifiersStackOntoBase() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.perks.add("piloting_brace");    // +0.05 HAZARD_RESIST
        stats.perks.add("athletics_conditioning"); // +0.05 HAZARD_RESIST
        assertEquals(0.10f, reg.applyModifiers(stats, PerkTarget.HAZARD_RESIST, 0f), 0.001f);
    }

    @Test
    void hasDetectsSpecialEffect() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        assertFalse(reg.has(stats, "firearms_rapid_reload"));
        stats.perks.add("firearms_rapid_reload");
        assertTrue(reg.has(stats, "firearms_rapid_reload"));
    }
}
