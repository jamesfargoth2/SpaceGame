package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.PlayerEnteredHostileInteriorEvent;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.components.ShipInteriorComponent;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Transitions the player into a freshly-breached hostile interior. Reacts to {@link
 * ShipBreachedEvent}: when the player's ship is the aggressor, activates the target interior,
 * switches the player to {@code ON_FOOT_INTERIOR} on the target, teleports them to the entry
 * point, and advances the operation to {@code INTERIOR_COMBAT}.
 *
 * <p>The optional {@code mainWorld} reference is used to detach the player's rigid body from the
 * exterior physics world on entry; it is null-safe so the transition logic is testable headless.
 */
public class BoardingEntrySystem extends EntitySystem {

    public static final int PRIORITY = 2;

    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<ShipInteriorComponent> INTERIOR_M =
        ComponentMapper.getFor(ShipInteriorComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);

    private final EventBus eventBus;
    private final btDiscreteDynamicsWorld mainWorld; // nullable (tests)
    private final Queue<ShipBreachedEvent> pending = new ArrayDeque<>();
    private final Matrix4 shipMat = new Matrix4();
    private final Vector3 entryWorld = new Vector3();
    private ImmutableArray<Entity> players;

    public BoardingEntrySystem(EventBus eventBus, btDiscreteDynamicsWorld mainWorld) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.mainWorld = mainWorld;
        eventBus.subscribe(ShipBreachedEvent.class, pending::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class, TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        players = null;
    }

    @Override
    public void update(float deltaTime) {
        ShipBreachedEvent event;
        while ((event = pending.poll()) != null) {
            BoardingOperationComponent op = OP_M.get(event.target);
            if (op == null || !op.playerIsAggressor) continue;
            enterInterior(event.target, op, event.entryLocalPosition);
        }
    }

    private void enterInterior(Entity target, BoardingOperationComponent op, Vector3 entryLocal) {
        if (players == null || players.size() == 0) return;
        Entity player = players.first();
        PlayerStateComponent state = STATE_M.get(player);

        // Keep the operation's entry point in sync with the breach that opened it.
        op.entryLocalPosition.set(entryLocal);

        // Activate the target interior so its physics/render systems step.
        ShipInteriorComponent interior = INTERIOR_M.get(target);
        if (interior != null) interior.active = true;

        // Compute entry world position from the target transform + ship-local entry point.
        TransformComponent targetTransform = TRANSFORM_M.get(target);
        if (targetTransform != null) {
            shipMat.set(targetTransform.position, targetTransform.rotation);
            entryWorld.set(entryLocal).mul(shipMat);
        } else {
            entryWorld.set(entryLocal);
        }

        detachFromMainWorld(player);
        teleport(player, entryWorld);

        state.currentMode = PlayerMode.ON_FOOT_INTERIOR;
        state.currentShip = target;

        op.phase = BoardingPhase.INTERIOR_COMBAT;
        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
    }

    private void detachFromMainWorld(Entity player) {
        if (mainWorld == null) return;
        PhysicsBodyComponent physics = PHYSICS_M.get(player);
        if (physics != null && physics.body != null) {
            // NOTE: removed from main world but not re-added to interiorWorld — on-foot interior
            // body physics is not wired project-wide yet.
            // TODO: add to interior.interiorWorld once it lands.
            mainWorld.removeRigidBody(physics.body);
        }
    }

    private void teleport(Entity player, Vector3 position) {
        TransformComponent transform = TRANSFORM_M.get(player);
        if (transform != null) transform.position.set(position);
        PhysicsBodyComponent physics = PHYSICS_M.get(player);
        if (physics != null && physics.body != null) {
            Matrix4 bodyTransform = physics.body.getWorldTransform();
            bodyTransform.setTranslation(position);
            physics.body.setWorldTransform(bodyTransform);
            physics.body.setLinearVelocity(new Vector3(0, 0, 0));
            physics.body.setAngularVelocity(new Vector3(0, 0, 0));
            physics.body.activate();
        }
    }
}
