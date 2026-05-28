package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;

/**
 * In-game initiation of a breach-pod boarding. While the player is PILOTING and a VULNERABLE
 * ship is within {@link #POD_RANGE}, pressing the board key (G → {@code boardPressed}) launches
 * a breaching pod via {@link BoardingAttachSystem#launchPod}.
 */
public class BoardingInitiationSystem extends EntitySystem {

    public static final int PRIORITY = 1;
    public static final float POD_RANGE = 150f;

    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private static final ComponentMapper<PlayerInputComponent> INPUT_M =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);

    private final EventBus eventBus;
    private final BoardingAttachSystem attachSystem;
    private ImmutableArray<Entity> players;

    public BoardingInitiationSystem(EventBus eventBus, BoardingAttachSystem attachSystem) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.attachSystem = attachSystem;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class, PlayerInputComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        players = null;
    }

    @Override
    public void update(float deltaTime) {
        if (players == null || players.size() == 0) return;
        Entity player = players.first();
        PlayerStateComponent state = STATE_M.get(player);
        PlayerInputComponent input = INPUT_M.get(player);
        if (state.currentMode != PlayerMode.PILOTING || state.currentShip == null) return;
        if (!input.boardPressed) return;
        input.boardPressed = false;

        TransformComponent shipT = TRANSFORM_M.get(state.currentShip);
        Vector3 origin = (shipT != null) ? shipT.position : Vector3.Zero;
        Entity target = nearestBoardable(getEngine(), state.currentShip, origin, POD_RANGE);
        if (target != null) {
            attachSystem.launchPod(state.currentShip, target);
        }
    }

    /**
     * Returns the nearest ship (other than {@code self}) within {@code range} of {@code origin}
     * that is in the VULNERABLE boarding phase, or null.
     */
    public static Entity nearestBoardable(Engine engine, Entity self, Vector3 origin, float range) {
        ImmutableArray<Entity> ships = engine.getEntitiesFor(
            Family.all(BoardingOperationComponent.class, TransformComponent.class).get());
        Entity best = null;
        float bestDist2 = range * range;
        for (int i = 0, n = ships.size(); i < n; i++) {
            Entity ship = ships.get(i);
            if (ship == self) continue;
            if (OP_M.get(ship).phase != BoardingPhase.VULNERABLE) continue;
            float d2 = TRANSFORM_M.get(ship).position.dst2(origin);
            if (d2 <= bestDist2) {
                bestDist2 = d2;
                best = ship;
            }
        }
        return best;
    }
}
