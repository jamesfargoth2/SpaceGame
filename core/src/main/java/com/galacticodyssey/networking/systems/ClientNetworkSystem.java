package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.common.protocol.*;

import java.util.ArrayList;
import java.util.List;
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

    public ClientNetworkSystem() {
        super(60);
    }

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

    public void handleEntityBatchUpdate(EntityBatchUpdate batch) {
        batchQueue.add(batch);
    }

    public void handleEntitySpawn(EntitySpawnMessage spawn) {
        spawnQueue.add(spawn);
    }

    public void handleEntityDestroy(EntityDestroyMessage destroy) {
        destroyQueue.add(destroy);
    }

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

    @Override
    public void update(float deltaTime) {
        // Process queued messages into ECS — will be expanded in Part 2 (prediction/interpolation)
    }
}
