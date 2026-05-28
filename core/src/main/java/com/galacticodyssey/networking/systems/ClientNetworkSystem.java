package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.common.protocol.*;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.InterpolationComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.interpolation.EntitySnapshot;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientNetworkSystem extends EntitySystem {

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private String sessionToken;
    private UUID localPlayerId;

    private final ConcurrentLinkedQueue<EntityBatchUpdate> batchQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<EntitySpawnMessage> spawnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<EntityDestroyMessage> destroyQueue = new ConcurrentLinkedQueue<>();

    private final Map<Integer, Entity> remoteEntities = new HashMap<>();
    private int localPlayerNetworkId = -1;

    private final ReconciliationSystem reconciliationSystem;
    private Engine engine;

    /**
     * Primary constructor.
     * Priority 0 ensures this system runs before ReconciliationSystem (priority 1),
     * so batches enqueued here are visible to reconciliation in the same engine tick.
     */
    public ClientNetworkSystem(ReconciliationSystem reconciliationSystem) {
        super(0);
        this.reconciliationSystem = reconciliationSystem;
    }

    /** Backward-compatible no-arg constructor. */
    public ClientNetworkSystem() {
        this(null);
    }

    // -------------------------------------------------------------------------
    // Ashley lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
    }

    // -------------------------------------------------------------------------
    // Public API — connection state
    // -------------------------------------------------------------------------

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public UUID getLocalPlayerId() {
        return localPlayerId;
    }

    public void handleLoginResponse(LoginResponse response) {
        if (response.success) {
            connectionState = ConnectionState.CONNECTED;
            sessionToken = response.sessionToken;
            localPlayerId = response.playerId;
        }
    }

    // -------------------------------------------------------------------------
    // Public API — local player network id
    // -------------------------------------------------------------------------

    public void setLocalPlayerNetworkId(int networkId) {
        this.localPlayerNetworkId = networkId;
    }

    public int getLocalPlayerNetworkId() {
        return localPlayerNetworkId;
    }

    // -------------------------------------------------------------------------
    // Public API — remote entity lookup
    // -------------------------------------------------------------------------

    /** Returns the remote entity registered under {@code networkId}, or {@code null}. */
    public Entity getRemoteEntity(int networkId) {
        return remoteEntities.get(networkId);
    }

    // -------------------------------------------------------------------------
    // Public API — message handlers (called from network thread)
    // -------------------------------------------------------------------------

    public void handleEntityBatchUpdate(EntityBatchUpdate batch) {
        batchQueue.add(batch);
    }

    public void handleEntitySpawn(EntitySpawnMessage spawn) {
        spawnQueue.add(spawn);
    }

    public void handleEntityDestroy(EntityDestroyMessage destroy) {
        destroyQueue.add(destroy);
    }

    // -------------------------------------------------------------------------
    // Public API — drain methods (for callers outside the engine update cycle)
    // -------------------------------------------------------------------------

    public List<EntityBatchUpdate> drainBatchUpdates() {
        List<EntityBatchUpdate> result = new ArrayList<>();
        EntityBatchUpdate item;
        while ((item = batchQueue.poll()) != null) result.add(item);
        return result;
    }

    public List<EntitySpawnMessage> drainSpawnMessages() {
        List<EntitySpawnMessage> result = new ArrayList<>();
        EntitySpawnMessage item;
        while ((item = spawnQueue.poll()) != null) result.add(item);
        return result;
    }

    public List<EntityDestroyMessage> drainDestroyMessages() {
        List<EntityDestroyMessage> result = new ArrayList<>();
        EntityDestroyMessage item;
        while ((item = destroyQueue.poll()) != null) result.add(item);
        return result;
    }

    // -------------------------------------------------------------------------
    // Engine update
    // -------------------------------------------------------------------------

    @Override
    public void update(float deltaTime) {
        processSpawns();
        processDestroys();
        processBatchUpdates();
    }

    // -------------------------------------------------------------------------
    // Private processing
    // -------------------------------------------------------------------------

    private void processSpawns() {
        EntitySpawnMessage spawn;
        while ((spawn = spawnQueue.poll()) != null) {
            if (remoteEntities.containsKey(spawn.networkId)) {
                continue; // already spawned
            }
            Entity entity = new Entity();
            entity.add(new NetworkIdComponent(spawn.networkId));
            entity.add(new TransformComponent());
            entity.add(new InterpolationComponent());
            if (engine != null) {
                engine.addEntity(entity);
            }
            remoteEntities.put(spawn.networkId, entity);
        }
    }

    private void processDestroys() {
        EntityDestroyMessage destroy;
        while ((destroy = destroyQueue.poll()) != null) {
            Entity entity = remoteEntities.remove(destroy.networkId);
            if (entity != null && engine != null) {
                engine.removeEntity(entity);
            }
        }
    }

    private void processBatchUpdates() {
        EntityBatchUpdate batch;
        while ((batch = batchQueue.poll()) != null) {
            if (batch.updates == null) continue;

            boolean routedToReconciliation = false;
            for (EntityStateUpdate update : batch.updates) {
                if (update.networkId == localPlayerNetworkId) {
                    // Route the entire batch to ReconciliationSystem (it searches for the player update)
                    if (reconciliationSystem != null && !routedToReconciliation) {
                        reconciliationSystem.enqueueServerUpdate(batch);
                        routedToReconciliation = true;
                    }
                } else {
                    applyRemoteUpdate(update, batch.serverTick);
                }
            }
        }
    }

    /** Feed a single state update into the remote entity's InterpolationComponent. */
    private void applyRemoteUpdate(EntityStateUpdate update, int serverTick) {
        Entity entity = remoteEntities.get(update.networkId);
        if (entity == null) return;

        InterpolationComponent ic = entity.getComponent(InterpolationComponent.class);
        if (ic == null) return;

        // Decode position from first 12 bytes of payload
        float posX = 0f, posY = 0f, posZ = 0f;
        if (update.payload != null && update.payload.length >= 12) {
            ByteBuffer bb = ByteBuffer.wrap(update.payload);
            posX = bb.getFloat();
            posY = bb.getFloat();
            posZ = bb.getFloat();
        }

        // Handle unfreeze: blend from the current rendered position
        if (ic.frozen) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (transform != null) {
                ic.blendFromX = transform.position.x;
                ic.blendFromY = transform.position.y;
                ic.blendFromZ = transform.position.z;
            }
            ic.blendFramesRemaining = InterpolationComponent.BLEND_FRAMES;
            ic.frozen = false;
            ic.extrapolationTimer = 0f;
        }

        EntitySnapshot snapshot = new EntitySnapshot(
                serverTick,
                posX, posY, posZ,
                0f, 0f, 0f, 1f,  // identity rotation when not encoded
                0f, 0f, 0f        // zero velocity when not encoded
        );
        ic.getSnapshotBuffer().add(snapshot);
    }
}
