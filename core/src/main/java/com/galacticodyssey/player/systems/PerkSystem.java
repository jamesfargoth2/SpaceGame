package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.events.PerkSelectedEvent;
import com.galacticodyssey.player.stats.PerkRegistry;

/** Validates and applies permanent player perk selections. No respec API. */
public class PerkSystem extends EntitySystem {

    public static final int PRIORITY = 26;

    private static final ComponentMapper<PlayerStatsComponent> STATS_M =
        ComponentMapper.getFor(PlayerStatsComponent.class);

    private final EventBus eventBus;
    private final PerkRegistry perkRegistry;

    public PerkSystem(EventBus eventBus, PerkRegistry perkRegistry) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.perkRegistry = perkRegistry;
    }

    /** @return true if the perk was selected. False if no pick available or prerequisites unmet. */
    public boolean selectPerk(Entity player, String perkId) {
        PlayerStatsComponent stats = STATS_M.get(player);
        if (stats == null || stats.unspentPerkPicks <= 0) return false;
        if (!perkRegistry.canSelect(stats, perkId)) return false;
        stats.perks.add(perkId);
        stats.unspentPerkPicks--;
        eventBus.publish(new PerkSelectedEvent(player, perkId));
        return true;
    }
}
