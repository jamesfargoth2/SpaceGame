package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipFlightComponent;

public class ShipFlightSystem extends EntitySystem {

    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ShipFlightComponent> flightMapper =
        ComponentMapper.getFor(ShipFlightComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<PlayerStateComponent> stateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);

    private ImmutableArray<Entity> playerEntities;

    private final Vector3 force = new Vector3();
    private final Vector3 torque = new Vector3();
    private final Vector3 localForward = new Vector3();
    private final Vector3 localRight = new Vector3();
    private final Vector3 localUp = new Vector3();
    private final Matrix4 shipTransform = new Matrix4();

    public ShipFlightSystem() {
        super(1);
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerInputComponent.class,
            PlayerStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (playerEntities.size() == 0) return;

        Entity player = playerEntities.first();
        PlayerStateComponent state = stateMapper.get(player);
        if (state.currentMode != PlayerMode.PILOTING || state.currentShip == null) return;

        PlayerInputComponent input = inputMapper.get(player);
        Entity ship = state.currentShip;

        PhysicsBodyComponent physics = physicsMapper.get(ship);
        ShipFlightComponent flight = flightMapper.get(ship);
        if (physics == null || physics.body == null || flight == null) return;

        physics.body.getWorldTransform(shipTransform);

        localForward.set(0, 0, -1).rot(shipTransform).nor();
        localRight.set(1, 0, 0).rot(shipTransform).nor();
        localUp.set(0, 1, 0).rot(shipTransform).nor();

        force.setZero();
        force.mulAdd(localForward, input.moveForward * flight.linearThrust);
        force.mulAdd(localRight, input.moveStrafe * flight.linearThrust * flight.strafeThrustFraction);
        if (input.thrustUp) force.mulAdd(localUp, flight.linearThrust * flight.verticalThrustFraction);
        if (input.thrustDown) force.mulAdd(localUp, -flight.linearThrust * flight.verticalThrustFraction);

        physics.body.applyCentralForce(force);

        torque.setZero();
        torque.mulAdd(localRight, input.mouseDeltaY * flight.pitchYawTorque);
        torque.mulAdd(localUp, -input.mouseDeltaX * flight.pitchYawTorque);
        if (input.rollLeft) torque.mulAdd(localForward, flight.rollTorque);
        if (input.rollRight) torque.mulAdd(localForward, -flight.rollTorque);

        physics.body.applyTorque(torque);

        physics.body.setDamping(flight.linearDrag, flight.angularDrag);

        flight.currentThrottle = input.moveForward;
        physics.body.activate();
    }
}
