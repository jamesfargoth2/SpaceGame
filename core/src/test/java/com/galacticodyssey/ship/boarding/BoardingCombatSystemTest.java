package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.BoardingClearedEvent;
import com.galacticodyssey.ship.boarding.events.PlayerEnteredHostileInteriorEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingCombatSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingCombatSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;
    private Entity aggressor;
    private Entity target;
    private final List<BoardingClearedEvent> cleared = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingCombatSystem(eventBus));

        player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        engine.addEntity(player);

        aggressor = new Entity();
        aggressor.add(new TransformComponent());
        engine.addEntity(aggressor);

        target = new Entity();
        target.add(new TransformComponent());
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.defenderCount = 3;
        def.defenderHealth = 50f;
        target.add(def);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.aggressorShip = aggressor;
        op.phase = BoardingPhase.INTERIOR_COMBAT;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);

        eventBus.subscribe(BoardingClearedEvent.class, cleared::add);
    }

    private ImmutableArray<Entity> defenders() {
        return engine.getEntitiesFor(Family.all(BoardingDefenderComponent.class).get());
    }

    @Test
    void enteringSpawnsDefenders() {
        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
        engine.update(0.016f);
        assertEquals(3, defenders().size());
        for (Entity d : defenders()) {
            assertEquals(50f, d.getComponent(HealthComponent.class).maxHP, 0.01f);
            assertSame(target, d.getComponent(BoardingDefenderComponent.class).operationShip);
        }
        assertTrue(cleared.isEmpty(), "not cleared while defenders alive");
    }

    @Test
    void killingAllDefendersClearsOperation() {
        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
        engine.update(0.016f);

        List<Entity> ds = new ArrayList<>();
        for (Entity d : defenders()) ds.add(d);
        for (Entity d : ds) {
            d.getComponent(HealthComponent.class).alive = false;
            eventBus.publish(new EntityKilledEvent(d, player));
        }
        engine.update(0.016f);

        assertEquals(1, cleared.size());
        assertSame(target, cleared.get(0).target);
        assertEquals(BoardingPhase.RESOLVING,
            target.getComponent(BoardingOperationComponent.class).phase);
    }

    @Test
    void doesNotSpawnTwice() {
        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
        engine.update(0.016f);
        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
        engine.update(0.016f);
        assertEquals(3, defenders().size(), "defenders spawned once per operation");
    }
}
