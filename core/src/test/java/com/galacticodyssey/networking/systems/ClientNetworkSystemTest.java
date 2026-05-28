package com.galacticodyssey.networking.systems;

import com.galacticodyssey.common.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientNetworkSystemTest {

    @Test
    void connectionStateStartsDisconnected() {
        ClientNetworkSystem system = new ClientNetworkSystem();
        assertEquals(ClientNetworkSystem.ConnectionState.DISCONNECTED, system.getConnectionState());
    }

    @Test
    void processLoginResponseUpdatesState() {
        ClientNetworkSystem system = new ClientNetworkSystem();

        LoginResponse response = new LoginResponse();
        response.success = true;
        response.sessionToken = "tok-123";
        response.playerId = UUID.randomUUID();
        system.handleLoginResponse(response);

        assertEquals(ClientNetworkSystem.ConnectionState.CONNECTED, system.getConnectionState());
        assertEquals("tok-123", system.getSessionToken());
    }

    @Test
    void processFailedLoginStaysDisconnected() {
        ClientNetworkSystem system = new ClientNetworkSystem();

        LoginResponse response = new LoginResponse();
        response.success = false;
        response.failureReason = "Invalid version";
        system.handleLoginResponse(response);

        assertEquals(ClientNetworkSystem.ConnectionState.DISCONNECTED, system.getConnectionState());
    }

    @Test
    void entityBatchUpdateIsQueued() {
        ClientNetworkSystem system = new ClientNetworkSystem();

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 10;
        batch.lastProcessedInputSequence = 5;
        batch.updates = new EntityStateUpdate[0];
        system.handleEntityBatchUpdate(batch);

        var queued = system.drainBatchUpdates();
        assertEquals(1, queued.size());
        assertEquals(10, queued.get(0).serverTick);
    }

    @Test
    void entitySpawnMessageIsQueued() {
        ClientNetworkSystem system = new ClientNetworkSystem();

        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 42;
        spawn.entityType = "ship";
        system.handleEntitySpawn(spawn);

        var queued = system.drainSpawnMessages();
        assertEquals(1, queued.size());
        assertEquals(42, queued.get(0).networkId);
    }

    @Test
    void entityDestroyMessageIsQueued() {
        ClientNetworkSystem system = new ClientNetworkSystem();

        EntityDestroyMessage destroy = new EntityDestroyMessage();
        destroy.networkId = 42;
        system.handleEntityDestroy(destroy);

        var queued = system.drainDestroyMessages();
        assertEquals(1, queued.size());
        assertEquals(42, queued.get(0).networkId);
    }
}
