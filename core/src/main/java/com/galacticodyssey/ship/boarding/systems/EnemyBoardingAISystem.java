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
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.BreachingPodComponent;

/**
 * Bidirectional boarding: when the player's ship has been disabled (its operation is VULNERABLE
 * and no aggressor has been assigned), a hostile NPC ship within {@link #BOARD_RANGE} becomes the
 * aggressor and launches a breaching pod at the player. Marks the operation {@code
 * playerIsAggressor = false} so the rest of the pipeline runs inverted.
 */
public class EnemyBoardingAISystem extends EntitySystem {

    public static final int PRIORITY = 10;
    public static final float BOARD_RANGE = 200f;

    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);

    private final EventBus eventBus;
    private final BoardingAttachSystem attachSystem;
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> ships;

    public EnemyBoardingAISystem(EventBus eventBus, BoardingAttachSystem attachSystem) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.attachSystem = attachSystem;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class).get());
        // Candidate aggressor ships: any positioned entity that is neither the player avatar
        // nor an in-flight breaching pod. The player's own ship is excluded by reference below.
        ships = engine.getEntitiesFor(Family.all(TransformComponent.class)
            .exclude(PlayerTagComponent.class, BreachingPodComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        players = null;
        ships = null;
    }

    @Override
    public void update(float deltaTime) {
        if (players == null || players.size() == 0) return;
        Entity playerShip = STATE_M.get(players.first()).currentShip;
        if (playerShip == null) return;

        BoardingOperationComponent op = OP_M.get(playerShip);
        if (op == null || op.phase != BoardingPhase.VULNERABLE) return;
        if (op.aggressorShip != null) return; // already being boarded

        TransformComponent playerT = TRANSFORM_M.get(playerShip);
        if (playerT == null) return;

        Entity npc = nearestHostileNpc(playerShip, playerT.position);
        if (npc == null) return;

        op.playerIsAggressor = false;
        op.aggressorShip = npc;
        attachSystem.launchPod(npc, playerShip);
    }

    private Entity nearestHostileNpc(Entity playerShip, Vector3 origin) {
        if (ships == null) return null;
        Entity best = null;
        float bestDist2 = BOARD_RANGE * BOARD_RANGE;
        for (int i = 0, n = ships.size(); i < n; i++) {
            Entity ship = ships.get(i);
            if (ship == playerShip) continue;
            // An NPC ship: not the player's own. (Player ownership is implied by currentShip.)
            float d2 = TRANSFORM_M.get(ship).position.dst2(origin);
            if (d2 <= bestDist2) {
                bestDist2 = d2;
                best = ship;
            }
        }
        return best;
    }
}
