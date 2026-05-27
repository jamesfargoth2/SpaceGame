package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.components.BuoyancyComponent;
import com.galacticodyssey.water.components.SubmarineHullComponent;

/**
 * Simulates surface wave effects on submarine buoyancy.
 *
 * When the submarine is at or near the surface, waves modulate the effective
 * water surface level, creating heave (vertical), pitch, and roll forces.
 * Multiple wave components are superimposed (Gerstner-style sum of sines)
 * to create a realistic-looking sea state.
 *
 * Wave effects attenuate with depth -- a deeply submerged submarine
 * should not be affected by surface waves.
 */
public class SubmarineWaveSystem extends IteratingSystem {

    private static final int PRIORITY = 4; // Run before SubmarineBuoyancySystem

    /** Depth at which wave effects are fully attenuated (meters). */
    private static final float WAVE_ATTENUATION_DEPTH = 30f;

    private final ComponentMapper<BuoyancyComponent> buoyancyMapper =
        ComponentMapper.getFor(BuoyancyComponent.class);
    private final ComponentMapper<SubmarineHullComponent> hullMapper =
        ComponentMapper.getFor(SubmarineHullComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private static final Pool<Vector3> vectorPool = new Pool<Vector3>(2, 8) {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };

    private final Matrix4 tempTransform = new Matrix4();

    /** Elapsed time for wave animation. */
    private float elapsedTime = 0f;

    // Wave parameters (data-driven values would come from weather/environment config)

    /** Wave component definitions: amplitude (m), frequency (rad/s), phase offset (rad). */
    private final float[][] waveComponents = {
        {1.5f, 0.4f, 0f},      // Primary swell
        {0.8f, 0.7f, 1.2f},    // Secondary swell
        {0.3f, 1.5f, 2.8f},    // Wind chop
        {0.15f, 2.5f, 4.1f},   // High-frequency ripple
    };

    /** Base surface level (Y coordinate). */
    private float baseSurfaceLevel = 0f;

    /** Overall wave amplitude multiplier (sea state scaling). */
    private float waveIntensity = 1f;

    public SubmarineWaveSystem() {
        super(Family.all(
            BuoyancyComponent.class,
            SubmarineHullComponent.class,
            PhysicsBodyComponent.class
        ).get(), PRIORITY);
    }

    /**
     * Sets the base water surface level and wave intensity.
     * Call this when the environment changes (weather, planet, etc.).
     */
    public void configure(float surfaceLevel, float intensity) {
        this.baseSurfaceLevel = surfaceLevel;
        this.waveIntensity = intensity;
    }

    /**
     * Sets individual wave component parameters.
     * Index 0-3 correspond to primary swell, secondary swell, wind chop, ripple.
     */
    public void setWaveComponent(int index, float amplitude, float frequency, float phase) {
        if (index >= 0 && index < waveComponents.length) {
            waveComponents[index][0] = amplitude;
            waveComponents[index][1] = frequency;
            waveComponents[index][2] = phase;
        }
    }

    @Override
    public void update(float deltaTime) {
        elapsedTime += deltaTime;
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        final BuoyancyComponent buoyancy = buoyancyMapper.get(entity);
        final PhysicsBodyComponent physics = physicsMapper.get(entity);

        if (physics.body == null) return;

        // Get entity position for position-dependent wave calculation
        Vector3 position = vectorPool.obtain();
        physics.body.getWorldTransform(tempTransform);
        tempTransform.getTranslation(position);

        // Calculate effective surface level at this position using superimposed waves
        float waveHeight = 0f;
        for (float[] wave : waveComponents) {
            float amplitude = wave[0] * waveIntensity;
            float frequency = wave[1];
            float phase = wave[2];

            // Position-dependent phase creates spatial variation
            float spatialPhase = position.x * 0.05f + position.z * 0.03f;
            waveHeight += amplitude * MathUtils.sin(frequency * elapsedTime + phase + spatialPhase);
        }

        // The effective surface level with waves
        float effectiveSurface = baseSurfaceLevel + waveHeight;

        // Calculate depth below surface for attenuation
        float depthBelowSurface = effectiveSurface - position.y;

        // Attenuate wave effects with depth (exponential decay)
        if (depthBelowSurface > 0f) {
            float attenuation = (float) Math.exp(-depthBelowSurface / WAVE_ATTENUATION_DEPTH);
            // Blend between wave-affected surface and flat surface
            effectiveSurface = baseSurfaceLevel + waveHeight * attenuation;
        }

        buoyancy.surfaceLevel = effectiveSurface;

        // Apply additional wave forces when near the surface
        if (depthBelowSurface < WAVE_ATTENUATION_DEPTH && depthBelowSurface > -5f) {
            applyWaveForces(physics, buoyancy, position, depthBelowSurface);
        }

        vectorPool.free(position);
    }

    /**
     * Applies lateral wave forces to create realistic surface motion.
     * These are orbital wave particle velocities that push the submarine.
     */
    private void applyWaveForces(PhysicsBodyComponent physics, BuoyancyComponent buoyancy,
                                  Vector3 position, float depthBelowSurface) {
        float attenuation = 1f;
        if (depthBelowSurface > 0f) {
            attenuation = (float) Math.exp(-depthBelowSurface / WAVE_ATTENUATION_DEPTH);
        }

        // Orbital velocity from waves (simplified)
        float orbitalX = 0f;
        float orbitalZ = 0f;
        for (float[] wave : waveComponents) {
            float amplitude = wave[0] * waveIntensity;
            float frequency = wave[1];
            float phase = wave[2];
            float spatialPhase = position.x * 0.05f + position.z * 0.03f;

            // Horizontal orbital velocity (derivative of wave displacement)
            orbitalX += amplitude * frequency * MathUtils.cos(
                frequency * elapsedTime + phase + spatialPhase) * 0.05f;
            orbitalZ += amplitude * frequency * MathUtils.cos(
                frequency * elapsedTime + phase + spatialPhase) * 0.03f;
        }

        // Scale force by attenuation and water density
        float forceScale = buoyancy.waterDensity * attenuation * 50f;

        Vector3 waveForce = vectorPool.obtain();
        waveForce.set(orbitalX * forceScale, 0f, orbitalZ * forceScale);
        physics.body.applyCentralForce(waveForce);
        vectorPool.free(waveForce);
    }
}
