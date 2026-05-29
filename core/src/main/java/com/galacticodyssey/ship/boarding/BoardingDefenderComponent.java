package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

/** Tags an entity as a defender spawned for the boarding operation on {@link #operationShip}. */
public class BoardingDefenderComponent implements Component {
    public Entity operationShip;
    /** True for attackers spawned when an NPC boards the player (inverted roles). */
    public boolean attacker;
    /** Guards against double-counting a death toward the defender tally. */
    public boolean counted;
}
