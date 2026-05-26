package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import java.util.Random;

public final class StarSystemGenerator {
    private final long galaxySeed;

    public StarSystemGenerator(long galaxySeed) {
        this.galaxySeed = galaxySeed;
    }

    public StarSystem generate(StarPosition star, GalaxyRegion region) {
        long starSeed = SeedDeriver.forId(
            SeedDeriver.domain(galaxySeed, SeedDeriver.STAR_DOMAIN), star.uniqueId);
        Random rng = new Random(starSeed);

        SpectralClass spectral = rollSpectralClass(rng, region);
        LuminosityClass luminosity = LuminosityClass.fromRoll(rng.nextFloat());

        float temperature = RngUtil.range(rng, spectral.tempMin, spectral.tempMax);
        float stellarMass = massFromSpectral(spectral, luminosity, rng);
        float stellarLuminosity = luminosityFromMass(stellarMass, luminosity);
        float stellarRadius = radiusFromMassLuminosity(stellarMass, luminosity, rng);
        float age = RngUtil.range(rng, 0.1f, 13.0f);
        Color color = colorFromTemperature(temperature);

        return new StarSystem(star.uniqueId, starSeed, spectral, luminosity,
            temperature, stellarLuminosity, stellarMass, stellarRadius, age, color);
    }

    private SpectralClass rollSpectralClass(Random rng, GalaxyRegion region) {
        float roll = rng.nextFloat();
        float modifier = switch (region) {
            case CORE -> -0.08f;
            case INNER_RIM -> 0f;
            case OUTER_RIM -> 0.05f;
            case VOID -> 0.10f;
        };
        roll = Math.max(0f, Math.min(0.9999f, roll + modifier));
        return SpectralClass.fromRoll(roll);
    }

    private float massFromSpectral(SpectralClass sc, LuminosityClass lc, Random rng) {
        float baseMass = switch (sc) {
            case O -> RngUtil.range(rng, 16f, 50f);
            case B -> RngUtil.range(rng, 2.1f, 16f);
            case A -> RngUtil.range(rng, 1.4f, 2.1f);
            case F -> RngUtil.range(rng, 1.04f, 1.4f);
            case G -> RngUtil.range(rng, 0.8f, 1.04f);
            case K -> RngUtil.range(rng, 0.45f, 0.8f);
            case M -> RngUtil.range(rng, 0.08f, 0.45f);
        };
        if (lc == LuminosityClass.GIANT) baseMass *= RngUtil.range(rng, 1.5f, 3f);
        if (lc == LuminosityClass.SUPERGIANT) baseMass *= RngUtil.range(rng, 3f, 10f);
        if (lc == LuminosityClass.WHITE_DWARF) baseMass = RngUtil.range(rng, 0.5f, 1.4f);
        return baseMass;
    }

    private float luminosityFromMass(float mass, LuminosityClass lc) {
        if (lc == LuminosityClass.WHITE_DWARF) return 0.001f + mass * 0.01f;
        if (lc == LuminosityClass.GIANT) return (float) Math.pow(mass, 3.5) * 10f;
        if (lc == LuminosityClass.SUPERGIANT) return (float) Math.pow(mass, 3.5) * 100f;
        return (float) Math.pow(mass, 3.5);
    }

    private float radiusFromMassLuminosity(float mass, LuminosityClass lc, Random rng) {
        if (lc == LuminosityClass.WHITE_DWARF) return RngUtil.range(rng, 0.008f, 0.02f);
        if (lc == LuminosityClass.GIANT) return (float) Math.pow(mass, 0.8) * RngUtil.range(rng, 5f, 25f);
        if (lc == LuminosityClass.SUPERGIANT) return (float) Math.pow(mass, 0.8) * RngUtil.range(rng, 30f, 200f);
        return (float) Math.pow(mass, 0.8);
    }

    private Color colorFromTemperature(float tempK) {
        float t = (tempK - 2000f) / 38000f;
        t = Math.max(0f, Math.min(1f, t));
        float r = 1f;
        float g = 0.5f + t * 0.5f;
        float b = 0.3f + t * 0.7f;
        if (t > 0.5f) { r = 1.2f - t * 0.4f; g = 1.1f - t * 0.2f; }
        return new Color(
            Math.min(1f, r), Math.min(1f, g), Math.min(1f, b), 1f);
    }
}
