package com.galacticodyssey.player;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.events.PerkSelectedEvent;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.systems.PerkSystem;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PerkSystemTest {

    private PerkSystem system(EventBus bus) {
        return new PerkSystem(bus, PerkRegistry.fromClasspath("data/player/perk_trees.json"));
    }

    private Entity player(PlayerStatsComponent stats) {
        Entity e = new Entity();
        e.add(stats);
        return e;
    }

    @Test
    void selectsPerkAndConsumesPick() {
        EventBus bus = new EventBus();
        AtomicReference<PerkSelectedEvent> seen = new AtomicReference<>();
        bus.subscribe(PerkSelectedEvent.class, seen::set);

        PerkSystem sys = system(bus);
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.unspentPerkPicks = 1;
        Entity p = player(stats);

        assertTrue(sys.selectPerk(p, "firearms_steady_hands"));
        assertTrue(stats.perks.contains("firearms_steady_hands", false));
        assertEquals(0, stats.unspentPerkPicks);
        assertNotNull(seen.get());
        assertEquals("firearms_steady_hands", seen.get().perkId);
    }

    @Test
    void failsWithNoPicks() {
        EventBus bus = new EventBus();
        PerkSystem sys = system(bus);
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.unspentPerkPicks = 0;
        assertFalse(sys.selectPerk(player(stats), "firearms_steady_hands"));
        assertFalse(stats.perks.contains("firearms_steady_hands", false));
    }

    @Test
    void failsWhenPrereqsUnmet() {
        EventBus bus = new EventBus();
        PerkSystem sys = system(bus);
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.unspentPerkPicks = 1; // but marksman needs the parent + skill 10
        assertFalse(sys.selectPerk(player(stats), "firearms_marksman"));
        assertEquals(1, stats.unspentPerkPicks, "pick not consumed on failure");
    }
}
