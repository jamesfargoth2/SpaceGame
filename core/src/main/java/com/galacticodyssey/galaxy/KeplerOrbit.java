package com.galacticodyssey.galaxy;

public final class KeplerOrbit {

    public float semiMajorAxis;
    public float eccentricity;
    public float inclination;
    public float longitudeAscNode;
    public float argumentPeriapsis;
    public float trueAnomalyAtEpoch;
    public float primaryMass;

    public float GM;
    public float period;
    public float periapsis;
    public float apoapsis;

    public KeplerOrbit() {}

    public KeplerOrbit(float semiMajorAxis, float eccentricity, float inclination,
                       float longitudeAscNode, float argumentPeriapsis,
                       float trueAnomalyAtEpoch, float primaryMass) {
        this.semiMajorAxis      = semiMajorAxis;
        this.eccentricity       = eccentricity;
        this.inclination        = inclination;
        this.longitudeAscNode   = longitudeAscNode;
        this.argumentPeriapsis  = argumentPeriapsis;
        this.trueAnomalyAtEpoch = trueAnomalyAtEpoch;
        this.primaryMass        = primaryMass;

        this.GM       = OrbitalMechanics.G * primaryMass;
        this.period   = OrbitalMechanics.orbitalPeriod(GM, semiMajorAxis);
        this.periapsis = semiMajorAxis * (1f - eccentricity);
        this.apoapsis  = semiMajorAxis * (1f + eccentricity);
    }
}
