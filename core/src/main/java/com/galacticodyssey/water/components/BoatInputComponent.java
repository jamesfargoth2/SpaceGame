package com.galacticodyssey.water.components;

import com.badlogic.ashley.core.Component;

/**
 * Player input state for piloting a surface vessel (boat, ship).
 * <p>
 * The {@link com.galacticodyssey.water.systems.BoatMotorSystem} reads this
 * each tick and translates input values into thrust and rudder forces applied
 * through Bullet physics.
 * <p>
 * Values are normalised to [-1, 1] where applicable, matching the convention
 * used by {@link com.galacticodyssey.ship.components.ShipFlightInputComponent}.
 */
public class BoatInputComponent implements Component {

    /** Forward/reverse throttle in [-1, 1]. Positive = forward thrust. */
    public float throttle;

    /** Steering (rudder) input in [-1, 1]. Positive = turn starboard (right). */
    public float steering;
}
