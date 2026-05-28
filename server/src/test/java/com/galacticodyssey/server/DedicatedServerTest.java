package com.galacticodyssey.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DedicatedServerTest {

    @Test
    void serverConfigDefaultValues() {
        DedicatedServer.Config config = new DedicatedServer.Config();
        assertEquals(7100, config.tcpPort);
        assertEquals(7101, config.udpPort);
        assertEquals(20, config.tickRate);
    }

    @Test
    void tickIntervalCalculation() {
        DedicatedServer.Config config = new DedicatedServer.Config();
        config.tickRate = 20;
        float expectedInterval = 1.0f / 20;
        assertEquals(expectedInterval, config.getTickInterval(), 1e-5f);
    }

    @Test
    void configAllowsCustomPorts() {
        DedicatedServer.Config config = new DedicatedServer.Config();
        config.tcpPort = 8000;
        config.udpPort = 8001;
        assertEquals(8000, config.tcpPort);
        assertEquals(8001, config.udpPort);
    }
}
