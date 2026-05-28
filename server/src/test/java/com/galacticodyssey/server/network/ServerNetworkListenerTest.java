package com.galacticodyssey.server.network;

import com.galacticodyssey.common.protocol.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServerNetworkListenerTest {

    private ServerNetworkListener listener;
    private List<Runnable> postedRunnables;

    @BeforeEach
    void setUp() {
        postedRunnables = new ArrayList<>();
        listener = new ServerNetworkListener(postedRunnables::add);
    }

    @Test
    void loginRequestCreatesSession() {
        listener.simulateConnected(1);

        LoginRequest request = new LoginRequest();
        request.username = "player1";
        request.clientVersion = "0.1.0";
        listener.simulateReceived(1, request);

        // Drain posted runnables (simulating main thread)
        postedRunnables.forEach(Runnable::run);

        PlayerSession session = listener.getSession(1);
        assertNotNull(session);
        assertEquals(1, session.getConnectionId());
    }

    @Test
    void inputPacketEnqueuesInputsToSession() {
        listener.simulateConnected(1);
        LoginRequest login = new LoginRequest();
        login.username = "player1";
        login.clientVersion = "0.1.0";
        listener.simulateReceived(1, login);
        postedRunnables.forEach(Runnable::run);
        postedRunnables.clear();

        InputPacket packet = new InputPacket();
        packet.inputs = new PlayerInput[1];
        packet.inputs[0] = new PlayerInput();
        packet.inputs[0].sequenceNumber = 42;
        packet.redundantInputs = new PlayerInput[0];
        listener.simulateReceived(1, packet);
        postedRunnables.forEach(Runnable::run);

        PlayerSession session = listener.getSession(1);
        var inputs = session.drainInputs();
        assertEquals(1, inputs.size());
        assertEquals(42, inputs.get(0).sequenceNumber);
    }

    @Test
    void disconnectRemovesSession() {
        listener.simulateConnected(1);
        LoginRequest login = new LoginRequest();
        login.username = "player1";
        login.clientVersion = "0.1.0";
        listener.simulateReceived(1, login);
        postedRunnables.forEach(Runnable::run);
        postedRunnables.clear();

        listener.simulateDisconnected(1);
        postedRunnables.forEach(Runnable::run);

        assertNull(listener.getSession(1));
    }

    @Test
    void getAllSessionsReturnsActiveSessions() {
        listener.simulateConnected(1);
        listener.simulateConnected(2);
        LoginRequest login1 = new LoginRequest();
        login1.username = "p1";
        login1.clientVersion = "0.1.0";
        LoginRequest login2 = new LoginRequest();
        login2.username = "p2";
        login2.clientVersion = "0.1.0";
        listener.simulateReceived(1, login1);
        listener.simulateReceived(2, login2);
        postedRunnables.forEach(Runnable::run);

        assertEquals(2, listener.getAllSessions().size());
    }
}
