package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.galacticodyssey.common.protocol.*;
import org.junit.jupiter.api.Test;

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
    void spawnCreatesRemoteEntity() {
        Engine engine = new Engine();
        ClientNetworkSystem system = new ClientNetworkSystem();
        engine.addSystem(system);

        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 42;
        spawn.entityType = "ship";
        system.handleEntitySpawn(spawn);

        system.update(0.016f);

        assertNotNull(system.getRemoteEntity(42));
    }

    @Test
    void destroyRemovesRemoteEntity() {
        Engine engine = new Engine();
        ClientNetworkSystem system = new ClientNetworkSystem();
        engine.addSystem(system);

        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 42;
        spawn.entityType = "ship";
        system.handleEntitySpawn(spawn);
        system.update(0.016f);
        assertNotNull(system.getRemoteEntity(42));

        EntityDestroyMessage destroy = new EntityDestroyMessage();
        destroy.networkId = 42;
        system.handleEntityDestroy(destroy);
        system.update(0.016f);

        assertNull(system.getRemoteEntity(42));
    }

    @Test
    void batchUpdateProcessedByUpdate() {
        Engine engine = new Engine();
        ClientNetworkSystem system = new ClientNetworkSystem();
        engine.addSystem(system);

        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 10;
        spawn.entityType = "ship";
        system.handleEntitySpawn(spawn);
        system.update(0.016f);

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 5;
        batch.lastProcessedInputSequence = 1;
        EntityStateUpdate upd = new EntityStateUpdate();
        upd.networkId = 10;
        upd.payload = new byte[12];
        batch.updates = new EntityStateUpdate[]{upd};
        system.handleEntityBatchUpdate(batch);

        system.update(0.016f);

        assertNotNull(system.getRemoteEntity(10));
    }
}
