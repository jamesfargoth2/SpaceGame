package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.mission.job.ReputationQuery;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.ship.boarding.BoardingDefenseComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.components.ShipDataComponent;

/**
 * Bidirectional boarding: when the player's ship has been disabled (its operation is VULNERABLE
 * and no aggressor has been assigned), a hostile NPC ship within {@link #BOARD_RANGE} becomes the
 * aggressor and launches a breaching pod at the player. A ship counts as hostile when the injected
 * {@link ReputationQuery} reports its faction standing strictly below the HOSTILE threshold
 * ({@link #HOSTILE_STANDING}). When no {@code ReputationQuery} is provided (reputation is not yet
 * wired into GameWorld), any in-range ship is treated as hostile as a fallback. Marks the operation
 * {@code playerIsAggressor = false} so the rest of the pipeline runs inverted.
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
    private static final ComponentMapper<BoardingDefenseComponent> DEF_M =
        ComponentMapper.getFor(BoardingDefenseComponent.class);

    /** Standing strictly below this is HOSTILE (matches ReputationTier.HOSTILE). */
    private static final float HOSTILE_STANDING = -50f;

    private final BoardingAttachSystem attachSystem;
    private final ReputationQuery reputation; // nullable: when null, any ship is treated hostile
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> ships;

    public EnemyBoardingAISystem(BoardingAttachSystem attachSystem, ReputationQuery reputation) {
        super(PRIORITY);
        this.attachSystem = attachSystem;
        this.reputation = reputation;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class).get());
        // Candidate aggressor ships: real ships (carry ShipDataComponent) with a position.
        // The player's own ship is excluded by reference below.
        ships = engine.getEntitiesFor(Family.all(ShipDataComponent.class, TransformComponent.class).get());
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
            if (!isHostile(ship)) continue;
            // An NPC ship: not the player's own. (Player ownership is implied by currentShip.)
            float d2 = TRANSFORM_M.get(ship).position.dst2(origin);
            if (d2 <= bestDist2) {
                bestDist2 = d2;
                best = ship;
            }
        }
        return best;
    }

    /** A ship is a valid boarding aggressor if its faction is hostile to the player. */
    private boolean isHostile(Entity ship) {
        if (reputation == null) return true; // no reputation wired → preserve prior behavior
        BoardingDefenseComponent def = DEF_M.get(ship);
        String factionId = (def != null) ? def.factionId : null;
        if (factionId == null) return true; // unknown faction → treat as hostile
        return reputation.getStanding(factionId) < HOSTILE_STANDING;
    }
}
