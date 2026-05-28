package com.galacticodyssey.player;

import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.persistence.snapshots.PlayerStatsSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStatsSnapshotTest {

    @Test
    void roundTripPreservesAllFields() {
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.characterLevel = 7;
        stats.totalXP = 1234.5f;
        stats.unspentPoints = 5;
        stats.unspentPerkPicks = 2;
        stats.realTimeSkills.get(RealTimeSkill.FIREARMS).level = 12;
        stats.realTimeSkills.get(RealTimeSkill.FIREARMS).xp = 40f;
        stats.pointSkills.put(PointSkill.ENGINEERING, 3);
        stats.perks.add("firearms_steady_hands");

        PlayerStatsSnapshot snap = stats.takeSnapshot();

        PlayerStatsComponent restored = new PlayerStatsComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals(7, restored.characterLevel);
        assertEquals(1234.5f, restored.totalXP, 0.001f);
        assertEquals(5, restored.unspentPoints);
        assertEquals(2, restored.unspentPerkPicks);
        assertEquals(12, restored.realTimeSkills.get(RealTimeSkill.FIREARMS).level);
        assertEquals(40f, restored.realTimeSkills.get(RealTimeSkill.FIREARMS).xp, 0.001f);
        assertEquals(3, restored.pointSkills.get(PointSkill.ENGINEERING, 0));
        assertTrue(restored.perks.contains("firearms_steady_hands", false));
    }
}
