---
name: libgdx-client-prediction
description: >
  How to implement client-side prediction, server reconciliation, and entity interpolation for
  SpaceGame's MMO multiplayer. Covers input buffering, prediction for FPS movement and 6DOF ship
  flight, server correction with rollback/replay, interpolation buffers for remote entities,
  extrapolation for late packets, floating-origin rebase synchronization, and lag compensation
  for combat hit detection. Use this skill whenever the user wants to: make movement feel responsive
  despite server authority, implement client prediction, add server reconciliation, interpolate
  remote players/ships, handle lag compensation for hitscan or projectile combat, synchronize
  floating origin rebases across the network, buffer inputs for the server, smooth out jittery
  remote entity movement, or deal with latency in combat. Also trigger for "rubberbanding",
  "input delay", "prediction", "reconciliation", "interpolation", "extrapolation",
  "lag compensation", "hit registration", "netcode feel", or "movement feels laggy".
---

# Client Prediction & Server Reconciliation

This skill covers making a server-authoritative MMO feel responsive. The fundamental tension: the server owns all state (CLAUDE.md rule 4), but waiting for a round-trip before the player sees their own movement would feel awful at 100+ ms latency. The solution is predict locally, then reconcile when the server confirms or corrects.

SpaceGame has two distinct movement domains — FPS character movement (Bullet capsule, gravity, slopes) and 6DOF ship flight (custom physics, no gravity) — and both need prediction. Combat also needs lag compensation so a player who aims accurately on their screen gets the hit, even though the server sees the target at a slightly different position.

## Core Architecture

```
Client:                          Server:
┌─────────────────┐             ┌─────────────────┐
│ Input sampled    │────────────→│ Input received   │
│ Input applied    │ (buffered)  │ Input applied    │
│ locally (predict)│             │ to authoritative │
│                  │             │ state            │
│                  │←────────────│ State sent back  │
│ State compared   │             │ (replication)    │
│ to prediction    │             │                  │
│ Correction if    │             │                  │
│ mismatch         │             │                  │
└─────────────────┘             └─────────────────┘
```

The client runs ahead of the server by approximately one round-trip time. It stores a buffer of unacknowledged inputs. When the server's authoritative state arrives, the client compares it against what it predicted for that tick. If they match (usually), nothing happens. If they differ, the client snaps to the server state and replays all inputs since then.

## New Files

```
core/src/main/java/com/galacticodyssey/networking/prediction/
  InputBuffer.java              Ring buffer of timestamped player inputs
  InputSnapshot.java            Single frame of input state (keys, mouse, throttle)
  PredictionSystem.java         Applies predicted inputs to local player entity
  ReconciliationSystem.java     Compares server state to predictions, corrects
  PredictionState.java          Snapshot of predicted entity state at a tick

core/src/main/java/com/galacticodyssey/networking/interpolation/
  InterpolationBuffer.java      Stores received server states for smooth playback
  InterpolationSystem.java      Interpolates remote entities between server snapshots
  ExtrapolationSystem.java      Extends last known state when packets are late

core/src/main/java/com/galacticodyssey/networking/lagcomp/
  LagCompensationSystem.java    Rewinds server state for hit detection
  HistoryBuffer.java            Circular buffer of past entity positions per tick

server/src/main/java/com/galacticodyssey/server/input/
  ServerInputProcessor.java     Receives and applies client inputs on the server
```

## InputSnapshot and InputBuffer

Every frame, the client captures its input state into an `InputSnapshot`:

```java
public class InputSnapshot {
    public int sequenceNumber;  // monotonically increasing
    public int serverTick;      // which server tick this corresponds to
    public float deltaTime;

    // FPS movement
    public float moveForward;   // -1 to 1
    public float moveStrafe;    // -1 to 1
    public boolean jump;
    public boolean sprint;

    // Ship flight (used when piloting)
    public float throttle;      // -1 to 1
    public float pitch;         // -1 to 1
    public float yaw;           // -1 to 1
    public float roll;          // -1 to 1

    // Combat
    public boolean primaryFire;
    public boolean secondaryFire;
    public float aimYaw;        // look direction
    public float aimPitch;

    public void copyFrom(InputSnapshot other) {
        this.sequenceNumber = other.sequenceNumber;
        this.serverTick = other.serverTick;
        this.deltaTime = other.deltaTime;
        this.moveForward = other.moveForward;
        this.moveStrafe = other.moveStrafe;
        this.jump = other.jump;
        this.sprint = other.sprint;
        this.throttle = other.throttle;
        this.pitch = other.pitch;
        this.yaw = other.yaw;
        this.roll = other.roll;
        this.primaryFire = other.primaryFire;
        this.secondaryFire = other.secondaryFire;
        this.aimYaw = other.aimYaw;
        this.aimPitch = other.aimPitch;
    }
}
```

`InputBuffer` stores the last N inputs (typically 128 — about 2 seconds at 60fps):

```java
public class InputBuffer {
    private final InputSnapshot[] buffer;
    private final int capacity;
    private int head;
    private int count;

    public InputBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new InputSnapshot[capacity];
        for (int i = 0; i < capacity; i++) {
            buffer[i] = new InputSnapshot();
        }
    }

    public InputSnapshot allocateNext() {
        int index = head % capacity;
        head++;
        count = Math.min(count + 1, capacity);
        return buffer[index];
    }

    public InputSnapshot getBySequence(int sequenceNumber) {
        for (int i = 0; i < count; i++) {
            int idx = ((head - 1 - i) % capacity + capacity) % capacity;
            if (buffer[idx].sequenceNumber == sequenceNumber) {
                return buffer[idx];
            }
        }
        return null;
    }

    public List<InputSnapshot> getInputsSince(int sequenceNumber) {
        List<InputSnapshot> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = ((head - count + i) % capacity + capacity) % capacity;
            if (buffer[idx].sequenceNumber > sequenceNumber) {
                result.add(buffer[idx]);
            }
        }
        return result;
    }

    public void discardUpTo(int sequenceNumber) {
        // Mark old inputs as consumed — they've been acknowledged by the server
        // Don't actually remove; just update count
        while (count > 0) {
            int oldest = ((head - count) % capacity + capacity) % capacity;
            if (buffer[oldest].sequenceNumber <= sequenceNumber) {
                count--;
            } else {
                break;
            }
        }
    }
}
```

Use `Pool<InputSnapshot>` from libGDX for the snapshots to avoid GC pressure if you're allocating outside the ring buffer.

## PredictionSystem

The client-side system that applies inputs immediately, before the server confirms:

```java
public class PredictionSystem extends EntitySystem {
    private final InputBuffer inputBuffer;
    private final List<PredictionState> predictionHistory = new ArrayList<>();
    private final int maxHistory = 128;

    @Override
    public void update(float delta) {
        // Only predict the local player entity — remote entities use interpolation
        Entity localPlayer = getLocalPlayer();
        if (localPlayer == null) return;

        InputSnapshot input = captureCurrentInput();
        inputBuffer.allocateNext().copyFrom(input);

        // Apply input to local entity (same logic the server will use)
        applyMovement(localPlayer, input);

        // Save predicted state for later reconciliation
        PredictionState ps = new PredictionState();
        ps.sequenceNumber = input.sequenceNumber;
        ps.position.set(getPosition(localPlayer));
        ps.velocity.set(getVelocity(localPlayer));
        ps.rotation.set(getRotation(localPlayer));
        predictionHistory.add(ps);

        if (predictionHistory.size() > maxHistory) {
            predictionHistory.remove(0);
        }
    }

    private void applyMovement(Entity entity, InputSnapshot input) {
        // This method must be IDENTICAL to the server's movement processing.
        // Extract it into a shared utility in core/ that both client and server call.
        PlayerMovementProcessor.applyInput(entity, input);
    }
}
```

The critical requirement: `applyMovement` must produce identical results on client and server given the same input and starting state. This means extracting movement logic into a shared `PlayerMovementProcessor` class in `core/` that both the `PredictionSystem` (client) and `ServerInputProcessor` (server) call.

### Shared Movement Processing

```java
// In core/src/main/java/com/galacticodyssey/player/
public class PlayerMovementProcessor {

    public static void applyInput(Entity entity, InputSnapshot input) {
        // Same physics as PlayerMovementSystem uses today, but parameterized
        // by InputSnapshot instead of reading raw input devices.
        MovementStateComponent move = MOVE_M.get(entity);
        TransformComponent transform = TRANSFORM_M.get(entity);

        float speed = input.sprint ? SPRINT_SPEED : WALK_SPEED;
        float dx = input.moveStrafe * speed * input.deltaTime;
        float dz = input.moveForward * speed * input.deltaTime;

        // Apply movement relative to look direction
        // ... (existing movement code from PlayerMovementSystem)

        if (input.jump && move.isGrounded) {
            move.velocityY = JUMP_FORCE;
            move.isGrounded = false;
        }
    }
}
```

Similarly for ship flight:

```java
public class ShipFlightProcessor {
    public static void applyInput(Entity entity, InputSnapshot input) {
        ShipFlightComponent flight = FLIGHT_M.get(entity);
        // Apply throttle, pitch, yaw, roll from input
        // Same physics as ShipFlightSystem
    }
}
```

## ReconciliationSystem

When the server sends back authoritative state for the local player, compare it against the prediction:

```java
public class ReconciliationSystem extends EntitySystem {
    private final InputBuffer inputBuffer;
    private final PredictionSystem predictionSystem;
    private static final float POSITION_THRESHOLD = 0.01f;
    private static final float SNAP_THRESHOLD = 5.0f;

    public void onServerStateReceived(ServerStateUpdate update) {
        Entity localPlayer = getLocalPlayer();
        if (localPlayer == null) return;

        int ackedSequence = update.lastProcessedInput;

        // Find the prediction we made for this sequence
        PredictionState predicted = predictionSystem.getPredictionForSequence(ackedSequence);
        if (predicted == null) return;

        Vector3 serverPos = update.position;
        float error = predicted.position.dst(serverPos);

        if (error < POSITION_THRESHOLD) {
            // Prediction was correct — discard old inputs, no correction needed
            inputBuffer.discardUpTo(ackedSequence);
            return;
        }

        if (error > SNAP_THRESHOLD) {
            // Too far off — hard snap (teleport, loading screen, or origin rebase)
            setPosition(localPlayer, serverPos);
            setVelocity(localPlayer, update.velocity);
            inputBuffer.discardUpTo(ackedSequence);
            predictionSystem.clearHistory();
            return;
        }

        // Mismatch — reconcile by replaying inputs from server state
        setPosition(localPlayer, serverPos);
        setVelocity(localPlayer, update.velocity);

        List<InputSnapshot> unackedInputs = inputBuffer.getInputsSince(ackedSequence);
        for (InputSnapshot input : unackedInputs) {
            PlayerMovementProcessor.applyInput(localPlayer, input);
        }

        inputBuffer.discardUpTo(ackedSequence);
    }
}
```

The reconciliation replays all unacknowledged inputs on top of the server's authoritative state. If the movement code is deterministic and the inputs are the same, the result will match what the client currently shows — no visual correction. If something differed (e.g., another player pushed you, or you hit a wall the client didn't know about), the replayed position will differ from the current predicted position, causing a small visual snap.

### Smoothing Corrections

Hard snaps look jarring. Smooth small corrections over a few frames:

```java
private Vector3 correctionOffset = new Vector3();
private static final float CORRECTION_SMOOTH_RATE = 10f;  // lerp speed

public void applyCorrectionSmoothing(Entity localPlayer, float delta) {
    if (correctionOffset.len2() < 0.0001f) {
        correctionOffset.setZero();
        return;
    }

    // Lerp the visual offset toward zero
    float t = Math.min(1f, CORRECTION_SMOOTH_RATE * delta);
    correctionOffset.scl(1f - t);

    // Apply offset to rendered position (not physics position)
    TransformComponent transform = TRANSFORM_M.get(localPlayer);
    transform.renderOffset.set(correctionOffset);
}
```

The physics position is always the authoritative one. The `renderOffset` smooths the visual transition. This is a common pattern in AAA netcode — the character controller and the rendered mesh can be at slightly different positions during correction.

## InterpolationBuffer and InterpolationSystem

Remote entities (other players, AI ships) don't use prediction — they use interpolation between server snapshots. The client renders remote entities slightly in the past (one server tick behind the latest received state) so it always has two states to interpolate between.

```java
public class InterpolationBuffer {
    private final int capacity;
    private final StateSnapshot[] buffer;
    private int head;
    private int count;

    public static class StateSnapshot {
        public int serverTick;
        public float timestamp;  // local time when received
        public final Vector3 position = new Vector3();
        public final Quaternion rotation = new Quaternion();
        public final Vector3 velocity = new Vector3();
    }

    public InterpolationBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new StateSnapshot[capacity];
        for (int i = 0; i < capacity; i++) {
            buffer[i] = new StateSnapshot();
        }
    }

    public void addSnapshot(int serverTick, Vector3 pos, Quaternion rot, Vector3 vel) {
        StateSnapshot snap = buffer[head % capacity];
        snap.serverTick = serverTick;
        snap.timestamp = getCurrentTime();
        snap.position.set(pos);
        snap.rotation.set(rot);
        snap.velocity.set(vel);
        head++;
        count = Math.min(count + 1, capacity);
    }

    public boolean getInterpolatedState(float renderTime, Vector3 outPos, Quaternion outRot) {
        if (count < 2) return false;

        // Find two snapshots bracketing renderTime
        StateSnapshot before = null, after = null;
        for (int i = count - 1; i > 0; i--) {
            int idx = ((head - 1 - i) % capacity + capacity) % capacity;
            int nextIdx = ((head - i) % capacity + capacity) % capacity;
            if (buffer[idx].timestamp <= renderTime && buffer[nextIdx].timestamp >= renderTime) {
                before = buffer[idx];
                after = buffer[nextIdx];
                break;
            }
        }

        if (before == null || after == null) return false;

        float range = after.timestamp - before.timestamp;
        float t = (range > 0) ? (renderTime - before.timestamp) / range : 0;
        t = Math.max(0, Math.min(1, t));

        outPos.set(before.position).lerp(after.position, t);
        outRot.set(before.rotation).slerp(after.rotation, t);
        return true;
    }
}
```

### InterpolationSystem

```java
public class InterpolationSystem extends IteratingSystem {
    private static final float INTERPOLATION_DELAY = 0.1f;  // 100ms behind real-time
    private final Map<Integer, InterpolationBuffer> buffers = new HashMap<>();

    public InterpolationSystem() {
        super(Family.all(NetworkIdentityComponent.class, TransformComponent.class)
            .exclude(PlayerTagComponent.class)  // don't interpolate local player
            .get(), /* priority */ 4);
    }

    @Override
    protected void processEntity(Entity entity, float delta) {
        NetworkIdentityComponent netId = NET_M.get(entity);
        if (netId.ownerClientId == localClientId) return;  // skip local player

        InterpolationBuffer buf = buffers.get(netId.networkId);
        if (buf == null) return;

        TransformComponent transform = TRANSFORM_M.get(entity);
        float renderTime = getCurrentTime() - INTERPOLATION_DELAY;

        Vector3 pos = new Vector3();  // use pooled vectors in production
        Quaternion rot = new Quaternion();

        if (buf.getInterpolatedState(renderTime, pos, rot)) {
            transform.position.set(pos);
            transform.rotation.set(rot);
        }
    }

    public void onEntityStateReceived(int networkId, int serverTick,
                                       Vector3 pos, Quaternion rot, Vector3 vel) {
        buffers.computeIfAbsent(networkId, k -> new InterpolationBuffer(32))
            .addSnapshot(serverTick, pos, rot, vel);
    }
}
```

The 100ms interpolation delay means remote entities are always rendered slightly in the past, but their movement is smooth. This delay is tunable — lower delay means more responsive but more likely to need extrapolation when packets are late.

### Extrapolation

When packets arrive late and the interpolation buffer runs out of future states, extrapolate from the last known velocity:

```java
public class ExtrapolationSystem extends EntitySystem {
    private static final float MAX_EXTRAPOLATION_TIME = 0.5f;  // 500ms max

    public void extrapolate(Entity entity, InterpolationBuffer buffer, float delta) {
        StateSnapshot latest = buffer.getLatest();
        if (latest == null) return;

        float timeSinceLastUpdate = getCurrentTime() - latest.timestamp;
        if (timeSinceLastUpdate < INTERPOLATION_DELAY) return;  // interpolation still has data
        if (timeSinceLastUpdate > MAX_EXTRAPOLATION_TIME) return;  // too stale, freeze

        TransformComponent t = TRANSFORM_M.get(entity);
        // Dead reckoning: position += velocity * dt
        t.position.set(latest.position).mulAdd(latest.velocity, timeSinceLastUpdate);
    }
}
```

After `MAX_EXTRAPOLATION_TIME`, freeze the entity — continued extrapolation will look increasingly wrong and create jarring snaps when the next update arrives.

## Floating Origin Rebase Synchronization

The floating origin is SpaceGame's most dangerous networking challenge. When `CoordinateManager` rebases the origin on the server, all clients must apply the same offset or positions will desync silently.

### Server-Initiated Rebase

The server rebases when the player with the most extreme position exceeds the threshold. It broadcasts a rebase event to all clients in the affected zone:

```java
// Server-side:
public class NetworkedOriginRebaseHandler {
    private final EventBus eventBus;
    private final NetworkTransport transport;

    public NetworkedOriginRebaseHandler(EventBus eventBus, NetworkTransport transport) {
        this.eventBus = eventBus;
        eventBus.subscribe(OriginRebasedEvent.class, this::onRebase);
    }

    private void onRebase(OriginRebasedEvent event) {
        OriginRebaseMessage msg = new OriginRebaseMessage();
        msg.deltaX = event.deltaX;
        msg.deltaY = event.deltaY;
        msg.deltaZ = event.deltaZ;
        msg.serverTick = currentTick;
        transport.broadcastReliable(msg);  // must be reliable — missing this is catastrophic
    }
}
```

### Client-Side Rebase Application

```java
// Client-side:
public class ClientOriginRebaseHandler {
    private final CoordinateManager coordinateManager;
    private final InterpolationSystem interpolationSystem;

    public void onRebaseMessageReceived(OriginRebaseMessage msg) {
        // Apply the same offset the server applied
        coordinateManager.forceRebase(msg.deltaX, msg.deltaY, msg.deltaZ);

        // Adjust all interpolation buffers — their positions are in the old frame
        interpolationSystem.offsetAllBuffers(
            (float) msg.deltaX, (float) msg.deltaY, (float) msg.deltaZ);

        // Adjust prediction history
        predictionSystem.offsetHistory(
            (float) msg.deltaX, (float) msg.deltaY, (float) msg.deltaZ);
    }
}
```

The rebase message must be **reliable and ordered** — if it arrives out of order or is lost, every subsequent position update will be wrong. Use KryoNet's TCP channel for this.

### Per-Client Origins

In a galaxy-scale game, clients in different sectors may have different floating origins. The server tracks each client's origin offset and translates positions when building replication packets:

```java
// In ReplicationManager:
private final Map<Integer, double[]> clientOrigins = new HashMap<>();

private Vector3 toClientLocalSpace(int clientId, double galaxyX, double galaxyY, double galaxyZ) {
    double[] origin = clientOrigins.get(clientId);
    return new Vector3(
        (float)(galaxyX - origin[0]),
        (float)(galaxyY - origin[1]),
        (float)(galaxyZ - origin[2])
    );
}
```

## Lag Compensation for Combat

When a player fires a hitscan weapon, they aim at where the target is *on their screen*. But due to latency, the target has already moved on the server. Lag compensation rewinds the server's world state to where it was when the player fired, then checks if the shot would have hit.

### HistoryBuffer

The server stores a circular buffer of recent entity positions:

```java
public class HistoryBuffer {
    private final int capacity;
    private final PositionSnapshot[] buffer;
    private int head;

    public static class PositionSnapshot {
        public int serverTick;
        public final Map<Integer, Vector3> entityPositions = new HashMap<>();
        public final Map<Integer, HitboxSnapshot> entityHitboxes = new HashMap<>();
    }

    public HistoryBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new PositionSnapshot[capacity];
        for (int i = 0; i < capacity; i++) buffer[i] = new PositionSnapshot();
    }

    public void recordTick(int serverTick, Engine engine) {
        PositionSnapshot snap = buffer[head % capacity];
        snap.serverTick = serverTick;
        snap.entityPositions.clear();
        snap.entityHitboxes.clear();

        for (Entity e : engine.getEntitiesFor(Family.all(
                NetworkIdentityComponent.class, TransformComponent.class).get())) {
            int nid = NET_M.get(e).networkId;
            TransformComponent t = TRANSFORM_M.get(e);
            snap.entityPositions.put(nid, new Vector3(t.position));

            HitboxComponent hb = HITBOX_M.get(e);
            if (hb != null) {
                snap.entityHitboxes.put(nid, hb.snapshot());
            }
        }
        head++;
    }

    public PositionSnapshot getAtTick(int serverTick) {
        for (int i = 0; i < capacity; i++) {
            int idx = ((head - 1 - i) % capacity + capacity) % capacity;
            if (buffer[idx].serverTick == serverTick) return buffer[idx];
        }
        return null;
    }
}
```

### LagCompensationSystem

```java
public class LagCompensationSystem {
    private final HistoryBuffer history;
    private static final int MAX_REWIND_TICKS = 15;  // ~250ms at 60 tick

    public HitscanResult checkHitscan(int shooterClientId, Ray ray, int clientTick) {
        // Clamp rewind to prevent abuse
        int rewindTicks = Math.min(currentServerTick - clientTick, MAX_REWIND_TICKS);
        int targetTick = currentServerTick - rewindTicks;

        HistoryBuffer.PositionSnapshot pastState = history.getAtTick(targetTick);
        if (pastState == null) {
            // History doesn't go back far enough — use current positions
            return checkHitscanCurrent(ray);
        }

        // Check ray against historical hitbox positions
        for (var entry : pastState.entityHitboxes.entrySet()) {
            int targetNid = entry.getKey();
            HitboxSnapshot hitbox = entry.getValue();
            Vector3 pastPos = pastState.entityPositions.get(targetNid);

            if (hitbox.intersectsRay(ray, pastPos)) {
                return new HitscanResult(targetNid, hitbox.getHitRegion(ray, pastPos));
            }
        }

        return HitscanResult.MISS;
    }
}
```

`MAX_REWIND_TICKS` caps how far back the server will look — without this limit, a cheater with artificially high latency could shoot around corners by rewinding too far. 250ms is generous enough for legitimate high-latency players.

## Server Tick Rate

The server tick rate determines how often the server processes inputs and sends updates. For SpaceGame:

| Parameter | Value | Rationale |
|---|---|---|
| Server tick rate | 20 Hz | Balance between responsiveness and bandwidth |
| Client input rate | 60 Hz | Match client frame rate, bundle 3 inputs per server tick |
| Interpolation delay | 100ms | Two server ticks, smooth playback |
| Max rewind | 250ms | ~5 server ticks, generous for high latency |

At 20 Hz, the server processes 20 batches of inputs per second. The client runs at 60 fps and sends inputs at 60 Hz — the server batches 3 client inputs per tick. This means 3 `applyMovement` calls per server tick for each client.

## Testing

```java
@Test
void reconciliationReplaysInputsFromServerState() {
    InputBuffer buffer = new InputBuffer(128);
    ReconciliationSystem recon = new ReconciliationSystem(buffer, predictionSystem);

    // Simulate: client predicted 3 inputs
    for (int i = 1; i <= 3; i++) {
        InputSnapshot input = buffer.allocateNext();
        input.sequenceNumber = i;
        input.moveForward = 1f;
        input.deltaTime = 1f / 60f;
    }

    // Server acknowledges input 1, but with a corrected position
    ServerStateUpdate update = new ServerStateUpdate();
    update.lastProcessedInput = 1;
    update.position = new Vector3(0, 0, 0.5f);  // server says 0.5, client predicted 0.3

    recon.onServerStateReceived(update);

    // After reconciliation: entity at server position + replay of inputs 2 and 3
    // (exact values depend on movement speed constant)
}

@Test
void interpolationBufferSmoothsRemoteEntity() {
    InterpolationBuffer buf = new InterpolationBuffer(32);

    buf.addSnapshot(1, new Vector3(0, 0, 0), new Quaternion(), new Vector3());
    buf.addSnapshot(2, new Vector3(10, 0, 0), new Quaternion(), new Vector3());

    Vector3 outPos = new Vector3();
    Quaternion outRot = new Quaternion();

    // Midpoint between two snapshots
    float midTime = (buf.getByTick(1).timestamp + buf.getByTick(2).timestamp) / 2f;
    assertTrue(buf.getInterpolatedState(midTime, outPos, outRot));
    assertEquals(5f, outPos.x, 0.1f);  // lerp midpoint
}

@Test
void lagCompensationRewindsToCorrectTick() {
    HistoryBuffer history = new HistoryBuffer(30);
    // Record entity at (0,0,0) on tick 10, then at (10,0,0) on tick 11
    // Check hitscan at tick 10 — should test against (0,0,0)
    // Check hitscan at tick 11 — should test against (10,0,0)
}

@Test
void inputBufferDiscardsAcknowledgedInputs() {
    InputBuffer buffer = new InputBuffer(128);
    for (int i = 1; i <= 10; i++) {
        buffer.allocateNext().sequenceNumber = i;
    }

    buffer.discardUpTo(5);

    List<InputSnapshot> remaining = buffer.getInputsSince(5);
    assertEquals(5, remaining.size());  // inputs 6-10
}
```

## Integration with Existing Skills

- **libgdx-network-replication** — Replication delivers the authoritative server state that triggers reconciliation. The `ServerStateUpdate` message is a subset of the replication packet focused on the local player's entity.
- **libgdx-network-protocol** — Inputs are sent via unreliable channel (UDP through KryoNet). State updates from server use reliable channel for critical data (health, death) and unreliable for position.
- **libgdx-server-zone-architecture** — When a player crosses a zone boundary, prediction history must be cleared and a fresh state snapshot bootstraps the new zone's prediction.
- **spacegame-combat-spatial** — Lag compensation uses the same hitbox data that the combat spatial system manages. The `HistoryBuffer` records hitbox positions from the spatial grid.

## Common Pitfalls

1. **Non-deterministic movement.** If `applyMovement` produces different results on client and server (e.g., due to float operation ordering, different physics timestep, or reading System.nanoTime), reconciliation will trigger every frame. The fix: extract shared logic, use fixed timestep, avoid time-dependent randomness.

2. **Forgetting to adjust interpolation buffers on origin rebase.** If the floating origin shifts and interpolation buffers still hold positions in the old frame, remote entities will teleport by the rebase offset.

3. **Extrapolating through walls.** Dead reckoning doesn't know about colliders. An extrapolated entity can appear inside walls. Cap extrapolation time aggressively.

4. **Prediction during state transitions.** When switching from FPS to ship piloting (or vice versa), clear prediction history. The movement model changes — replaying FPS inputs against ship flight physics will produce garbage.

5. **Input flooding.** Sending 60 inputs/sec × 1000 clients = 60K messages/sec to the server. Bundle multiple inputs per packet (3 per server tick at 20Hz) to reduce packet overhead. Each packet carries the last 3 inputs plus their sequence numbers.
