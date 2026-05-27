package com.galacticodyssey.ship.thermal;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.atmosphere.AtmosphereProfile;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;

/**
 * Core thermal simulation system. Computes heat generation (engine, solar) and
 * dissipation (radiative, convective, heat sink drain) each tick, then updates
 * temperatures on {@link ThermalStateComponent}.
 *
 * <p>Priority 10 -- runs after weapon heat (9) and before thermal penalties (11).</p>
 */
public class ThermalSystem extends EntitySystem {

    private static final int PRIORITY = 10;

    /** Stefan-Boltzmann constant (W / m^2 K^4). */
    private static final float STEFAN_BOLTZMANN = 5.67e-8f;

    private static final ComponentMapper<ThermalStateComponent> thermalMapper =
            ComponentMapper.getFor(ThermalStateComponent.class);
    private static final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<ShipFlightComponent> flightMapper =
            ComponentMapper.getFor(ShipFlightComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    // --- Star environment (configurable per scene) ---
    private final Vector3 starPosition = new Vector3();
    private float starLuminosity = 3.828e26f; // Watts (default: Sol)

    // --- Optional atmosphere reference (null in open space) ---
    private AtmosphereProfile atmosphereProfile;
    private float planetSurfaceRadius;

    public ThermalSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    // --- Configuration setters ---

    public void setStarPosition(Vector3 position) {
        starPosition.set(position);
    }

    public void setStarLuminosity(float luminosity) {
        this.starLuminosity = luminosity;
    }

    public void setAtmosphereProfile(AtmosphereProfile profile, float surfaceRadius) {
        this.atmosphereProfile = profile;
        this.planetSurfaceRadius = surfaceRadius;
    }

    public void clearAtmosphereProfile() {
        this.atmosphereProfile = null;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
                Family.all(ThermalStateComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ThermalStateComponent thermal = thermalMapper.get(entity);
            TransformComponent transform = transformMapper.get(entity);

            updateEngine(thermal, entity, deltaTime);
            updateHull(thermal, transform, entity, deltaTime);
            updateHeatSinkDrain(thermal, deltaTime);
            clampTemperatures(thermal);
        }
    }

    private void updateEngine(ThermalStateComponent t, Entity entity, float dt) {
        // Determine throttle from ShipFlightComponent if present
        float throttle = 0f;
        ShipFlightComponent flight = flightMapper.get(entity);
        if (flight != null) {
            throttle = flight.currentThrottle;
        }

        // Engine heat generation scales with throttle^2 (quadratic)
        float engineHeat = t.engineMaxHeatRate * throttle * throttle;

        // Radiative cooling via Stefan-Boltzmann
        float engineRadCooling = radiativeCooling(t.engineTemp, t.ambientTemp,
                t.radiatorArea * 0.3f, t.radiatorEmissivity);

        // Net temperature change
        float engineDeltaT = (engineHeat - engineRadCooling) / t.engineThermalMass;
        t.engineTemp += engineDeltaT * dt;
    }

    private void updateHull(ThermalStateComponent t, TransformComponent transform,
                            Entity entity, float dt) {
        // Solar heating: flux = L / (4*pi*r^2)
        float distToStar = transform.position.dst(starPosition);
        float solarFlux = 0f;
        if (distToStar > 1f) {
            solarFlux = starLuminosity / (4f * MathUtils.PI * distToStar * distToStar);
        }
        float solarIn = solarFlux * t.hullAbsorptivity * t.hullArea;

        // Hull radiative cooling
        float hullRadCooling = radiativeCooling(t.hullTemp, t.ambientTemp,
                t.radiatorArea, t.radiatorEmissivity);

        // Convective cooling (atmosphere only)
        float convCooling = 0f;
        if (atmosphereProfile != null) {
            float altitude = transform.position.len() - planetSurfaceRadius;
            float density = atmosphereProfile.densityAt(altitude);
            if (density > 0f) {
                float speed = getEntitySpeed(entity);
                float h = 5f * (float) Math.sqrt(density * speed);
                float ambientAtmo = atmosphereProfile.surfaceTemperature;
                convCooling = h * (t.hullTemp - ambientAtmo);
            }
        }

        // Net hull temperature change
        float hullDeltaT = (solarIn - hullRadCooling - convCooling) / t.hullThermalMass;
        t.hullTemp += hullDeltaT * dt;
    }

    private void updateHeatSinkDrain(ThermalStateComponent t, float dt) {
        if (t.heatSinkCharge > 0f) {
            t.heatSinkCharge = Math.max(0f,
                    t.heatSinkCharge - t.heatSinkPassiveDrainRate * dt);
        }
    }

    private void clampTemperatures(ThermalStateComponent t) {
        t.engineTemp = Math.max(t.engineTemp, t.ambientTemp);
        t.weaponBankTemp = Math.max(t.weaponBankTemp, t.ambientTemp);
        t.hullTemp = Math.max(t.hullTemp, t.ambientTemp);
        t.reactorTemp = Math.max(t.reactorTemp, t.ambientTemp);
    }

    /**
     * Stefan-Boltzmann radiative cooling: P = emissivity * sigma * A * (T^4 - T_ambient^4).
     */
    private static float radiativeCooling(float surfaceTemp, float ambientTemp,
                                          float area, float emissivity) {
        float t4 = surfaceTemp * surfaceTemp * surfaceTemp * surfaceTemp;
        float ta4 = ambientTemp * ambientTemp * ambientTemp * ambientTemp;
        return emissivity * STEFAN_BOLTZMANN * area * (t4 - ta4);
    }

    /**
     * Extracts the linear speed of an entity from its Bullet rigid body, or
     * returns 0 if no physics body is present.
     */
    private float getEntitySpeed(Entity entity) {
        PhysicsBodyComponent physics = physicsMapper.get(entity);
        if (physics != null && physics.body != null) {
            Vector3 vel = physics.body.getLinearVelocity();
            return vel.len();
        }
        return 0f;
    }
}
