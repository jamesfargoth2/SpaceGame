package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.*;

public class InteractionSystem extends EntitySystem {

    private final EventBus eventBus;
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerStateComponent> stateMapper = ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper = ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<ShipEntryPointComponent> entryMapper = ComponentMapper.getFor(ShipEntryPointComponent.class);
    private final ComponentMapper<PilotSeatComponent> seatMapper = ComponentMapper.getFor(PilotSeatComponent.class);
    private final ComponentMapper<ShipInteriorComponent> interiorMapper = ComponentMapper.getFor(ShipInteriorComponent.class);

    private ImmutableArray<Entity> playerEntities;
    private ImmutableArray<Entity> shipEntities;
    private final Vector3 tempVec = new Vector3();

    public InteractionSystem(EventBus eventBus) {
        super(0);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, TransformComponent.class,
            PlayerInputComponent.class, PlayerStateComponent.class).get());
        shipEntities = engine.getEntitiesFor(Family.all(
            ShipEntryPointComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (playerEntities.size() == 0) return;
        Entity player = playerEntities.first();
        TransformComponent playerTransform = transformMapper.get(player);
        PlayerStateComponent state = stateMapper.get(player);
        PlayerInputComponent input = inputMapper.get(player);

        switch (state.currentMode) {
            case ON_FOOT_EXTERIOR: checkShipEntry(player, playerTransform, state, input); break;
            case ON_FOOT_INTERIOR:
                checkPilotSeat(player, state, input);
                checkShipExit(player, state, input);
                break;
            case PILOTING: checkStopPiloting(player, state, input); break;
        }
        input.interactPressed = false;
    }

    private void checkShipEntry(Entity player, TransformComponent playerTransform,
                                PlayerStateComponent state, PlayerInputComponent input) {
        Entity nearestShip = null;
        float nearestDist = Float.MAX_VALUE;

        for (int i = 0; i < shipEntities.size(); i++) {
            Entity ship = shipEntities.get(i);
            ShipEntryPointComponent entry = entryMapper.get(ship);
            if (!entry.rampDeployed) continue;
            float dist = tempVec.set(playerTransform.position).dst(entry.worldPosition);
            if (dist < entry.triggerRadius && dist < nearestDist) {
                nearestDist = dist;
                nearestShip = ship;
            }
        }

        if (nearestShip != null) {
            state.interactionTarget = nearestShip;
            eventBus.publish(new InteractionPromptEvent("Press E to enter ship", true));
            if (input.interactPressed) {
                eventBus.publish(new PlayerEnterShipEvent(player, nearestShip));
                state.currentMode = PlayerMode.ON_FOOT_INTERIOR;
                state.currentShip = nearestShip;
                ShipInteriorComponent interior = interiorMapper.get(nearestShip);
                interior.active = true;
            }
        } else {
            if (state.interactionTarget != null) {
                eventBus.publish(new InteractionPromptEvent("", false));
                state.interactionTarget = null;
            }
        }
    }

    private void checkPilotSeat(Entity player, PlayerStateComponent state, PlayerInputComponent input) {
        if (state.currentShip == null) return;
        PilotSeatComponent seat = seatMapper.get(state.currentShip);
        TransformComponent playerTransform = transformMapper.get(player);
        if (seat == null) return;
        float dist = tempVec.set(playerTransform.position).dst(seat.interiorPosition);
        if (dist < seat.triggerRadius) {
            eventBus.publish(new InteractionPromptEvent("Press E to pilot", true));
            if (input.interactPressed) {
                seat.occupied = true;
                seat.occupant = player;
                state.currentMode = PlayerMode.PILOTING;
                eventBus.publish(new PlayerStartPilotingEvent(player, state.currentShip));
            }
        }
    }

    private void checkShipExit(Entity player, PlayerStateComponent state, PlayerInputComponent input) {
        if (state.currentShip == null) return;
        ShipEntryPointComponent entry = entryMapper.get(state.currentShip);
        TransformComponent playerTransform = transformMapper.get(player);
        if (entry == null) return;
        float dist = tempVec.set(playerTransform.position).dst(entry.interiorPosition);
        if (dist < entry.triggerRadius) {
            eventBus.publish(new InteractionPromptEvent("Press E to exit ship", true));
            if (input.interactPressed) {
                ShipInteriorComponent interior = interiorMapper.get(state.currentShip);
                interior.active = false;
                Entity ship = state.currentShip;
                state.currentMode = PlayerMode.ON_FOOT_EXTERIOR;
                state.currentShip = null;
                eventBus.publish(new PlayerExitShipEvent(player, ship));
            }
        }
    }

    private void checkStopPiloting(Entity player, PlayerStateComponent state, PlayerInputComponent input) {
        if (input.interactPressed && state.currentShip != null) {
            PilotSeatComponent seat = seatMapper.get(state.currentShip);
            if (seat != null) { seat.occupied = false; seat.occupant = null; }
            state.currentMode = PlayerMode.ON_FOOT_INTERIOR;
            eventBus.publish(new PlayerStopPilotingEvent(player, state.currentShip));
        }
    }
}
