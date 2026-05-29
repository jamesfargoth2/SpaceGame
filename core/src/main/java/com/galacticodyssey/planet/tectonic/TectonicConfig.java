package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.planet.PlanetType;

/** Tunable parameters for tectonic generation. Defaults are hardcoded; JSON can override. */
public final class TectonicConfig {
    public int plateCountMin = 7;
    public int plateCountMax = 15;
    public float plateCountPerRadius = 2.0f; // extra plates per unit planet radius
    public int lloydIterations = 2;
    public int hotspotMin = 1;
    public int hotspotMax = 4;

    public float continentalBase = 0.35f;  // base elevation of continental plates
    public float oceanicDepth = -0.35f;     // base elevation of oceanic plates
    public float mountainUplift = 0.55f;
    public float trenchDepth = 0.45f;
    public float riftDepth = 0.18f;
    public float ridgeUplift = 0.12f;
    public float hotspotUplift = 0.30f;

    public float boundaryInfluence = 0.18f; // angular radians of boundary effect
    public float hotspotInfluence = 0.08f;  // angular radians of hotspot bump

    // Continental-fraction targets per planet type (probability a plate is continental).
    public float continentalFractionOcean = 0.15f;
    public float continentalFractionTerran = 0.45f;
    public float continentalFractionArid = 0.70f;
    public float continentalFractionDefault = 0.50f;

    public float continentalFractionTarget(PlanetType type) {
        return switch (type) {
            case OCEAN -> continentalFractionOcean;
            case TERRAN -> continentalFractionTerran;
            case ARID, BARREN, TOXIC -> continentalFractionArid;
            default -> continentalFractionDefault;
        };
    }

    public static TectonicConfig defaults() {
        return new TectonicConfig();
    }

    /** Overlays any present fields from JSON onto a fresh defaults() instance. */
    public static TectonicConfig fromJson(JsonValue root) {
        TectonicConfig c = defaults();
        if (root == null) return c;
        c.plateCountMin = root.getInt("plateCountMin", c.plateCountMin);
        c.plateCountMax = root.getInt("plateCountMax", c.plateCountMax);
        c.plateCountPerRadius = root.getFloat("plateCountPerRadius", c.plateCountPerRadius);
        c.lloydIterations = root.getInt("lloydIterations", c.lloydIterations);
        c.hotspotMin = root.getInt("hotspotMin", c.hotspotMin);
        c.hotspotMax = root.getInt("hotspotMax", c.hotspotMax);
        c.continentalBase = root.getFloat("continentalBase", c.continentalBase);
        c.oceanicDepth = root.getFloat("oceanicDepth", c.oceanicDepth);
        c.mountainUplift = root.getFloat("mountainUplift", c.mountainUplift);
        c.trenchDepth = root.getFloat("trenchDepth", c.trenchDepth);
        c.riftDepth = root.getFloat("riftDepth", c.riftDepth);
        c.ridgeUplift = root.getFloat("ridgeUplift", c.ridgeUplift);
        c.hotspotUplift = root.getFloat("hotspotUplift", c.hotspotUplift);
        c.boundaryInfluence = root.getFloat("boundaryInfluence", c.boundaryInfluence);
        c.hotspotInfluence = root.getFloat("hotspotInfluence", c.hotspotInfluence);
        c.continentalFractionOcean = root.getFloat("continentalFractionOcean", c.continentalFractionOcean);
        c.continentalFractionTerran = root.getFloat("continentalFractionTerran", c.continentalFractionTerran);
        c.continentalFractionArid = root.getFloat("continentalFractionArid", c.continentalFractionArid);
        c.continentalFractionDefault = root.getFloat("continentalFractionDefault", c.continentalFractionDefault);
        return c;
    }
}
