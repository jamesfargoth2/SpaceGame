---
name: libgdx-network-protocol
description: >
  How to design and implement SpaceGame's network protocol layer using KryoNet for MMO-scale
  multiplayer. Covers KryoNet server/client setup, Kryo serialization registration, message type
  hierarchy with reliable (TCP) and unreliable (UDP) channels, bandwidth budgeting, connection
  lifecycle (connect, authenticate, heartbeat, timeout, reconnect), input message bundling,
  state update packet format, event messages for combat and economy, and tick synchronization
  between server and clients. Use this skill whenever the user wants to: set up KryoNet, define
  network message types, register Kryo serializers, choose between TCP and UDP for a message,
  design packet formats, implement connection handling, add heartbeat/keepalive, handle
  disconnection and reconnection, optimize packet size, bundle messages, synchronize tick
  counters, or add a new network message type. Also trigger for "KryoNet setup", "message
  types", "TCP vs UDP", "packet format", "serialization", "Kryo registration", "connection
  lifecycle", "heartbeat", "timeout", "reconnection", "bandwidth", "packet size",
  "tick sync", or "network transport".
---

# Network Protocol & Message Design

This skill covers the transport layer: how SpaceGame sends data between clients and servers. It builds on KryoNet (the transport library specified in DESIGN.md) and defines the message types, serialization strategy, channel selection, and connection lifecycle that all other networking skills depend on.

The protocol must handle two very different traffic patterns: high-frequency, loss-tolerant state updates (positions every tick) and low-frequency, must-arrive events (damage dealt, item acquired, zone transitions). KryoNet provides both TCP (reliable, ordered) and UDP (unreliable, unordered) through the same connection, making this split natural.

## Dependencies

Add KryoNet to the build. In `core/build.gradle.kts` (for shared message types) and `server/build.gradle.kts` (for server networking):

```kotlin
// In core/build.gradle.kts:
dependencies {
    api("com.esotericsoftware:kryonet:2.22.9")
}

// KryoNet transitively includes Kryo 5.x for serialization.
// If version conflicts arise with libGDX's internal Kryo, shade or exclude.
```

KryoNet uses Kryo for serialization — the same library libGDX uses internally. This means efficient binary serialization with minimal overhead.

## New Files

```
core/src/main/java/com/galacticodyssey/networking/protocol/
  NetworkMessages.java          All message type definitions (inner classes)
  KryoRegistrar.java            Registers all message types with Kryo
  ChannelPolicy.java            Decides TCP vs UDP per message type
  PacketMetrics.java            Tracks bandwidth usage per message type

core/src/main/java/com/galacticodyssey/networking/transport/
  NetworkTransport.java         Abstract transport interface
  KryoNetTransport.java         KryoNet implementation
  ConnectionHandler.java        Connection lifecycle management
  ReconnectionManager.java      Handles client reconnection with state recovery

server/src/main/java/com/galacticodyssey/server/network/
  GameServer.java               KryoNet server wrapper
  ClientConnection.java         Per-client connection state
  TickSynchronizer.java         Synchronizes client tick counters with server
```

## Message Type Hierarchy

All network messages extend a base class. Group them by purpose:

```java
public class NetworkMessages {

    // ═══════════════════════════════════════
    // Base
    // ═══════════════════════════════════════

    public static abstract class NetworkMessage {
        public int serverTick;
    }

    // ═══════════════════════════════════════
    // Connection lifecycle (TCP only)
    // ═══════════════════════════════════════

    public static class LoginRequest extends NetworkMessage {
        public String username;
        public byte[] authToken;  // pre-authenticated token, not raw password
    }

    public static class LoginResponse extends NetworkMessage {
        public boolean success;
        public String errorMessage;
        public String zoneServerAddress;
        public int zoneServerPort;
        public String sessionToken;
        public int assignedClientId;
    }

    public static class ZoneJoinRequest extends NetworkMessage {
        public String sessionToken;
        public int networkId;  // for reconnection to existing entity
    }

    public static class Heartbeat extends NetworkMessage {
        public long clientTimestamp;  // for RTT measurement
    }

    public static class HeartbeatAck extends NetworkMessage {
        public long clientTimestamp;  // echo back for RTT calc
        public long serverTimestamp;
    }

    public static class Disconnect extends NetworkMessage {
        public String reason;
    }

    // ═══════════════════════════════════════
    // Client → Server: Inputs (UDP, bundled)
    // ═══════════════════════════════════════

    public static class InputPacket extends NetworkMessage {
        public int clientId;
        public InputSnapshot[] inputs;  // 3 inputs bundled per packet
        public int latestSequence;      // highest sequence number in this batch
    }

    // ═══════════════════════════════════════
    // Server → Client: State updates (UDP)
    // ═══════════════════════════════════════

    public static class EntityStateUpdate extends NetworkMessage {
        public int networkId;
        public long dirtyMask;         // which fields changed
        public byte[] componentData;   // serialized dirty fields only
    }

    public static class EntityBatchUpdate extends NetworkMessage {
        public EntityStateUpdate[] updates;
        public int lastProcessedInput;  // for reconciliation
    }

    public static class EntitySpawn extends NetworkMessage {
        public int networkId;
        public int entityType;         // EntityType ordinal
        public byte[] fullState;       // complete component snapshot
    }

    public static class EntityDestroy extends NetworkMessage {
        public int networkId;
    }

    // ═══════════════════════════════════════
    // Server → Client: Events (TCP, reliable)
    // ═══════════════════════════════════════

    public static class DamageEvent extends NetworkMessage {
        public int targetNetworkId;
        public int sourceNetworkId;
        public float damage;
        public int damageType;        // DamageType ordinal
        public int hitRegion;         // HitRegion ordinal
    }

    public static class EntityKilledEvent extends NetworkMessage {
        public int targetNetworkId;
        public int killerNetworkId;
    }

    public static class WeaponFiredEvent extends NetworkMessage {
        public int shooterNetworkId;
        public int weaponType;
        public float originX, originY, originZ;
        public float dirX, dirY, dirZ;
    }

    public static class ShieldAbsorbEvent extends NetworkMessage {
        public int entityNetworkId;
        public float absorbedDamage;
        public float remainingShield;
    }

    // ═══════════════════════════════════════
    // Economy events (TCP)
    // ═══════════════════════════════════════

    public static class TradeCompleted extends NetworkMessage {
        public int buyerNetworkId;
        public int sellerNetworkId;
        public String commodityId;
        public int quantity;
        public long totalCredits;
    }

    public static class WalletUpdate extends NetworkMessage {
        public long credits;
    }

    public static class CargoUpdate extends NetworkMessage {
        public String[] itemIds;
        public int[] quantities;
    }

    // ═══════════════════════════════════════
    // Zone management (TCP)
    // ═══════════════════════════════════════

    public static class ZoneRedirect extends NetworkMessage {
        public String newServerAddress;
        public int newServerPort;
        public String sessionToken;
        public int networkId;
    }

    public static class WorldSnapshot extends NetworkMessage {
        public EntitySpawn[] entities;
        public long serverTime;
        public int currentTick;
    }

    public static class OriginRebase extends NetworkMessage {
        public double deltaX, deltaY, deltaZ;
    }

    // ═══════════════════════════════════════
    // Chat / social (TCP)
    // ═══════════════════════════════════════

    public static class ChatMessage extends NetworkMessage {
        public int senderNetworkId;
        public String senderName;
        public String message;
        public int channel;  // 0=local, 1=zone, 2=faction, 3=whisper
    }
}
```

## Kryo Registration

Kryo requires all serialized types to be registered in the **same order** on both client and server. Use a shared registrar:

```java
public class KryoRegistrar {

    public static void registerAll(Kryo kryo) {
        // Registration order is the serialization contract.
        // NEVER reorder existing entries — only append new ones at the end.
        // Each registration gets an implicit ID starting from a base offset.

        int id = 100;  // Start above Kryo's built-in type IDs

        // Primitives and collections
        kryo.register(byte[].class, id++);
        kryo.register(int[].class, id++);
        kryo.register(float[].class, id++);
        kryo.register(String[].class, id++);

        // Input types
        kryo.register(InputSnapshot.class, id++);
        kryo.register(InputSnapshot[].class, id++);

        // Connection lifecycle
        kryo.register(NetworkMessages.LoginRequest.class, id++);
        kryo.register(NetworkMessages.LoginResponse.class, id++);
        kryo.register(NetworkMessages.ZoneJoinRequest.class, id++);
        kryo.register(NetworkMessages.Heartbeat.class, id++);
        kryo.register(NetworkMessages.HeartbeatAck.class, id++);
        kryo.register(NetworkMessages.Disconnect.class, id++);

        // State updates
        kryo.register(NetworkMessages.InputPacket.class, id++);
        kryo.register(NetworkMessages.EntityStateUpdate.class, id++);
        kryo.register(NetworkMessages.EntityStateUpdate[].class, id++);
        kryo.register(NetworkMessages.EntityBatchUpdate.class, id++);
        kryo.register(NetworkMessages.EntitySpawn.class, id++);
        kryo.register(NetworkMessages.EntityDestroy.class, id++);

        // Combat events
        kryo.register(NetworkMessages.DamageEvent.class, id++);
        kryo.register(NetworkMessages.EntityKilledEvent.class, id++);
        kryo.register(NetworkMessages.WeaponFiredEvent.class, id++);
        kryo.register(NetworkMessages.ShieldAbsorbEvent.class, id++);

        // Economy
        kryo.register(NetworkMessages.TradeCompleted.class, id++);
        kryo.register(NetworkMessages.WalletUpdate.class, id++);
        kryo.register(NetworkMessages.CargoUpdate.class, id++);

        // Zone management
        kryo.register(NetworkMessages.ZoneRedirect.class, id++);
        kryo.register(NetworkMessages.WorldSnapshot.class, id++);
        kryo.register(NetworkMessages.EntitySpawn[].class, id++);
        kryo.register(NetworkMessages.OriginRebase.class, id++);

        // Chat
        kryo.register(NetworkMessages.ChatMessage.class, id++);
    }
}
```

**Registration order is a versioning contract.** Changing the order or inserting entries in the middle breaks all existing clients. Always append new types at the end. For a live service, track the registration list version and reject clients with mismatched versions during login.

## Channel Policy

Not all messages need the same delivery guarantee:

```java
public class ChannelPolicy {

    public enum Channel {
        TCP,    // reliable, ordered — connection-based
        UDP     // unreliable, unordered — faster for frequent updates
    }

    public static Channel getChannel(NetworkMessages.NetworkMessage message) {
        // State updates: UDP — they're sent every tick; losing one is fine
        if (message instanceof NetworkMessages.EntityStateUpdate) return Channel.UDP;
        if (message instanceof NetworkMessages.EntityBatchUpdate) return Channel.UDP;
        if (message instanceof NetworkMessages.InputPacket) return Channel.UDP;
        if (message instanceof NetworkMessages.Heartbeat) return Channel.UDP;
        if (message instanceof NetworkMessages.HeartbeatAck) return Channel.UDP;

        // Everything else: TCP — must arrive, must be ordered
        return Channel.TCP;
    }
}
```

| Category | Channel | Rationale |
|---|---|---|
| Position/state updates | UDP | High frequency, superseded by next update if lost |
| Input packets | UDP | Bundled with redundancy (3 per packet), latest wins |
| Heartbeat | UDP | Regular cadence, loss detected by absence |
| Damage/kill events | TCP | Must arrive — player sees incorrect HP otherwise |
| Economy transactions | TCP | Financial data must be reliable |
| Login/zone management | TCP | Connection-critical, must be ordered |
| Spawn/destroy | TCP | Missing a spawn = invisible entity forever |
| Origin rebase | TCP | Missing a rebase = permanent position desync |

## KryoNet Server and Client Setup

### GameServer (Server-Side)

```java
public class GameServer {
    private final Server kryoServer;
    private final Map<Integer, ClientConnection> clients = new ConcurrentHashMap<>();
    private final EventBus eventBus;

    public GameServer(int tcpPort, int udpPort, EventBus eventBus) {
        this.eventBus = eventBus;
        this.kryoServer = new Server(16384, 8192);  // write buffer, object buffer
        KryoRegistrar.registerAll(kryoServer.getKryo());
    }

    public void start(int tcpPort, int udpPort) throws IOException {
        kryoServer.start();
        kryoServer.bind(tcpPort, udpPort);

        kryoServer.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                // Don't create ClientConnection yet — wait for LoginRequest
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof NetworkMessages.NetworkMessage msg) {
                    handleMessage(connection, msg);
                }
            }

            @Override
            public void disconnected(Connection connection) {
                handleDisconnect(connection);
            }
        });
    }

    private void handleMessage(Connection connection, NetworkMessages.NetworkMessage msg) {
        if (msg instanceof NetworkMessages.LoginRequest login) {
            handleLogin(connection, login);
        } else if (msg instanceof NetworkMessages.InputPacket input) {
            handleInput(connection, input);
        } else if (msg instanceof NetworkMessages.Heartbeat hb) {
            handleHeartbeat(connection, hb);
        }
        // ... route to appropriate handler
    }

    public void sendToClient(int clientId, NetworkMessages.NetworkMessage message) {
        ClientConnection client = clients.get(clientId);
        if (client == null) return;

        Channel channel = ChannelPolicy.getChannel(message);
        if (channel == Channel.TCP) {
            client.connection.sendTCP(message);
        } else {
            client.connection.sendUDP(message);
        }
    }

    public void broadcastToZone(NetworkMessages.NetworkMessage message) {
        Channel channel = ChannelPolicy.getChannel(message);
        for (ClientConnection client : clients.values()) {
            if (channel == Channel.TCP) {
                client.connection.sendTCP(message);
            } else {
                client.connection.sendUDP(message);
            }
        }
    }
}
```

### KryoNet Client (Client-Side)

```java
public class GameClient {
    private final Client kryoClient;
    private final EventBus eventBus;
    private int assignedClientId;
    private boolean connected;

    public GameClient(EventBus eventBus) {
        this.eventBus = eventBus;
        this.kryoClient = new Client(16384, 8192);
        KryoRegistrar.registerAll(kryoClient.getKryo());
    }

    public void connect(String host, int tcpPort, int udpPort) throws IOException {
        kryoClient.start();
        kryoClient.connect(5000, host, tcpPort, udpPort);  // 5s timeout

        kryoClient.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof NetworkMessages.NetworkMessage msg) {
                    // Route to appropriate client-side handler
                    // Must be thread-safe — KryoNet listener runs on its own thread
                    Gdx.app.postRunnable(() -> handleMessage(msg));
                }
            }

            @Override
            public void disconnected(Connection connection) {
                connected = false;
                eventBus.publish(new ServerDisconnectedEvent());
            }
        });

        connected = true;
    }

    public void sendInput(NetworkMessages.InputPacket packet) {
        if (!connected) return;
        kryoClient.sendUDP(packet);
    }

    public void sendReliable(NetworkMessages.NetworkMessage message) {
        if (!connected) return;
        kryoClient.sendTCP(message);
    }
}
```

**Thread safety note:** KryoNet's listener runs on a background thread. Use `Gdx.app.postRunnable()` to dispatch received messages to the main game thread, or use a lock-free queue that the main thread polls each frame.

## Connection Lifecycle

```
Client                              Server
  │                                   │
  │──── LoginRequest ─────────────────→│  TCP
  │                                   │  (validate credentials)
  │←──── LoginResponse ──────────────│  TCP (success + zone server address)
  │                                   │
  │  [connect to zone server]         │
  │                                   │
  │──── ZoneJoinRequest ──────────────→│  TCP
  │                                   │  (validate session, load player)
  │←──── WorldSnapshot ──────────────│  TCP (all visible entities)
  │                                   │
  │  [gameplay loop begins]           │
  │                                   │
  │──── InputPacket ──────────────────→│  UDP (every frame, bundled)
  │←──── EntityBatchUpdate ──────────│  UDP (every server tick)
  │←──── DamageEvent ────────────────│  TCP (on combat)
  │                                   │
  │──── Heartbeat ────────────────────→│  UDP (every 2 sec)
  │←──── HeartbeatAck ───────────────│  UDP
  │                                   │
  │  [disconnect or timeout]          │
  │──── Disconnect ───────────────────→│  TCP (graceful)
  │  or                               │
  │  [no heartbeat for 10 sec]        │  (server-side timeout)
```

### ConnectionHandler

```java
public class ConnectionHandler {
    private static final float HEARTBEAT_INTERVAL = 2f;    // send every 2 seconds
    private static final float TIMEOUT_THRESHOLD = 10f;    // disconnect after 10s silence
    private static final float RECONNECT_GRACE = 30f;      // hold session for 30s

    private final Map<Integer, Float> lastHeartbeat = new HashMap<>();

    public void update(float delta) {
        float now = getCurrentTime();

        for (var entry : lastHeartbeat.entrySet()) {
            float timeSinceLastHeartbeat = now - entry.getValue();

            if (timeSinceLastHeartbeat > TIMEOUT_THRESHOLD) {
                // Client timed out — but don't destroy their entity yet
                onClientTimeout(entry.getKey());
            }
        }
    }

    public void onHeartbeatReceived(int clientId, NetworkMessages.Heartbeat hb) {
        lastHeartbeat.put(clientId, getCurrentTime());

        // Echo back for RTT measurement
        NetworkMessages.HeartbeatAck ack = new NetworkMessages.HeartbeatAck();
        ack.clientTimestamp = hb.clientTimestamp;
        ack.serverTimestamp = System.currentTimeMillis();
        sendToClient(clientId, ack);
    }

    private void onClientTimeout(int clientId) {
        // Don't destroy the player entity immediately — they might reconnect
        // Pause their entity (stop processing inputs, mark as AFK)
        // After RECONNECT_GRACE seconds, serialize and remove
    }
}
```

### RTT Measurement

The client measures round-trip time from heartbeat echoes:

```java
// Client-side:
public void onHeartbeatAck(NetworkMessages.HeartbeatAck ack) {
    long rtt = System.currentTimeMillis() - ack.clientTimestamp;
    rttSamples.add(rtt);

    // Exponential moving average
    smoothedRtt = (long)(smoothedRtt * 0.85 + rtt * 0.15);

    // Use smoothedRtt to calibrate interpolation delay
    interpolationDelay = Math.max(50, smoothedRtt + 20);  // RTT + jitter buffer
}
```

## Tick Synchronization

The client needs to know the server's current tick to tag its inputs correctly. Use heartbeat responses to synchronize:

```java
public class TickSynchronizer {
    private int serverTick;
    private long serverTickTimestamp;  // when we received the server tick
    private float tickRate;           // server ticks per second

    public void onServerTickReceived(int tick, long serverTimestamp) {
        this.serverTick = tick;
        this.serverTickTimestamp = System.currentTimeMillis();
    }

    public int estimateCurrentServerTick() {
        long elapsed = System.currentTimeMillis() - serverTickTimestamp;
        int ticksElapsed = (int)(elapsed * tickRate / 1000);
        return serverTick + ticksElapsed;
    }

    public void syncFromHeartbeat(NetworkMessages.HeartbeatAck ack) {
        long oneWayLatency = smoothedRtt / 2;
        // The server tick in the ack was current at (now - oneWayLatency)
        serverTick = ack.serverTick;
        serverTickTimestamp = System.currentTimeMillis() - oneWayLatency;
    }
}
```

Accurate tick estimation is critical for the prediction system — if the client tags inputs with the wrong tick, reconciliation will use the wrong server state as the baseline.

## Input Bundling

Sending one UDP packet per client frame (60/sec) wastes header bytes. Bundle 3 frames of input into one packet sent every server tick (50ms at 20Hz):

```java
// Client-side input sender:
public class InputSender {
    private final InputSnapshot[] pendingInputs = new InputSnapshot[3];
    private int pendingCount;
    private float accumulator;
    private static final float SEND_INTERVAL = 1f / 20f;  // match server tick rate

    public void addInput(InputSnapshot input) {
        if (pendingCount < 3) {
            pendingInputs[pendingCount] = input;
            pendingCount++;
        }
    }

    public void update(float delta) {
        accumulator += delta;
        if (accumulator >= SEND_INTERVAL && pendingCount > 0) {
            accumulator -= SEND_INTERVAL;

            NetworkMessages.InputPacket packet = new NetworkMessages.InputPacket();
            packet.clientId = localClientId;
            packet.inputs = Arrays.copyOf(pendingInputs, pendingCount);
            packet.latestSequence = pendingInputs[pendingCount - 1].sequenceNumber;

            gameClient.sendInput(packet);
            pendingCount = 0;
        }
    }
}
```

The server processes all inputs in the bundle when it arrives, applying them in sequence. If a bundle is lost (UDP), the next bundle's inputs will cover the gap — the server might apply a slightly stale input, but the reconciliation system corrects this.

### Redundant Input Sending

For extra robustness, include the previous tick's inputs alongside the current tick's. This way, if one packet is lost, the next packet carries the lost inputs:

```java
// Instead of sending just the last 3 inputs,
// send the last 6 (current 3 + previous 3):
packet.inputs = getLastNInputs(6);
packet.latestSequence = packet.inputs[5].sequenceNumber;
// Server deduplicates by sequence number
```

This doubles input packet size (~120 bytes → ~240 bytes) but virtually eliminates input loss. At 20 packets/sec, that's 4.8 KB/s — well within the 5 KB/s client→server budget.

## Bandwidth Metrics

Track bandwidth usage to detect issues and tune:

```java
public class PacketMetrics {
    private final Map<Class<?>, long[]> metrics = new HashMap<>();
    // long[0] = packet count, long[1] = total bytes

    public void recordSent(NetworkMessages.NetworkMessage msg, int bytes) {
        long[] m = metrics.computeIfAbsent(msg.getClass(), k -> new long[2]);
        m[0]++;
        m[1] += bytes;
    }

    public void logStats(float intervalSeconds) {
        for (var entry : metrics.entrySet()) {
            String type = entry.getKey().getSimpleName();
            long count = entry.getValue()[0];
            long bytes = entry.getValue()[1];
            float bps = bytes / intervalSeconds;
            // Log: "EntityBatchUpdate: 200 packets, 48.2 KB/s"
        }
        metrics.clear();
    }
}
```

### Bandwidth Budget by Message Type

| Message type | Direction | Size (bytes) | Rate | Bandwidth |
|---|---|---|---|---|
| InputPacket | C→S | ~240 | 20/sec | 4.8 KB/s |
| EntityBatchUpdate | S→C | ~1000-3000 | 20/sec | 20-60 KB/s |
| EntitySpawn | S→C | ~200 | sporadic | ~1 KB/s avg |
| EntityDestroy | S→C | ~8 | sporadic | <0.1 KB/s |
| DamageEvent | S→C | ~24 | sporadic | ~0.5 KB/s |
| Heartbeat/Ack | both | ~16 | 0.5/sec | <0.1 KB/s |
| **Total** | | | | **~30-65 KB/s per client** |

At 1000 clients: 30-65 MB/s server egress. This is manageable with modern server hardware and interest management limiting per-client entity counts.

## Reconnection

When a client disconnects unexpectedly (network blip, game crash), their session is preserved for `RECONNECT_GRACE` seconds. The player entity stays in the world (marked AFK) and the session token remains valid in Redis:

```java
public class ReconnectionManager {
    private final Map<Integer, ReconnectionState> pendingReconnects = new HashMap<>();

    public void onClientTimeout(int clientId, int playerId) {
        ReconnectionState state = new ReconnectionState();
        state.playerId = playerId;
        state.disconnectedAt = System.currentTimeMillis();
        state.entityNetworkId = getPlayerNetworkId(playerId);
        pendingReconnects.put(playerId, state);

        // Mark entity as AFK — stop processing inputs, make invulnerable briefly
        markEntityAFK(state.entityNetworkId);
    }

    public boolean attemptReconnect(int playerId, String sessionToken) {
        ReconnectionState state = pendingReconnects.get(playerId);
        if (state == null) return false;

        long elapsed = System.currentTimeMillis() - state.disconnectedAt;
        if (elapsed > RECONNECT_GRACE * 1000) {
            // Too late — entity has been removed
            pendingReconnects.remove(playerId);
            return false;
        }

        // Reconnect: resume entity, send current state snapshot
        unmarkEntityAFK(state.entityNetworkId);
        pendingReconnects.remove(playerId);
        return true;
    }
}
```

## KryoNet Buffer Sizing

KryoNet has two buffer size parameters: write buffer and object buffer. Size them based on your largest message:

```java
// WorldSnapshot can be large — hundreds of entities × ~200 bytes each
// Worst case: 500 entities × 200 bytes = 100 KB

// Server:
Server server = new Server(
    131072,  // write buffer: 128 KB (accommodates WorldSnapshot)
    16384    // object buffer: 16 KB (single object max size)
);

// Client:
Client client = new Client(
    65536,   // write buffer: 64 KB
    16384    // object buffer: 16 KB
);
```

If `WorldSnapshot` exceeds the object buffer, split it into multiple `WorldSnapshotChunk` messages and reassemble on the client.

## Testing

```java
@Test
void kryoRegistrationOrderIsStable() {
    // Verify that registration IDs don't change between versions
    Kryo kryo = new Kryo();
    KryoRegistrar.registerAll(kryo);

    // LoginRequest should always be ID 106 (100 + 6 registrations before it)
    Registration reg = kryo.getRegistration(NetworkMessages.LoginRequest.class);
    assertEquals(106, reg.getId());
}

@Test
void channelPolicyRoutesCorrectly() {
    assertTrue(ChannelPolicy.getChannel(new NetworkMessages.EntityBatchUpdate())
        == ChannelPolicy.Channel.UDP);
    assertTrue(ChannelPolicy.getChannel(new NetworkMessages.DamageEvent())
        == ChannelPolicy.Channel.TCP);
    assertTrue(ChannelPolicy.getChannel(new NetworkMessages.OriginRebase())
        == ChannelPolicy.Channel.TCP);
}

@Test
void inputBundlingBundles3Inputs() {
    InputSender sender = new InputSender();
    sender.addInput(createInput(1));
    sender.addInput(createInput(2));
    sender.addInput(createInput(3));

    // Simulate send interval elapsed
    NetworkMessages.InputPacket packet = sender.buildPacket();
    assertEquals(3, packet.inputs.length);
    assertEquals(3, packet.latestSequence);
}

@Test
void heartbeatMeasuresRTT() {
    ConnectionHandler handler = new ConnectionHandler();

    NetworkMessages.Heartbeat hb = new NetworkMessages.Heartbeat();
    hb.clientTimestamp = System.currentTimeMillis() - 50;  // simulate 50ms ago

    NetworkMessages.HeartbeatAck ack = handler.processHeartbeat(hb);
    assertEquals(hb.clientTimestamp, ack.clientTimestamp);

    // Client-side RTT calc:
    long rtt = System.currentTimeMillis() - ack.clientTimestamp;
    assertTrue(rtt >= 50 && rtt < 200);
}

@Test
void reconnectionWithinGracePeriodSucceeds() {
    ReconnectionManager mgr = new ReconnectionManager();
    mgr.onClientTimeout(1, 100);

    // Immediate reconnect — should succeed
    assertTrue(mgr.attemptReconnect(100, "token"));
}

@Test
void reconnectionAfterGracePeriodFails() {
    ReconnectionManager mgr = new ReconnectionManager();
    mgr.onClientTimeout(1, 100);

    // Simulate grace period expiry (in a real test, use a clock mock)
    // After 30+ seconds:
    assertFalse(mgr.attemptReconnect(100, "token"));
}
```

## Integration with Existing Skills

- **libgdx-network-replication** — The replication system builds `EntityBatchUpdate` and `EntitySpawn` messages defined here. It calls `GameServer.sendToClient()` with the channel policy handling TCP vs UDP automatically.
- **libgdx-client-prediction** — `InputPacket` carries the client's inputs to the server. `EntityBatchUpdate.lastProcessedInput` tells the prediction system which inputs have been acknowledged.
- **libgdx-server-zone-architecture** — `LoginResponse` directs the client to the correct zone server. `ZoneRedirect` handles zone transitions. The gateway server uses `LoginRequest` / `LoginResponse` for initial routing.
- **spacegame-combat-spatial** — Combat events (`DamageEvent`, `WeaponFiredEvent`) are published on the EventBus server-side and converted to network messages for replication to clients.

## Common Pitfalls

1. **Kryo registration mismatch.** If client and server register types in different order, deserialization produces garbage or crashes. Always use the shared `KryoRegistrar` and version it.

2. **Sending large objects via UDP.** UDP has a practical payload limit of ~1400 bytes (MTU minus headers). KryoNet will fragment larger UDP messages, but fragments can arrive out of order or be lost independently. Keep UDP messages under 1200 bytes. Large messages (WorldSnapshot) must use TCP.

3. **Blocking the game thread.** KryoNet's `sendTCP` and `sendUDP` are non-blocking (they write to a buffer), but `connect()` blocks. Always connect on a background thread and post the result to the game thread.

4. **Forgetting `Gdx.app.postRunnable`.** KryoNet listeners run on KryoNet's network thread. Modifying ECS components or game state from the listener thread causes race conditions. Always dispatch to the main thread.

5. **Not handling partial reads.** KryoNet handles message framing internally — you always receive complete objects, never partial reads. But if the object buffer is too small for a message, KryoNet throws an exception silently and the message is lost. Size buffers for your largest message type.
