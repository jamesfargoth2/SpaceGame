package com.galacticodyssey.mech.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

/**
 * Player/AI movement input for a mech. Written by an input processor,
 * consumed by {@link com.galacticodyssey.mech.systems.MechLocomotionSystem}.
 */
public class MechInputComponent implements Component {

    /** XZ movement input in body-local space, each axis in [-1, 1]. */
    public final Vector2 moveInput = new Vector2();

    /** Turn input in [-1, 1]. Positive = clockwise yaw. */
    public float turnInput;
}
