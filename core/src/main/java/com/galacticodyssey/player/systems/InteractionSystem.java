package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.*;
import com.galacticodyssey.npc.systems.DialogSystem;
import com.galacticodyssey.npc.components.NpcDialogComponent;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.planet.terrain.GroundVehicleComponent;
import com.galacticodyssey.planet.terrain.VehicleBayService;
import com.galacticodyssey.planet.terrain.VehicleEntryPointComponent;
import com.galacticodyssey.planet.terrain.VehicleTagComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.components.*;

public class InteractionSystem extends EntitySystem {

    private final EventBus eventBus;
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerStateComponent> stateMapper = ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper = ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<ShipEntryPointComponent> entryMapper = ComponentMapper.getFor(ShipEntryPointComponent.class);
    private final ComponentMapper<PilotSeatComponent> seatMapper = ComponentMapper.getFor(PilotSeatComponent.class);
    private final ComponentMapper<ShipInteriorComponent> interiorMapper = ComponentMapper.getFor(ShipInteriorComponent.class);
    private final ComponentMapper<NpcDialogComponent> npcDialogMapper = ComponentMapper.getFor(NpcDialogComponent.class);
    private final ComponentMapper<NpcIdentityComponent> npcIdentityMapper = ComponentMapper.getFor(NpcIdentityComponent.class);
    private final ComponentMapper<VehicleEntryPointComponent> vehicleEntryMapper =
        ComponentMapper.getFor(VehicleEntryPointComponent.class);
    private final ComponentMapper<com.galacticodyssey.ship.components.VehicleBayComponent> bayMapper =
        ComponentMapper.getFor(com.galacticodyssey.ship.components.VehicleBayComponent.class);

    private ImmutableArray<Entity> playerEntities;
    private ImmutableArray<Entity> shipEntities;
    private ImmutableArray<Entity> npcEntities;
    private ImmutableArray<Entity> vehicleEntities;
    private com.badlogic.ashley.utils.ImmutableArray<Entity> bayShipEntities;
    private VehicleBayService bayService; // optional; set during GameWorld wiring
    private DialogSystem dialogSystem; // optional; suppresses NPC prompt while dialog is active

    public void setVehicleBayService(VehicleBayService bayService) { this.bayService = bayService; }
    public void setDialogSystem(DialogSystem dialogSystem) { this.dialogSystem = dialogSystem; }
    private final Vector3 tempVec = new Vector3();
    private final Vector3 worldPos = new Vector3();
    private final Matrix4 shipWorldMat = new Matrix4();
    private int logTimer = 0;
    private int npcLogTimer = 0;

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
        npcEntities = engine.getEntitiesFor(Family.all(
            NpcDialogComponent.class, NpcIdentityComponent.class, TransformComponent.class).get());
        vehicleEntities = engine.getEntitiesFor(Family.all(
            VehicleTagComponent.class, VehicleEntryPointComponent.class, TransformComponent.class).get());
        bayShipEntities = engine.getEntitiesFor(Family.all(
            com.galacticodyssey.ship.components.VehicleBayComponent.class,
            TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (playerEntities.size() == 0) return;
        Entity player = playerEntities.first();
        TransformComponent playerTransform = transformMapper.get(player);
        PlayerStateComponent state = stateMapper.get(player);
        PlayerInputComponent input = inputMapper.get(player);

        eventBus.publish(new InteractionPromptEvent("", false));

        switch (state.currentMode) {
            case ON_FOOT_EXTERIOR:
                if (!checkNpcDialog(player, playerTransform, input)) {
                    if (!checkEnterVehicle(player, playerTransform, state, input)) {
                        checkShipEntry(player, playerTransform, state, input);
                    }
                }
                break;
            case ON_FOOT_INTERIOR:
                checkPilotSeat(player, state, input);
                checkShipExit(player, state, input);
                break;
            case PILOTING: checkStopPiloting(player, state, input); break;
            case DRIVING: checkExitVehicle(player, state, input); break;
        }
        input.interactPressed = false;
    }

    private boolean checkNpcDialog(Entity player, TransformComponent playerTransform,
                                   PlayerInputComponent input) {
        if (dialogSystem != null && dialogSystem.isActive()) return true;

        Entity nearestNpc = null;
        float nearestDist = Float.MAX_VALUE;

        for (int i = 0; i < npcEntities.size(); i++) {
            Entity npc = npcEntities.get(i);
            TransformComponent npcTransform = transformMapper.get(npc);
            NpcDialogComponent dialog = npcDialogMapper.get(npc);
            float dist = tempVec.set(playerTransform.position).dst(npcTransform.position);
            if (dist < dialog.interactionRadius && dist < nearestDist) {
                nearestDist = dist;
                nearestNpc = npc;
            }
        }

        npcLogTimer++;
        if (npcLogTimer % 120 == 1 && Gdx.app != null) {
            Gdx.app.log("Dialog", "NPC scan: npcEntities=" + npcEntities.size()
                + (nearestNpc != null ? " nearestDist=" + nearestDist : " (none in radius)"));
        }

        if (nearestNpc == null) return false;

        NpcIdentityComponent identity = npcIdentityMapper.get(nearestNpc);
        NpcDialogComponent dialog = npcDialogMapper.get(nearestNpc);
        String npcName = identity.name != null ? identity.name : "NPC";

        eventBus.publish(new InteractionPromptEvent("[F] Talk to " + npcName, true));

        if (input.interactPressed) {
            if (Gdx.app != null) Gdx.app.log("Dialog", "[F] pressed near '" + npcName + "' dist=" + nearestDist
                + " publishing NpcDialogueEvent topic='" + dialog.dialogTreeId + "'");
            eventBus.publish(new NpcDialogueEvent(identity.npcId, dialog.dialogTreeId));
        }

        return true;
    }

    private void checkShipEntry(Entity player, TransformComponent playerTransform,
                                PlayerStateComponent state, PlayerInputComponent input) {
        Entity nearestShip = null;
        float nearestDist = Float.MAX_VALUE;

        for (int i = 0; i < shipEntities.size(); i++) {
            Entity ship = shipEntities.get(i);
            ShipEntryPointComponent entry = entryMapper.get(ship);
            if (!entry.rampDeployed) continue;
            TransformComponent shipTransformExt = transformMapper.get(ship);
            toWorldSpace(shipTransformExt, entry.localExteriorPosition, worldPos);
            float dist = tempVec.set(playerTransform.position).dst(worldPos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestShip = ship;
            }
        }

        if (nearestShip == null) {
            state.interactionTarget = null;
            eventBus.publish(new InteractionPromptEvent("No ships detected", true));
            return;
        }

        logTimer++;
        if (logTimer % 120 == 1) {
            TransformComponent st = transformMapper.get(nearestShip);
            ShipEntryPointComponent entry = entryMapper.get(nearestShip);
            Gdx.app.log("Interaction",
                "ships=" + shipEntities.size()
                + " shipPos=" + st.position
                + " extLocal=" + entry.localExteriorPosition
                + " extWorld=" + worldPos
                + " playerPos=" + playerTransform.position
                + " dist=" + nearestDist
                + " trigger=" + entry.triggerRadius);
        }

        ShipEntryPointComponent entry = entryMapper.get(nearestShip);
        if (nearestDist < entry.triggerRadius) {
            state.interactionTarget = nearestShip;
            eventBus.publish(new InteractionPromptEvent(
                String.format("[F] Enter Ship (%.1fm)", nearestDist), true));
            if (input.interactPressed) {
                state.currentMode = PlayerMode.PILOTING;
                state.currentShip = nearestShip;

                PilotSeatComponent seat = seatMapper.get(nearestShip);
                if (seat != null) {
                    seat.occupied = true;
                    seat.occupant = player;
                }

                freezePlayerBody(player);

                eventBus.publish(new PlayerEnterShipEvent(player, nearestShip));
                eventBus.publish(new PlayerStartPilotingEvent(player, nearestShip));
            }
        } else {
            state.interactionTarget = null;
            eventBus.publish(new InteractionPromptEvent(
                String.format("Ship: %.1fm away", nearestDist), true));
        }
    }

    private void checkPilotSeat(Entity player, PlayerStateComponent state, PlayerInputComponent input) {
        if (state.currentShip == null) return;
        PilotSeatComponent seat = seatMapper.get(state.currentShip);
        TransformComponent playerTransform = transformMapper.get(player);
        TransformComponent shipTransform = transformMapper.get(state.currentShip);
        if (seat == null) return;

        toWorldSpace(shipTransform, seat.interiorPosition, worldPos);
        float dist = tempVec.set(playerTransform.position).dst(worldPos);

        if (dist < seat.triggerRadius) {
            eventBus.publish(new InteractionPromptEvent("[F] Pilot Ship", true));
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
        TransformComponent shipTransform = transformMapper.get(state.currentShip);
        if (entry == null) return;

        toWorldSpace(shipTransform, entry.interiorPosition, worldPos);
        float dist = tempVec.set(playerTransform.position).dst(worldPos);

        if (dist < entry.triggerRadius) {
            Entity homeShip = boardingHomeShip(state.currentShip);
            if (homeShip != null) {
                eventBus.publish(new InteractionPromptEvent("[F] Return to Your Ship", true));
                if (input.interactPressed) {
                    ShipInteriorComponent interior = interiorMapper.get(state.currentShip);
                    if (interior != null) interior.active = false;
                    state.currentMode = PlayerMode.PILOTING;
                    state.currentShip = homeShip;
                    TransformComponent homeTransform = transformMapper.get(homeShip);
                    if (homeTransform != null) {
                        worldPos.set(homeTransform.position);
                        teleportPlayer(player, worldPos);
                    }
                }
                return;
            }

            eventBus.publish(new InteractionPromptEvent("[F] Exit Ship", true));
            if (input.interactPressed) {
                ShipInteriorComponent interior = interiorMapper.get(state.currentShip);
                interior.active = false;
                Entity ship = state.currentShip;
                state.currentMode = PlayerMode.ON_FOOT_EXTERIOR;
                state.currentShip = null;
                eventBus.publish(new PlayerExitShipEvent(player, ship));

                teleportPlayer(player, worldPos);
            }
        }
    }

    private void checkStopPiloting(Entity player, PlayerStateComponent state, PlayerInputComponent input) {
        if (input.interactPressed && state.currentShip != null) {
            Entity ship = state.currentShip;

            PilotSeatComponent seat = seatMapper.get(ship);
            if (seat != null) { seat.occupied = false; seat.occupant = null; }

            state.currentMode = PlayerMode.ON_FOOT_EXTERIOR;
            state.currentShip = null;

            TransformComponent shipTransform = transformMapper.get(ship);
            ShipEntryPointComponent entry = entryMapper.get(ship);
            if (entry != null) {
                toWorldSpace(shipTransform, entry.localExteriorPosition, worldPos);
            }

            unfreezePlayerBody(player);
            teleportPlayer(player, worldPos);

            eventBus.publish(new PlayerStopPilotingEvent(player, ship));
            eventBus.publish(new PlayerExitShipEvent(player, ship));
        }
    }

    private boolean checkEnterVehicle(Entity player, TransformComponent playerTransform,
                                      PlayerStateComponent state, PlayerInputComponent input) {
        Entity nearest = null;
        float nearestDist = Float.MAX_VALUE;
        for (int i = 0; i < vehicleEntities.size(); i++) {
            Entity v = vehicleEntities.get(i);
            TransformComponent vt = transformMapper.get(v);
            VehicleEntryPointComponent entry = vehicleEntryMapper.get(v);
            float dist = tempVec.set(playerTransform.position).dst(vt.position);
            if (dist < entry.triggerRadius && dist < nearestDist) {
                nearestDist = dist;
                nearest = v;
            }
        }
        if (nearest == null) return false;

        eventBus.publish(new InteractionPromptEvent("[F] Enter Vehicle", true));
        if (input.interactPressed) {
            state.currentMode = PlayerMode.DRIVING;
            state.currentVehicle = nearest;
            freezePlayerBody(player);
            eventBus.publish(new PlayerEnterVehicleEvent(player, nearest));
        }
        return true;
    }

    private void checkExitVehicle(Entity player, PlayerStateComponent state, PlayerInputComponent input) {
        if (state.currentVehicle == null) return;
        Entity vehicle = state.currentVehicle;
        TransformComponent vt = transformMapper.get(vehicle);

        // Look for a nearby ship bay to retrieve into — retrieve takes priority over plain exit.
        Entity bayShip = null;
        if (vt != null && bayShipEntities != null) {
            float best = Float.MAX_VALUE;
            for (int i = 0; i < bayShipEntities.size(); i++) {
                Entity s = bayShipEntities.get(i);
                com.galacticodyssey.ship.components.VehicleBayComponent bay = bayMapper.get(s);
                TransformComponent st = transformMapper.get(s);
                float dist = tempVec.set(vt.position).dst(st.position);
                if (dist < bay.triggerRadius && dist < best) { best = dist; bayShip = s; }
            }
        }

        if (bayShip != null) {
            eventBus.publish(new InteractionPromptEvent("[F] Retrieve Vehicle", true));
            if (input.interactPressed) {
                state.currentMode = PlayerMode.ON_FOOT_EXTERIOR;
                state.currentVehicle = null;
                unfreezePlayerBody(player);
                TransformComponent st = transformMapper.get(bayShip);
                if (st != null) { worldPos.set(st.position); teleportPlayer(player, worldPos); }
                if (bayService != null) bayService.retrieve(bayShip, vehicle);
            }
            return;
        }

        // Plain exit (no nearby bay).
        eventBus.publish(new InteractionPromptEvent("[F] Exit Vehicle", true));
        if (!input.interactPressed) return;

        state.currentMode = PlayerMode.ON_FOOT_EXTERIOR;
        state.currentVehicle = null;
        GroundVehicleComponent gv = vehicle.getComponent(GroundVehicleComponent.class);
        if (gv != null) { gv.throttleInput = 0f; gv.steerInput = 0f; }

        unfreezePlayerBody(player);
        if (vt != null) {
            VehicleEntryPointComponent entry = vehicleEntryMapper.get(vehicle);
            if (entry != null) {
                worldPos.set(vt.position).add(entry.localExitOffset);
            } else {
                worldPos.set(vt.position).add(2f, 0f, 0f);
            }
            teleportPlayer(player, worldPos);
        }
        eventBus.publish(new PlayerExitVehicleEvent(player, vehicle));
    }

    /**
     * If {@code currentShip} is a ship the player has boarded as an aggressor (active boarding
     * op, not yet RESOLVED), returns the player's own (aggressor) ship to return to on exit;
     * otherwise null (the ship is the player's own — use the normal exit path).
     */
    public static Entity boardingHomeShip(Entity currentShip) {
        if (currentShip == null) return null;
        BoardingOperationComponent op = currentShip.getComponent(BoardingOperationComponent.class);
        if (op == null) return null;
        if (op.phase == BoardingOperationComponent.BoardingPhase.RESOLVED
                || op.phase == BoardingOperationComponent.BoardingPhase.NONE) return null;
        // Only route to the aggressor ship when the PLAYER is the aggressor. For an
        // NPC-boards-player op, aggressorShip is the NPC — returning it would wrongly route the
        // player toward the NPC on exit. Fall through to the normal exit path instead.
        if (!op.playerIsAggressor) return null;
        return op.aggressorShip;
    }

    private void freezePlayerBody(Entity player) {
        PhysicsBodyComponent physics = physicsMapper.get(player);
        if (physics != null && physics.body != null) {
            physics.body.setGravity(new Vector3(0, 0, 0));
            physics.body.setLinearVelocity(new Vector3(0, 0, 0));
            physics.body.setAngularVelocity(new Vector3(0, 0, 0));
        }
    }

    private void unfreezePlayerBody(Entity player) {
        PhysicsBodyComponent physics = physicsMapper.get(player);
        if (physics != null && physics.body != null) {
            physics.body.setGravity(new Vector3(0, -9.81f, 0));
            physics.body.activate();
        }
    }

    private void toWorldSpace(TransformComponent shipTransform, Vector3 localPos, Vector3 out) {
        shipWorldMat.set(shipTransform.position, shipTransform.rotation);
        out.set(localPos).mul(shipWorldMat);
    }

    private void teleportPlayer(Entity player, Vector3 position) {
        PhysicsBodyComponent physics = physicsMapper.get(player);
        if (physics != null && physics.body != null) {
            Matrix4 bodyTransform = physics.body.getWorldTransform();
            bodyTransform.setTranslation(position);
            physics.body.setWorldTransform(bodyTransform);
            physics.body.setLinearVelocity(new Vector3(0, 0, 0));
            physics.body.setAngularVelocity(new Vector3(0, 0, 0));
            physics.body.activate();
        }
        TransformComponent transform = transformMapper.get(player);
        if (transform != null) {
            transform.position.set(position);
        }
    }
}
