package com.galacticodyssey.ship.atmosphere;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.atmosphere.events.HeatShieldFailureEvent;

/**
 * Computes atmospheric entry heating using the Sutton-Graves approximation
 * and accumulates/dissipates heat on each entity's heat shield.
 * <p>
 * Only active when atmospheric density is positive and speed exceeds 100 m/s.
 * Runs at priority 6, after {@link AeroForceSystem}.
 */
public class EntryHeatSystem extends EntitySystem {

    /** Sutton-Graves constant for general atmospheric entry (approximate). */
    private static final float SUTTON_GRAVES_K = 1.83e-4f;

    /** Stefan-Boltzmann constant in W/(m^2 * K^4). */
    private static final float STEFAN_BOLTZMANN = 5.67e-8f;

    /** Minimum speed (m/s) below which entry heating is negligible. */
    private static final float MIN_HEATING_SPEED = 100f;

    private static final ComponentMapper<AeroBodyComponent> aeroBodyMapper =
        ComponentMapper.getFor(AeroBodyComponent.class);
    private static final ComponentMapper<AeroStateComponent> aeroStateMapper =
        ComponentMapper.getFor(AeroStateComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private final EventBus eventBus;

    private ImmutableArray<Entity> entities;
    private AtmosphereProfile atmosphere;

    // Scratch vector
    private final Vector3 velocity = new Vector3();

    public EntryHeatSystem(EventBus eventBus) {
        super(6);
        this.eventBus = eventBus;
    }

    /** Set the active atmosphere profile. Pass {@code null} to clear it. */
    public void setAtmosphereProfile(AtmosphereProfile profile) {
        this.atmosphere = profile;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(Family.all(
            AeroBodyComponent.class,
            AeroStateComponent.class,
            PhysicsBodyComponent.class,
            TransformComponent.class
        ).get());
    }

    @Override
    public void update(float deltaTime) {
        if (atmosphere == null) return;

        for (int i = 0; i < entities.size(); i++) {
            processEntity(entities.get(i), deltaTime);
        }
    }

    private void processEntity(Entity entity, float dt) {
        AeroBodyComponent aero = aeroBodyMapper.get(entity);
        AeroStateComponent state = aeroStateMapper.get(entity);
        PhysicsBodyComponent physics = physicsMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);

        if (physics.body == null) return;

        float altitude = transform.position.len();
        float density = atmosphere.densityAt(altitude);
        if (density <= 0f) return;

        velocity.set(physics.body.getLinearVelocity());
        float speed = velocity.len();
        if (speed < MIN_HEATING_SPEED) return;

        // Stagnation heat flux: q = k * sqrt(rho / R_n) * v^3   (W/m^2)
        float flux = stagnationHeatFlux(density, speed, aero.noseRadius);

        // Total heating power on the heat shield (W)
        float heatingPower = flux * aero.heatShieldArea;

        // Temperature rise: dT = (P / (m * Cp)) * dt
        if (state.heatShieldMass > 0f && state.heatShieldCp > 0f) {
            state.heatShieldTemp +=
                (heatingPower / (state.heatShieldMass * state.heatShieldCp)) * dt;
        }

        // Radiative cooling (Stefan-Boltzmann): P_rad = sigma * A * T^4
        float tempSq = state.heatShieldTemp * state.heatShieldTemp;
        float radiatedPower = STEFAN_BOLTZMANN * aero.heatShieldArea * tempSq * tempSq;

        if (state.heatShieldMass > 0f && state.heatShieldCp > 0f) {
            state.heatShieldTemp -=
                (radiatedPower / (state.heatShieldMass * state.heatShieldCp)) * dt;
        }

        // Clamp to ambient temperature floor
        state.heatShieldTemp = Math.max(state.heatShieldTemp, atmosphere.surfaceTemperature);

        // Heat shield failure check
        if (state.heatShieldTemp > state.heatShieldMaxTemp) {
            eventBus.publish(new HeatShieldFailureEvent(entity));
        }
    }

    /**
     * Sutton-Graves stagnation-point heat flux in W/m^2.
     *
     * @param density    local atmospheric density in kg/m^3
     * @param speed      vehicle speed in m/s
     * @param noseRadius nose radius in metres
     * @return convective heat flux in W/m^2
     */
    private static float stagnationHeatFlux(float density, float speed, float noseRadius) {
        if (noseRadius <= 0f) return 0f;
        float speed3 = speed * speed * speed;
        return SUTTON_GRAVES_K * (float) Math.sqrt(density / noseRadius) * speed3;
    }
}
