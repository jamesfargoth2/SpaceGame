package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

/** A heat (or cold) emitter: flamethrower cone, lava patch, cryo field. */
public class HeatSourceComponent implements Component {
    public float power = 0f;        // W delivered at the source; negative = cooling
    public float radius = 5f;       // m -- effective range (sphere, or cone length)
    public boolean cone = false;    // false = spherical falloff
    public final Vector3 direction = new Vector3(0f, 0f, -1f); // cone axis (local)
    public float coneHalfAngleRad = 0.5f;
    public float lifetime = -1f;    // < 0 = permanent; otherwise seconds remaining
}
