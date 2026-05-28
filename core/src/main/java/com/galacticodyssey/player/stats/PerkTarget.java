package com.galacticodyssey.player.stats;

/**
 * Stat surfaces a perk modifier can affect. Each value MUST have a consumption site:
 * the first block is read by {@link PlayerStatQuery}; the damage values are applied to
 * player outgoing damage in DamageSystem/MeleeSystem.
 */
public enum PerkTarget {
    TRADE_PRICE,        // multiplicative, <1 = better
    REP_GAIN,           // multiplicative
    MAX_CREW,           // additive (whole crew slots)
    CRAFT_QUALITY,      // multiplicative
    HAZARD_RESIST,      // additive (0-1)
    CREW_XP,            // multiplicative
    HEAL_EFF,           // multiplicative
    SCAN_QUALITY,       // multiplicative
    DAMAGE_BALLISTIC,   // multiplicative, player outgoing
    DAMAGE_ENERGY,      // multiplicative, player outgoing
    DAMAGE_MELEE        // multiplicative, player outgoing
}
