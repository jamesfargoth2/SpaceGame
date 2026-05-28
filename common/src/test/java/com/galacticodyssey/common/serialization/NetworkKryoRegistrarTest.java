package com.galacticodyssey.common.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.galacticodyssey.common.protocol.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NetworkKryoRegistrarTest {

    private Kryo kryo;

    @BeforeEach
    void setUp() {
        kryo = new Kryo();
        NetworkKryoRegistrar.register(kryo);
    }

    @Test
    void roundTripLoginRequest() {
        LoginRequest original = new LoginRequest();
        original.username = "testplayer";
        original.clientVersion = "0.1.0";

        LoginRequest restored = roundTrip(original, LoginRequest.class);

        assertEquals("testplayer", restored.username);
        assertEquals("0.1.0", restored.clientVersion);
    }

    @Test
    void roundTripLoginResponse() {
        LoginResponse original = new LoginResponse();
        original.success = true;
        original.sessionToken = "token-abc";
        original.zoneServerHost = "localhost";
        original.zoneServerTcpPort = 7100;
        original.zoneServerUdpPort = 7101;
        original.playerId = UUID.randomUUID();

        LoginResponse restored = roundTrip(original, LoginResponse.class);

        assertTrue(restored.success);
        assertEquals("token-abc", restored.sessionToken);
        assertEquals(original.playerId, restored.playerId);
    }

    @Test
    void roundTripInputPacket() {
        InputPacket original = new InputPacket();
        original.inputs = new PlayerInput[3];
        for (int i = 0; i < 3; i++) {
            original.inputs[i] = new PlayerInput();
            original.inputs[i].sequenceNumber = 100 + i;
            original.inputs[i].moveForward = 0.5f * i;
        }
        original.redundantInputs = new PlayerInput[0];

        InputPacket restored = roundTrip(original, InputPacket.class);

        assertEquals(3, restored.inputs.length);
        assertEquals(101, restored.inputs[1].sequenceNumber);
        assertEquals(0.5f, restored.inputs[1].moveForward, 1e-5f);
    }

    @Test
    void roundTripEntityBatchUpdate() {
        EntityBatchUpdate original = new EntityBatchUpdate();
        original.serverTick = 500;
        original.lastProcessedInputSequence = 120;
        original.updates = new EntityStateUpdate[1];
        original.updates[0] = new EntityStateUpdate();
        original.updates[0].networkId = 42;
        original.updates[0].serverTick = 500;
        original.updates[0].dirtyMask = 0b101;
        original.updates[0].payload = new byte[]{10, 20, 30};

        EntityBatchUpdate restored = roundTrip(original, EntityBatchUpdate.class);

        assertEquals(500, restored.serverTick);
        assertEquals(120, restored.lastProcessedInputSequence);
        assertEquals(42, restored.updates[0].networkId);
        assertArrayEquals(new byte[]{10, 20, 30}, restored.updates[0].payload);
    }

    @Test
    void roundTripEntitySpawnMessage() {
        EntitySpawnMessage original = new EntitySpawnMessage();
        original.networkId = 1;
        original.entityType = "player";
        original.persistenceId = UUID.randomUUID();
        original.componentData = new byte[]{1, 2, 3, 4};

        EntitySpawnMessage restored = roundTrip(original, EntitySpawnMessage.class);

        assertEquals(1, restored.networkId);
        assertEquals("player", restored.entityType);
        assertEquals(original.persistenceId, restored.persistenceId);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, restored.componentData);
    }

    @Test
    void roundTripDisconnect() {
        Disconnect original = new Disconnect();
        original.reason = Disconnect.Reason.TIMEOUT;

        Disconnect restored = roundTrip(original, Disconnect.class);

        assertEquals(Disconnect.Reason.TIMEOUT, restored.reason);
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
