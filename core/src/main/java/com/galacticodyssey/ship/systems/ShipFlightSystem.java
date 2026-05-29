package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.EngineSpecComponent;
import com.galacticodyssey.ship.components.FuelTankComponent;
import com.galacticodyssey.core.RelativisticConstants;
import com.galacticodyssey.core.RelativisticMath;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;

public class ShipFlightSystem extends EntitySystem {

    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ShipFlightComponent> flightMapper =
        ComponentMapper.getFor(ShipFlightComponent.class);
    private final ComponentMapper<ShipFlightInputComponent> flightInputMapper =
        ComponentMapper.getFor(ShipFlightInputComponent.class);
    private final ComponentMapper<PlayerStateComponent> stateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<EngineSpecComponent> engineMapper =
        ComponentMapper.getFor(EngineSpecComponent.class);
    private final ComponentMapper<FuelTankComponent> fuelMapper =
        ComponentMapper.getFor(FuelTankComponent.class);
    private final ComponentMapper<com.galacticodyssey.ship.components.ShipDataComponent> shipDataMapper =
        ComponentMapper.getFor(com.galacticodyssey.ship.components.ShipDataComponent.class);

    private static final float DEFAULT_MAX_SPEED = 100f;
    private static final float DEFAULT_MAX_TURN_RATE_DEG = 45f;

    private ImmutableArray<Entity> playerEntities;
    private ImmutableArray<Entity> npcShips;

    private final Vector3 force = new Vector3();
    private final Vector3 torque = new Vector3();
    private final Vector3 localForward = new Vector3();
    private final Vector3 localRight = new Vector3();
    private final Vector3 localUp = new Vector3();
    private final Matrix4 shipTransform = new Matrix4();
    private final Vector3 currentVelocity = new Vector3();
    private final Vector3 lateralVel = new Vector3();
    private final Vector3 forwardVelComp = new Vector3();

    /** False when the ship has subsystems and its engines are non-operational. */
    public static boolean canThrust(Entity ship) {
        com.galacticodyssey.ship.boarding.ShipSubsystemsComponent subs =
            ship.getComponent(com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.class);
        return subs == null || subs.enginesOperational();
    }

    public ShipFlightSystem() {
        super(3);
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class).get());
        npcShips = engine.getEntitiesFor(Family.all(
            ShipFlightInputComponent.class, ShipFlightComponent.class,
            PhysicsBodyComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        // Player-piloted ship: input lives on the player entity, applied to currentShip.
        if (playerEntities.size() > 0) {
            Entity player = playerEntities.first();
            PlayerStateComponent state = stateMapper.get(player);
            if (state.currentMode == PlayerMode.PILOTING && state.currentShip != null) {
                ShipFlightInputComponent input = flightInputMapper.get(player);
                if (input != null) {
                    applyFlight(state.currentShip, input, deltaTime);
                }
            }
        }

        // NPC ships: input lives on the ship entity itself (driven by AI).
        for (int i = 0; i < npcShips.size(); i++) {
            Entity ship = npcShips.get(i);
            applyFlight(ship, flightInputMapper.get(ship), deltaTime);
        }
    }

    private void applyFlight(Entity ship, ShipFlightInputComponent input, float deltaTime) {
        if (input == null) return;

        PhysicsBodyComponent physics = physicsMapper.get(ship);
        ShipFlightComponent flight = flightMapper.get(ship);
        if (physics == null || physics.body == null || flight == null) return;

        // Flight-Assist toggle is processed even when engines are down (it's a computer mode).
        if (input.flightAssistTogglePressed) {
            flight.flightAssistEnabled = !flight.flightAssistEnabled;
            input.flightAssistTogglePressed = false;
        }

        // Engines disabled (destroyed or EMP) → ship coasts, ignore pilot thrust/turn input.
        if (!canThrust(ship)) {
            return;
        }

        // Throttle management via EngineSpec
        EngineSpecComponent engineSpec = engineMapper.get(ship);
        float effectiveThrottle = input.throttle;
        if (engineSpec != null) {
            float target = input.throttle;
            engineSpec.currentThrottle = MathUtils.lerp(engineSpec.currentThrottle, target,
                engineSpec.throttleResponseRate * deltaTime);
            effectiveThrottle = engineSpec.currentThrottle;
            engineSpec.actualThrust = effectiveThrottle * engineSpec.maxThrust;
        }

        // Fuel consumption: massFlowRate = thrust / (isp * g0)
        FuelTankComponent fuel = fuelMapper.get(ship);
        if (fuel != null && engineSpec != null && Math.abs(effectiveThrottle) > 0.001f) {
            float thrust = Math.abs(effectiveThrottle) * flight.linearThrust;
            float massFlowRate = thrust / (engineSpec.isp * 9.81f);
            fuel.currentMass -= massFlowRate * deltaTime;
            if (fuel.currentMass <= 0) {
                fuel.currentMass = 0;
                effectiveThrottle = 0;
            }
        }

        physics.body.getWorldTransform(shipTransform);

        localForward.set(0, 0, -1).rot(shipTransform).nor();
        localRight.set(1, 0, 0).rot(shipTransform).nor();
        localUp.set(0, 1, 0).rot(shipTransform).nor();

        // --- Effective max speed (live from ShipData; boost handled in a later task) ---
        com.galacticodyssey.ship.components.ShipDataComponent shipData = shipDataMapper.get(ship);
        float maxSpeed = (shipData != null && shipData.maxSpeed > 0f)
            ? shipData.maxSpeed : DEFAULT_MAX_SPEED;
        if (flight.boostTimer > 0f) maxSpeed *= flight.boostSpeedMultiplier;

        currentVelocity.set(physics.body.getLinearVelocity());
        force.setZero();

        if (flight.flightAssistEnabled) {
            // Forward axis tracking toward target speed (P-controller, mass-relative, thrust-capped).
            float targetSpeed = effectiveThrottle * maxSpeed;
            float forwardSpeed = currentVelocity.dot(localForward);
            float fwdError = targetSpeed - forwardSpeed;
            float fwdForce = MathUtils.clamp(flight.faLinearGain * physics.mass * fwdError,
                -flight.linearThrust, flight.linearThrust);
            force.mulAdd(localForward, fwdForce);

            // Lateral velocity = velocity minus forward component.
            forwardVelComp.set(localForward).scl(forwardSpeed);
            lateralVel.set(currentVelocity).sub(forwardVelComp);

            // Strafe / vertical: intentional input adds thrust; otherwise bleed drift.
            float strafeThrust = flight.linearThrust * flight.strafeThrustFraction;
            float vertThrust   = flight.linearThrust * flight.verticalThrustFraction;
            float rightVel = lateralVel.dot(localRight);
            float upVel    = lateralVel.dot(localUp);

            float rightForce = (Math.abs(input.strafe) > 0.05f)
                ? input.strafe * strafeThrust
                : MathUtils.clamp(-flight.faLateralBleed * physics.mass * rightVel,
                    -strafeThrust, strafeThrust);
            float upForce = (Math.abs(input.verticalThrust) > 0.05f)
                ? input.verticalThrust * vertThrust
                : MathUtils.clamp(-flight.faLateralBleed * physics.mass * upVel,
                    -vertThrust, vertThrust);

            force.mulAdd(localRight, rightForce);
            force.mulAdd(localUp, upForce);
        } else {
            // Newtonian: direct thrust, no cap, no bleed.
            force.mulAdd(localForward, effectiveThrottle * flight.linearThrust);
            force.mulAdd(localRight, input.strafe * flight.linearThrust * flight.strafeThrustFraction);
            force.mulAdd(localUp, input.verticalThrust * flight.linearThrust * flight.verticalThrustFraction);
        }

        // Relativistic correction near light speed (unchanged behavior).
        final float speed = currentVelocity.len();
        if (speed > RelativisticConstants.THRESHOLD) {
            final float restMass = physics.mass;
            final Vector3 velDir = currentVelocity.cpy().nor();
            final float longComponent = force.dot(velDir);
            final Vector3 longForce = new Vector3(velDir).scl(longComponent);
            final Vector3 transForce = new Vector3(force).sub(longForce);
            final float longAccel = RelativisticMath.longitudinalAcceleration(longComponent, restMass, speed);
            final float transAccelMag = transForce.len();
            final float transAccel = transAccelMag > 0f
                ? RelativisticMath.transverseAcceleration(transAccelMag, restMass, speed)
                : 0f;
            final Vector3 relForce = new Vector3(velDir).scl(longAccel * restMass);
            if (transAccelMag > 0f) {
                relForce.add(new Vector3(transForce).nor().scl(transAccel * restMass));
            }
            physics.body.applyCentralForce(relForce);
        } else {
            physics.body.applyCentralForce(force);
        }

        // --- Rotation ---
        float maxTurnDeg = (shipData != null && shipData.maxTurnRate > 0f)
            ? shipData.maxTurnRate : DEFAULT_MAX_TURN_RATE_DEG;
        float maxTurnRad = maxTurnDeg * MathUtils.degreesToRadians;
        float blue = FlightControlMath.blueZoneFactor(effectiveThrottle,
            flight.blueZoneLow, flight.blueZoneHigh, flight.offBandTurnScale);
        float maxRate = maxTurnRad * blue;

        // Clamp raw inputs (player mouse delta may exceed 1).
        float pitchCmd = MathUtils.clamp(input.pitchInput, -1f, 1f);
        float yawCmd   = MathUtils.clamp(input.yawInput, -1f, 1f);
        float rollCmd  = MathUtils.clamp(input.rollInput, -1f, 1f);

        // Current angular velocity projected onto local axes.
        Vector3 angVel = physics.body.getAngularVelocity();
        float ratePitch = angVel.dot(localRight);
        float rateYaw   = angVel.dot(localUp);
        float rateRoll  = angVel.dot(localForward);

        torque.setZero();
        if (flight.flightAssistEnabled) {
            // Desired local rates. Sign matches the legacy torque mapping
            // (pitch +pitchInput about right; yaw -yawInput about up; roll +rollInput about fwd).
            float desiredPitch =  pitchCmd * maxRate;
            float desiredYaw   = -yawCmd   * maxRate;
            float desiredRoll  =  rollCmd  * maxRate;

            float tp = MathUtils.clamp((desiredPitch - ratePitch) / maxTurnRad * flight.rotStiffness, -1f, 1f);
            float ty = MathUtils.clamp((desiredYaw   - rateYaw)   / maxTurnRad * flight.rotStiffness, -1f, 1f);
            float tr = MathUtils.clamp((desiredRoll  - rateRoll)  / maxTurnRad * flight.rotStiffness, -1f, 1f);

            torque.mulAdd(localRight, tp * flight.pitchYawTorque);
            torque.mulAdd(localUp,    ty * flight.pitchYawTorque);
            torque.mulAdd(localForward, tr * flight.rollTorque);
        } else {
            // Newtonian: raw torque from input, no auto-stop.
            torque.mulAdd(localRight, pitchCmd * flight.pitchYawTorque);
            torque.mulAdd(localUp, -yawCmd * flight.pitchYawTorque);
            torque.mulAdd(localForward, rollCmd * flight.rollTorque);
        }

        physics.body.applyTorque(torque);
        // Controllers govern convergence: the linear FA block tracks the target
        // velocity and the rotation block zeroes angular velocity on stick release.
        // Bullet damping stays 0 on both axes so we never double-damp.
        physics.body.setDamping(0f, 0f);

        flight.currentThrottle = effectiveThrottle;
        physics.body.activate();
    }
}
