package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceResolverTest {

    @Test
    void resolvesPlayerShipReference() {
        Engine engine = new Engine();

        UUID shipUuid = UUID.randomUUID();
        Entity ship = new Entity();
        PersistenceIdComponent shipPid = new PersistenceIdComponent(shipUuid);
        ship.add(shipPid);
        engine.addEntity(ship);

        Entity player = new Entity();
        PlayerStateComponent ps = new PlayerStateComponent();
        ps.currentShipId = shipUuid;
        player.add(ps);
        player.add(new PersistenceIdComponent());
        engine.addEntity(player);

        Map<UUID, Entity> entityMap = new HashMap<>();
        entityMap.put(shipUuid, ship);
        entityMap.put(player.getComponent(PersistenceIdComponent.class).uuid, player);

        ReferenceResolver resolver = new ReferenceResolver();
        resolver.resolve(engine, entityMap);

        assertSame(ship, ps.currentShip);
    }

    @Test
    void handlesNullReferencesGracefully() {
        Engine engine = new Engine();

        Entity player = new Entity();
        PlayerStateComponent ps = new PlayerStateComponent();
        ps.currentShipId = null;
        player.add(ps);
        player.add(new PersistenceIdComponent());
        engine.addEntity(player);

        Map<UUID, Entity> entityMap = new HashMap<>();
        entityMap.put(player.getComponent(PersistenceIdComponent.class).uuid, player);

        ReferenceResolver resolver = new ReferenceResolver();
        resolver.resolve(engine, entityMap);

        assertNull(ps.currentShip);
    }
}
