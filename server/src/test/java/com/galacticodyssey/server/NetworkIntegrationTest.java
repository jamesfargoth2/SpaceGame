package com.galacticodyssey.server;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.common.protocol.*;
import com.galacticodyssey.common.serialization.NetworkKryoRegistrar;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.systems.ClientNetworkSystem;
import com.galacticodyssey.persistence.KryoRegistrar;
import com.galacticodyssey.server.network.PlayerSession;
import com.galacticodyssey.server.replication.ServerReplicationSystem;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NetworkIntegrationTest {

    private Engine serverEngine;
    private Engine clientEngine;
    private ServerReplicationSystem replicationSystem;
    private ClientNetworkSystem clientSystem;
    private Kryo kryo;
    private List<ServerReplicationSystem.SentPacket> serverPackets;

    @BeforeEach
    void setUp() {
        serverPackets = new ArrayList<>();
        replicationSystem = new ServerReplicationSystem(serverPackets::add);
        serverEngine = new Engine();
        serverEngine.addSystem(replicationSystem);

        clientSystem = new ClientNetworkSystem();
        clientEngine = new Engine();
        clientEngine.addSystem(clientSystem);

        kryo = new Kryo();
        KryoRegistrar.register(kryo);
        NetworkKryoRegistrar.register(kryo);
    }

    @Test
    void fullRoundTrip_loginInputSpawnUpdate() {
        // 1. Client sends LoginRequest
        LoginRequest loginReq = new LoginRequest();
        loginReq.username = "testplayer";
        loginReq.clientVersion = "0.1.0";

        // Serialize and deserialize (simulates network transport)
        LoginRequest receivedLogin = roundTrip(loginReq, LoginRequest.class);
        assertEquals("testplayer", receivedLogin.username);

        // 2. Server creates session
        UUID playerId = UUID.randomUUID();
        PlayerSession session = new PlayerSession(1, playerId, "session-token");
        session.setPlayerNetworkId(100);
        session.setGalaxyPosition(0, 0, 0);
        replicationSystem.addSession(session);

        // Server sends LoginResponse
        LoginResponse resp = new LoginResponse();
        resp.success = true;
        resp.sessionToken = "session-token";
        resp.playerId = playerId;

        LoginResponse receivedResp = roundTrip(resp, LoginResponse.class);
        clientSystem.handleLoginResponse(receivedResp);
        assertEquals(ClientNetworkSystem.ConnectionState.CONNECTED, clientSystem.getConnectionState());

        // 3. Create a world entity on the server
        Entity npc = new Entity();
        npc.add(new NetworkIdComponent(1));
        TransformComponent tc = new TransformComponent();
        tc.position.set(200, 0, 0);
        npc.add(tc);
        npc.add(new HealthComponent());
        serverEngine.addEntity(npc);

        // 4. Server ticks — should produce EntitySpawnMessage
        replicationSystem.setServerTick(0);
        replicationSystem.setOriginOffset(0, 0, 0);
        serverEngine.update(0.05f);

        boolean foundSpawn = false;
        for (var packet : serverPackets) {
            if (packet.message() instanceof EntitySpawnMessage spawn) {
                EntitySpawnMessage receivedSpawn = roundTrip(spawn, EntitySpawnMessage.class);
                clientSystem.handleEntitySpawn(receivedSpawn);
                foundSpawn = true;
            }
        }
        assertTrue(foundSpawn, "Server should have sent spawn");
        clientEngine.update(0.05f);
        assertNotNull(clientSystem.getRemoteEntity(1), "Client should have spawned remote entity");

        // 5. Server ticks again — should produce EntityBatchUpdate
        serverPackets.clear();
        tc.position.set(210, 0, 0);
        replicationSystem.setServerTick(1);
        serverEngine.update(0.05f);

        boolean foundBatch = false;
        for (var packet : serverPackets) {
            if (packet.message() instanceof EntityBatchUpdate batch) {
                EntityBatchUpdate receivedBatch = roundTrip(batch, EntityBatchUpdate.class);
                clientSystem.handleEntityBatchUpdate(receivedBatch);
                foundBatch = true;
            }
        }
        assertTrue(foundBatch, "Server should have sent batch update");
        clientEngine.update(0.05f);

        // 6. Client sends InputPacket
        InputPacket inputPkt = new InputPacket();
        inputPkt.inputs = new PlayerInput[3];
        for (int i = 0; i < 3; i++) {
            inputPkt.inputs[i] = new PlayerInput();
            inputPkt.inputs[i].sequenceNumber = i;
            inputPkt.inputs[i].moveForward = 1.0f;
        }
        inputPkt.redundantInputs = new PlayerInput[0];

        InputPacket receivedInput = roundTrip(inputPkt, InputPacket.class);
        assertEquals(3, receivedInput.inputs.length);
        assertEquals(1.0f, receivedInput.inputs[0].moveForward, 1e-5f);
    }

    private byte[] serialize(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Output output = new Output(baos)) {
            kryo.writeObject(output, obj);
        }
        return baos.toByteArray();
    }

    private <T> T deserialize(byte[] bytes, Class<T> type) {
        try (Input input = new Input(new ByteArrayInputStream(bytes))) {
            return kryo.readObject(input, type);
        }
    }

    private <T> T roundTrip(T obj, Class<T> type) {
        return deserialize(serialize(obj), type);
    }
}
