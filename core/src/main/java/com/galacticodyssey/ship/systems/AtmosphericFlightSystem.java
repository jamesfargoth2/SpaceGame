package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.AtmosphereZoneComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipAerodynamicsComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipThermalComponent;
import com.galacticodyssey.ship.events.AtmosphereEnteredEvent;
import com.galacticodyssey.ship.events.AtmosphereExitedEvent;
import com.galacticodyssey.ship.events.ReentryHeatingEvent;
import com.galacticodyssey.ship.events.StallWarningEvent;

/**
 * Applies aerodynamic forces (drag, lift, stall, re-entry heating) to the player's
 * ship when it is inside a planet's atmosphere. Queries the nearest
 * {@link AtmosphereZoneComponent} to determine air density, then applies:
 * <ul>
 *   <li>Drag — opposing velocity, proportional to dynamic pressure and cross-section area.</li>
 *   <li>Lift — perpendicular to velocity via the ship's up axis, based on angle-of-attack
 *       and the wing's lift curve.</li>
 *   <li>Re-entry heating — accumulates on {@link ShipThermalComponent} above the Mach threshold.</li>
 * </ul>
 * Forces are blended to zero at the atmosphere's transition altitude so there is no
 * discontinuity when exiting to vacuum. {@link AtmosphereEnteredEvent} and
 * {@link AtmosphereExitedEvent} are published on boundary crossings.
 */
public class AtmosphericFlightSystem extends EntitySystem {

    private final EventBus eventBus;
    private ImmutableArray<Entity> playerEntities;
    private ImmutableArray<Entity> planetEntities;

    private final ComponentMapper<PlayerStateComponent> stateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ShipAerodynamicsComponent> aeroMapper =
        ComponentMapper.getFor(ShipAerodynamicsComponent.class);
    private final ComponentMapper<ShipFlightComponent> flightMapper =
        ComponentMapper.getFor(ShipFlightComponent.class);
    private final ComponentMapper<ShipThermalComponent> thermalMapper =
        ComponentMapper.getFor(ShipThermalComponent.class);
    private final ComponentMapper<AtmosphereZoneComponent> atmoMapper =
        ComponentMapper.getFor(AtmosphereZoneComponent.class);

    // Reusable vectors — never cross-contaminate between uses within one update call.
    private final Vector3 velocity = new Vector3();
    private final Vector3 velNorm = new Vector3();
    private final Vector3 dragForce = new Vector3();
    private final Vector3 liftForce = new Vector3();
    private final Vector3 shipForward = new Vector3();
    private final Vector3 shipUp = new Vector3();
    private final Matrix4 tempMat = new Matrix4();

    private boolean wasInAtmosphere;

    public AtmosphericFlightSystem(EventBus eventBus) {
        super(4);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(
            Family.all(PlayerTagComponent.class, PlayerStateComponent.class).get());
        planetEntities = engine.getEntitiesFor(
            Family.all(AtmosphereZoneComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (playerEntities.size() == 0 || planetEntities.size() == 0) return;

        Entity player = playerEntities.first();
        PlayerStateComponent state = stateMapper.get(player);
        if (state.currentMode != PlayerMode.PILOTING || state.currentShip == null) return;

        Entity ship = state.currentShip;
        PhysicsBodyComponent physics = physicsMapper.get(ship);
        ShipAerodynamicsComponent aero = aeroMapper.get(ship);
        TransformComponent shipTransform = transformMapper.get(ship);
        if (physics == null || physics.body == null || aero == null || shipTransform == null) return;

        Entity planet = planetEntities.first();
        AtmosphereZoneComponent atmo = atmoMapper.get(planet);
        TransformComponent planetTransform = transformMapper.get(planet);

        float altitude = shipTransform.position.dst(planetTransform.position) - atmo.surfaceRadius;
        float atmosphereAltitude = atmo.atmosphereRadius - atmo.surfaceRadius;

        if (altitude >= atmosphereAltitude) {
            if (wasInAtmosphere) {
                eventBus.publish(new AtmosphereExitedEvent(ship));
                wasInAtmosphere = false;
            }
            return;
        }

        if (!wasInAtmosphere) {
            eventBus.publish(new AtmosphereEnteredEvent(ship, planet));
            wasInAtmosphere = true;
        }

        // Blend factor: 0 at atmosphere top, 1 below transitionAltitude.
        float blendFactor = MathUtils.clamp(
            (atmosphereAltitude - altitude) / (atmosphereAltitude - atmo.transitionAltitude),
            0f, 1f);

        float density = atmo.surfaceDensity * (float) Math.exp(-altitude / atmo.scaleHeight);

        // Capture velocity before any mutation.
        velocity.set(physics.body.getLinearVelocity());
        float speed = velocity.len();
        if (speed < 0.01f) return;

        float dynamicPressure = 0.5f * density * speed * speed;

        // --- Drag ---
        // velNorm is a separate copy so we do not mutate the cached velocity vector.
        velNorm.set(velocity).nor();
        float dragMagnitude = dynamicPressure * aero.dragCoefficient * aero.crossSectionArea * blendFactor;
        dragForce.set(velNorm).scl(-dragMagnitude);
        physics.body.applyCentralForce(dragForce);

        // --- Lift ---
        physics.body.getWorldTransform(tempMat);
        shipForward.set(0, 0, -1).rot(tempMat).nor();
        shipUp.set(0, 1, 0).rot(tempMat).nor();

        // Angle-of-attack: angle between velocity direction and ship forward axis.
        float aoaRad = (float) Math.acos(MathUtils.clamp(velNorm.dot(shipForward), -1f, 1f));
        float aoaDeg = aoaRad * MathUtils.radiansToDegrees;
        float cl = aero.getLiftCoefficient(aoaDeg);

        if (aoaDeg > aero.stallAngle) {
            eventBus.publish(new StallWarningEvent(ship));
        }

        float liftMagnitude = dynamicPressure * cl * aero.wingArea * blendFactor;
        liftForce.set(shipUp).scl(liftMagnitude);
        physics.body.applyCentralForce(liftForce);

        // --- Re-entry heating ---
        float mach = speed / atmo.speedOfSound;
        ShipThermalComponent thermal = thermalMapper.get(ship);
        if (mach > atmo.machThreshold && thermal != null) {
            float heatInput = (mach - atmo.machThreshold) * density * 10f * deltaTime
                * thermal.heatShieldFactor;
            thermal.currentHeat += heatInput;
            thermal.currentHeat = Math.min(thermal.currentHeat, thermal.maxHeat);
            eventBus.publish(new ReentryHeatingEvent(ship, thermal.currentHeat / thermal.maxHeat));
        }

        // --- Heat dissipation ---
        if (thermal != null && thermal.currentHeat > 0) {
            thermal.currentHeat -= thermal.dissipationRate * deltaTime;
            thermal.currentHeat = Math.max(0f, thermal.currentHeat);
        }

        physics.body.activate();
    }
}
