package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.ship.ShipSizeClass;

/**
 * Data-driven defender complement for a ship that is boarded. Ships carry no crew roster yet,
 * so this component (stocked by {@code ShipFactory}) is the source of FPS defenders spawned by
 * {@link com.galacticodyssey.ship.boarding.systems.BoardingCombatSystem}. Also the single
 * source of the ship's {@code factionId} for ransom/ownership.
 */
public class BoardingDefenseComponent implements Component {
    public int defenderCount = 2;
    public float defenderHealth = 100f;
    public float defenderDamage = 12f;
    public String factionId = "independent";

    /** Defender complement scaled by hull size class. */
    public static BoardingDefenseComponent forSizeClass(ShipSizeClass sizeClass) {
        BoardingDefenseComponent c = new BoardingDefenseComponent();
        switch (sizeClass) {
            case SMALL:  c.defenderCount = 2; c.defenderHealth = 80f;  c.defenderDamage = 10f; break;
            case MEDIUM: c.defenderCount = 4; c.defenderHealth = 100f; c.defenderDamage = 12f; break;
            default:     c.defenderCount = 7; c.defenderHealth = 120f; c.defenderDamage = 15f; break;
        }
        return c;
    }
}
