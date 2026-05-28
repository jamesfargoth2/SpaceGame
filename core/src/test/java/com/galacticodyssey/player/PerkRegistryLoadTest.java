package com.galacticodyssey.player;

import com.galacticodyssey.player.stats.PerkNodeDef;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.RealTimeSkill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerkRegistryLoadTest {

    private PerkRegistry load() {
        return PerkRegistry.fromClasspath("data/player/perk_trees.json");
    }

    @Test
    void loadsEveryRealTimeSkillTree() {
        PerkRegistry reg = load();
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            assertTrue(reg.getTree(skill).size > 0,
                "expected a perk tree for " + skill);
        }
    }

    @Test
    void resolvesNodeById() {
        PerkRegistry reg = load();
        PerkNodeDef node = reg.get("firearms_marksman");
        assertNotNull(node);
        assertEquals("FIREARMS", node.treeSkill);
        assertEquals(1, node.tier);
        assertTrue(node.prerequisitePerkIds.contains("firearms_steady_hands"));
    }
}
