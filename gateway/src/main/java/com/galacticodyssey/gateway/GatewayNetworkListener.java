package com.galacticodyssey.gateway;

import com.galacticodyssey.common.protocol.LoginRequest;
import com.galacticodyssey.common.protocol.LoginResponse;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class GatewayNetworkListener {

    private final SessionManager sessionManager;
    private final ZoneRouter zoneRouter;
    private final BiConsumer<Integer, Object> sendCallback;
    private final Consumer<Runnable> mainThreadPoster;
    private final Map<Integer, String> connectionTokens = new ConcurrentHashMap<>();

    public GatewayNetworkListener(SessionManager sessionManager,
                                  ZoneRouter zoneRouter,
                                  BiConsumer<Integer, Object> sendCallback,
                                  Consumer<Runnable> mainThreadPoster) {
        this.sessionManager = sessionManager;
        this.zoneRouter = zoneRouter;
        this.sendCallback = sendCallback;
        this.mainThreadPoster = mainThreadPoster;
    }

    public void simulateConnected(int connectionId) {}

    public void simulateReceived(int connectionId, Object message) {
        if (message instanceof LoginRequest login) {
            mainThreadPoster.accept(() -> handleLogin(connectionId, login));
        }
    }

    public void simulateDisconnected(int connectionId) {
        mainThreadPoster.accept(() -> {
            String token = connectionTokens.remove(connectionId);
            if (token != null) sessionManager.destroySession(token);
        });
    }

    private void handleLogin(int connectionId, LoginRequest request) {
        UUID playerId = UUID.nameUUIDFromBytes(request.username.getBytes());

        String zoneAddress = null;
        String zoneId = null;

        if (zoneRouter != null) {
            try {
                ZoneRouter.RouteInfo route = zoneRouter.resolveByPlayer(request.username);
                if (route != null) {
                    zoneAddress = route.serverAddress();
                    zoneId = route.zoneId().toString();
                }
            } catch (Exception e) {
                // Fall through with default zone
            }
        }

        if (zoneId == null) zoneId = "00000000-0000-0000-0000-000000000001";

        String token = sessionManager.createSession(playerId, zoneId);
        connectionTokens.put(connectionId, token);

        LoginResponse response = new LoginResponse();
        response.success = true;
        response.sessionToken = token;
        response.playerId = playerId;
        response.zoneServerAddress = zoneAddress;

        sendCallback.accept(connectionId, response);
    }
}
