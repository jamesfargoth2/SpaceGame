package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.PlayerStatQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the perk damage multiplier the combat systems apply to player-sourced damage. */
class PlayerDamagePerkTest {

    @BeforeEach
    void setUp() {
        PlayerStatQuery.setPerkRegistry(PerkRegistry.fromClasspath("data/player/perk_trees.json"));
    }

    @AfterEach
    void tearDown() { PlayerStatQuery.setPerkRegistry(null); }

    private float scaled(Entity attacker, float raw, DamageType type) {
        PlayerStatsComponent stats = attacker.getComponent(PlayerStatsComponent.class);
        return stats == null ? raw : raw * PlayerStatQuery.getOutgoingDamageMultiplier(stats, type);
    }

    @Test
    void playerWithBallisticPerkDealsMore() {
        Entity player = new Entity();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.perks.add("firearms_steady_hands"); // *1.05
        player.add(stats);
        assertEquals(105f, scaled(player, 100f, DamageType.BALLISTIC), 0.01f);
    }

    @Test
    void nonPlayerAttackerUnaffected() {
        Entity npc = new Entity();
        assertEquals(100f, scaled(npc, 100f, DamageType.BALLISTIC), 0.01f);
    }
}
