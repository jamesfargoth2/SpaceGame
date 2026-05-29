package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.AwayTeamComponent;
import com.galacticodyssey.ship.boarding.BoardingDefenderComponent;
import com.galacticodyssey.ship.boarding.BoardingDefenseComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.BoardingOutcome;
import com.galacticodyssey.ship.boarding.BridgeComponent;
import com.galacticodyssey.ship.boarding.events.BoardingClearedEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionRequestedEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolvedEvent;
import com.galacticodyssey.ship.boarding.events.PlayerEnteredHostileInteriorEvent;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.components.ShipInteriorComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Owns interior boarding combat: spawns defenders (and the away team) when the player enters a
 * hostile interior, tags the bridge, and watches for the win condition (all defenders dead, or
 * the player capturing the bridge). On a win it publishes {@link BoardingClearedEvent} and
 * advances the operation to {@code RESOLVING}.
 */
public class BoardingCombatSystem extends EntitySystem {

    public static final int PRIORITY = 11;

    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);
    private static final ComponentMapper<BoardingDefenseComponent> DEF_M =
        ComponentMapper.getFor(BoardingDefenseComponent.class);
    private static final ComponentMapper<BoardingDefenderComponent> DEFENDER_M =
        ComponentMapper.getFor(BoardingDefenderComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<ShipInteriorComponent> INTERIOR_M =
        ComponentMapper.getFor(ShipInteriorComponent.class);
    private static final ComponentMapper<BridgeComponent> BRIDGE_M =
        ComponentMapper.getFor(BridgeComponent.class);

    private final EventBus eventBus;
    private final Queue<PlayerEnteredHostileInteriorEvent> entries = new ArrayDeque<>();
    private final Queue<EntityKilledEvent> kills = new ArrayDeque<>();
    private final Queue<BoardingResolvedEvent> resolutions = new ArrayDeque<>();
    private final Queue<ShipBreachedEvent> breaches = new ArrayDeque<>();
    /** Set while draining the kill queue when the player entity died this frame. */
    private boolean playerDied;
    /** Scratch list for deferred entity removal (avoids mutating a family mid-iteration). */
    private final List<Entity> toRemove = new ArrayList<>();
    private final Matrix4 shipMat = new Matrix4();
    private final Vector3 bridgeWorld = new Vector3();
    /**
     * Targets whose defenders were spawned during the current update. Ashley defers entity-add
     * until after a system's update, so the defender family is not yet populated this frame; skip
     * their win check until next frame to avoid a spurious same-frame bridge capture.
     */
    private final Set<Entity> spawnedThisFrame = new HashSet<>();

    private ImmutableArray<Entity> defenders;
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> operations;

    public BoardingCombatSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(PlayerEnteredHostileInteriorEvent.class, entries::add);
        eventBus.subscribe(EntityKilledEvent.class, kills::add);
        eventBus.subscribe(BoardingResolvedEvent.class, resolutions::add);
        eventBus.subscribe(ShipBreachedEvent.class, breaches::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        defenders = engine.getEntitiesFor(Family.all(BoardingDefenderComponent.class).get());
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, TransformComponent.class).get());
        operations = engine.getEntitiesFor(Family.all(BoardingOperationComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        defenders = null;
        players = null;
        operations = null;
    }

    @Override
    public void update(float deltaTime) {
        spawnedThisFrame.clear();
        ShipBreachedEvent breach;
        while ((breach = breaches.poll()) != null) {
            onNpcBreach(breach);
        }
        PlayerEnteredHostileInteriorEvent entry;
        while ((entry = entries.poll()) != null) {
            spawnForEntry(entry.targetShip);
        }
        playerDied = false;
        EntityKilledEvent kill;
        while ((kill = kills.poll()) != null) {
            countDefenderKill(kill.target);
            if (kill.target != null && kill.target.getComponent(PlayerTagComponent.class) != null) {
                playerDied = true;
            }
        }
        checkWinConditions();
        BoardingResolvedEvent resolved;
        while ((resolved = resolutions.poll()) != null) {
            despawnCombatants(resolved.target);
        }
    }

    /**
     * Removes every combatant (defenders, away team, and attackers) spawned for the operation on
     * {@code target} once it resolves, so they don't linger in the engine. Collects matches first,
     * then removes — avoids mutating the defender family while iterating it.
     */
    private void despawnCombatants(Entity target) {
        if (defenders == null || target == null) return;
        toRemove.clear();
        for (int i = 0, n = defenders.size(); i < n; i++) {
            Entity d = defenders.get(i);
            BoardingDefenderComponent tag = DEFENDER_M.get(d);
            if (tag != null && tag.operationShip == target) {
                toRemove.add(d);
            }
        }
        for (int i = 0, n = toRemove.size(); i < n; i++) {
            getEngine().removeEntity(toRemove.get(i));
        }
        toRemove.clear();
    }

    /** Decrements the defender or attacker tally on the relevant operation when a combatant dies. */
    private void countDefenderKill(Entity dead) {
        if (dead == null) return;
        BoardingDefenderComponent tag = DEFENDER_M.get(dead);
        if (tag == null || tag.awayTeam || tag.counted || tag.operationShip == null) return;
        BoardingOperationComponent op = OP_M.get(tag.operationShip);
        if (op == null) return;
        tag.counted = true;
        if (tag.attacker) {
            op.attackersRemaining = Math.max(0, op.attackersRemaining - 1);
        } else {
            op.defendersRemaining = Math.max(0, op.defendersRemaining - 1);
        }
    }

    private void spawnForEntry(Entity target) {
        BoardingOperationComponent op = OP_M.get(target);
        if (op == null || op.spawned) return;
        op.spawned = true;
        spawnedThisFrame.add(target);

        tagBridge(target);

        TransformComponent targetTransform = TRANSFORM_M.get(target);
        Vector3 base = (targetTransform != null) ? targetTransform.position : Vector3.Zero;

        BoardingDefenseComponent def = DEF_M.get(target);
        int count = (def != null) ? def.defenderCount : 2;
        float hp = (def != null) ? def.defenderHealth : 100f;
        float dmg = (def != null) ? def.defenderDamage : 12f;
        op.defendersRemaining = count;
        for (int i = 0; i < count; i++) {
            spawnCombatant(target, base, i, hp, dmg, /*attacker*/ false);
        }

        // Away team (optional) — friendly crew at the entry, count from AwayTeamComponent.
        if (players != null && players.size() > 0) {
            AwayTeamComponent away = players.first().getComponent(AwayTeamComponent.class);
            int awaySize = (away != null) ? away.size : 0;
            for (int i = 0; i < awaySize; i++) {
                Entity mate = spawnCombatant(target, base, 100 + i, 100f, 12f, false);
                // Keep the tag (so they're despawned on resolution) but mark as away team
                // so they are never tallied as defenders.
                DEFENDER_M.get(mate).awayTeam = true;
            }
        }
    }

    /** When an NPC breaches the player's ship, spawn attackers and begin inverted combat. */
    private void onNpcBreach(ShipBreachedEvent event) {
        Entity target = event.target; // the player's ship
        BoardingOperationComponent op = OP_M.get(target);
        if (op == null || op.playerIsAggressor || op.spawned) return;
        op.spawned = true;
        op.phase = BoardingPhase.INTERIOR_COMBAT;
        spawnedThisFrame.add(target);

        TransformComponent t = TRANSFORM_M.get(target);
        Vector3 base = (t != null) ? t.position : Vector3.Zero;
        BoardingDefenseComponent def = DEF_M.get(target);
        int count = (def != null) ? def.defenderCount : 2;
        float hp = (def != null) ? def.defenderHealth : 100f;
        float dmg = (def != null) ? def.defenderDamage : 12f;
        op.attackersRemaining = count;
        for (int i = 0; i < count; i++) {
            spawnCombatant(target, base, i, hp, dmg, /*attacker*/ true);
        }
    }

    private Entity spawnCombatant(Entity target, Vector3 base, int index,
                                  float hp, float dmg, boolean attacker) {
        Entity e = new Entity();

        TransformComponent t = new TransformComponent();
        // Fan out around the ship origin so they aren't stacked.
        t.position.set(base).add((index % 3) * 1.5f - 1.5f, 0f, (index / 3) * 1.5f);
        e.add(t);

        HealthComponent health = new HealthComponent();
        health.currentHP = hp;
        health.maxHP = hp;
        health.alive = true;
        e.add(health);

        e.add(new CombatAIComponent());
        e.add(new CombatInputComponent());
        e.add(new HitboxComponent());

        RangedWeaponComponent weapon = new RangedWeaponComponent();
        weapon.damage = dmg;
        weapon.fireRate = 1.5f;
        weapon.range = 20f;
        weapon.currentAmmo = 30;
        weapon.magSize = 30;
        weapon.hitscan = true;
        weapon.damageType = DamageType.BALLISTIC;
        e.add(weapon);

        BoardingDefenderComponent tag = new BoardingDefenderComponent();
        tag.operationShip = target;
        tag.attacker = attacker;
        e.add(tag);

        getEngine().addEntity(e);
        return e;
    }

    private void tagBridge(Entity target) {
        if (BRIDGE_M.get(target) != null) return;
        ShipInteriorComponent interior = INTERIOR_M.get(target);
        BridgeComponent bridge = new BridgeComponent();
        if (interior != null && interior.layout != null) {
            bridge.localCenter.set(interior.layout.pilotSeatPosition);
        }
        target.add(bridge);
    }

    private void checkWinConditions() {
        if (operations == null) return;
        for (int i = 0, n = operations.size(); i < n; i++) {
            Entity target = operations.get(i);
            BoardingOperationComponent op = OP_M.get(target);
            if (op == null || op.phase != BoardingPhase.INTERIOR_COMBAT || !op.spawned) continue;
            if (spawnedThisFrame.contains(target)) continue; // combatant family not yet populated
            if (op.playerIsAggressor) {
                if (op.defendersRemaining <= 0 || bridgeCaptured(target)) {
                    fireCleared(op, target);
                }
            } else {
                // NPC boarded the player: player wins by clearing attackers, loses on death.
                if (playerDied) {
                    autoResolve(op, target, BoardingOutcome.ENEMY_CAPTURE);
                } else if (op.attackersRemaining <= 0) {
                    autoResolve(op, target, BoardingOutcome.REPELLED);
                }
            }
        }
    }

    /** NPC-aggressor outcomes resolve without a player menu. */
    private void autoResolve(BoardingOperationComponent op, Entity target, BoardingOutcome outcome) {
        op.phase = BoardingPhase.RESOLVING;
        eventBus.publish(new BoardingResolutionChosenEvent(target, outcome));
    }

    private boolean bridgeCaptured(Entity target) {
        BridgeComponent bridge = BRIDGE_M.get(target);
        if (bridge == null || players == null || players.size() == 0) return false;
        TransformComponent targetTransform = TRANSFORM_M.get(target);
        if (targetTransform != null) {
            shipMat.set(targetTransform.position, targetTransform.rotation);
            bridgeWorld.set(bridge.localCenter).mul(shipMat);
        } else {
            bridgeWorld.set(bridge.localCenter);
        }
        Vector3 playerPos = TRANSFORM_M.get(players.first()).position;
        boolean playerInBridge = playerPos.dst2(bridgeWorld) <= bridge.radius * bridge.radius;
        if (!playerInBridge) return false;
        return liveDefenders(target, /*withinBridgeOnly*/ true, bridge.radius) == 0;
    }

    /** Counts live defenders of {@code target}; optionally only those within the bridge radius. */
    private int liveDefenders(Entity target, boolean withinBridgeOnly, float radius) {
        if (defenders == null) return 0;
        int live = 0;
        for (int i = 0, n = defenders.size(); i < n; i++) {
            Entity d = defenders.get(i);
            BoardingDefenderComponent tag = DEFENDER_M.get(d);
            if (tag == null || tag.operationShip != target || tag.attacker || tag.awayTeam) continue;
            HealthComponent h = HEALTH_M.get(d);
            if (h == null || !h.alive) continue;
            if (withinBridgeOnly) {
                Vector3 dp = TRANSFORM_M.get(d).position;
                if (dp.dst2(bridgeWorld) > radius * radius) continue;
            }
            live++;
        }
        return live;
    }

    private void fireCleared(BoardingOperationComponent op, Entity target) {
        op.phase = BoardingPhase.RESOLVING;
        eventBus.publish(new BoardingClearedEvent(op.aggressorShip, target));
        eventBus.publish(new BoardingResolutionRequestedEvent(target));
    }
}
