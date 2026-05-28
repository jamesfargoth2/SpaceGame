package com.galacticodyssey.planet;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScatteringParamsTest {

    @Test
    void earthLikeHasBlueRayleigh() {
        ScatteringParams p = ScatteringParams.earthLike();
        assertTrue(p.rayleighCoeffR < p.rayleighCoeffB,
            "Earth-like Rayleigh blue (" + p.rayleighCoeffB +
            ") should exceed red (" + p.rayleighCoeffR + ")");
    }

    @Test
    void earthLikeHasReasonableDefaults() {
        ScatteringParams p = ScatteringParams.earthLike();
        assertTrue(p.planetRadius > 0f);
        assertTrue(p.atmosphereRadius > p.planetRadius);
        assertTrue(p.scaleHeightRayleigh > 0f);
        assertTrue(p.scaleHeightMie > 0f);
        assertTrue(p.mieCoeff > 0f);
        assertTrue(p.mieG > 0f && p.mieG < 1f);
        assertTrue(p.sunIntensity > 0f);
        assertTrue(p.cloudCoverage >= 0f && p.cloudCoverage <= 1f);
        assertTrue(p.cloudBase < p.cloudTop);
        assertTrue(p.fogDensity > 0f);
    }

    @Test
    void co2AtmosphereShiftsRayleighTowardAmber() {
        Map<Gas, Float> co2Comp = new EnumMap<>(Gas.class);
        co2Comp.put(Gas.CO2, 0.95f);
        co2Comp.put(Gas.N2, 0.05f);
        Atmosphere co2Atmo = new Atmosphere(co2Comp, 1.0f, 1.0f, 250f, 250f,
            false, EnumSet.of(AtmoHazard.NONE));
        Planet co2Planet = new Planet(1L, PlanetType.ARID, 1.0f, 1.0f, 5.5f,
            24f, 10f, false);
        co2Planet.atmosphere = co2Atmo;

        ScatteringParams p = ScatteringParams.fromPlanet(co2Planet);
        assertTrue(p.rayleighCoeffR > p.rayleighCoeffB,
            "CO2-heavy Rayleigh red (" + p.rayleighCoeffR +
            ") should exceed blue (" + p.rayleighCoeffB + ")");
    }

    @Test
    void vacuumAtmosphereNearZeroScattering() {
        Map<Gas, Float> thinComp = new EnumMap<>(Gas.class);
        thinComp.put(Gas.N2, 1.0f);
        Atmosphere vacuum = new Atmosphere(thinComp, 0.001f, 1.0f, 200f, 200f,
            false, EnumSet.of(AtmoHazard.VACUUM));
        Planet barren = new Planet(2L, PlanetType.BARREN, 0.5f, 0.3f, 4.0f,
            48f, 5f, false);
        barren.atmosphere = vacuum;

        ScatteringParams p = ScatteringParams.fromPlanet(barren);
        assertTrue(p.rayleighCoeffR < 1e-7f);
        assertTrue(p.rayleighCoeffG < 1e-7f);
        assertTrue(p.rayleighCoeffB < 1e-7f);
        assertEquals(0f, p.fogDensity, 1e-6f);
        assertEquals(0f, p.cloudCoverage, 1e-6f);
    }

    @Test
    void highPressureLowersScaleHeightAndIncreaseFogDensity() {
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        comp.put(Gas.N2, 0.7f);
        comp.put(Gas.O2, 0.3f);
        Atmosphere dense = new Atmosphere(comp, 15.0f, 2.0f, 300f, 600f,
            false, EnumSet.of(AtmoHazard.CRUSHING));
        Planet crushPlanet = new Planet(3L, PlanetType.TOXIC, 1.2f, 1.5f, 6.0f,
            30f, 8f, false);
        crushPlanet.atmosphere = dense;

        ScatteringParams earthP = ScatteringParams.earthLike();
        ScatteringParams crushP = ScatteringParams.fromPlanet(crushPlanet);

        assertTrue(crushP.scaleHeightRayleigh < earthP.scaleHeightRayleigh,
            "Crushing atmosphere should have lower scale height");
        assertTrue(crushP.fogDensity > earthP.fogDensity,
            "Crushing atmosphere should have denser fog");
    }

    @Test
    void oceanPlanetHighCloudCoverage() {
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        comp.put(Gas.N2, 0.55f);
        comp.put(Gas.H2O, 0.35f);
        comp.put(Gas.CO2, 0.10f);
        Atmosphere humid = new Atmosphere(comp, 1.2f, 1.5f, 280f, 300f,
            true, EnumSet.of(AtmoHazard.NONE));
        Planet ocean = new Planet(4L, PlanetType.OCEAN, 1.1f, 1.2f, 5.5f,
            22f, 15f, false);
        ocean.atmosphere = humid;

        ScatteringParams p = ScatteringParams.fromPlanet(ocean);
        assertTrue(p.cloudCoverage >= 0.5f,
            "Ocean planet cloud coverage (" + p.cloudCoverage + ") should be >= 0.5");
    }

    @Test
    void nullAtmosphereUsesVacuumDefaults() {
        Planet noAtmo = new Planet(5L, PlanetType.BARREN, 0.4f, 0.2f, 4.0f,
            100f, 2f, false);
        // atmosphere is null by default
        ScatteringParams p = ScatteringParams.fromPlanet(noAtmo);
        assertTrue(p.rayleighCoeffR < 1e-7f);
        assertEquals(0f, p.fogDensity, 1e-6f);
    }
}
