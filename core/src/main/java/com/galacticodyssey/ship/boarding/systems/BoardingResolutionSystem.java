package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.ship.boarding.BoardingDefenseComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.BoardingOutcome;
import com.galacticodyssey.ship.boarding.OwnedShipComponent;
import com.galacticodyssey.ship.boarding.OwnedShipComponent.Owner;
import com.galacticodyssey.ship.boarding.PlayerGarageComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.Subsystem;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolvedEvent;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.components.ShipInteriorComponent;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Applies a chosen boarding resolution: hijack / scrap / ransom / tow. Reacts to {@link
 * BoardingResolutionChosenEvent}, mutates ownership / wallet / cargo / reputation / garage,
 * publishes {@link BoardingResolvedEvent}, and finalizes the operation to {@code RESOLVED}.
 */
public class BoardingResolutionSystem extends EntitySystem {

    public static final int PRIORITY = 12;

    /** Credits per hull-HP point when scrapping or ransoming. */
    private static final float SCRAP_CREDITS_PER_HP = 0.5f;
    private static final float RANSOM_CREDITS_PER_HP = 1.0f;
    private static final float RANSOM_REPUTATION_DELTA = -8f;

    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);
    private static final ComponentMapper<ShipDataComponent> DATA_M =
        ComponentMapper.getFor(ShipDataComponent.class);
    private static final ComponentMapper<ShipSubsystemsComponent> SUBS_M =
        ComponentMapper.getFor(ShipSubsystemsComponent.class);
    private static final ComponentMapper<BoardingDefenseComponent> DEF_M =
        ComponentMapper.getFor(BoardingDefenseComponent.class);
    private static final ComponentMapper<PlayerWalletComponent> WALLET_M =
        ComponentMapper.getFor(PlayerWalletComponent.class);
    private static final ComponentMapper<CargoBayComponent> CARGO_M =
        ComponentMapper.getFor(CargoBayComponent.class);
    private static final ComponentMapper<PlayerGarageComponent> GARAGE_M =
        ComponentMapper.getFor(PlayerGarageComponent.class);

    private final EventBus eventBus;
    private final Queue<BoardingResolutionChosenEvent> pending = new ArrayDeque<>();
    private ImmutableArray<Entity> players;

    public BoardingResolutionSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(BoardingResolutionChosenEvent.class, pending::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerTagComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        players = null;
    }

    @Override
    public void update(float deltaTime) {
        BoardingResolutionChosenEvent event;
        while ((event = pending.poll()) != null) {
            resolve(event.target, event.outcome);
        }
    }

    /** Applies {@code outcome} to the operation on {@code target}, then finalizes it. */
    public void resolve(Entity target, BoardingOutcome outcome) {
        BoardingOperationComponent op = OP_M.get(target);
        if (op == null || op.phase != BoardingPhase.RESOLVING) return;
        Entity player = playerEntity();

        switch (outcome) {
            case HIJACK: hijack(target, op, player); break;
            case SCRAP:  scrap(target, op, player); break;
            case RANSOM: ransom(target, op, player); break;
            case TOW:    tow(target, op, player); break;
            case ENEMY_CAPTURE: /* handled by EnemyBoardingAISystem path */ break;
        }

        op.phase = BoardingPhase.RESOLVED;
        ShipInteriorComponent interior = target.getComponent(ShipInteriorComponent.class);
        if (interior != null) interior.active = false;
        eventBus.publish(new BoardingResolvedEvent(target, outcome));
    }

    private void hijack(Entity target, BoardingOperationComponent op, Entity player) {
        int tactics = playerTactics(player);
        if (!hijackSucceeds(tactics, 0)) return; // gate; default required=0 → succeeds
        flagOwned(target);
        restoreEngines(target);
        addGarageEntry(player, target, "HIJACK");
    }

    private void scrap(Entity target, BoardingOperationComponent op, Entity player) {
        ShipDataComponent data = DATA_M.get(target);
        float hull = (data != null) ? data.hullHp : 100f;
        int salvage = Math.max(1, Math.round(hull / 50f));
        if (player != null) {
            CargoBayComponent cargo = CARGO_M.get(player);
            if (cargo != null) cargo.contents.merge("salvaged_alloy", salvage, Integer::sum);
            PlayerWalletComponent wallet = WALLET_M.get(player);
            if (wallet != null) wallet.credits += Math.round(hull * SCRAP_CREDITS_PER_HP);
        }
        if (data != null) data.currentHullHp = 0f; // marked destroyed (removal handled in-game)
    }

    private void ransom(Entity target, BoardingOperationComponent op, Entity player) {
        ShipDataComponent data = DATA_M.get(target);
        float hull = (data != null) ? data.hullHp : 100f;
        if (player != null) {
            PlayerWalletComponent wallet = WALLET_M.get(player);
            if (wallet != null) wallet.credits += Math.round(hull * RANSOM_CREDITS_PER_HP);
        }
        BoardingDefenseComponent def = DEF_M.get(target);
        String factionId = (def != null) ? def.factionId : "independent";
        eventBus.publish(new ReputationChangeEvent(factionId, RANSOM_REPUTATION_DELTA, "boarding:ransom"));
    }

    private void tow(Entity target, BoardingOperationComponent op, Entity player) {
        flagOwned(target);
        addGarageEntry(player, target, "TOW");
        // Towed ships stay engine-disabled (cannot fly themselves).
    }

    private void flagOwned(Entity target) {
        OwnedShipComponent owned = target.getComponent(OwnedShipComponent.class);
        if (owned == null) {
            owned = new OwnedShipComponent();
            target.add(owned);
        }
        owned.owner = Owner.PLAYER;
        BoardingDefenseComponent def = DEF_M.get(target);
        if (def != null) owned.factionId = def.factionId;
    }

    private void restoreEngines(Entity target) {
        ShipSubsystemsComponent subs = SUBS_M.get(target);
        if (subs == null) return;
        Subsystem engines = subs.get(SubsystemType.ENGINES);
        if (engines != null) {
            engines.health = engines.maxHealth;
            engines.empDisableTimer = 0f;
            engines.destroyed = false;
        }
    }

    private void addGarageEntry(Entity player, Entity target, String via) {
        if (player == null) return;
        PlayerGarageComponent garage = GARAGE_M.get(player);
        if (garage == null) return;
        PlayerGarageComponent.GarageEntry entry = new PlayerGarageComponent.GarageEntry();
        ShipDataComponent data = DATA_M.get(target);
        if (data != null && data.blueprint != null) {
            entry.seed = data.blueprint.seed;
            entry.sizeClass = data.blueprint.sizeClass.name();
        }
        entry.shipName = "Captured Ship";
        entry.acquiredVia = via;
        garage.ships.add(entry);
    }

    private Entity playerEntity() {
        return (players != null && players.size() > 0) ? players.first() : null;
    }

    private int playerTactics(Entity player) {
        // Tactics skill source is not yet wired into combat; default high so hijack succeeds.
        return 100;
    }

    /** Deterministic hijack gate: succeeds when the player's Tactics meets the requirement. */
    public static boolean hijackSucceeds(int tacticsSkill, int requiredTactics) {
        return tacticsSkill >= requiredTactics;
    }
}
