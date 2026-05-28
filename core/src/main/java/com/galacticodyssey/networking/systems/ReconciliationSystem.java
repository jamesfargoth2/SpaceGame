package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.common.protocol.EntityBatchUpdate;
import com.galacticodyssey.common.protocol.EntityStateUpdate;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.networking.prediction.PredictedState;
import com.galacticodyssey.networking.prediction.TimestampedInput;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ReconciliationSystem extends EntitySystem {

    private final ConcurrentLinkedQueue<EntityBatchUpdate> serverUpdates = new ConcurrentLinkedQueue<>();

    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<NetworkIdComponent> netMapper =
            ComponentMapper.getFor(NetworkIdComponent.class);
    private final ComponentMapper<PredictionComponent> predictionMapper =
            ComponentMapper.getFor(PredictionComponent.class);

    private ImmutableArray<Entity> predictedEntities;

    public ReconciliationSystem() {
        super(1);
    }

    @Override
    public void addedToEngine(Engine engine) {
        predictedEntities = engine.getEntitiesFor(
                Family.all(TransformComponent.class, NetworkIdComponent.class,
                        PredictionComponent.class).get());
    }

    public void enqueueServerUpdate(EntityBatchUpdate batch) {
        serverUpdates.add(batch);
    }

    @Override
    public void update(float deltaTime) {
        // Decay smoothing offset on all predicted entities
        for (int i = 0; i < predictedEntities.size(); i++) {
            PredictionComponent pred = predictionMapper.get(predictedEntities.get(i));
            if (pred.smoothingFramesRemaining > 0) {
                float factor = 1.0f / pred.smoothingFramesRemaining;
                pred.smoothingOffsetX -= pred.smoothingOffsetX * factor;
                pred.smoothingOffsetY -= pred.smoothingOffsetY * factor;
                pred.smoothingOffsetZ -= pred.smoothingOffsetZ * factor;
                pred.smoothingFramesRemaining--;
            }
        }

        EntityBatchUpdate batch;
        while ((batch = serverUpdates.poll()) != null) {
            processBatch(batch);
        }
    }

    private void processBatch(EntityBatchUpdate batch) {
        for (int i = 0; i < predictedEntities.size(); i++) {
            Entity entity = predictedEntities.get(i);
            NetworkIdComponent netId = netMapper.get(entity);
            PredictionComponent pred = predictionMapper.get(entity);
            TransformComponent transform = transformMapper.get(entity);

            EntityStateUpdate myUpdate = findUpdate(batch.updates, netId.networkId);
            if (myUpdate == null) continue;

            int ackedSeq = batch.lastProcessedInputSequence;
            pred.lastAcknowledgedSequence = ackedSeq;

            // Decode server position from payload
            float serverX = 0, serverY = 0, serverZ = 0;
            if (myUpdate.payload != null && myUpdate.payload.length >= 12) {
                ByteBuffer bb = ByteBuffer.wrap(myUpdate.payload).order(ByteOrder.BIG_ENDIAN);
                serverX = bb.getFloat();
                serverY = bb.getFloat();
                serverZ = bb.getFloat();
            }

            float dx = serverX - transform.position.x;
            float dy = serverY - transform.position.y;
            float dz = serverZ - transform.position.z;
            float error = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            // Discard acknowledged inputs
            pred.getInputBuffer().discardUpTo(ackedSeq);

            if (error <= PredictionComponent.ACCEPT_THRESHOLD) {
                continue;
            }

            if (error > PredictionComponent.HARD_SNAP_THRESHOLD) {
                transform.position.set(serverX, serverY, serverZ);
                pred.smoothingOffsetX = 0;
                pred.smoothingOffsetY = 0;
                pred.smoothingOffsetZ = 0;
                pred.smoothingFramesRemaining = 0;
            } else {
                float oldX = transform.position.x;
                float oldY = transform.position.y;
                float oldZ = transform.position.z;

                List<TimestampedInput> unacked = pred.getInputBuffer().getUnacknowledged(ackedSeq);
                float replayDx = 0, replayDy = 0, replayDz = 0;
                if (unacked.size() >= 2) {
                    PredictedState first = unacked.get(0).predictedState;
                    PredictedState last = unacked.get(unacked.size() - 1).predictedState;
                    replayDx = last.posX - first.posX;
                    replayDy = last.posY - first.posY;
                    replayDz = last.posZ - first.posZ;
                }

                float newX = serverX + replayDx;
                float newY = serverY + replayDy;
                float newZ = serverZ + replayDz;
                transform.position.set(newX, newY, newZ);

                pred.smoothingOffsetX = oldX - newX;
                pred.smoothingOffsetY = oldY - newY;
                pred.smoothingOffsetZ = oldZ - newZ;
                pred.smoothingFramesRemaining = PredictionComponent.SMOOTHING_FRAMES;
            }
        }
    }

    private EntityStateUpdate findUpdate(EntityStateUpdate[] updates, int networkId) {
        if (updates == null) return null;
        for (EntityStateUpdate u : updates) {
            if (u.networkId == networkId) return u;
        }
        return null;
    }
}
