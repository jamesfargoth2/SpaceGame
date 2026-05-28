package com.galacticodyssey.player;

import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.RealTimeSkill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerkRegistryCanSelectTest {

    private PerkRegistry reg() { return PerkRegistry.fromClasspath("data/player/perk_trees.json"); }

    @Test
    void rootSelectableAtSkillZeroNoPrereqs() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        assertTrue(reg.canSelect(stats, "firearms_steady_hands"));
    }

    @Test
    void childBlockedUntilPrereqOwned() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.realTimeSkills.get(RealTimeSkill.FIREARMS).level = 50; // skill gate met
        assertFalse(reg.canSelect(stats, "firearms_marksman"), "needs parent perk");
        stats.perks.add("firearms_steady_hands");
        assertTrue(reg.canSelect(stats, "firearms_marksman"));
    }

    @Test
    void childBlockedByInsufficientSkillLevel() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.perks.add("firearms_steady_hands");
        stats.realTimeSkills.get(RealTimeSkill.FIREARMS).level = 5; // < required 10
        assertFalse(reg.canSelect(stats, "firearms_marksman"));
    }

    @Test
    void alreadyOwnedNotSelectable() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.perks.add("firearms_steady_hands");
        assertFalse(reg.canSelect(stats, "firearms_steady_hands"));
    }

    @Test
    void unknownPerkNotSelectable() {
        assertFalse(reg().canSelect(new PlayerStatsComponent(), "does_not_exist"));
    }
}
