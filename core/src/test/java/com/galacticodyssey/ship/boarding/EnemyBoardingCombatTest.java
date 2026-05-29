package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.AttachMethod;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.OwnedShipComponent.Owner;
import com.galacticodyssey.ship.boarding.events.BoardingResolvedEvent;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingCombatSystem;
import com.galacticodyssey.ship.boarding.systems.BoardingResolutionSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnemyBoardingCombatTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;
    private Entity playerShip;
    private Entity npc;
    private final List<BoardingResolvedEvent> resolved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingCombatSystem(eventBus));
        engine.addSystem(new BoardingResolutionSystem(eventBus, null));

        player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        HealthComponent ph = new HealthComponent();
        ph.currentHP = 100f; ph.maxHP = 100f; ph.alive = true;
        player.add(ph);
        engine.addEntity(player);

        npc = new Entity();
        npc.add(new TransformComponent());
        engine.addEntity(npc);

        playerShip = new Entity();
        playerShip.add(new TransformComponent());
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.defenderCount = 3; // becomes attacker count when NPC boards
        def.defenderHealth = 40f;
        playerShip.add(def);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = playerShip;
        op.aggressorShip = npc;
        op.phase = BoardingPhase.BREACHED;
        op.playerIsAggressor = false;
        playerShip.add(op);
        engine.addEntity(playerShip);

        eventBus.subscribe(BoardingResolvedEvent.class, resolved::add);
    }

    private void breach() {
        eventBus.publish(new ShipBreachedEvent(npc, playerShip, AttachMethod.BREACH_POD, new Vector3()));
        engine.update(0.016f);
    }

    private ImmutableArray<Entity> attackers() {
        return engine.getEntitiesFor(Family.all(BoardingDefenderComponent.class).get());
    }

    @Test
    void npcBreachSpawnsAttackersAndEntersCombat() {
        breach();
        assertEquals(3, attackers().size());
        for (Entity a : attackers()) {
            assertTrue(a.getComponent(BoardingDefenderComponent.class).attacker, "spawned as attacker");
        }
        assertEquals(BoardingPhase.INTERIOR_COMBAT,
            playerShip.getComponent(BoardingOperationComponent.class).phase);
    }

    @Test
    void clearingAllAttackersRepels() {
        breach();
        List<Entity> as = new ArrayList<>();
        for (Entity a : attackers()) as.add(a);
        for (Entity a : as) {
            a.getComponent(HealthComponent.class).alive = false;
            eventBus.publish(new EntityKilledEvent(a, player));
        }
        engine.update(0.016f); // combat detects repel → publishes chosen(REPELLED)
        engine.update(0.016f); // resolution applies it
        assertEquals(BoardingPhase.RESOLVED,
            playerShip.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(BoardingOutcome.REPELLED, resolved.get(resolved.size() - 1).outcome);
        assertNull(playerShip.getComponent(OwnedShipComponent.class),
            "repelled: player keeps the ship");
    }

    @Test
    void playerDeathYieldsEnemyCapture() {
        breach();
        player.getComponent(HealthComponent.class).alive = false;
        eventBus.publish(new EntityKilledEvent(player, npc));
        engine.update(0.016f); // combat detects player death → chosen(ENEMY_CAPTURE)
        engine.update(0.016f); // resolution applies it
        assertEquals(BoardingPhase.RESOLVED,
            playerShip.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(BoardingOutcome.ENEMY_CAPTURE, resolved.get(resolved.size() - 1).outcome);
        assertEquals(Owner.NPC, playerShip.getComponent(OwnedShipComponent.class).owner);
    }
}
