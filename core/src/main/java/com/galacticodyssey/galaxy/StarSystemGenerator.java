package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import java.util.Random;

public final class StarSystemGenerator {
    private static final float UNIVERSE_AGE_GYR = 13f;
    private static final float BINARY_CHANCE = 0.30f;

    private final long galaxySeed;

    public StarSystemGenerator(long galaxySeed) {
        this.galaxySeed = galaxySeed;
    }

    public StarSystem generate(StarPosition star, GalaxyRegion region) {
        long starSeed = SeedDeriver.forId(
            SeedDeriver.domain(galaxySeed, SeedDeriver.STAR_DOMAIN), star.uniqueId);
        Random rng = new Random(starSeed);

        SpectralClass spectral = rollSpectralClass(rng, region);
        float stellarMass = massFromSpectral(spectral, rng);

        // Determine age and luminosity class from stellar evolution
        float maxAge = Math.min(StellarEvolution.mainSequenceLifetime(stellarMass), UNIVERSE_AGE_GYR);
        float age = RngUtil.range(rng, 0.1f, UNIVERSE_AGE_GYR);
        LuminosityClass luminosityClass = determineLuminosityClass(stellarMass, age, rng);

        // Constrain age for main-sequence stars
        if (luminosityClass == LuminosityClass.MAIN_SEQUENCE) {
            age = Math.min(age, maxAge);
        }

        float temperature = RngUtil.range(rng, spectral.tempMin, spectral.tempMax);

        // Compute luminosity and radius using StellarEvolution
        float stellarLuminosity;
        float stellarRadius;
        if (luminosityClass == LuminosityClass.MAIN_SEQUENCE) {
            stellarLuminosity = StellarEvolution.mainSequenceLuminosity(stellarMass);
            stellarRadius = StellarEvolution.mainSequenceRadius(stellarMass);
        } else {
            stellarLuminosity = StellarEvolution.evolvedLuminosity(stellarMass, luminosityClass, rng);
            stellarRadius = StellarEvolution.evolvedRadius(stellarMass, luminosityClass, rng);
        }

        Color color = StellarEvolution.colorFromTemperature(temperature);

        StarSystem system = new StarSystem(star.uniqueId, starSeed, spectral, luminosityClass,
            temperature, stellarLuminosity, stellarMass, stellarRadius, age, color);

        // Binary companion generation (30% chance)
        if (rng.nextFloat() < BINARY_CHANCE) {
            system.companion = generateCompanion(stellarMass, rng);
        }

        return system;
    }

    private LuminosityClass determineLuminosityClass(float mass, float age, Random rng) {
        if (!StellarEvolution.isEvolved(mass, age)) {
            return LuminosityClass.MAIN_SEQUENCE;
        }
        // Evolved star: classify based on original mass
        if (mass > 8f) {
            return LuminosityClass.SUPERGIANT;
        }
        if (mass > 0.5f) {
            return LuminosityClass.GIANT;
        }
        return LuminosityClass.WHITE_DWARF;
    }

    private BinaryStarData generateCompanion(float primaryMass, Random rng) {
        float companionMass = primaryMass * RngUtil.range(rng, 0.1f, 1.0f);
        // Log-uniform separation from 0.1 to 100 AU
        float separationAU = 0.1f * (float) Math.pow(1000f, rng.nextFloat());

        SpectralClass companionSpectral = spectralClassFromMass(companionMass);
        float companionTemp = RngUtil.range(rng, companionSpectral.tempMin, companionSpectral.tempMax);
        float companionLuminosity = StellarEvolution.mainSequenceLuminosity(companionMass);
        Color companionColor = StellarEvolution.colorFromTemperature(companionTemp);

        return new BinaryStarData(companionSpectral, companionMass, companionLuminosity,
            companionTemp, separationAU, companionColor);
    }

    private SpectralClass spectralClassFromMass(float mass) {
        if (mass >= 16f) return SpectralClass.O;
        if (mass >= 2.1f) return SpectralClass.B;
        if (mass >= 1.4f) return SpectralClass.A;
        if (mass >= 1.04f) return SpectralClass.F;
        if (mass >= 0.8f) return SpectralClass.G;
        if (mass >= 0.45f) return SpectralClass.K;
        return SpectralClass.M;
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

    private float massFromSpectral(SpectralClass sc, Random rng) {
        return switch (sc) {
            case O -> RngUtil.range(rng, 16f, 50f);
            case B -> RngUtil.range(rng, 2.1f, 16f);
            case A -> RngUtil.range(rng, 1.4f, 2.1f);
            case F -> RngUtil.range(rng, 1.04f, 1.4f);
            case G -> RngUtil.range(rng, 0.8f, 1.04f);
            case K -> RngUtil.range(rng, 0.45f, 0.8f);
            case M -> RngUtil.range(rng, 0.08f, 0.45f);
        };
    }
}
