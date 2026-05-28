package com.galacticodyssey.server.network;

import com.galacticodyssey.common.protocol.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Handles KryoNet connection lifecycle events and inbound message dispatch.
 *
 * <p>In production, {@code connected()}, {@code received()}, and {@code disconnected()}
 * are called from the KryoNet listener thread. All mutable game-state work is
 * forwarded to the main game-loop thread via the {@code mainThreadPoster}, which in
 * production is {@code Gdx.app::postRunnable}.
 *
 * <p>The {@code simulate*} methods mirror the KryoNet API surface so that unit tests
 * can drive this class without a live network stack.
 */
public class ServerNetworkListener {

    private final Consumer<Runnable> mainThreadPoster;
    private final Map<Integer, PlayerSession> sessions = new ConcurrentHashMap<>();
    private Consumer<PlayerSession> onSessionCreated;
    private BiConsumer<Integer, Object> sendCallback;

    /**
     * @param mainThreadPoster callback used to marshal work onto the main game-loop
     *                         thread; in production pass {@code Gdx.app::postRunnable}
     */
    public ServerNetworkListener(Consumer<Runnable> mainThreadPoster) {
        this.mainThreadPoster = mainThreadPoster;
    }

    public void setOnSessionCreated(Consumer<PlayerSession> callback) {
        this.onSessionCreated = callback;
    }

    public void setSendCallback(BiConsumer<Integer, Object> callback) {
        this.sendCallback = callback;
    }

    // -------------------------------------------------------------------------
    // KryoNet-mirror entry points
    // -------------------------------------------------------------------------

    /**
     * Called when a new TCP connection is established. No session is created here;
     * we wait for a {@link LoginRequest} to authenticate.
     *
     * @param connectionId KryoNet connection ID
     */
    public void simulateConnected(int connectionId) {
        // Intentionally a no-op: session is only created after a successful login.
    }

    /**
     * Routes an inbound message from the given connection to the appropriate handler,
     * posting real work to the main thread.
     *
     * @param connectionId KryoNet connection ID
     * @param message      deserialized message object from Kryo
     */
    public void simulateReceived(int connectionId, Object message) {
        if (message instanceof LoginRequest login) {
            mainThreadPoster.accept(() -> handleLogin(connectionId, login));
        } else if (message instanceof InputPacket packet) {
            mainThreadPoster.accept(() -> handleInput(connectionId, packet));
        } else if (message instanceof Heartbeat hb) {
            mainThreadPoster.accept(() -> handleHeartbeat(connectionId, hb));
        }
    }

    /**
     * Called when a connection is dropped. Removes the associated session.
     *
     * @param connectionId KryoNet connection ID
     */
    public void simulateDisconnected(int connectionId) {
        mainThreadPoster.accept(() -> sessions.remove(connectionId));
    }

    // -------------------------------------------------------------------------
    // Main-thread handlers
    // -------------------------------------------------------------------------

    private void handleLogin(int connectionId, LoginRequest request) {
        UUID playerId = UUID.nameUUIDFromBytes(request.username.getBytes());
        String token = UUID.randomUUID().toString();
        PlayerSession session = new PlayerSession(connectionId, playerId, token);
        sessions.put(connectionId, session);

        if (onSessionCreated != null) {
            onSessionCreated.accept(session);
        }

        LoginResponse response = new LoginResponse();
        response.success = true;
        response.sessionToken = token;
        response.playerId = playerId;
        if (sendCallback != null) {
            sendCallback.accept(connectionId, response);
        }
    }

    private void handleInput(int connectionId, InputPacket packet) {
        PlayerSession session = sessions.get(connectionId);
        if (session == null) return;

        if (packet.inputs != null) {
            for (PlayerInput input : packet.inputs) {
                if (input != null) {
                    session.enqueueInput(input);
                }
            }
        }
    }

    private void handleHeartbeat(int connectionId, Heartbeat hb) {
        // Placeholder: will be used for RTT estimation in a later task.
    }

    // -------------------------------------------------------------------------
    // Session accessors (called from main thread)
    // -------------------------------------------------------------------------

    /**
     * Returns the active session for the given connection, or {@code null} if no
     * session exists (e.g. not yet logged in, or already disconnected).
     *
     * @param connectionId KryoNet connection ID
     * @return the {@link PlayerSession}, or {@code null}
     */
    public PlayerSession getSession(int connectionId) {
        return sessions.get(connectionId);
    }

    /**
     * Returns an unmodifiable view of all currently active sessions.
     *
     * @return collection of active {@link PlayerSession} objects
     */
    public Collection<PlayerSession> getAllSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    /**
     * Explicitly removes the session for the given connection. Prefer
     * {@link #simulateDisconnected} for normal disconnection paths.
     *
     * @param connectionId KryoNet connection ID
     */
    public void removeSession(int connectionId) {
        sessions.remove(connectionId);
    }
}
