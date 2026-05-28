package com.galacticodyssey.gateway;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.galacticodyssey.common.serialization.NetworkKryoRegistrar;

import java.io.IOException;

public class GatewayServer {

    public static class Config {
        public int tcpPort = 7000;
        public String dbHost = "localhost";
        public int dbPort = 5432;
        public String dbName = "galactic_odyssey";
        public String dbUser = "galactic";
        public String dbPassword = "dev_only";
        public String redisHost = "localhost";
        public int redisPort = 6379;
        public int sessionTtlSeconds = 300;
    }

    private final Config config;
    private Server kryoServer;
    private GatewayNetworkListener networkListener;

    public GatewayServer(Config config) {
        this.config = config;
    }

    public void setNetworkListener(GatewayNetworkListener listener) {
        this.networkListener = listener;
    }

    public void start() throws IOException {
        kryoServer = new Server(131072, 16384);
        NetworkKryoRegistrar.register(kryoServer.getKryo());

        kryoServer.addListener(new Listener() {
            @Override public void connected(Connection connection) {
                if (networkListener != null) networkListener.simulateConnected(connection.getID());
            }
            @Override public void received(Connection connection, Object object) {
                if (networkListener != null) networkListener.simulateReceived(connection.getID(), object);
            }
            @Override public void disconnected(Connection connection) {
                if (networkListener != null) networkListener.simulateDisconnected(connection.getID());
            }
        });

        kryoServer.bind(config.tcpPort);
        kryoServer.start();
    }

    public void stop() {
        if (kryoServer != null) { kryoServer.stop(); kryoServer.close(); }
    }

    public Config getConfig() { return config; }

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        GatewayServer server = new GatewayServer(config);
        server.start();
    }
}
