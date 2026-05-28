package com.galacticodyssey.gateway;

import com.galacticodyssey.common.protocol.LoginRequest;
import com.galacticodyssey.common.protocol.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class GatewayNetworkListenerTest {

    private final ConcurrentLinkedQueue<Object> sentMessages = new ConcurrentLinkedQueue<>();
    private SessionManager sessionManager;
    private GatewayNetworkListener listener;

    @BeforeEach
    void setUp() {
        SessionManagerTest.FakeRedisAdapter redis = new SessionManagerTest.FakeRedisAdapter();
        sessionManager = new SessionManager(redis, 300);
        BiConsumer<Integer, Object> sendCallback = (connId, msg) -> sentMessages.add(msg);
        listener = new GatewayNetworkListener(sessionManager, null, sendCallback, Runnable::run);
    }

    @Test
    void loginRequestCreatesSessionAndResponds() {
        LoginRequest request = new LoginRequest();
        request.username = "testplayer";
        request.clientVersion = "1.0";
        listener.simulateReceived(1, request);
        assertEquals(1, sentMessages.size());
        Object msg = sentMessages.poll();
        assertInstanceOf(LoginResponse.class, msg);
        LoginResponse response = (LoginResponse) msg;
        assertTrue(response.success);
        assertNotNull(response.sessionToken);
        assertNotNull(response.playerId);
    }

    @Test
    void loginWithNullRouterStillSucceeds() {
        LoginRequest request = new LoginRequest();
        request.username = "player2";
        request.clientVersion = "1.0";
        listener.simulateReceived(2, request);
        LoginResponse response = (LoginResponse) sentMessages.poll();
        assertTrue(response.success);
    }

    @Test
    void unknownMessageIsIgnored() {
        listener.simulateReceived(1, "garbage");
        assertTrue(sentMessages.isEmpty());
    }

    @Test
    void disconnectCleansUp() {
        LoginRequest request = new LoginRequest();
        request.username = "dcplayer";
        request.clientVersion = "1.0";
        listener.simulateReceived(1, request);
        sentMessages.clear();
        listener.simulateDisconnected(1);
        // No crash
    }
}
