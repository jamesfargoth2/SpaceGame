package com.galacticodyssey.water.data;

import com.badlogic.gdx.utils.Array;

/**
 * Data-driven definition of a submarine type.
 * Loaded from JSON at runtime; never hardcode submarine stats.
 */
public class SubmarineData {

    /** Unique identifier for this submarine type. */
    public String id = "";

    /** Display name. */
    public String name = "";

    /** Dry mass in kg (hull + machinery, no ballast water). */
    public float dryMass = 50000f;

    /** Hull displacement volume in cubic meters. */
    public float displacementVolume = 120f;

    /** Maximum safe operating depth (meters). */
    public float crushDepth = 500f;

    /** Warning threshold as fraction of crush depth. */
    public float depthWarningFraction = 0.85f;

    /** Rate of hull integrity loss per second at crush depth. */
    public float crushDamageRate = 0.05f;

    /** Hull dimensions (meters). */
    public float length = 25f;
    public float beam = 6f;
    public float height = 5f;

    /** Hydrodynamic drag coefficients. */
    public float forwardDragCoefficient = 0.04f;
    public float lateralDragCoefficient = 1.2f;
    public float verticalDragCoefficient = 0.8f;

    /** Added mass coefficient for entrained water. */
    public float addedMassCoefficient = 0.2f;

    /** Angular drag coefficient. */
    public float angularDragCoefficient = 5.0f;

    /** Depth control PID gains. */
    public float depthKP = 0.02f;
    public float depthKI = 0.001f;
    public float depthKD = 0.05f;

    /** Maximum descent/ascent rates (m/s). */
    public float maxDescentRate = 5f;
    public float maxAscentRate = 8f;

    /** Ballast tank definitions. */
    public final Array<BallastTankData> ballastTanks = new Array<>();

    /** Compartment definitions. */
    public final Array<CompartmentDefinition> compartments = new Array<>();

    /** Nested data for a single ballast tank. */
    public static class BallastTankData {
        public String id = "";
        public float capacity = 10f;
        public float flowRate = 1f;
        public float forwardOffset = 0f;
        public float verticalOffset = 0f;
    }
}
