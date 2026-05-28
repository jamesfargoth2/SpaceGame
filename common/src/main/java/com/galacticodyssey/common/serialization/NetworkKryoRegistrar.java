package com.galacticodyssey.common.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import com.galacticodyssey.common.protocol.*;

import java.util.UUID;

/**
 * Kryo type registration for network messages. IDs 110–149, appended to the
 * persistence range (10–109) defined in KryoRegistrar. Append only —
 * never reorder or reuse IDs.
 */
public final class NetworkKryoRegistrar {

    private NetworkKryoRegistrar() {}

    public static void register(Kryo kryo) {
        kryo.setDefaultSerializer(CompatibleFieldSerializer.class);
        kryo.setRegistrationRequired(true);

        // UUID needed if not already registered (standalone use without KryoRegistrar)
        if (kryo.getClassResolver().getRegistration(UUID.class) == null) {
            kryo.register(UUID.class, new DefaultSerializers.UUIDSerializer(), 10);
        }

        // --- Network protocol messages (110–149) ---
        kryo.register(LoginRequest.class, 110);
        kryo.register(LoginResponse.class, 111);
        kryo.register(Heartbeat.class, 112);
        kryo.register(HeartbeatAck.class, 113);
        kryo.register(Disconnect.class, 114);
        kryo.register(Disconnect.Reason.class, 115);
        kryo.register(PlayerInput.class, 116);
        kryo.register(PlayerInput[].class, 117);
        kryo.register(boolean[].class, 118);
        kryo.register(InputPacket.class, 119);
        kryo.register(EntityStateUpdate.class, 120);
        kryo.register(EntityStateUpdate[].class, 121);
        kryo.register(EntityBatchUpdate.class, 122);
        kryo.register(EntitySpawnMessage.class, 123);
        kryo.register(EntityDestroyMessage.class, 124);

        // byte[] and String are registered by KryoRegistrar (IDs 15 and implicit).
        // Guard to avoid ID conflicts when both registrars are active.
        if (kryo.getClassResolver().getRegistration(byte[].class) == null) {
            kryo.register(byte[].class, 125);
        }
        if (kryo.getClassResolver().getRegistration(String.class) == null) {
            kryo.register(String.class, 126);
        }

        // --- Zone protocol messages (127–131) ---
        kryo.register(ZoneJoinRequest.class, 127);
        kryo.register(ZoneJoinResponse.class, 128);
        kryo.register(ZoneRedirect.class, 129);
        kryo.register(HandoffPrepare.class, 130);
        kryo.register(HandoffTransferAck.class, 131);
    }
}
