package com.galacticodyssey.server.replication;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.common.protocol.EntityBatchUpdate;
import com.galacticodyssey.common.protocol.EntitySpawnMessage;
import com.galacticodyssey.common.protocol.EntityDestroyMessage;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.server.network.PlayerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ServerReplicationSystemTest {

    private Engine engine;
    private ServerReplicationSystem replicationSystem;
    private List<ServerReplicationSystem.SentPacket> sentPackets;

    @BeforeEach
    void setUp() {
        sentPackets = new ArrayList<>();
        replicationSystem = new ServerReplicationSystem(sentPackets::add);
        engine = new Engine();
        engine.addSystem(replicationSystem);
    }

    @Test
    void sendsSpawnMessageWhenEntityEntersInterest() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setPlayerNetworkId(100);
        session.setGalaxyPosition(0, 0, 0);
        replicationSystem.addSession(session);

        Entity e = new Entity();
        e.add(new NetworkIdComponent(1));
        TransformComponent tc = new TransformComponent();
        tc.position.set(100, 0, 0);
        e.add(tc);
        e.add(new HealthComponent());
        engine.addEntity(e);

        replicationSystem.setServerTick(0);
        replicationSystem.setOriginOffset(0, 0, 0);
        engine.update(0.05f);

        boolean hasSpawn = sentPackets.stream()
            .anyMatch(p -> p.message() instanceof EntitySpawnMessage);
        assertTrue(hasSpawn, "Expected EntitySpawnMessage for new entity in interest");
    }

    @Test
    void sendsDestroyMessageWhenEntityLeavesInterest() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setPlayerNetworkId(100);
        session.setGalaxyPosition(0, 0, 0);
        replicationSystem.addSession(session);

        Entity e = new Entity();
        e.add(new NetworkIdComponent(1));
        TransformComponent tc = new TransformComponent();
        tc.position.set(100, 0, 0);
        e.add(tc);
        engine.addEntity(e);

        replicationSystem.setServerTick(0);
        replicationSystem.setOriginOffset(0, 0, 0);
        engine.update(0.05f);
        sentPackets.clear();

        tc.position.set(15000, 0, 0);
        replicationSystem.setServerTick(1);
        engine.update(0.05f);

        boolean hasDestroy = sentPackets.stream()
            .anyMatch(p -> p.message() instanceof EntityDestroyMessage);
        assertTrue(hasDestroy, "Expected EntityDestroyMessage for entity leaving interest");
    }

    @Test
    void doesNotReplicatePlayerEntityToItself() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setPlayerNetworkId(5);
        session.setGalaxyPosition(0, 0, 0);
        replicationSystem.addSession(session);

        Entity playerEntity = new Entity();
        playerEntity.add(new NetworkIdComponent(5));
        TransformComponent tc = new TransformComponent();
        tc.position.set(0, 0, 0);
        playerEntity.add(tc);
        engine.addEntity(playerEntity);

        replicationSystem.setServerTick(0);
        replicationSystem.setOriginOffset(0, 0, 0);
        engine.update(0.05f);

        boolean sentToSelf = sentPackets.stream()
            .filter(p -> p.connectionId() == 1)
            .anyMatch(p -> {
                if (p.message() instanceof EntitySpawnMessage spawn) {
                    return spawn.networkId == 5;
                }
                return false;
            });
        assertFalse(sentToSelf, "Should not replicate player entity to itself");
    }

    @Test
    void sendsBatchUpdateForKnownEntities() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setPlayerNetworkId(100);
        session.setGalaxyPosition(0, 0, 0);
        session.setLastProcessedInputSequence(10);
        replicationSystem.addSession(session);

        Entity e = new Entity();
        e.add(new NetworkIdComponent(1));
        TransformComponent tc = new TransformComponent();
        tc.position.set(100, 0, 0);
        e.add(tc);
        engine.addEntity(e);

        replicationSystem.setServerTick(0);
        replicationSystem.setOriginOffset(0, 0, 0);
        engine.update(0.05f);
        sentPackets.clear();

        tc.position.set(110, 0, 0);
        replicationSystem.setServerTick(1);
        engine.update(0.05f);

        boolean hasBatch = sentPackets.stream()
            .anyMatch(p -> p.message() instanceof EntityBatchUpdate);
        assertTrue(hasBatch, "Expected EntityBatchUpdate for existing entity");
    }
}
