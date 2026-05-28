package com.galacticodyssey.server.replication;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.common.protocol.*;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.server.network.PlayerSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ashley {@link EntitySystem} that runs each server tick and sends interest-based
 * entity replication updates to connected clients.
 *
 * <p>For each {@link PlayerSession}, the system:
 * <ol>
 *   <li>Iterates all entities with {@link NetworkIdComponent} + {@link TransformComponent}</li>
 *   <li>Computes a distance-based {@link InterestTier} using {@link InterestManager}</li>
 *   <li>Sends {@link EntitySpawnMessage} when an entity enters a client's interest range</li>
 *   <li>Sends {@link EntityDestroyMessage} when an entity leaves or disappears</li>
 *   <li>Sends {@link EntityBatchUpdate} with per-entity state for the current tick</li>
 *   <li>Suppresses replication of a player's own entity back to themselves</li>
 * </ol>
 *
 * <p>Per-client, per-entity tracking is maintained in a nested map via
 * {@link ReplicationState}.
 */
public class ServerReplicationSystem extends EntitySystem {

    /** Callback interface for sending packets — allows test injection. */
    @FunctionalInterface
    public interface PacketSender {
        void accept(SentPacket packet);
    }

    /**
     * Captures a single outbound packet for a specific connection. The
     * {@code connectionId} matches the KryoNet connection identifier and
     * {@code message} is the protocol object to be serialised and sent.
     *
     * <p>Exposed as a public record so tests can inspect what was sent.
     */
    public record SentPacket(int connectionId, Object message) {}

    private final PacketSender packetSender;
    private final InterestManager interestManager = new InterestManager();
    private final Map<Integer, PlayerSession> sessions = new ConcurrentHashMap<>();

    /** connectionId -> (networkId -> ReplicationState) */
    private final Map<Integer, Map<Integer, ReplicationState>> clientStates = new HashMap<>();

    private final ComponentMapper<NetworkIdComponent> netMapper =
            ComponentMapper.getFor(NetworkIdComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);

    private ImmutableArray<Entity> networkedEntities;

    private int serverTick;
    private double originOffsetX;
    private double originOffsetY;
    private double originOffsetZ;

    /**
     * Creates the system with the given {@link PacketSender}.
     * Because {@link PacketSender} is a {@code @FunctionalInterface} with the same
     * shape as {@code Consumer<SentPacket>}, callers may pass either a lambda or a
     * method reference (e.g. {@code list::add}) directly.
     *
     * @param sender callback invoked for every outbound packet
     */
    public ServerReplicationSystem(PacketSender sender) {
        super(50);
        this.packetSender = sender;
    }

    /**
     * Registers a connected client session. Must be called from the game-loop thread.
     *
     * @param session the newly connected player session
     */
    public void addSession(PlayerSession session) {
        sessions.put(session.getConnectionId(), session);
        clientStates.put(session.getConnectionId(), new HashMap<>());
    }

    /**
     * Removes a disconnected client session. Must be called from the game-loop thread.
     *
     * @param connectionId the KryoNet connection ID that disconnected
     */
    public void removeSession(int connectionId) {
        sessions.remove(connectionId);
        clientStates.remove(connectionId);
    }

    /**
     * Sets the current authoritative server tick. Must be updated once per tick
     * before {@link Engine#update(float)} is called.
     *
     * @param tick monotonically increasing tick counter (0-based)
     */
    public void setServerTick(int tick) {
        this.serverTick = tick;
    }

    /**
     * Updates the floating-origin offset applied when converting local-space entity
     * positions to galaxy-space for interest-range calculations.
     *
     * @param x galaxy X offset (metres)
     * @param y galaxy Y offset (metres)
     * @param z galaxy Z offset (metres)
     */
    public void setOriginOffset(double x, double y, double z) {
        this.originOffsetX = x;
        this.originOffsetY = y;
        this.originOffsetZ = z;
    }

    @Override
    public void addedToEngine(Engine engine) {
        networkedEntities = engine.getEntitiesFor(
                Family.all(NetworkIdComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (PlayerSession session : sessions.values()) {
            final int connId = session.getConnectionId();
            final Map<Integer, ReplicationState> states = clientStates.get(connId);
            if (states == null) continue;

            final double px = session.getGalaxyX();
            final double py = session.getGalaxyY();
            final double pz = session.getGalaxyZ();

            final Set<Integer> seenThisTick = new HashSet<>();
            final List<EntityStateUpdate> updates = new ArrayList<>();

            for (int i = 0; i < networkedEntities.size(); i++) {
                final Entity entity = networkedEntities.get(i);
                final NetworkIdComponent netId = netMapper.get(entity);
                final TransformComponent transform = transformMapper.get(entity);

                // Never replicate a player's own entity back to themselves.
                if (netId.networkId == session.getPlayerNetworkId()) continue;

                final double ex = transform.position.x + originOffsetX;
                final double ey = transform.position.y + originOffsetY;
                final double ez = transform.position.z + originOffsetZ;

                final InterestTier tier = interestManager.computeTier(px, py, pz, ex, ey, ez);
                seenThisTick.add(netId.networkId);

                ReplicationState repState = states.get(netId.networkId);

                if (tier == InterestTier.NONE) {
                    if (repState != null) {
                        // Entity moved out of interest — tell the client to remove it.
                        final EntityDestroyMessage destroy = new EntityDestroyMessage();
                        destroy.networkId = netId.networkId;
                        packetSender.accept(new SentPacket(connId, destroy));
                        states.remove(netId.networkId);
                    }
                    continue;
                }

                if (repState == null) {
                    // Entity just entered interest range — send a spawn message.
                    repState = new ReplicationState(netId.networkId);
                    repState.setCurrentTier(tier);
                    states.put(netId.networkId, repState);

                    final EntitySpawnMessage spawn = new EntitySpawnMessage();
                    spawn.networkId = netId.networkId;
                    spawn.entityType = "entity";
                    spawn.componentData = new byte[0];
                    packetSender.accept(new SentPacket(connId, spawn));
                    repState.markSent(serverTick);
                    continue;
                }

                // Entity is already known to this client — queue a state update if the
                // tier's cadence says we should send this tick.
                repState.setCurrentTier(tier);
                if (interestManager.shouldSendThisTick(tier, serverTick)) {
                    final EntityStateUpdate update = new EntityStateUpdate();
                    update.networkId = netId.networkId;
                    update.serverTick = serverTick;
                    update.dirtyMask = 0b1;
                    update.payload = new byte[0];
                    updates.add(update);
                    repState.markSent(serverTick);
                }
            }

            // Send destroy for entities that have been removed from the engine entirely.
            final Set<Integer> toRemove = new HashSet<>();
            for (int networkId : states.keySet()) {
                if (!seenThisTick.contains(networkId)) {
                    final EntityDestroyMessage destroy = new EntityDestroyMessage();
                    destroy.networkId = networkId;
                    packetSender.accept(new SentPacket(connId, destroy));
                    toRemove.add(networkId);
                }
            }
            toRemove.forEach(states::remove);

            // Flush all queued state updates as a single batch packet.
            if (!updates.isEmpty()) {
                final EntityBatchUpdate batch = new EntityBatchUpdate();
                batch.serverTick = serverTick;
                batch.lastProcessedInputSequence = session.getLastProcessedInputSequence();
                batch.updates = updates.toArray(new EntityStateUpdate[0]);
                packetSender.accept(new SentPacket(connId, batch));
            }
        }
    }
}
