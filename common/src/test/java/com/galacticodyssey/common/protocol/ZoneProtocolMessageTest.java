package com.galacticodyssey.common.protocol;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.galacticodyssey.common.serialization.NetworkKryoRegistrar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneProtocolMessageTest {

    private Kryo kryo;

    @BeforeEach
    void setUp() {
        kryo = new Kryo();
        NetworkKryoRegistrar.register(kryo);
    }

    @Test
    void zoneJoinRequestRoundTrip() {
        ZoneJoinRequest msg = new ZoneJoinRequest();
        msg.sessionToken = "tok-abc";
        msg.zoneId = UUID.randomUUID();
        ZoneJoinRequest result = roundTrip(msg, ZoneJoinRequest.class);
        assertEquals("tok-abc", result.sessionToken);
        assertEquals(msg.zoneId, result.zoneId);
    }

    @Test
    void zoneJoinResponseRoundTrip() {
        ZoneJoinResponse msg = new ZoneJoinResponse();
        msg.success = true;
        msg.worldSnapshotData = new byte[]{1, 2, 3};
        ZoneJoinResponse result = roundTrip(msg, ZoneJoinResponse.class);
        assertTrue(result.success);
        assertArrayEquals(new byte[]{1, 2, 3}, result.worldSnapshotData);
    }

    @Test
    void zoneRedirectRoundTrip() {
        ZoneRedirect msg = new ZoneRedirect();
        msg.newZoneAddress = "10.0.0.5:7100";
        msg.handoffToken = "handoff-xyz";
        msg.targetZoneId = UUID.randomUUID();
        ZoneRedirect result = roundTrip(msg, ZoneRedirect.class);
        assertEquals("10.0.0.5:7100", result.newZoneAddress);
        assertEquals("handoff-xyz", result.handoffToken);
        assertEquals(msg.targetZoneId, result.targetZoneId);
    }

    @Test
    void handoffPrepareRoundTrip() {
        HandoffPrepare msg = new HandoffPrepare();
        msg.entityNetworkId = 42;
        msg.sourceZoneId = UUID.randomUUID();
        msg.targetZoneId = UUID.randomUUID();
        msg.serializedEntityData = new byte[]{10, 20, 30};
        msg.playerSessionToken = "tok-player";
        HandoffPrepare result = roundTrip(msg, HandoffPrepare.class);
        assertEquals(42, result.entityNetworkId);
        assertEquals(msg.sourceZoneId, result.sourceZoneId);
        assertEquals(msg.targetZoneId, result.targetZoneId);
        assertArrayEquals(new byte[]{10, 20, 30}, result.serializedEntityData);
        assertEquals("tok-player", result.playerSessionToken);
    }

    @Test
    void handoffTransferAckRoundTrip() {
        HandoffTransferAck msg = new HandoffTransferAck();
        msg.entityNetworkId = 42;
        msg.sourceZoneId = UUID.randomUUID();
        msg.targetZoneId = UUID.randomUUID();
        msg.success = true;
        HandoffTransferAck result = roundTrip(msg, HandoffTransferAck.class);
        assertEquals(42, result.entityNetworkId);
        assertEquals(msg.sourceZoneId, result.sourceZoneId);
        assertEquals(msg.targetZoneId, result.targetZoneId);
        assertTrue(result.success);
    }

    @SuppressWarnings("unchecked")
    private <T> T roundTrip(T obj, Class<T> type) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeObject(output, obj);
        output.close();
        Input input = new Input(new ByteArrayInputStream(baos.toByteArray()));
        return kryo.readObject(input, type);
    }
}
