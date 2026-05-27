package com.galacticodyssey.core.solar;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;

/**
 * Marks an entity as a star or other solar radiation/wind source.
 * <p>
 * Attach to any entity that also has a
 * {@link com.galacticodyssey.core.components.TransformComponent} so that
 * radiation and wind systems can read its world position.
 */
public class SolarSourceComponent implements Component {

    /** Total electromagnetic luminosity (Watts). */
    public float luminosity;

    /** Solar wind mass flux at reference distance (kg/m^2/s). */
    public float solarWindMassFlux;

    /** Solar wind particle stream velocity (m/s). */
    public float solarWindSpeed;

    /** Whether a coronal mass ejection is currently active. */
    public boolean cmeActive;

    /** Multiplier applied to wind pressure during a CME (1 = normal). */
    public float cmeIntensityMultiplier = 1f;

    /** Direction of the CME cone axis (radians, in the XZ plane). */
    public float cmeDirection;

    /** Remaining duration of the current CME (seconds). */
    public float cmeRemainingTime;

    /** Radiation belts associated with orbiting bodies of this star. */
    public final Array<RadiationBelt> radiationBelts = new Array<>();
}
