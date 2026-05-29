package com.galacticodyssey.ship.boarding;

/** Resolution outcomes for a completed boarding operation. */
public enum BoardingOutcome {
    HIJACK,
    SCRAP,
    RANSOM,
    TOW,
    /** Inverted operation: an NPC captured the player's ship. */
    ENEMY_CAPTURE
}
