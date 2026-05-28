package com.galacticodyssey.server.zone;

import com.galacticodyssey.common.protocol.HandoffPrepare;
import com.galacticodyssey.common.protocol.HandoffTransferAck;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ZoneHandoffManager {

    public record HandoffResult(boolean success, String playerSessionToken, UUID targetZoneId) {}

    private record PendingHandoff(UUID sourceZoneId, UUID targetZoneId, String playerSessionToken) {}

    private final Consumer<HandoffPrepare> publishCallback;
    private final Map<Integer, PendingHandoff> pendingHandoffs = new HashMap<>();

    public ZoneHandoffManager(Consumer<HandoffPrepare> publishCallback) {
        this.publishCallback = publishCallback;
    }

    public void initiateHandoff(int entityNetworkId, UUID sourceZoneId, UUID targetZoneId,
                                byte[] serializedEntityData, String playerSessionToken) {
        HandoffPrepare prepare = new HandoffPrepare();
        prepare.entityNetworkId = entityNetworkId;
        prepare.sourceZoneId = sourceZoneId;
        prepare.targetZoneId = targetZoneId;
        prepare.serializedEntityData = serializedEntityData;
        prepare.playerSessionToken = playerSessionToken;

        pendingHandoffs.put(entityNetworkId, new PendingHandoff(sourceZoneId, targetZoneId, playerSessionToken));
        publishCallback.accept(prepare);
    }

    public boolean isPending(int entityNetworkId) {
        return pendingHandoffs.containsKey(entityNetworkId);
    }

    public HandoffResult acknowledgeHandoff(HandoffTransferAck ack) {
        PendingHandoff pending = pendingHandoffs.remove(ack.entityNetworkId);
        if (pending == null) return null;

        return new HandoffResult(ack.success, pending.playerSessionToken, pending.targetZoneId);
    }
}
