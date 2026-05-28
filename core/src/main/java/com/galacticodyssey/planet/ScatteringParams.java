package com.galacticodyssey.planet;

public final class ScatteringParams {

    // Rayleigh scattering coefficients (per-wavelength: R, G, B)
    public final float rayleighCoeffR;
    public final float rayleighCoeffG;
    public final float rayleighCoeffB;

    // Mie scattering
    public final float mieCoeff;
    public final float mieG;

    // Absorption (ozone-like, per-wavelength)
    public final float absorptionCoeffR;
    public final float absorptionCoeffG;
    public final float absorptionCoeffB;

    // Scale heights (render units)
    public final float scaleHeightRayleigh;
    public final float scaleHeightMie;

    // Atmosphere geometry (render units)
    public final float planetRadius;
    public final float atmosphereRadius;

    // Sun
    public final float sunIntensity;
    public final float sunAngularRadius;

    // Clouds
    public final float cloudBase;
    public final float cloudTop;
    public final float cloudCoverage;

    // Fog
    public final float fogDensity;

    private ScatteringParams(float rayleighR, float rayleighG, float rayleighB,
                             float mieCoeff, float mieG,
                             float absR, float absG, float absB,
                             float scaleHeightR, float scaleHeightM,
                             float planetRadius, float atmosphereRadius,
                             float sunIntensity, float sunAngularRadius,
                             float cloudBase, float cloudTop, float cloudCoverage,
                             float fogDensity) {
        this.rayleighCoeffR = rayleighR;
        this.rayleighCoeffG = rayleighG;
        this.rayleighCoeffB = rayleighB;
        this.mieCoeff = mieCoeff;
        this.mieG = mieG;
        this.absorptionCoeffR = absR;
        this.absorptionCoeffG = absG;
        this.absorptionCoeffB = absB;
        this.scaleHeightRayleigh = scaleHeightR;
        this.scaleHeightMie = scaleHeightM;
        this.planetRadius = planetRadius;
        this.atmosphereRadius = atmosphereRadius;
        this.sunIntensity = sunIntensity;
        this.sunAngularRadius = sunAngularRadius;
        this.cloudBase = cloudBase;
        this.cloudTop = cloudTop;
        this.cloudCoverage = cloudCoverage;
        this.fogDensity = fogDensity;
    }

    public static ScatteringParams earthLike() {
        float pR = 6371f;
        float scaleH = 8.5f;
        return new ScatteringParams(
            5.5e-3f, 13.0e-3f, 22.4e-3f,   // Rayleigh RGB per-km (blue > red)
            21e-3f, 0.76f,                   // Mie coeff per-km + asymmetry
            2.1e-3f, 0.0f, 0.0f,            // Absorption per-km (ozone-like, mostly red channel)
            scaleH, 1.2f,                    // Scale heights (km)
            pR, pR + scaleH * 6f,            // Planet + atmosphere radius (km)
            22.0f, 0.00465f,                 // Sun intensity + angular radius (radians)
            1.5f, 4.0f, 0.45f,              // Cloud base, top, coverage (km)
            0.004f                           // Fog density
        );
    }

    public static ScatteringParams fromPlanet(Planet planet) {
        if (planet.atmosphere == null || planet.atmosphere.surfacePressure < 0.01f) {
            return vacuum(planet.radius);
        }

        Atmosphere atmo = planet.atmosphere;
        float pressure = atmo.surfacePressure;

        float n2Frac  = atmo.composition.getOrDefault(Gas.N2,  0f);
        float o2Frac  = atmo.composition.getOrDefault(Gas.O2,  0f);
        float co2Frac = atmo.composition.getOrDefault(Gas.CO2, 0f);
        float h2oFrac = atmo.composition.getOrDefault(Gas.H2O, 0f);
        float so2Frac = atmo.composition.getOrDefault(Gas.SO2, 0f);

        float earthLikeFrac = n2Frac + o2Frac;
        float heavyFrac     = co2Frac + so2Frac;

        // Rayleigh: wavelength-dependent, scaled by pressure and composition.
        // CO2/SO2 shift toward red; N2/O2 favour blue.
        float pressureScale = pressure;
        float rBase = 5.5e-3f  * earthLikeFrac + 18.0e-3f * heavyFrac;
        float gBase = 13.0e-3f * earthLikeFrac + 14.0e-3f * heavyFrac;
        float bBase = 22.4e-3f * earthLikeFrac +  8.0e-3f * heavyFrac;

        float rayleighR = rBase * pressureScale;
        float rayleighG = gBase * pressureScale;
        float rayleighB = bBase * pressureScale;

        // Scale height: inversely related to pressure
        float scaleH = 8.5f / (float) Math.sqrt(Math.max(0.1f, pressure));
        scaleH = Math.max(4f, Math.min(12f, scaleH));

        // Mie: driven by planet type (aerosol proxy)
        float mieCoeff;
        float mieG;
        switch (planet.type) {
            case ARID:
                mieCoeff = 30e-3f * pressure;
                mieG = 0.85f;
                break;
            case TOXIC:
                mieCoeff = 50e-3f * pressure;
                mieG = 0.6f;
                break;
            case OCEAN:
                mieCoeff = 5e-3f * pressure;
                mieG = 0.76f;
                break;
            case ICE_WORLD:
                mieCoeff = 10e-3f * pressure;
                mieG = 0.7f;
                break;
            default:
                mieCoeff = 21e-3f * pressure;
                mieG = 0.76f;
                break;
        }

        // Absorption
        float absR = o2Frac  > 0.1f  ? 2.1e-3f * pressure : 0f;
        float absG = so2Frac > 0.05f ? 1.5e-3f * pressure : 0f;
        float absB = 0f;

        // Planet geometry — radius is in Earth-radii, convert to km for render-space
        float pR   = planet.radius * 6371f;
        float atmoR = pR + scaleH * 6f;

        // Sun intensity from equilibrium temperature
        float sunIntensity = Math.max(0f, 22.0f * (atmo.equilibriumTemp / 255f));

        // Clouds
        float cloudCoverage;
        switch (planet.type) {
            case OCEAN:     cloudCoverage = 0.6f + h2oFrac * 0.3f;  break;
            case ARID:      cloudCoverage = 0.05f + h2oFrac * 0.15f; break;
            case ICE_WORLD: cloudCoverage = 0.3f + h2oFrac * 0.2f;  break;
            case TOXIC:     cloudCoverage = 0.4f + so2Frac * 0.4f;  break;
            default:        cloudCoverage = 0.3f + h2oFrac * 0.4f;  break;
        }
        cloudCoverage = Math.max(0f, Math.min(1f, cloudCoverage));

        float cloudBase = 1.5f + (1f / Math.max(0.1f, pressure));
        float cloudTop  = cloudBase + 2f + pressure * 0.5f;
        cloudBase = Math.max(1f, Math.min(4f, cloudBase));
        cloudTop  = Math.max(cloudBase + 1f, Math.min(8f, cloudTop));

        // Fog density from pressure
        float fogDensity;
        if      (pressure < 0.1f)  fogDensity = 0.001f;
        else if (pressure < 2f)    fogDensity = 0.004f * pressure;
        else if (pressure < 10f)   fogDensity = 0.008f + (pressure - 2f) * 0.001f;
        else                       fogDensity = 0.02f  + (pressure - 10f) * 0.002f;
        fogDensity = Math.min(0.05f, fogDensity);

        float scaleHeightMie = 1.2f / (float) Math.sqrt(Math.max(0.1f, pressure));

        return new ScatteringParams(
            rayleighR, rayleighG, rayleighB,
            mieCoeff, mieG,
            absR, absG, absB,
            scaleH, scaleHeightMie,
            pR, atmoR,
            sunIntensity, 0.00465f,
            cloudBase, cloudTop, cloudCoverage,
            fogDensity
        );
    }

    private static ScatteringParams vacuum(float radiusEarthRadii) {
        float pR = radiusEarthRadii * 6371f;
        return new ScatteringParams(
            0f, 0f, 0f,
            0f, 0.76f,
            0f, 0f, 0f,
            8.5f, 1.2f,
            pR, pR + 50f,
            22.0f, 0.00465f,
            0f, 0f, 0f,
            0f
        );
    }
}
