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

    private ImmutableArray<Entity> playerEntities;
    private ImmutableArray<Entity> npcShips;

    private final Vector3 force = new Vector3();
    private final Vector3 torque = new Vector3();
    private final Vector3 localForward = new Vector3();
    private final Vector3 localRight = new Vector3();
    private final Vector3 localUp = new Vector3();
    private final Matrix4 shipTransform = new Matrix4();
    private final Vector3 currentVelocity = new Vector3();

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

        force.setZero();
        force.mulAdd(localForward, effectiveThrottle * flight.linearThrust);
        force.mulAdd(localRight, input.strafe * flight.linearThrust * flight.strafeThrustFraction);
        force.mulAdd(localUp, input.verticalThrust * flight.linearThrust * flight.verticalThrustFraction);

        currentVelocity.set(physics.body.getLinearVelocity());
        final float speed = currentVelocity.len();
        if (speed > RelativisticConstants.THRESHOLD) {
            final float restMass = physics.mass;
            final Vector3 velDir = currentVelocity.nor();
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

        torque.setZero();
        torque.mulAdd(localRight, input.pitchInput * flight.pitchYawTorque);
        torque.mulAdd(localUp, -input.yawInput * flight.pitchYawTorque);
        torque.mulAdd(localForward, input.rollInput * flight.rollTorque);

        physics.body.applyTorque(torque);
        physics.body.setDamping(flight.linearDrag, flight.angularDrag);

        flight.currentThrottle = effectiveThrottle;
        physics.body.activate();
    }
}
