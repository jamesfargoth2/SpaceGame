package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.common.protocol.*;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.InterpolationComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class ClientNetworkSystemIntegrationTest {

    private Engine engine;
    private ClientNetworkSystem netSystem;
    private ReconciliationSystem reconSystem;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        reconSystem = new ReconciliationSystem();
        netSystem = new ClientNetworkSystem(reconSystem);
        engine.addSystem(netSystem);
        engine.addSystem(reconSystem);
    }

    @Test
    void spawnCreatesRemoteEntityWithInterpolation() {
        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 100;
        spawn.entityType = "ship";
        spawn.componentData = new byte[0];

        netSystem.handleEntitySpawn(spawn);
        engine.update(0.05f);

        Entity remote = netSystem.getRemoteEntity(100);
        assertNotNull(remote);
        assertNotNull(remote.getComponent(NetworkIdComponent.class));
        assertNotNull(remote.getComponent(TransformComponent.class));
        assertNotNull(remote.getComponent(InterpolationComponent.class));
    }

    @Test
    void destroyRemovesRemoteEntity() {
        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 100;
        spawn.entityType = "ship";
        spawn.componentData = new byte[0];
        netSystem.handleEntitySpawn(spawn);
        engine.update(0.05f);
        assertNotNull(netSystem.getRemoteEntity(100));

        EntityDestroyMessage destroy = new EntityDestroyMessage();
        destroy.networkId = 100;
        netSystem.handleEntityDestroy(destroy);
        engine.update(0.05f);

        assertNull(netSystem.getRemoteEntity(100));
    }

    @Test
    void batchUpdateFeedsRemoteInterpolationBuffer() {
        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 200;
        spawn.entityType = "npc";
        spawn.componentData = new byte[0];
        netSystem.handleEntitySpawn(spawn);
        engine.update(0.05f);

        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = 200;
        update.serverTick = 5;
        update.dirtyMask = 0b1;
        ByteBuffer bb = ByteBuffer.allocate(12);
        bb.putFloat(10f);
        bb.putFloat(20f);
        bb.putFloat(30f);
        update.payload = bb.array();

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 5;
        batch.lastProcessedInputSequence = -1;
        batch.updates = new EntityStateUpdate[]{update};

        netSystem.handleEntityBatchUpdate(batch);
        engine.update(0.05f);

        Entity remote = netSystem.getRemoteEntity(200);
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        assertEquals(1, ic.getSnapshotBuffer().size());
        assertEquals(5, ic.getSnapshotBuffer().getNewestTick());
    }

    @Test
    void batchUpdateForLocalPlayerGoesToReconciliation() {
        int localNetId = 42;
        Entity player = new Entity();
        player.add(new TransformComponent());
        player.add(new NetworkIdComponent(localNetId));
        player.add(new PredictionComponent());
        player.add(new PlayerInputComponent());
        PlayerStateComponent ps = new PlayerStateComponent();
        ps.currentMode = PlayerStateComponent.PlayerMode.ON_FOOT_EXTERIOR;
        player.add(ps);
        engine.addEntity(player);

        netSystem.setLocalPlayerNetworkId(localNetId);

        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        com.galacticodyssey.common.protocol.PlayerInput pi = new com.galacticodyssey.common.protocol.PlayerInput();
        pi.sequenceNumber = 0;
        pred.getInputBuffer().add(new com.galacticodyssey.networking.prediction.TimestampedInput(
                0, pi, new com.galacticodyssey.networking.prediction.PredictedState(10, 0, 0, 0, 0, 0, 1, 0, 0, 0)));
        pred.nextSequenceNumber = 1;

        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = localNetId;
        update.serverTick = 1;
        update.dirtyMask = 0b1;
        ByteBuffer bb = ByteBuffer.allocate(12);
        bb.putFloat(10f);
        bb.putFloat(0f);
        bb.putFloat(0f);
        update.payload = bb.array();

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 1;
        batch.lastProcessedInputSequence = 0;
        batch.updates = new EntityStateUpdate[]{update};

        netSystem.handleEntityBatchUpdate(batch);
        engine.update(0.05f);

        assertEquals(0, pred.lastAcknowledgedSequence);
    }
}
