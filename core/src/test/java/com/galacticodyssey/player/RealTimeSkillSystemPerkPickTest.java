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

        assertEquals(5, stats.characterLevel);
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

        assertEquals(2, stats.characterLevel);
        assertEquals(0, stats.unspentPerkPicks);
    }

    @Test
    void crossingTwoMilestonesGrantsTwoPerkPicks() {
        EventBus bus = new EventBus();
        RealTimeSkillSystem system = new RealTimeSkillSystem(bus);
        Entity player = new Entity();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        player.add(stats);

        // Level 10 requires totalXP = (10-1)^2 * 250 = 20250. Crosses both level 5 and level 10.
        system.awardSkillXP(player, RealTimeSkill.FIREARMS, 20250f, 1f);

        assertEquals(10, stats.characterLevel);
        assertEquals(2, stats.unspentPerkPicks);
    }
}
