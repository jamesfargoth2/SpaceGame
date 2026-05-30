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
        // Lockstep with real-scale terrain frame (SP1): all geometry in metres.
        // Full sky/density retuning (optical-path coefficients) deferred to SP3.
        float pR = 6_371_000f;              // metres (6 371 km × 1 000)
        float scaleH = 8_500f;              // metres (8.5 km × 1 000)
        return new ScatteringParams(
            5.5e-3f, 13.0e-3f, 22.4e-3f,   // Rayleigh RGB per-km (blue > red)
            21e-3f, 0.76f,                   // Mie coeff per-km + asymmetry
            2.1e-3f, 0.0f, 0.0f,            // Absorption per-km (ozone-like, mostly red channel)
            scaleH, 1_200f,                  // Scale heights (m)
            pR, pR + scaleH * 6f,            // Planet + atmosphere radius (m)
            22.0f, 0.00465f,                 // Sun intensity + angular radius (radians)
            1_500f, 4_000f, 0.45f,          // Cloud base, top (m), coverage
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

        // Lockstep with real-scale terrain frame (SP1): geometry in metres.
        // Full sky/density retuning (optical-path coefficients) deferred to SP3.
        float pR   = planet.radius * 6371f * 1000f; // metres (Earth-radii → km → m)
        float scaleHm = scaleH * 1000f;             // scale height in metres
        float atmoR = pR + scaleHm * 6f;

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

        // Cloud layers in metres (were km; lockstep with real-scale terrain frame SP1)
        float cloudBase = (1.5f + (1f / Math.max(0.1f, pressure))) * 1000f;
        float cloudTop  = cloudBase + (2f + pressure * 0.5f) * 1000f;
        cloudBase = Math.max(1_000f, Math.min(4_000f, cloudBase));
        cloudTop  = Math.max(cloudBase + 1_000f, Math.min(8_000f, cloudTop));

        // Fog density from pressure
        float fogDensity;
        if      (pressure < 0.1f)  fogDensity = 0.001f;
        else if (pressure < 2f)    fogDensity = 0.004f * pressure;
        else if (pressure < 10f)   fogDensity = 0.008f + (pressure - 2f) * 0.001f;
        else                       fogDensity = 0.02f  + (pressure - 10f) * 0.002f;
        fogDensity = Math.min(0.05f, fogDensity);

        // Mie scale height in metres (was km)
        float scaleHeightMie = (1.2f / (float) Math.sqrt(Math.max(0.1f, pressure))) * 1000f;

        return new ScatteringParams(
            rayleighR, rayleighG, rayleighB,
            mieCoeff, mieG,
            absR, absG, absB,
            scaleHm, scaleHeightMie,
            pR, atmoR,
            sunIntensity, 0.00465f,
            cloudBase, cloudTop, cloudCoverage,
            fogDensity
        );
    }

    private static ScatteringParams vacuum(float radiusEarthRadii) {
        // Lockstep with real-scale terrain frame (SP1): geometry in metres.
        // Full sky/density retuning (optical-path coefficients) deferred to SP3.
        float pR = radiusEarthRadii * 6371f * 1000f; // metres (Earth-radii → km → m)
        return new ScatteringParams(
            0f, 0f, 0f,
            0f, 0.76f,
            0f, 0f, 0f,
            8_500f, 1_200f,             // scale heights in metres
            pR, pR + 50_000f,           // atmosphere shell: 50 km above surface (metres)
            22.0f, 0.00465f,
            0f, 0f, 0f,
            0f
        );
    }
}
