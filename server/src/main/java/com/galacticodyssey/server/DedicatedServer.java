package com.galacticodyssey.server;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.ApplicationAdapter;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.galacticodyssey.common.protocol.*;
import com.galacticodyssey.common.serialization.NetworkKryoRegistrar;
import com.galacticodyssey.persistence.KryoRegistrar;
import com.galacticodyssey.server.network.PlayerSession;
import com.galacticodyssey.server.network.ServerNetworkListener;
import com.galacticodyssey.server.replication.ServerReplicationSystem;

import java.io.IOException;
import java.util.List;

/**
 * Main headless server application that manages the 20 Hz game loop.
 *
 * <p>Extends {@link ApplicationAdapter} to integrate with the libGDX
 * {@link com.badlogic.gdx.backends.headless.HeadlessApplication} lifecycle.
 * On each {@link #render()} call (driven at {@link Config#tickRate} Hz by
 * {@code HeadlessApplicationConfiguration.updatesPerSecond}):
 * <ol>
 *   <li>Player inputs are drained from {@link PlayerSession} queues.</li>
 *   <li>The Ashley engine is stepped by one tick interval.</li>
 *   <li>{@link ServerReplicationSystem} sends entity-state packets to clients.</li>
 * </ol>
 */
public class DedicatedServer extends ApplicationAdapter {

    /**
     * Server configuration. All fields are public for convenient CLI parsing in
     * {@link ServerLauncher}.
     */
    public static class Config {
        /** TCP port for reliable messages (login, spawn, destroy). */
        public int tcpPort = 7100;
        /** UDP port for unreliable messages (state updates). */
        public int udpPort = 7101;
        /** Simulation ticks per second. */
        public int tickRate = 20;

        /**
         * Returns the duration of a single tick in seconds.
         *
         * @return {@code 1.0f / tickRate}
         */
        public float getTickInterval() {
            return 1.0f / tickRate;
        }
    }

    private final Config config;
    private Engine engine;
    private Server kryoServer;
    private ServerNetworkListener networkListener;
    private ServerReplicationSystem replicationSystem;
    private int currentTick;

    /**
     * Creates a server with the supplied configuration.
     *
     * @param config server configuration (ports, tick rate)
     */
    public DedicatedServer(Config config) {
        this.config = config;
    }

    /** Creates a server with default configuration (TCP 7100, UDP 7101, 20 Hz). */
    public DedicatedServer() {
        this(new Config());
    }

    @Override
    public void create() {
        engine = new Engine();

        // Replication system sends packets via KryoNet
        replicationSystem = new ServerReplicationSystem(packet -> {
            if (kryoServer == null) return;
            Connection[] connections = kryoServer.getConnections();
            for (Connection conn : connections) {
                if (conn.getID() == packet.connectionId()) {
                    if (packet.message() instanceof EntityBatchUpdate) {
                        conn.sendUDP(packet.message());
                    } else {
                        conn.sendTCP(packet.message());
                    }
                    break;
                }
            }
        });
        engine.addSystem(replicationSystem);

        // Network listener marshals KryoNet callbacks onto the main game-loop thread
        networkListener = new ServerNetworkListener(runnable ->
            com.badlogic.gdx.Gdx.app.postRunnable(runnable));

        networkListener.setOnSessionCreated(session ->
            replicationSystem.addSession(session));

        // Start KryoNet server
        kryoServer = new Server(131072, 16384);
        KryoRegistrar.register(kryoServer.getKryo());
        NetworkKryoRegistrar.register(kryoServer.getKryo());

        networkListener.setSendCallback((connectionId, message) -> {
            Connection[] connections = kryoServer.getConnections();
            for (Connection conn : connections) {
                if (conn.getID() == connectionId) {
                    conn.sendTCP(message);
                    break;
                }
            }
        });

        kryoServer.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                networkListener.simulateConnected(connection.getID());
            }

            @Override
            public void received(Connection connection, Object object) {
                networkListener.simulateReceived(connection.getID(), object);
            }

            @Override
            public void disconnected(Connection connection) {
                final int connId = connection.getID();
                networkListener.simulateDisconnected(connId);
                com.badlogic.gdx.Gdx.app.postRunnable(() ->
                    replicationSystem.removeSession(connId));
            }
        });

        try {
            kryoServer.bind(config.tcpPort, config.udpPort);
            kryoServer.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to bind server on ports " +
                config.tcpPort + "/" + config.udpPort, e);
        }
    }

    /**
     * Called by {@link com.badlogic.gdx.backends.headless.HeadlessApplication} at
     * {@link Config#tickRate} Hz. Drains player inputs then steps the ECS engine.
     */
    @Override
    public void render() {
        float tickInterval = config.getTickInterval();

        // Drain and acknowledge player inputs for each connected session
        for (PlayerSession session : networkListener.getAllSessions()) {
            List<PlayerInput> inputs = session.drainInputs();
            for (PlayerInput input : inputs) {
                session.setLastProcessedInputSequence(input.sequenceNumber);
            }
        }

        // Step simulation; ServerReplicationSystem runs as part of engine.update()
        replicationSystem.setServerTick(currentTick);
        engine.update(tickInterval);
        currentTick++;
    }

    @Override
    public void dispose() {
        if (kryoServer != null) {
            kryoServer.stop();
            kryoServer.close();
        }
    }

    /**
     * Returns the Ashley engine. Primarily used by integration tests to add
     * entities or inspect system state.
     *
     * @return the server-side Ashley {@link Engine}
     */
    public Engine getEngine() {
        return engine;
    }

    /**
     * Returns the monotonically increasing tick counter (0-based), incremented
     * once per {@link #render()} call.
     *
     * @return current tick count
     */
    public int getCurrentTick() {
        return currentTick;
    }
}
