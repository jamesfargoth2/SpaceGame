package com.galacticodyssey.server;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

/**
 * Entry point for the dedicated headless server. Parses optional CLI arguments,
 * constructs a {@link DedicatedServer.Config}, and boots a
 * {@link HeadlessApplication} running at the configured tick rate.
 */
public class ServerLauncher {

    public static void main(String[] args) {
        DedicatedServer.Config config = new DedicatedServer.Config();

        // Parse optional CLI args
        for (int i = 0; i < args.length; i++) {
            if ("--tcp-port".equals(args[i]) && i + 1 < args.length) {
                config.tcpPort = Integer.parseInt(args[++i]);
            } else if ("--udp-port".equals(args[i]) && i + 1 < args.length) {
                config.udpPort = Integer.parseInt(args[++i]);
            } else if ("--tick-rate".equals(args[i]) && i + 1 < args.length) {
                config.tickRate = Integer.parseInt(args[++i]);
            }
        }

        HeadlessApplicationConfiguration appConfig = new HeadlessApplicationConfiguration();
        appConfig.updatesPerSecond = config.tickRate;
        new HeadlessApplication(new DedicatedServer(config), appConfig);
    }
}
