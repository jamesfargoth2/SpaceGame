package com.galacticodyssey.water;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;

/**
 * Defines the hull characteristics of any floating or submersible vessel —
 * boats, ships, submarines, or debris.
 *
 * <p>Buoyancy is computed per {@link BuoyancySamplePoint}, not from mesh
 * intersection, so cost is fixed regardless of mesh complexity.
 */
public class HullComponent implements Component {

    /** Hull surface patches used for buoyancy and drag sampling. */
    public final Array<BuoyancySamplePoint> samplePoints = new Array<>();

    /** Mass of the hull without ballast or cargo, in kg. */
    public float dryMass;

    /** Total volume that would be displaced at full submersion, in m³. */
    public float totalDisplacementVolume;

    /** Viscous (skin friction) drag coefficient. */
    public float dragCoefficientLinear = 0.05f;

    /** Form (pressure) drag coefficient Cd. */
    public float dragCoefficientQuad = 0.8f;

    /** Wetted area below the waterline in m². */
    public float wettedArea;

    /** Hull width at the waterline in metres. */
    public float beamWidth;

    /** Hull length at the waterline in metres. */
    public float hullLength;

    /** Whether this hull can operate fully submerged. */
    public boolean isSubmersible = false;

    /** Maximum safe depth in metres below surface. {@code Float.MAX_VALUE} = unlimited. */
    public float crushDepth = Float.MAX_VALUE;
}
