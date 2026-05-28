package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.common.protocol.EntityBatchUpdate;
import com.galacticodyssey.common.protocol.EntityStateUpdate;
import com.galacticodyssey.common.protocol.PlayerInput;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.networking.prediction.PredictedState;
import com.galacticodyssey.networking.prediction.TimestampedInput;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationSystemTest {

    private Engine engine;
    private ReconciliationSystem system;
    private Entity player;
    private static final int PLAYER_NET_ID = 42;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        system = new ReconciliationSystem();
        engine.addSystem(system);

        player = new Entity();
        player.add(new TransformComponent());
        player.add(new NetworkIdComponent(PLAYER_NET_ID));
        player.add(new PredictionComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerStateComponent.PlayerMode.ON_FOOT_EXTERIOR;
        player.add(state);
        engine.addEntity(player);
    }

    private byte[] encodePosition(float x, float y, float z) {
        ByteBuffer bb = ByteBuffer.allocate(12);
        bb.putFloat(x);
        bb.putFloat(y);
        bb.putFloat(z);
        return bb.array();
    }

    private void seedBuffer(PredictionComponent pred, int fromSeq, int toSeq, float posX) {
        for (int seq = fromSeq; seq <= toSeq; seq++) {
            PlayerInput input = new PlayerInput();
            input.sequenceNumber = seq;
            input.moveForward = 1.0f;
            PredictedState ps = new PredictedState(posX + seq * 0.1f, 0, 0, 0, 0, 0, 1, 0, 0, 0);
            pred.getInputBuffer().add(new TimestampedInput(seq, input, ps));
        }
        pred.nextSequenceNumber = toSeq + 1;
    }

    @Test
    void acceptsPredictionWithinThreshold() {
        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        transform.position.set(10.005f, 0, 0);

        seedBuffer(pred, 0, 5, 10f);

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 3;
        batch.lastProcessedInputSequence = 3;
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = PLAYER_NET_ID;
        update.serverTick = 3;
        update.dirtyMask = 0b1;
        update.payload = encodePosition(10.003f, 0, 0);
        batch.updates = new EntityStateUpdate[]{update};

        system.enqueueServerUpdate(batch);
        engine.update(0.05f);

        assertEquals(10.005f, transform.position.x, 1e-3f);
        assertEquals(0, pred.smoothingFramesRemaining);
    }

    @Test
    void smoothCorrectionWithinRange() {
        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        transform.position.set(11f, 0, 0);

        seedBuffer(pred, 0, 5, 10f);

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 3;
        batch.lastProcessedInputSequence = 3;
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = PLAYER_NET_ID;
        update.serverTick = 3;
        update.dirtyMask = 0b1;
        update.payload = encodePosition(10f, 0, 0);
        batch.updates = new EntityStateUpdate[]{update};

        system.enqueueServerUpdate(batch);
        engine.update(0.05f);

        assertEquals(PredictionComponent.SMOOTHING_FRAMES, pred.smoothingFramesRemaining);
        assertTrue(Math.abs(pred.smoothingOffsetX) > 0.001f);
    }

    @Test
    void hardSnapBeyondThreshold() {
        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        transform.position.set(100f, 0, 0);

        seedBuffer(pred, 0, 5, 10f);

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 3;
        batch.lastProcessedInputSequence = 3;
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = PLAYER_NET_ID;
        update.serverTick = 3;
        update.dirtyMask = 0b1;
        update.payload = encodePosition(10f, 0, 0);
        batch.updates = new EntityStateUpdate[]{update};

        system.enqueueServerUpdate(batch);
        engine.update(0.05f);

        assertEquals(10f, transform.position.x, 0.5f);
        assertEquals(0, pred.smoothingFramesRemaining);
    }

    @Test
    void discardsAcknowledgedInputs() {
        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        transform.position.set(10f, 0, 0);

        seedBuffer(pred, 0, 5, 10f);
        assertEquals(6, pred.getInputBuffer().size());

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 3;
        batch.lastProcessedInputSequence = 3;
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = PLAYER_NET_ID;
        update.serverTick = 3;
        update.dirtyMask = 0b1;
        update.payload = encodePosition(10f, 0, 0);
        batch.updates = new EntityStateUpdate[]{update};

        system.enqueueServerUpdate(batch);
        engine.update(0.05f);

        assertEquals(2, pred.getInputBuffer().size());
        assertEquals(3, pred.lastAcknowledgedSequence);
    }

    @Test
    void smoothingOffsetDecaysEachFrame() {
        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        pred.smoothingOffsetX = 1.0f;
        pred.smoothingFramesRemaining = PredictionComponent.SMOOTHING_FRAMES;

        engine.update(0.05f);

        assertTrue(pred.smoothingFramesRemaining < PredictionComponent.SMOOTHING_FRAMES);
        assertTrue(pred.smoothingOffsetX < 1.0f);
        assertTrue(pred.smoothingOffsetX > 0f);
    }
}
