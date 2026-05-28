package com.galacticodyssey.networking;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.common.protocol.*;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.InterpolationComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.networking.interpolation.EntitySnapshot;
import com.galacticodyssey.networking.prediction.PredictedState;
import com.galacticodyssey.networking.prediction.TimestampedInput;
import com.galacticodyssey.networking.systems.*;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class PredictionInterpolationIntegrationTest {

    @Test
    void fullClientPipeline() {
        Engine engine = new Engine();

        ReconciliationSystem reconSystem = new ReconciliationSystem();
        ClientPredictionSystem predSystem = new ClientPredictionSystem();
        ClientNetworkSystem netSystem = new ClientNetworkSystem(reconSystem);
        InterpolationSystem interpSystem = new InterpolationSystem(0.05f);

        engine.addSystem(predSystem);       // priority 0
        engine.addSystem(reconSystem);      // priority 1
        engine.addSystem(interpSystem);     // priority 55
        engine.addSystem(netSystem);        // priority 0

        // Local player entity
        int localNetId = 1;
        Entity player = new Entity();
        TransformComponent playerTransform = new TransformComponent();
        playerTransform.position.set(100, 0, 0);
        player.add(playerTransform);
        player.add(new NetworkIdComponent(localNetId));
        player.add(new PredictionComponent());
        player.add(new PlayerInputComponent());
        PlayerStateComponent ps = new PlayerStateComponent();
        ps.currentMode = PlayerStateComponent.PlayerMode.ON_FOOT_EXTERIOR;
        player.add(ps);
        engine.addEntity(player);
        netSystem.setLocalPlayerNetworkId(localNetId);

        // Spawn a remote entity via network message
        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 50;
        spawn.entityType = "ship";
        spawn.componentData = new byte[0];
        netSystem.handleEntitySpawn(spawn);

        // Run a few frames — prediction captures inputs
        for (int i = 0; i < 3; i++) {
            player.getComponent(PlayerInputComponent.class).moveForward = 1.0f;
            engine.update(0.05f);
        }

        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        assertEquals(3, pred.getInputBuffer().size());

        // Remote entity should exist with interpolation component
        Entity remote = netSystem.getRemoteEntity(50);
        assertNotNull(remote);
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        assertNotNull(ic);

        // Feed remote entity some snapshots for interpolation
        ic.getSnapshotBuffer().add(new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 5, 0, 0));
        ic.getSnapshotBuffer().add(new EntitySnapshot(4, 20, 0, 0, 0, 0, 0, 1, 5, 0, 0));
        interpSystem.setCurrentServerTick(4);

        // Feed server batch update for local player (server confirms position ~100)
        EntityStateUpdate localUpdate = new EntityStateUpdate();
        localUpdate.networkId = localNetId;
        localUpdate.serverTick = 1;
        localUpdate.dirtyMask = 0b1;
        ByteBuffer bb = ByteBuffer.allocate(12);
        bb.putFloat(100f);
        bb.putFloat(0f);
        bb.putFloat(0f);
        localUpdate.payload = bb.array();

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 1;
        batch.lastProcessedInputSequence = 0;
        batch.updates = new EntityStateUpdate[]{localUpdate};
        netSystem.handleEntityBatchUpdate(batch);

        // One more engine step to process everything
        engine.update(0.05f);

        // Verify reconciliation ran — lastAcknowledgedSequence updated
        assertEquals(0, pred.lastAcknowledgedSequence);

        // Verify remote entity was interpolated (targetTick = 4-2 = 2, t = 2/4 = 0.5, posX ~ 10)
        TransformComponent remoteTransform = remote.getComponent(TransformComponent.class);
        assertTrue(remoteTransform.position.x > 0, "Remote entity should have been interpolated from snapshots");
    }
}
