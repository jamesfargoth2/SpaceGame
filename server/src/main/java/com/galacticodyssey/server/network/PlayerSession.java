package com.galacticodyssey.server.network;

import com.galacticodyssey.common.protocol.PlayerInput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Server-side representation of a single connected client. Created on successful
 * login and destroyed when the connection is dropped.
 *
 * <p>Thread safety: {@link #enqueueInput} may be called from the KryoNet listener
 * thread; {@link #drainInputs} is called from the main game-loop thread. The
 * underlying {@link ConcurrentLinkedQueue} makes this safe without extra locking.
 * All other fields are only accessed from the game-loop thread.
 */
public class PlayerSession {

    private final int connectionId;
    private final UUID playerId;
    private final String sessionToken;
    private final ConcurrentLinkedQueue<PlayerInput> inputQueue = new ConcurrentLinkedQueue<>();

    private int lastProcessedInputSequence = -1;
    private int playerNetworkId = -1;
    private double galaxyX;
    private double galaxyY;
    private double galaxyZ;
    private UUID zoneId;

    public UUID getZoneId() { return zoneId; }
    public void setZoneId(UUID zoneId) { this.zoneId = zoneId; }

    /**
     * @param connectionId  KryoNet connection identifier for this session
     * @param playerId      persistent player UUID from authentication
     * @param sessionToken  opaque bearer token issued at login
     */
    public PlayerSession(int connectionId, UUID playerId, String sessionToken) {
        this.connectionId = connectionId;
        this.playerId = playerId;
        this.sessionToken = sessionToken;
    }

    /** @return the KryoNet connection ID for this session */
    public int getConnectionId() {
        return connectionId;
    }

    /** @return the persistent player UUID */
    public UUID getPlayerId() {
        return playerId;
    }

    /** @return the bearer token issued at login */
    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Enqueues a player input packet. Safe to call from the KryoNet listener thread.
     *
     * @param input the input packet received from the client
     */
    public void enqueueInput(PlayerInput input) {
        inputQueue.add(input);
    }

    /**
     * Drains all queued inputs into a list, preserving FIFO order, and clears
     * the queue. Called from the main game-loop thread each tick.
     *
     * @return ordered list of inputs since the last drain (may be empty)
     */
    public List<PlayerInput> drainInputs() {
        List<PlayerInput> result = new ArrayList<>();
        PlayerInput input;
        while ((input = inputQueue.poll()) != null) {
            result.add(input);
        }
        return result;
    }

    /**
     * @return the sequence number of the last input fully processed by the server,
     *         or {@code -1} if no input has been processed yet
     */
    public int getLastProcessedInputSequence() {
        return lastProcessedInputSequence;
    }

    /**
     * Records the sequence number of the most recently processed input so it can
     * be piggybacked onto state-update packets for client-side reconciliation.
     *
     * @param seq the sequence number that was just applied
     */
    public void setLastProcessedInputSequence(int seq) {
        this.lastProcessedInputSequence = seq;
    }

    /**
     * @return the network entity ID assigned to this player's avatar, or {@code -1}
     *         if no entity has been spawned yet
     */
    public int getPlayerNetworkId() {
        return playerNetworkId;
    }

    /**
     * Assigns the network entity ID for this player's avatar. Used by
     * {@code ServerReplicationSystem} to suppress sending the player's own state
     * back to themselves.
     *
     * @param id the {@code NetworkIdComponent} value of the player entity
     */
    public void setPlayerNetworkId(int id) {
        this.playerNetworkId = id;
    }

    /**
     * Records the player's galaxy-space position for interest-management distance
     * calculations. Coordinates are in 64-bit doubles (meters from galaxy origin).
     *
     * @param x galaxy X coordinate
     * @param y galaxy Y coordinate
     * @param z galaxy Z coordinate
     */
    public void setGalaxyPosition(double x, double y, double z) {
        this.galaxyX = x;
        this.galaxyY = y;
        this.galaxyZ = z;
    }

    /** @return galaxy-space X coordinate (meters from galaxy origin) */
    public double getGalaxyX() {
        return galaxyX;
    }

    /** @return galaxy-space Y coordinate (meters from galaxy origin) */
    public double getGalaxyY() {
        return galaxyY;
    }

    /** @return galaxy-space Z coordinate (meters from galaxy origin) */
    public double getGalaxyZ() {
        return galaxyZ;
    }
}
