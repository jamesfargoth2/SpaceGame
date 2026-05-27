package com.galacticodyssey.ship.data;

/** Atmospheric physics profile for a planet type, loaded from JSON. */
public class AtmosphereProfile {
    public String id;
    public String name;
    /** Surface air density in kg/m³. */
    public float surfaceDensity;
    /** Exponential scale height in metres. */
    public float scaleHeight;
    /** Speed of sound at the surface in m/s. */
    public float speedOfSound;
    /** Mach number at which compressibility effects become dominant. */
    public float machThreshold;
    /** Human-readable chemical composition (e.g. "N2/O2"). */
    public String composition;
}
