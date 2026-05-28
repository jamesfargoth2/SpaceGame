package com.galacticodyssey.player;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.systems.RealTimeSkillSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RealTimeSkillSystemPerkPickTest {

    @Test
    void crossingLevelFiveGrantsExactlyOnePerkPick() {
        EventBus bus = new EventBus();
        RealTimeSkillSystem system = new RealTimeSkillSystem(bus);
        Entity player = new Entity();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        player.add(stats);

        // totalXP for character level 5 = (5-1)^2 * 250 = 4000. Award enough to reach level >=5.
        system.awardSkillXP(player, RealTimeSkill.FIREARMS, 4000f, 1f);

        assertTrue(stats.characterLevel >= 5);
        assertEquals(1, stats.unspentPerkPicks,
            "exactly one perk pick granted when crossing the level-5 boundary once");
    }

    @Test
    void noPerkPickBeforeLevelFive() {
        EventBus bus = new EventBus();
        RealTimeSkillSystem system = new RealTimeSkillSystem(bus);
        Entity player = new Entity();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        player.add(stats);

        system.awardSkillXP(player, RealTimeSkill.FIREARMS, 500f, 1f); // level ~2-3

        assertTrue(stats.characterLevel < 5);
        assertEquals(0, stats.unspentPerkPicks);
    }
}
