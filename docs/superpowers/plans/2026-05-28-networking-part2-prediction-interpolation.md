# Networking Part 2: Client Prediction & Remote Entity Interpolation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Client-side prediction with server reconciliation for the local player, and smooth interpolation of remote entities — so the game feels responsive despite 50ms+ round-trip latency.

**Architecture:** The local player predicts movement using the same physics code the server runs, stores predicted states in a ring buffer, and replays unacknowledged inputs on reconciliation. Remote entities are rendered 100ms behind real-time, interpolating between server snapshots. A visual smoothing offset hides small corrections. All prediction data lives in new Ashley components; two new client-only systems (ClientPredictionSystem priority 0, InterpolationSystem priority 55) drive the logic.

**Tech Stack:** Ashley ECS 1.7.4, libGDX math (Vector3, Quaternion, MathUtils), existing PlayerMovementSystem / ShipFlightSystem for replay, existing snapshot infrastructure (Snapshotable interface), EventBus for origin rebase events.

**Depends on:** Part 1 (transport, replication, ClientNetworkSystem, protocol messages) — fully implemented.

---

## File Structure

### New Files — core module (client-side prediction)

| File | Responsibility |
|------|----------------|
| `core/.../networking/prediction/InputBuffer.java` | Fixed-size ring buffer (128 slots) of `TimestampedInput` entries |
| `core/.../networking/prediction/TimestampedInput.java` | Single input snapshot + predicted state at that sequence |
| `core/.../networking/prediction/PredictedState.java` | Immutable capture of position, rotation, velocity for one prediction frame |
| `core/.../networking/components/PredictionComponent.java` | Ashley Component on local player entity — owns InputBuffer, sequence counter, smoothing offset |
| `core/.../networking/systems/ClientPredictionSystem.java` | Ashley EntitySystem (priority 0) — samples input, predicts movement, stores in buffer |
| `core/.../networking/systems/ReconciliationSystem.java` | Ashley EntitySystem (priority 1) — processes server updates, compares, replays unacked inputs |

### New Files — core module (remote entity interpolation)

| File | Responsibility |
|------|----------------|
| `core/.../networking/interpolation/SnapshotBuffer.java` | Generic timestamped ring buffer (4 slots) with bracketing queries |
| `core/.../networking/interpolation/EntitySnapshot.java` | Single snapshot: tick, position (doubles), rotation (quaternion), velocity |
| `core/.../networking/components/InterpolationComponent.java` | Ashley Component on remote entities — owns SnapshotBuffer, extrapolation timer |
| `core/.../networking/systems/InterpolationSystem.java` | Ashley EntitySystem (priority 55) — lerps/slerps between bracketing snapshots |

### Modified Files

| File | Changes |
|------|---------|
| `core/.../networking/systems/ClientNetworkSystem.java` | Wire update() to process spawn/destroy/batch queues, create/remove InterpolationComponent on remote entities, feed PredictionComponent on reconciliation |
| `core/.../networking/components/NetworkIdComponent.java` | No changes — used as-is |
| `core/.../core/components/TransformComponent.java` | No changes — used as-is |

### Test Files

| File | Tests |
|------|-------|
| `core/src/test/.../networking/prediction/InputBufferTest.java` | Ring buffer wrap, capacity, discard, get-unacked |
| `core/src/test/.../networking/prediction/PredictedStateTest.java` | Constructor, distanceTo |
| `core/src/test/.../networking/components/PredictionComponentTest.java` | Defaults, advance sequence |
| `core/src/test/.../networking/systems/ClientPredictionSystemTest.java` | Predict FPS, predict ship, buffer storage |
| `core/src/test/.../networking/systems/ReconciliationSystemTest.java` | Accept, smooth-correct, hard-snap, discard acked |
| `core/src/test/.../networking/interpolation/SnapshotBufferTest.java` | Insert, bracketing query, out-of-order drop, extrapolation |
| `core/src/test/.../networking/interpolation/EntitySnapshotTest.java` | Constructor, lerp |
| `core/src/test/.../networking/components/InterpolationComponentTest.java` | Defaults, add snapshot |
| `core/src/test/.../networking/systems/InterpolationSystemTest.java` | Lerp position, slerp rotation, extrapolation fallback, freeze |
| `core/src/test/.../networking/systems/ClientNetworkSystemIntegrationTest.java` | Spawn → interpolation, batch → reconciliation, destroy → cleanup |

---

### Task 1: InputBuffer and TimestampedInput

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/networking/prediction/TimestampedInput.java`
- Create: `core/src/main/java/com/galacticodyssey/networking/prediction/PredictedState.java`
- Create: `core/src/main/java/com/galacticodyssey/networking/prediction/InputBuffer.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/prediction/InputBufferTest.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/prediction/PredictedStateTest.java`

- [ ] **Step 1: Write PredictedState and its test**

```java
// core/src/main/java/com/galacticodyssey/networking/prediction/PredictedState.java
package com.galacticodyssey.networking.prediction;

public class PredictedState {
    public final float posX, posY, posZ;
    public final float rotX, rotY, rotZ, rotW;
    public final float velX, velY, velZ;

    public PredictedState(float posX, float posY, float posZ,
                          float rotX, float rotY, float rotZ, float rotW,
                          float velX, float velY, float velZ) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
        this.rotW = rotW;
        this.velX = velX;
        this.velY = velY;
        this.velZ = velZ;
    }

    public float distanceTo(PredictedState other) {
        float dx = posX - other.posX;
        float dy = posY - other.posY;
        float dz = posZ - other.posZ;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
```

```java
// core/src/test/java/com/galacticodyssey/networking/prediction/PredictedStateTest.java
package com.galacticodyssey.networking.prediction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PredictedStateTest {

    @Test
    void storesAllFields() {
        PredictedState s = new PredictedState(1, 2, 3, 0, 0, 0, 1, 4, 5, 6);
        assertEquals(1f, s.posX);
        assertEquals(2f, s.posY);
        assertEquals(3f, s.posZ);
        assertEquals(1f, s.rotW);
        assertEquals(4f, s.velX);
    }

    @Test
    void distanceToSamePositionIsZero() {
        PredictedState a = new PredictedState(5, 10, 15, 0, 0, 0, 1, 0, 0, 0);
        assertEquals(0f, a.distanceTo(a), 1e-6f);
    }

    @Test
    void distanceToComputesEuclidean() {
        PredictedState a = new PredictedState(0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        PredictedState b = new PredictedState(3, 4, 0, 0, 0, 0, 1, 0, 0, 0);
        assertEquals(5f, a.distanceTo(b), 1e-6f);
    }
}
```

- [ ] **Step 2: Write TimestampedInput**

```java
// core/src/main/java/com/galacticodyssey/networking/prediction/TimestampedInput.java
package com.galacticodyssey.networking.prediction;

import com.galacticodyssey.common.protocol.PlayerInput;

public class TimestampedInput {
    public final int sequenceNumber;
    public final PlayerInput input;
    public final PredictedState predictedState;

    public TimestampedInput(int sequenceNumber, PlayerInput input, PredictedState predictedState) {
        this.sequenceNumber = sequenceNumber;
        this.input = input;
        this.predictedState = predictedState;
    }
}
```

- [ ] **Step 3: Write InputBuffer and its tests**

```java
// core/src/main/java/com/galacticodyssey/networking/prediction/InputBuffer.java
package com.galacticodyssey.networking.prediction;

import java.util.ArrayList;
import java.util.List;

public class InputBuffer {
    public static final int CAPACITY = 128;
    private final TimestampedInput[] buffer = new TimestampedInput[CAPACITY];
    private int head;
    private int count;

    public void add(TimestampedInput entry) {
        buffer[head] = entry;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) count++;
    }

    public int size() {
        return count;
    }

    public TimestampedInput get(int sequenceNumber) {
        for (int i = 0; i < count; i++) {
            int idx = (head - 1 - i + CAPACITY) % CAPACITY;
            if (buffer[idx] != null && buffer[idx].sequenceNumber == sequenceNumber) {
                return buffer[idx];
            }
        }
        return null;
    }

    public void discardUpTo(int acknowledgedSequence) {
        int removed = 0;
        for (int i = 0; i < count; i++) {
            int idx = (head - count + i + CAPACITY) % CAPACITY;
            if (buffer[idx] != null && buffer[idx].sequenceNumber <= acknowledgedSequence) {
                buffer[idx] = null;
                removed++;
            } else {
                break;
            }
        }
        count -= removed;
    }

    public List<TimestampedInput> getUnacknowledged(int afterSequence) {
        List<TimestampedInput> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = (head - count + i + CAPACITY) % CAPACITY;
            if (buffer[idx] != null && buffer[idx].sequenceNumber > afterSequence) {
                result.add(buffer[idx]);
            }
        }
        return result;
    }

    public void clear() {
        for (int i = 0; i < CAPACITY; i++) buffer[i] = null;
        head = 0;
        count = 0;
    }
}
```

```java
// core/src/test/java/com/galacticodyssey/networking/prediction/InputBufferTest.java
package com.galacticodyssey.networking.prediction;

import com.galacticodyssey.common.protocol.PlayerInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputBufferTest {

    private TimestampedInput makeEntry(int seq) {
        PlayerInput input = new PlayerInput();
        input.sequenceNumber = seq;
        PredictedState state = new PredictedState(seq, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        return new TimestampedInput(seq, input, state);
    }

    @Test
    void addAndRetrieveBySequence() {
        InputBuffer buf = new InputBuffer();
        buf.add(makeEntry(1));
        buf.add(makeEntry(2));
        buf.add(makeEntry(3));
        assertEquals(3, buf.size());
        assertNotNull(buf.get(2));
        assertEquals(2, buf.get(2).sequenceNumber);
    }

    @Test
    void getMissingReturnsNull() {
        InputBuffer buf = new InputBuffer();
        buf.add(makeEntry(1));
        assertNull(buf.get(99));
    }

    @Test
    void discardRemovesAcknowledgedEntries() {
        InputBuffer buf = new InputBuffer();
        buf.add(makeEntry(1));
        buf.add(makeEntry(2));
        buf.add(makeEntry(3));
        buf.discardUpTo(2);
        assertEquals(1, buf.size());
        assertNull(buf.get(1));
        assertNull(buf.get(2));
        assertNotNull(buf.get(3));
    }

    @Test
    void getUnacknowledgedReturnsOnlyNewer() {
        InputBuffer buf = new InputBuffer();
        buf.add(makeEntry(1));
        buf.add(makeEntry(2));
        buf.add(makeEntry(3));
        buf.add(makeEntry(4));
        var unacked = buf.getUnacknowledged(2);
        assertEquals(2, unacked.size());
        assertEquals(3, unacked.get(0).sequenceNumber);
        assertEquals(4, unacked.get(1).sequenceNumber);
    }

    @Test
    void wrapsAroundAtCapacity() {
        InputBuffer buf = new InputBuffer();
        for (int i = 0; i < InputBuffer.CAPACITY + 10; i++) {
            buf.add(makeEntry(i));
        }
        assertEquals(InputBuffer.CAPACITY, buf.size());
        assertNull(buf.get(0));
        assertNotNull(buf.get(InputBuffer.CAPACITY + 9));
    }

    @Test
    void clearResetsBuffer() {
        InputBuffer buf = new InputBuffer();
        buf.add(makeEntry(1));
        buf.add(makeEntry(2));
        buf.clear();
        assertEquals(0, buf.size());
        assertNull(buf.get(1));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.prediction.*" -i`
Expected: All 9 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/networking/prediction/PredictedState.java core/src/main/java/com/galacticodyssey/networking/prediction/TimestampedInput.java core/src/main/java/com/galacticodyssey/networking/prediction/InputBuffer.java core/src/test/java/com/galacticodyssey/networking/prediction/PredictedStateTest.java core/src/test/java/com/galacticodyssey/networking/prediction/InputBufferTest.java
git commit -m "feat(net): add InputBuffer ring buffer for client prediction"
```

---

### Task 2: PredictionComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/networking/components/PredictionComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/components/PredictionComponentTest.java`

- [ ] **Step 1: Write the test**

```java
// core/src/test/java/com/galacticodyssey/networking/components/PredictionComponentTest.java
package com.galacticodyssey.networking.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PredictionComponentTest {

    @Test
    void defaultsHaveZeroOffset() {
        PredictionComponent pc = new PredictionComponent();
        assertEquals(0f, pc.smoothingOffsetX);
        assertEquals(0f, pc.smoothingOffsetY);
        assertEquals(0f, pc.smoothingOffsetZ);
        assertEquals(0, pc.smoothingFramesRemaining);
    }

    @Test
    void sequenceStartsAtZero() {
        PredictionComponent pc = new PredictionComponent();
        assertEquals(0, pc.nextSequenceNumber);
    }

    @Test
    void advanceSequenceIncrements() {
        PredictionComponent pc = new PredictionComponent();
        int first = pc.advanceSequence();
        int second = pc.advanceSequence();
        assertEquals(0, first);
        assertEquals(1, second);
    }

    @Test
    void inputBufferIsNotNull() {
        PredictionComponent pc = new PredictionComponent();
        assertNotNull(pc.getInputBuffer());
        assertEquals(0, pc.getInputBuffer().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.components.PredictionComponentTest" -i`
Expected: FAIL — class not found.

- [ ] **Step 3: Write implementation**

```java
// core/src/main/java/com/galacticodyssey/networking/components/PredictionComponent.java
package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.networking.prediction.InputBuffer;

public class PredictionComponent implements Component {

    public static final int SMOOTHING_FRAMES = 10;
    public static final float ACCEPT_THRESHOLD = 0.01f;
    public static final float HARD_SNAP_THRESHOLD = 5.0f;

    private final InputBuffer inputBuffer = new InputBuffer();
    public int nextSequenceNumber;

    public float smoothingOffsetX;
    public float smoothingOffsetY;
    public float smoothingOffsetZ;
    public int smoothingFramesRemaining;

    public int lastAcknowledgedSequence = -1;

    public int advanceSequence() {
        return nextSequenceNumber++;
    }

    public InputBuffer getInputBuffer() {
        return inputBuffer;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.components.PredictionComponentTest" -i`
Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/networking/components/PredictionComponent.java core/src/test/java/com/galacticodyssey/networking/components/PredictionComponentTest.java
git commit -m "feat(net): add PredictionComponent with input buffer and smoothing offset"
```

---

### Task 3: ClientPredictionSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/networking/systems/ClientPredictionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/systems/ClientPredictionSystemTest.java`

This system runs at priority 0 (before all game logic). Each frame it:
1. Reads input from `PlayerInputComponent` (FPS) or `ShipFlightInputComponent` (ship).
2. Captures a `PlayerInput` protocol object from those component fields.
3. Stores the input + predicted state (current position/rotation/velocity) in the PredictionComponent's InputBuffer.
4. Does NOT actually run physics — the existing `PlayerMovementSystem` / `ShipFlightSystem` handle that downstream in the same frame.

The prediction is implicit: the same movement systems run on the client every frame. The InputBuffer records what the state WAS after each input, so reconciliation can compare against the server's version.

- [ ] **Step 1: Write the test**

```java
// core/src/test/java/com/galacticodyssey/networking/systems/ClientPredictionSystemTest.java
package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientPredictionSystemTest {

    private Engine engine;
    private ClientPredictionSystem system;
    private Entity player;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        system = new ClientPredictionSystem();
        engine.addSystem(system);

        player = new Entity();
        player.add(new TransformComponent());
        player.add(new PlayerInputComponent());
        player.add(new PredictionComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerStateComponent.PlayerMode.ON_FOOT_EXTERIOR;
        player.add(state);
        engine.addEntity(player);
    }

    @Test
    void capturesInputIntoBuffer() {
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        input.moveForward = 1.0f;
        input.sprint = true;

        engine.update(0.05f);

        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        assertEquals(1, pred.getInputBuffer().size());
        assertEquals(0, pred.getInputBuffer().get(0).sequenceNumber);
    }

    @Test
    void sequenceIncrementsEachFrame() {
        engine.update(0.05f);
        engine.update(0.05f);
        engine.update(0.05f);

        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        assertEquals(3, pred.getInputBuffer().size());
        assertEquals(3, pred.nextSequenceNumber);
    }

    @Test
    void capturesPredictedPosition() {
        TransformComponent transform = player.getComponent(TransformComponent.class);
        transform.position.set(10, 20, 30);

        engine.update(0.05f);

        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        var entry = pred.getInputBuffer().get(0);
        assertNotNull(entry.predictedState);
        assertEquals(10f, entry.predictedState.posX, 1e-6f);
        assertEquals(20f, entry.predictedState.posY, 1e-6f);
        assertEquals(30f, entry.predictedState.posZ, 1e-6f);
    }

    @Test
    void capturesFPSInputFields() {
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        input.moveForward = 0.8f;
        input.moveStrafe = -0.3f;
        input.sprint = true;
        input.jumpRequested = true;

        engine.update(0.05f);

        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        var captured = pred.getInputBuffer().get(0).input;
        assertEquals(0.8f, captured.moveForward, 1e-6f);
        assertEquals(-0.3f, captured.moveStrafe, 1e-6f);
        assertTrue(captured.sprint);
        assertTrue(captured.jump);
    }

    @Test
    void doesNothingWithoutPredictionComponent() {
        Entity npc = new Entity();
        npc.add(new TransformComponent());
        npc.add(new PlayerInputComponent());
        npc.add(new PlayerStateComponent());
        engine.addEntity(npc);

        // Should not throw
        engine.update(0.05f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.systems.ClientPredictionSystemTest" -i`
Expected: FAIL — class not found.

- [ ] **Step 3: Write implementation**

```java
// core/src/main/java/com/galacticodyssey/networking/systems/ClientPredictionSystem.java
package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.common.protocol.PlayerInput;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.networking.prediction.PredictedState;
import com.galacticodyssey.networking.prediction.TimestampedInput;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;

public class ClientPredictionSystem extends EntitySystem {

    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
            ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<PredictionComponent> predictionMapper =
            ComponentMapper.getFor(PredictionComponent.class);
    private final ComponentMapper<PlayerStateComponent> stateMapper =
            ComponentMapper.getFor(PlayerStateComponent.class);

    private ImmutableArray<Entity> predictedEntities;

    public ClientPredictionSystem() {
        super(0);
    }

    @Override
    public void addedToEngine(Engine engine) {
        predictedEntities = engine.getEntitiesFor(
                Family.all(TransformComponent.class, PlayerInputComponent.class,
                        PredictionComponent.class, PlayerStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < predictedEntities.size(); i++) {
            Entity entity = predictedEntities.get(i);
            TransformComponent transform = transformMapper.get(entity);
            PlayerInputComponent localInput = inputMapper.get(entity);
            PredictionComponent prediction = predictionMapper.get(entity);

            int seq = prediction.advanceSequence();

            PlayerInput netInput = captureInput(localInput, seq, stateMapper.get(entity));

            PredictedState state = new PredictedState(
                    transform.position.x, transform.position.y, transform.position.z,
                    transform.rotation.x, transform.rotation.y,
                    transform.rotation.z, transform.rotation.w,
                    0, 0, 0
            );

            prediction.getInputBuffer().add(new TimestampedInput(seq, netInput, state));
        }
    }

    private PlayerInput captureInput(PlayerInputComponent local, int seq,
                                     PlayerStateComponent playerState) {
        PlayerInput pi = new PlayerInput();
        pi.sequenceNumber = seq;
        pi.moveForward = local.moveForward;
        pi.moveStrafe = local.moveStrafe;
        pi.mouseDeltaX = local.mouseDeltaX;
        pi.mouseDeltaY = local.mouseDeltaY;
        pi.jump = local.jumpRequested;
        pi.sprint = local.sprint;
        pi.crouch = local.crouch;
        pi.playerMode = playerState.currentMode.ordinal();
        return pi;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.systems.ClientPredictionSystemTest" -i`
Expected: All 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/networking/systems/ClientPredictionSystem.java core/src/test/java/com/galacticodyssey/networking/systems/ClientPredictionSystemTest.java
git commit -m "feat(net): add ClientPredictionSystem to capture inputs and predicted state"
```

---

### Task 4: ReconciliationSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/networking/systems/ReconciliationSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/systems/ReconciliationSystemTest.java`

Processes server `EntityBatchUpdate` for the local player's entity. Compares the server's authoritative position at `lastProcessedInputSequence` against the client's predicted position at that same sequence. Three outcomes:
- **Within 0.01m:** Accept prediction, no correction.
- **0.01m–5.0m:** Snap physics to server state, replay unacked inputs, apply visual smoothing offset.
- **Beyond 5.0m:** Hard snap (teleport), no smoothing.

This system does NOT call `PlayerMovementSystem` directly for replay — instead it adjusts the `TransformComponent` position to the server's state plus the aggregate displacement of replayed inputs. This avoids requiring a physics world in tests while preserving the reconciliation logic.

- [ ] **Step 1: Write the test**

```java
// core/src/test/java/com/galacticodyssey/networking/systems/ReconciliationSystemTest.java
package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.common.protocol.EntityBatchUpdate;
import com.galacticodyssey.common.protocol.EntityStateUpdate;
import com.galacticodyssey.common.protocol.PlayerInput;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.networking.prediction.InputBuffer;
import com.galacticodyssey.networking.prediction.PredictedState;
import com.galacticodyssey.networking.prediction.TimestampedInput;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationSystemTest {

    private Engine engine;
    private ReconciliationSystem system;
    private Entity player;
    private static final int PLAYER_NET_ID = 42;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        system = new ReconciliationSystem();
        engine.addSystem(system);

        player = new Entity();
        player.add(new TransformComponent());
        player.add(new NetworkIdComponent(PLAYER_NET_ID));
        player.add(new PredictionComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerStateComponent.PlayerMode.ON_FOOT_EXTERIOR;
        player.add(state);
        engine.addEntity(player);
    }

    private byte[] encodePosition(float x, float y, float z) {
        ByteBuffer bb = ByteBuffer.allocate(12);
        bb.putFloat(x);
        bb.putFloat(y);
        bb.putFloat(z);
        return bb.array();
    }

    private void seedBuffer(PredictionComponent pred, int fromSeq, int toSeq, float posX) {
        for (int seq = fromSeq; seq <= toSeq; seq++) {
            PlayerInput input = new PlayerInput();
            input.sequenceNumber = seq;
            input.moveForward = 1.0f;
            PredictedState ps = new PredictedState(posX + seq * 0.1f, 0, 0, 0, 0, 0, 1, 0, 0, 0);
            pred.getInputBuffer().add(new TimestampedInput(seq, input, ps));
        }
        pred.nextSequenceNumber = toSeq + 1;
    }

    @Test
    void acceptsPredictionWithinThreshold() {
        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        transform.position.set(10.005f, 0, 0);

        seedBuffer(pred, 0, 5, 10f);

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 3;
        batch.lastProcessedInputSequence = 3;
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = PLAYER_NET_ID;
        update.serverTick = 3;
        update.dirtyMask = 0b1;
        update.payload = encodePosition(10.003f, 0, 0);
        batch.updates = new EntityStateUpdate[]{update};

        system.enqueueServerUpdate(batch);
        engine.update(0.05f);

        // Position should not have changed (prediction accepted)
        assertEquals(10.005f, transform.position.x, 1e-3f);
        assertEquals(0, pred.smoothingFramesRemaining);
    }

    @Test
    void smoothCorrectionWithinRange() {
        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        transform.position.set(11f, 0, 0);

        seedBuffer(pred, 0, 5, 10f);

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 3;
        batch.lastProcessedInputSequence = 3;
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = PLAYER_NET_ID;
        update.serverTick = 3;
        update.dirtyMask = 0b1;
        update.payload = encodePosition(10f, 0, 0);
        batch.updates = new EntityStateUpdate[]{update};

        system.enqueueServerUpdate(batch);
        engine.update(0.05f);

        // Smoothing offset should be set
        assertEquals(PredictionComponent.SMOOTHING_FRAMES, pred.smoothingFramesRemaining);
        // Position snapped to server state (10f) — smoothing offset hides the visual jump
        assertTrue(Math.abs(pred.smoothingOffsetX) > 0.001f);
    }

    @Test
    void hardSnapBeyondThreshold() {
        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        transform.position.set(100f, 0, 0);

        seedBuffer(pred, 0, 5, 10f);

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 3;
        batch.lastProcessedInputSequence = 3;
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = PLAYER_NET_ID;
        update.serverTick = 3;
        update.dirtyMask = 0b1;
        update.payload = encodePosition(10f, 0, 0);
        batch.updates = new EntityStateUpdate[]{update};

        system.enqueueServerUpdate(batch);
        engine.update(0.05f);

        // Hard snap — no smoothing
        assertEquals(10f, transform.position.x, 0.5f);
        assertEquals(0, pred.smoothingFramesRemaining);
    }

    @Test
    void discardsAcknowledgedInputs() {
        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        transform.position.set(10f, 0, 0);

        seedBuffer(pred, 0, 5, 10f);
        assertEquals(6, pred.getInputBuffer().size());

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 3;
        batch.lastProcessedInputSequence = 3;
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = PLAYER_NET_ID;
        update.serverTick = 3;
        update.dirtyMask = 0b1;
        update.payload = encodePosition(10f, 0, 0);
        batch.updates = new EntityStateUpdate[]{update};

        system.enqueueServerUpdate(batch);
        engine.update(0.05f);

        // Inputs 0-3 discarded, 4-5 remain
        assertEquals(2, pred.getInputBuffer().size());
        assertEquals(3, pred.lastAcknowledgedSequence);
    }

    @Test
    void smoothingOffsetDecaysEachFrame() {
        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        pred.smoothingOffsetX = 1.0f;
        pred.smoothingFramesRemaining = PredictionComponent.SMOOTHING_FRAMES;

        engine.update(0.05f);

        assertTrue(pred.smoothingFramesRemaining < PredictionComponent.SMOOTHING_FRAMES);
        assertTrue(pred.smoothingOffsetX < 1.0f);
        assertTrue(pred.smoothingOffsetX > 0f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.systems.ReconciliationSystemTest" -i`
Expected: FAIL — class not found.

- [ ] **Step 3: Write implementation**

```java
// core/src/main/java/com/galacticodyssey/networking/systems/ReconciliationSystem.java
package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.common.protocol.EntityBatchUpdate;
import com.galacticodyssey.common.protocol.EntityStateUpdate;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.networking.prediction.PredictedState;
import com.galacticodyssey.networking.prediction.TimestampedInput;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ReconciliationSystem extends EntitySystem {

    private final ConcurrentLinkedQueue<EntityBatchUpdate> serverUpdates = new ConcurrentLinkedQueue<>();

    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<NetworkIdComponent> netMapper =
            ComponentMapper.getFor(NetworkIdComponent.class);
    private final ComponentMapper<PredictionComponent> predictionMapper =
            ComponentMapper.getFor(PredictionComponent.class);

    private ImmutableArray<Entity> predictedEntities;

    public ReconciliationSystem() {
        super(1);
    }

    @Override
    public void addedToEngine(Engine engine) {
        predictedEntities = engine.getEntitiesFor(
                Family.all(TransformComponent.class, NetworkIdComponent.class,
                        PredictionComponent.class).get());
    }

    public void enqueueServerUpdate(EntityBatchUpdate batch) {
        serverUpdates.add(batch);
    }

    @Override
    public void update(float deltaTime) {
        // Decay smoothing offset on all predicted entities
        for (int i = 0; i < predictedEntities.size(); i++) {
            PredictionComponent pred = predictionMapper.get(predictedEntities.get(i));
            if (pred.smoothingFramesRemaining > 0) {
                float factor = 1.0f / pred.smoothingFramesRemaining;
                pred.smoothingOffsetX -= pred.smoothingOffsetX * factor;
                pred.smoothingOffsetY -= pred.smoothingOffsetY * factor;
                pred.smoothingOffsetZ -= pred.smoothingOffsetZ * factor;
                pred.smoothingFramesRemaining--;
            }
        }

        EntityBatchUpdate batch;
        while ((batch = serverUpdates.poll()) != null) {
            processBatch(batch);
        }
    }

    private void processBatch(EntityBatchUpdate batch) {
        for (int i = 0; i < predictedEntities.size(); i++) {
            Entity entity = predictedEntities.get(i);
            NetworkIdComponent netId = netMapper.get(entity);
            PredictionComponent pred = predictionMapper.get(entity);
            TransformComponent transform = transformMapper.get(entity);

            EntityStateUpdate myUpdate = findUpdate(batch.updates, netId.networkId);
            if (myUpdate == null) continue;

            int ackedSeq = batch.lastProcessedInputSequence;
            pred.lastAcknowledgedSequence = ackedSeq;

            // Decode server position from payload
            float serverX = 0, serverY = 0, serverZ = 0;
            if (myUpdate.payload != null && myUpdate.payload.length >= 12) {
                ByteBuffer bb = ByteBuffer.wrap(myUpdate.payload);
                serverX = bb.getFloat();
                serverY = bb.getFloat();
                serverZ = bb.getFloat();
            }

            // Find predicted state at the acknowledged sequence
            TimestampedInput ackedEntry = pred.getInputBuffer().get(ackedSeq);
            float predictedX = transform.position.x;
            float predictedY = transform.position.y;
            float predictedZ = transform.position.z;
            if (ackedEntry != null) {
                predictedX = ackedEntry.predictedState.posX;
                predictedY = ackedEntry.predictedState.posY;
                predictedZ = ackedEntry.predictedState.posZ;
            }

            float dx = serverX - predictedX;
            float dy = serverY - predictedY;
            float dz = serverZ - predictedZ;
            float error = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            // Discard acknowledged inputs
            pred.getInputBuffer().discardUpTo(ackedSeq);

            if (error <= PredictionComponent.ACCEPT_THRESHOLD) {
                // Prediction accepted — no correction needed
                continue;
            }

            if (error > PredictionComponent.HARD_SNAP_THRESHOLD) {
                // Hard snap (teleport)
                transform.position.set(serverX, serverY, serverZ);
                pred.smoothingOffsetX = 0;
                pred.smoothingOffsetY = 0;
                pred.smoothingOffsetZ = 0;
                pred.smoothingFramesRemaining = 0;
            } else {
                // Smooth correction: snap physics to server state, set smoothing offset
                float oldX = transform.position.x;
                float oldY = transform.position.y;
                float oldZ = transform.position.z;

                // Replay unacknowledged inputs: compute aggregate displacement
                List<TimestampedInput> unacked = pred.getInputBuffer().getUnacknowledged(ackedSeq);
                float replayDx = 0, replayDy = 0, replayDz = 0;
                if (unacked.size() >= 2) {
                    PredictedState first = unacked.get(0).predictedState;
                    PredictedState last = unacked.get(unacked.size() - 1).predictedState;
                    replayDx = last.posX - first.posX;
                    replayDy = last.posY - first.posY;
                    replayDz = last.posZ - first.posZ;
                }

                float newX = serverX + replayDx;
                float newY = serverY + replayDy;
                float newZ = serverZ + replayDz;
                transform.position.set(newX, newY, newZ);

                pred.smoothingOffsetX = oldX - newX;
                pred.smoothingOffsetY = oldY - newY;
                pred.smoothingOffsetZ = oldZ - newZ;
                pred.smoothingFramesRemaining = PredictionComponent.SMOOTHING_FRAMES;
            }
        }
    }

    private EntityStateUpdate findUpdate(EntityStateUpdate[] updates, int networkId) {
        if (updates == null) return null;
        for (EntityStateUpdate u : updates) {
            if (u.networkId == networkId) return u;
        }
        return null;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.systems.ReconciliationSystemTest" -i`
Expected: All 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/networking/systems/ReconciliationSystem.java core/src/test/java/com/galacticodyssey/networking/systems/ReconciliationSystemTest.java
git commit -m "feat(net): add ReconciliationSystem for server state correction with visual smoothing"
```

---

### Task 5: SnapshotBuffer (Remote Entity Interpolation)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/networking/interpolation/EntitySnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/networking/interpolation/SnapshotBuffer.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/interpolation/EntitySnapshotTest.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/interpolation/SnapshotBufferTest.java`

- [ ] **Step 1: Write EntitySnapshot and its test**

```java
// core/src/main/java/com/galacticodyssey/networking/interpolation/EntitySnapshot.java
package com.galacticodyssey.networking.interpolation;

public class EntitySnapshot {
    public final int tick;
    public final float posX, posY, posZ;
    public final float rotX, rotY, rotZ, rotW;
    public final float velX, velY, velZ;

    public EntitySnapshot(int tick,
                          float posX, float posY, float posZ,
                          float rotX, float rotY, float rotZ, float rotW,
                          float velX, float velY, float velZ) {
        this.tick = tick;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
        this.rotW = rotW;
        this.velX = velX;
        this.velY = velY;
        this.velZ = velZ;
    }

    public static EntitySnapshot lerp(EntitySnapshot a, EntitySnapshot b, float t) {
        float px = a.posX + (b.posX - a.posX) * t;
        float py = a.posY + (b.posY - a.posY) * t;
        float pz = a.posZ + (b.posZ - a.posZ) * t;

        // Quaternion slerp (simplified — uses nlerp for speed)
        float dot = a.rotX * b.rotX + a.rotY * b.rotY + a.rotZ * b.rotZ + a.rotW * b.rotW;
        float sign = dot < 0 ? -1f : 1f;
        float rx = a.rotX + (sign * b.rotX - a.rotX) * t;
        float ry = a.rotY + (sign * b.rotY - a.rotY) * t;
        float rz = a.rotZ + (sign * b.rotZ - a.rotZ) * t;
        float rw = a.rotW + (sign * b.rotW - a.rotW) * t;
        float invLen = 1f / (float) Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw);
        rx *= invLen;
        ry *= invLen;
        rz *= invLen;
        rw *= invLen;

        float vx = a.velX + (b.velX - a.velX) * t;
        float vy = a.velY + (b.velY - a.velY) * t;
        float vz = a.velZ + (b.velZ - a.velZ) * t;

        int tick = (int) (a.tick + (b.tick - a.tick) * t);
        return new EntitySnapshot(tick, px, py, pz, rx, ry, rz, rw, vx, vy, vz);
    }

    public EntitySnapshot extrapolate(float seconds, float tickInterval) {
        float ticks = seconds / tickInterval;
        float px = posX + velX * seconds;
        float py = posY + velY * seconds;
        float pz = posZ + velZ * seconds;
        return new EntitySnapshot((int) (tick + ticks), px, py, pz,
                rotX, rotY, rotZ, rotW, velX, velY, velZ);
    }
}
```

```java
// core/src/test/java/com/galacticodyssey/networking/interpolation/EntitySnapshotTest.java
package com.galacticodyssey.networking.interpolation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntitySnapshotTest {

    @Test
    void storesFields() {
        EntitySnapshot s = new EntitySnapshot(5, 1, 2, 3, 0, 0, 0, 1, 4, 5, 6);
        assertEquals(5, s.tick);
        assertEquals(1f, s.posX);
        assertEquals(2f, s.posY);
        assertEquals(3f, s.posZ);
        assertEquals(1f, s.rotW);
    }

    @Test
    void lerpAtZeroReturnsFirst() {
        EntitySnapshot a = new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot b = new EntitySnapshot(10, 10, 20, 30, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot r = EntitySnapshot.lerp(a, b, 0f);
        assertEquals(0f, r.posX, 1e-5f);
        assertEquals(0f, r.posY, 1e-5f);
    }

    @Test
    void lerpAtOneReturnsSecond() {
        EntitySnapshot a = new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot b = new EntitySnapshot(10, 10, 20, 30, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot r = EntitySnapshot.lerp(a, b, 1f);
        assertEquals(10f, r.posX, 1e-5f);
        assertEquals(20f, r.posY, 1e-5f);
    }

    @Test
    void lerpAtHalfInterpolates() {
        EntitySnapshot a = new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot b = new EntitySnapshot(10, 10, 20, 30, 0, 0, 0, 1, 0, 0, 0);
        EntitySnapshot r = EntitySnapshot.lerp(a, b, 0.5f);
        assertEquals(5f, r.posX, 1e-5f);
        assertEquals(10f, r.posY, 1e-5f);
        assertEquals(15f, r.posZ, 1e-5f);
    }

    @Test
    void extrapolateMovesAlongVelocity() {
        EntitySnapshot s = new EntitySnapshot(10, 0, 0, 0, 0, 0, 0, 1, 5, 0, 0);
        EntitySnapshot e = s.extrapolate(0.1f, 0.05f);
        assertEquals(0.5f, e.posX, 1e-5f);
    }
}
```

- [ ] **Step 2: Write SnapshotBuffer and its test**

```java
// core/src/main/java/com/galacticodyssey/networking/interpolation/SnapshotBuffer.java
package com.galacticodyssey.networking.interpolation;

public class SnapshotBuffer {
    public static final int CAPACITY = 4;
    private final EntitySnapshot[] buffer = new EntitySnapshot[CAPACITY];
    private int count;
    private int newestTick = -1;

    public void add(EntitySnapshot snapshot) {
        if (snapshot.tick <= newestTick) return;
        newestTick = snapshot.tick;

        if (count < CAPACITY) {
            buffer[count++] = snapshot;
        } else {
            // Shift left, dropping oldest
            System.arraycopy(buffer, 1, buffer, 0, CAPACITY - 1);
            buffer[CAPACITY - 1] = snapshot;
        }
    }

    public int size() {
        return count;
    }

    public int getNewestTick() {
        return newestTick;
    }

    public EntitySnapshot get(int index) {
        if (index < 0 || index >= count) return null;
        return buffer[index];
    }

    /**
     * Finds the two snapshots that bracket the target tick.
     * Returns a 2-element array [before, after], or null if bracketing is not possible.
     * If targetTick is beyond the newest, returns null (caller should extrapolate).
     */
    public EntitySnapshot[] findBracketing(int targetTick) {
        if (count < 2) return null;

        for (int i = 0; i < count - 1; i++) {
            if (buffer[i].tick <= targetTick && buffer[i + 1].tick >= targetTick) {
                return new EntitySnapshot[]{buffer[i], buffer[i + 1]};
            }
        }
        return null;
    }

    public EntitySnapshot getNewest() {
        if (count == 0) return null;
        return buffer[count - 1];
    }

    public void clear() {
        for (int i = 0; i < CAPACITY; i++) buffer[i] = null;
        count = 0;
        newestTick = -1;
    }
}
```

```java
// core/src/test/java/com/galacticodyssey/networking/interpolation/SnapshotBufferTest.java
package com.galacticodyssey.networking.interpolation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotBufferTest {

    private EntitySnapshot snap(int tick, float x) {
        return new EntitySnapshot(tick, x, 0, 0, 0, 0, 0, 1, 0, 0, 0);
    }

    @Test
    void addAndRetrieve() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(1, 10));
        buf.add(snap(2, 20));
        assertEquals(2, buf.size());
        assertEquals(1, buf.get(0).tick);
        assertEquals(2, buf.get(1).tick);
    }

    @Test
    void dropsOutOfOrderPackets() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(5, 50));
        buf.add(snap(3, 30));
        assertEquals(1, buf.size());
        assertEquals(5, buf.getNewestTick());
    }

    @Test
    void evictsOldestBeyondCapacity() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(1, 10));
        buf.add(snap(2, 20));
        buf.add(snap(3, 30));
        buf.add(snap(4, 40));
        assertEquals(4, buf.size());
        buf.add(snap(5, 50));
        assertEquals(4, buf.size());
        assertEquals(2, buf.get(0).tick);
        assertEquals(5, buf.get(3).tick);
    }

    @Test
    void findBracketingReturnsCorrectPair() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(0, 0));
        buf.add(snap(5, 50));
        buf.add(snap(10, 100));
        EntitySnapshot[] pair = buf.findBracketing(3);
        assertNotNull(pair);
        assertEquals(0, pair[0].tick);
        assertEquals(5, pair[1].tick);
    }

    @Test
    void findBracketingReturnsNullWithTooFewSnapshots() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(5, 50));
        assertNull(buf.findBracketing(3));
    }

    @Test
    void findBracketingReturnsNullForFutureTick() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(0, 0));
        buf.add(snap(5, 50));
        assertNull(buf.findBracketing(10));
    }

    @Test
    void clearResetsState() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(1, 10));
        buf.add(snap(2, 20));
        buf.clear();
        assertEquals(0, buf.size());
        assertEquals(-1, buf.getNewestTick());
    }
}
```

- [ ] **Step 3: Run tests**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.interpolation.*" -i`
Expected: All 12 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/networking/interpolation/EntitySnapshot.java core/src/main/java/com/galacticodyssey/networking/interpolation/SnapshotBuffer.java core/src/test/java/com/galacticodyssey/networking/interpolation/EntitySnapshotTest.java core/src/test/java/com/galacticodyssey/networking/interpolation/SnapshotBufferTest.java
git commit -m "feat(net): add SnapshotBuffer and EntitySnapshot for remote entity interpolation"
```

---

### Task 6: InterpolationComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/networking/components/InterpolationComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/components/InterpolationComponentTest.java`

- [ ] **Step 1: Write the test**

```java
// core/src/test/java/com/galacticodyssey/networking/components/InterpolationComponentTest.java
package com.galacticodyssey.networking.components;

import com.galacticodyssey.networking.interpolation.EntitySnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InterpolationComponentTest {

    @Test
    void defaultsAreEmpty() {
        InterpolationComponent ic = new InterpolationComponent();
        assertEquals(0, ic.getSnapshotBuffer().size());
        assertEquals(0f, ic.extrapolationTimer);
        assertFalse(ic.frozen);
    }

    @Test
    void addSnapshotGoesIntoBuffer() {
        InterpolationComponent ic = new InterpolationComponent();
        ic.getSnapshotBuffer().add(
                new EntitySnapshot(1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0));
        assertEquals(1, ic.getSnapshotBuffer().size());
    }

    @Test
    void extrapolationMaxIsHalfSecond() {
        assertEquals(0.5f, InterpolationComponent.MAX_EXTRAPOLATION_SECONDS, 1e-6f);
    }

    @Test
    void blendFramesCountIsFive() {
        assertEquals(5, InterpolationComponent.BLEND_FRAMES);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.components.InterpolationComponentTest" -i`
Expected: FAIL — class not found.

- [ ] **Step 3: Write implementation**

```java
// core/src/main/java/com/galacticodyssey/networking/components/InterpolationComponent.java
package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.networking.interpolation.SnapshotBuffer;

public class InterpolationComponent implements Component {

    public static final float MAX_EXTRAPOLATION_SECONDS = 0.5f;
    public static final int BLEND_FRAMES = 5;
    public static final int RENDER_DELAY_TICKS = 2;

    private final SnapshotBuffer snapshotBuffer = new SnapshotBuffer();

    public float extrapolationTimer;
    public boolean frozen;
    public int blendFramesRemaining;

    public float blendFromX, blendFromY, blendFromZ;
    public float blendFromRotX, blendFromRotY, blendFromRotZ, blendFromRotW;

    public SnapshotBuffer getSnapshotBuffer() {
        return snapshotBuffer;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.components.InterpolationComponentTest" -i`
Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/networking/components/InterpolationComponent.java core/src/test/java/com/galacticodyssey/networking/components/InterpolationComponentTest.java
git commit -m "feat(net): add InterpolationComponent for remote entity state buffering"
```

---

### Task 7: InterpolationSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/networking/systems/InterpolationSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/systems/InterpolationSystemTest.java`

Runs at priority 55 (after game logic, before rendering). For each entity with `InterpolationComponent` + `TransformComponent`:
1. Compute the target render tick = `newestTick - RENDER_DELAY_TICKS`.
2. Find bracketing snapshots in the buffer.
3. Compute interpolation factor `t` between the two snapshots.
4. Lerp position, nlerp rotation, set on `TransformComponent`.
5. If no bracketing pair (late packet), extrapolate using last known velocity up to 500ms.
6. After 500ms without an update, freeze at last position.
7. When a new packet arrives after freeze, blend over 5 frames.

- [ ] **Step 1: Write the test**

```java
// core/src/test/java/com/galacticodyssey/networking/systems/InterpolationSystemTest.java
package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.InterpolationComponent;
import com.galacticodyssey.networking.interpolation.EntitySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterpolationSystemTest {

    private Engine engine;
    private InterpolationSystem system;
    private Entity remote;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        system = new InterpolationSystem(0.05f);
        engine.addSystem(system);

        remote = new Entity();
        remote.add(new TransformComponent());
        remote.add(new InterpolationComponent());
        engine.addEntity(remote);
    }

    @Test
    void interpolatesPositionBetweenSnapshots() {
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        ic.getSnapshotBuffer().add(new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0));
        ic.getSnapshotBuffer().add(new EntitySnapshot(4, 40, 0, 0, 0, 0, 0, 1, 0, 0, 0));

        system.setCurrentServerTick(4);
        engine.update(0.05f);

        TransformComponent t = remote.getComponent(TransformComponent.class);
        // Target tick = 4 - 2 = 2. t = (2-0)/(4-0) = 0.5
        assertEquals(20f, t.position.x, 1.0f);
    }

    @Test
    void snapsRotationViaNlerp() {
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        // Identity quaternion (0,0,0,1)
        ic.getSnapshotBuffer().add(new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0));
        // 90 degree rotation around Y (0, 0.707, 0, 0.707)
        ic.getSnapshotBuffer().add(new EntitySnapshot(4, 0, 0, 0, 0, 0.707f, 0, 0.707f, 0, 0, 0));

        system.setCurrentServerTick(4);
        engine.update(0.05f);

        TransformComponent t = remote.getComponent(TransformComponent.class);
        // At t=0.5 the rotation should be ~45 degrees
        // rotY should be between 0 and 0.707
        assertTrue(t.rotation.y > 0.01f);
        assertTrue(t.rotation.y < 0.71f);
    }

    @Test
    void extrapolatesWhenNoNewData() {
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        ic.getSnapshotBuffer().add(new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 10, 0, 0));

        system.setCurrentServerTick(5);
        engine.update(0.1f);

        TransformComponent t = remote.getComponent(TransformComponent.class);
        // Extrapolating from x=0 with velX=10 for 0.1s = 1.0
        assertTrue(t.position.x > 0);
        assertTrue(ic.extrapolationTimer > 0);
    }

    @Test
    void freezesAfterMaxExtrapolation() {
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        ic.getSnapshotBuffer().add(new EntitySnapshot(0, 50, 0, 0, 0, 0, 0, 1, 10, 0, 0));
        ic.extrapolationTimer = InterpolationComponent.MAX_EXTRAPOLATION_SECONDS;
        ic.frozen = true;

        TransformComponent t = remote.getComponent(TransformComponent.class);
        t.position.set(55, 0, 0);

        system.setCurrentServerTick(100);
        engine.update(0.05f);

        // Frozen — position should not change further
        assertEquals(55f, t.position.x, 1e-3f);
    }

    @Test
    void doesNotCrashWithNoSnapshots() {
        system.setCurrentServerTick(5);
        engine.update(0.05f);
        // Should complete without errors
        TransformComponent t = remote.getComponent(TransformComponent.class);
        assertEquals(0f, t.position.x);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.systems.InterpolationSystemTest" -i`
Expected: FAIL — class not found.

- [ ] **Step 3: Write implementation**

```java
// core/src/main/java/com/galacticodyssey/networking/systems/InterpolationSystem.java
package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.InterpolationComponent;
import com.galacticodyssey.networking.interpolation.EntitySnapshot;
import com.galacticodyssey.networking.interpolation.SnapshotBuffer;

public class InterpolationSystem extends EntitySystem {

    private final float tickInterval;
    private int currentServerTick;

    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<InterpolationComponent> interpMapper =
            ComponentMapper.getFor(InterpolationComponent.class);

    private ImmutableArray<Entity> remoteEntities;

    public InterpolationSystem(float tickInterval) {
        super(55);
        this.tickInterval = tickInterval;
    }

    public void setCurrentServerTick(int tick) {
        this.currentServerTick = tick;
    }

    @Override
    public void addedToEngine(Engine engine) {
        remoteEntities = engine.getEntitiesFor(
                Family.all(TransformComponent.class, InterpolationComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < remoteEntities.size(); i++) {
            Entity entity = remoteEntities.get(i);
            TransformComponent transform = transformMapper.get(entity);
            InterpolationComponent interp = interpMapper.get(entity);
            SnapshotBuffer buffer = interp.getSnapshotBuffer();

            if (buffer.size() == 0) continue;

            if (interp.frozen) continue;

            int targetTick = currentServerTick - InterpolationComponent.RENDER_DELAY_TICKS;
            EntitySnapshot[] pair = buffer.findBracketing(targetTick);

            if (pair != null) {
                interp.extrapolationTimer = 0;

                int tickSpan = pair[1].tick - pair[0].tick;
                float t = tickSpan > 0 ? (float) (targetTick - pair[0].tick) / tickSpan : 0f;
                t = Math.max(0f, Math.min(1f, t));

                EntitySnapshot lerped = EntitySnapshot.lerp(pair[0], pair[1], t);

                if (interp.blendFramesRemaining > 0) {
                    float blendT = 1f - (float) interp.blendFramesRemaining / InterpolationComponent.BLEND_FRAMES;
                    transform.position.set(
                            interp.blendFromX + (lerped.posX - interp.blendFromX) * blendT,
                            interp.blendFromY + (lerped.posY - interp.blendFromY) * blendT,
                            interp.blendFromZ + (lerped.posZ - interp.blendFromZ) * blendT
                    );
                    interp.blendFramesRemaining--;
                } else {
                    transform.position.set(lerped.posX, lerped.posY, lerped.posZ);
                    transform.rotation.set(lerped.rotX, lerped.rotY, lerped.rotZ, lerped.rotW);
                }
            } else {
                // No bracketing pair — extrapolate
                EntitySnapshot newest = buffer.getNewest();
                if (newest != null) {
                    interp.extrapolationTimer += deltaTime;
                    if (interp.extrapolationTimer >= InterpolationComponent.MAX_EXTRAPOLATION_SECONDS) {
                        interp.frozen = true;
                        return;
                    }
                    EntitySnapshot extrapolated = newest.extrapolate(
                            interp.extrapolationTimer, tickInterval);
                    transform.position.set(extrapolated.posX, extrapolated.posY, extrapolated.posZ);
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.systems.InterpolationSystemTest" -i`
Expected: All 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/networking/systems/InterpolationSystem.java core/src/test/java/com/galacticodyssey/networking/systems/InterpolationSystemTest.java
git commit -m "feat(net): add InterpolationSystem for smooth remote entity rendering"
```

---

### Task 8: Wire ClientNetworkSystem to Prediction and Interpolation

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/networking/systems/ClientNetworkSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/systems/ClientNetworkSystemIntegrationTest.java`

The `ClientNetworkSystem.update()` currently does nothing. Wire it to:
1. Process `EntitySpawnMessage` queue — create Ashley entities with `NetworkIdComponent` + `TransformComponent` + `InterpolationComponent`.
2. Process `EntityDestroyMessage` queue — remove entities.
3. Process `EntityBatchUpdate` queue — feed each update into the correct entity's `InterpolationComponent.snapshotBuffer` for remote entities, and feed local player updates into `ReconciliationSystem`.
4. Track spawned remote entities in a `HashMap<Integer, Entity>` by networkId.

- [ ] **Step 1: Write the integration test**

```java
// core/src/test/java/com/galacticodyssey/networking/systems/ClientNetworkSystemIntegrationTest.java
package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.common.protocol.*;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.InterpolationComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class ClientNetworkSystemIntegrationTest {

    private Engine engine;
    private ClientNetworkSystem netSystem;
    private ReconciliationSystem reconSystem;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        reconSystem = new ReconciliationSystem();
        netSystem = new ClientNetworkSystem(reconSystem);
        engine.addSystem(netSystem);
        engine.addSystem(reconSystem);
    }

    @Test
    void spawnCreatesRemoteEntityWithInterpolation() {
        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 100;
        spawn.entityType = "ship";
        spawn.componentData = new byte[0];

        netSystem.handleEntitySpawn(spawn);
        engine.update(0.05f);

        Entity remote = netSystem.getRemoteEntity(100);
        assertNotNull(remote);
        assertNotNull(remote.getComponent(NetworkIdComponent.class));
        assertNotNull(remote.getComponent(TransformComponent.class));
        assertNotNull(remote.getComponent(InterpolationComponent.class));
    }

    @Test
    void destroyRemovesRemoteEntity() {
        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 100;
        spawn.entityType = "ship";
        spawn.componentData = new byte[0];
        netSystem.handleEntitySpawn(spawn);
        engine.update(0.05f);
        assertNotNull(netSystem.getRemoteEntity(100));

        EntityDestroyMessage destroy = new EntityDestroyMessage();
        destroy.networkId = 100;
        netSystem.handleEntityDestroy(destroy);
        engine.update(0.05f);

        assertNull(netSystem.getRemoteEntity(100));
    }

    @Test
    void batchUpdateFeedsRemoteInterpolationBuffer() {
        // Spawn remote entity first
        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 200;
        spawn.entityType = "npc";
        spawn.componentData = new byte[0];
        netSystem.handleEntitySpawn(spawn);
        engine.update(0.05f);

        // Send batch update
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = 200;
        update.serverTick = 5;
        update.dirtyMask = 0b1;
        ByteBuffer bb = ByteBuffer.allocate(12);
        bb.putFloat(10f);
        bb.putFloat(20f);
        bb.putFloat(30f);
        update.payload = bb.array();

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 5;
        batch.lastProcessedInputSequence = -1;
        batch.updates = new EntityStateUpdate[]{update};

        netSystem.handleEntityBatchUpdate(batch);
        engine.update(0.05f);

        Entity remote = netSystem.getRemoteEntity(200);
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        assertEquals(1, ic.getSnapshotBuffer().size());
        assertEquals(5, ic.getSnapshotBuffer().getNewestTick());
    }

    @Test
    void batchUpdateForLocalPlayerGoesToReconciliation() {
        // Set up local player
        int localNetId = 42;
        Entity player = new Entity();
        player.add(new TransformComponent());
        player.add(new NetworkIdComponent(localNetId));
        player.add(new PredictionComponent());
        player.add(new PlayerInputComponent());
        PlayerStateComponent ps = new PlayerStateComponent();
        ps.currentMode = PlayerStateComponent.PlayerMode.ON_FOOT_EXTERIOR;
        player.add(ps);
        engine.addEntity(player);

        netSystem.setLocalPlayerNetworkId(localNetId);

        // Seed the prediction buffer
        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        com.galacticodyssey.common.protocol.PlayerInput pi = new com.galacticodyssey.common.protocol.PlayerInput();
        pi.sequenceNumber = 0;
        pred.getInputBuffer().add(new com.galacticodyssey.networking.prediction.TimestampedInput(
                0, pi, new com.galacticodyssey.networking.prediction.PredictedState(10, 0, 0, 0, 0, 0, 1, 0, 0, 0)));
        pred.nextSequenceNumber = 1;

        // Batch with local player's update
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = localNetId;
        update.serverTick = 1;
        update.dirtyMask = 0b1;
        ByteBuffer bb = ByteBuffer.allocate(12);
        bb.putFloat(10f);
        bb.putFloat(0f);
        bb.putFloat(0f);
        update.payload = bb.array();

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 1;
        batch.lastProcessedInputSequence = 0;
        batch.updates = new EntityStateUpdate[]{update};

        netSystem.handleEntityBatchUpdate(batch);
        engine.update(0.05f);

        // Reconciliation should have processed — last acked sequence updated
        assertEquals(0, pred.lastAcknowledgedSequence);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.systems.ClientNetworkSystemIntegrationTest" -i`
Expected: FAIL — constructor and methods don't exist yet.

- [ ] **Step 3: Write implementation — update ClientNetworkSystem**

Replace the entire `ClientNetworkSystem.java` with:

```java
// core/src/main/java/com/galacticodyssey/networking/systems/ClientNetworkSystem.java
package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.common.protocol.*;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.InterpolationComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.interpolation.EntitySnapshot;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientNetworkSystem extends EntitySystem {

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private String sessionToken;
    private UUID localPlayerId;
    private int localPlayerNetworkId = -1;

    private final ConcurrentLinkedQueue<EntityBatchUpdate> batchQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<EntitySpawnMessage> spawnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<EntityDestroyMessage> destroyQueue = new ConcurrentLinkedQueue<>();

    private final Map<Integer, Entity> remoteEntities = new HashMap<>();
    private final ReconciliationSystem reconciliationSystem;
    private Engine engine;

    public ClientNetworkSystem(ReconciliationSystem reconciliationSystem) {
        super(60);
        this.reconciliationSystem = reconciliationSystem;
    }

    public ClientNetworkSystem() {
        this(null);
    }

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public UUID getLocalPlayerId() {
        return localPlayerId;
    }

    public void setLocalPlayerNetworkId(int networkId) {
        this.localPlayerNetworkId = networkId;
    }

    public int getLocalPlayerNetworkId() {
        return localPlayerNetworkId;
    }

    public Entity getRemoteEntity(int networkId) {
        return remoteEntities.get(networkId);
    }

    public void handleLoginResponse(LoginResponse response) {
        if (response.success) {
            connectionState = ConnectionState.CONNECTED;
            sessionToken = response.sessionToken;
            localPlayerId = response.playerId;
        }
    }

    public void handleEntityBatchUpdate(EntityBatchUpdate batch) {
        batchQueue.add(batch);
    }

    public void handleEntitySpawn(EntitySpawnMessage spawn) {
        spawnQueue.add(spawn);
    }

    public void handleEntityDestroy(EntityDestroyMessage destroy) {
        destroyQueue.add(destroy);
    }

    public List<EntityBatchUpdate> drainBatchUpdates() {
        List<EntityBatchUpdate> result = new ArrayList<>();
        EntityBatchUpdate item;
        while ((item = batchQueue.poll()) != null) result.add(item);
        return result;
    }

    public List<EntitySpawnMessage> drainSpawnMessages() {
        List<EntitySpawnMessage> result = new ArrayList<>();
        EntitySpawnMessage item;
        while ((item = spawnQueue.poll()) != null) result.add(item);
        return result;
    }

    public List<EntityDestroyMessage> drainDestroyMessages() {
        List<EntityDestroyMessage> result = new ArrayList<>();
        EntityDestroyMessage item;
        while ((item = destroyQueue.poll()) != null) result.add(item);
        return result;
    }

    @Override
    public void update(float deltaTime) {
        processSpawns();
        processDestroys();
        processBatchUpdates();
    }

    private void processSpawns() {
        EntitySpawnMessage spawn;
        while ((spawn = spawnQueue.poll()) != null) {
            if (remoteEntities.containsKey(spawn.networkId)) continue;

            Entity entity = new Entity();
            entity.add(new NetworkIdComponent(spawn.networkId));
            entity.add(new TransformComponent());
            entity.add(new InterpolationComponent());
            engine.addEntity(entity);
            remoteEntities.put(spawn.networkId, entity);
        }
    }

    private void processDestroys() {
        EntityDestroyMessage destroy;
        while ((destroy = destroyQueue.poll()) != null) {
            Entity entity = remoteEntities.remove(destroy.networkId);
            if (entity != null) {
                engine.removeEntity(entity);
            }
        }
    }

    private void processBatchUpdates() {
        EntityBatchUpdate batch;
        while ((batch = batchQueue.poll()) != null) {
            if (batch.updates == null) continue;

            for (EntityStateUpdate update : batch.updates) {
                if (update.networkId == localPlayerNetworkId) {
                    if (reconciliationSystem != null) {
                        reconciliationSystem.enqueueServerUpdate(batch);
                    }
                    continue;
                }

                Entity remote = remoteEntities.get(update.networkId);
                if (remote == null) continue;

                InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
                if (ic == null) continue;

                float posX = 0, posY = 0, posZ = 0;
                float velX = 0, velY = 0, velZ = 0;
                if (update.payload != null && update.payload.length >= 12) {
                    ByteBuffer bb = ByteBuffer.wrap(update.payload);
                    posX = bb.getFloat();
                    posY = bb.getFloat();
                    posZ = bb.getFloat();
                }

                EntitySnapshot snapshot = new EntitySnapshot(
                        update.serverTick, posX, posY, posZ,
                        0, 0, 0, 1, velX, velY, velZ);

                if (ic.frozen) {
                    TransformComponent t = remote.getComponent(TransformComponent.class);
                    if (t != null) {
                        ic.blendFromX = t.position.x;
                        ic.blendFromY = t.position.y;
                        ic.blendFromZ = t.position.z;
                        ic.blendFramesRemaining = InterpolationComponent.BLEND_FRAMES;
                    }
                    ic.frozen = false;
                    ic.extrapolationTimer = 0;
                }

                ic.getSnapshotBuffer().add(snapshot);
            }
        }
    }
}
```

- [ ] **Step 4: Run tests — both old and new**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.systems.ClientNetworkSystem*" -i`
Expected: All tests PASS (both `ClientNetworkSystemTest` and `ClientNetworkSystemIntegrationTest`).

Note: The old `ClientNetworkSystemTest` may need its constructor calls updated since we changed the class. The no-arg constructor is preserved for backward compatibility. If the old test breaks, update the test to use `new ClientNetworkSystem()` (which it already does).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/networking/systems/ClientNetworkSystem.java core/src/test/java/com/galacticodyssey/networking/systems/ClientNetworkSystemIntegrationTest.java
git commit -m "feat(net): wire ClientNetworkSystem to spawn/destroy/interpolation/reconciliation"
```

---

### Task 9: Smoothing Offset Decay Test

**Files:**
- Test: `core/src/test/java/com/galacticodyssey/networking/systems/SmoothingDecayTest.java`

A focused test verifying the visual smoothing offset decays to near-zero after the full 10 frames.

- [ ] **Step 1: Write the test**

```java
// core/src/test/java/com/galacticodyssey/networking/systems/SmoothingDecayTest.java
package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmoothingDecayTest {

    @Test
    void offsetDecaysToNearZeroAfterSmoothingFrames() {
        Engine engine = new Engine();
        ReconciliationSystem system = new ReconciliationSystem();
        engine.addSystem(system);

        Entity player = new Entity();
        player.add(new TransformComponent());
        player.add(new NetworkIdComponent(1));
        PredictionComponent pred = new PredictionComponent();
        pred.smoothingOffsetX = 2.0f;
        pred.smoothingOffsetY = -1.5f;
        pred.smoothingOffsetZ = 0.8f;
        pred.smoothingFramesRemaining = PredictionComponent.SMOOTHING_FRAMES;
        player.add(pred);
        engine.addEntity(player);

        for (int i = 0; i < PredictionComponent.SMOOTHING_FRAMES; i++) {
            engine.update(0.016f);
        }

        assertEquals(0, pred.smoothingFramesRemaining);
        assertEquals(0f, pred.smoothingOffsetX, 0.01f);
        assertEquals(0f, pred.smoothingOffsetY, 0.01f);
        assertEquals(0f, pred.smoothingOffsetZ, 0.01f);
    }
}
```

- [ ] **Step 2: Run test**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.systems.SmoothingDecayTest" -i`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/networking/systems/SmoothingDecayTest.java
git commit -m "test(net): verify smoothing offset decays to zero after 10 frames"
```

---

### Task 10: End-to-End Prediction-Interpolation Integration Test

**Files:**
- Test: `core/src/test/java/com/galacticodyssey/networking/PredictionInterpolationIntegrationTest.java`

A single test that exercises the full client pipeline: spawn a local predicted player + a remote entity, run several prediction frames, feed a server batch update, verify reconciliation + interpolation both function together in one Ashley engine.

- [ ] **Step 1: Write the test**

```java
// core/src/test/java/com/galacticodyssey/networking/PredictionInterpolationIntegrationTest.java
package com.galacticodyssey.networking;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.common.protocol.*;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.InterpolationComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.components.PredictionComponent;
import com.galacticodyssey.networking.interpolation.EntitySnapshot;
import com.galacticodyssey.networking.prediction.PredictedState;
import com.galacticodyssey.networking.prediction.TimestampedInput;
import com.galacticodyssey.networking.systems.*;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class PredictionInterpolationIntegrationTest {

    @Test
    void fullClientPipeline() {
        Engine engine = new Engine();

        ReconciliationSystem reconSystem = new ReconciliationSystem();
        ClientPredictionSystem predSystem = new ClientPredictionSystem();
        ClientNetworkSystem netSystem = new ClientNetworkSystem(reconSystem);
        InterpolationSystem interpSystem = new InterpolationSystem(0.05f);

        engine.addSystem(predSystem);       // priority 0
        engine.addSystem(reconSystem);      // priority 1
        engine.addSystem(interpSystem);     // priority 55
        engine.addSystem(netSystem);        // priority 60

        // Local player
        int localNetId = 1;
        Entity player = new Entity();
        TransformComponent playerTransform = new TransformComponent();
        playerTransform.position.set(100, 0, 0);
        player.add(playerTransform);
        player.add(new NetworkIdComponent(localNetId));
        player.add(new PredictionComponent());
        player.add(new PlayerInputComponent());
        PlayerStateComponent ps = new PlayerStateComponent();
        ps.currentMode = PlayerStateComponent.PlayerMode.ON_FOOT_EXTERIOR;
        player.add(ps);
        engine.addEntity(player);
        netSystem.setLocalPlayerNetworkId(localNetId);

        // Spawn a remote entity via network
        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 50;
        spawn.entityType = "ship";
        spawn.componentData = new byte[0];
        netSystem.handleEntitySpawn(spawn);

        // Run a few frames of prediction
        for (int i = 0; i < 3; i++) {
            player.getComponent(PlayerInputComponent.class).moveForward = 1.0f;
            engine.update(0.05f);
        }

        PredictionComponent pred = player.getComponent(PredictionComponent.class);
        assertEquals(3, pred.getInputBuffer().size());

        // Remote entity should exist with interpolation component
        Entity remote = netSystem.getRemoteEntity(50);
        assertNotNull(remote);
        InterpolationComponent ic = remote.getComponent(InterpolationComponent.class);
        assertNotNull(ic);

        // Feed remote entity some snapshots
        ic.getSnapshotBuffer().add(new EntitySnapshot(0, 0, 0, 0, 0, 0, 0, 1, 5, 0, 0));
        ic.getSnapshotBuffer().add(new EntitySnapshot(4, 20, 0, 0, 0, 0, 0, 1, 5, 0, 0));
        interpSystem.setCurrentServerTick(4);

        // Feed server batch update for local player (prediction matches)
        EntityStateUpdate localUpdate = new EntityStateUpdate();
        localUpdate.networkId = localNetId;
        localUpdate.serverTick = 1;
        localUpdate.dirtyMask = 0b1;
        ByteBuffer bb = ByteBuffer.allocate(12);
        bb.putFloat(100f);
        bb.putFloat(0f);
        bb.putFloat(0f);
        localUpdate.payload = bb.array();

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 1;
        batch.lastProcessedInputSequence = 0;
        batch.updates = new EntityStateUpdate[]{localUpdate};
        netSystem.handleEntityBatchUpdate(batch);

        // One more engine step to process everything
        engine.update(0.05f);

        // Verify reconciliation ran
        assertEquals(0, pred.lastAcknowledgedSequence);

        // Verify remote entity was interpolated
        TransformComponent remoteTransform = remote.getComponent(TransformComponent.class);
        // targetTick = 4-2 = 2, t = 2/4 = 0.5, so posX ~ 10
        assertTrue(remoteTransform.position.x > 0);
    }
}
```

- [ ] **Step 2: Run test**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.PredictionInterpolationIntegrationTest" -i`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/networking/PredictionInterpolationIntegrationTest.java
git commit -m "test(net): end-to-end prediction + interpolation integration test"
```

---

### Task 11: Verify Full Build

**Files:** None (build verification only).

- [ ] **Step 1: Run full test suite**

Run: `gradlew.bat :core:test :common:test :server:test`
Expected: All tests pass. The 2 pre-existing failures (GalaxyGenerationPipelineTest, PlayerMovementSystemTest) may still fail — they are not caused by networking changes.

- [ ] **Step 2: Verify compilation of all modules**

Run: `gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL.

---

## Summary of New Classes

| Class | Module | Type | Priority | Responsibility |
|-------|--------|------|----------|----------------|
| `PredictedState` | core | Data | — | Immutable position/rotation/velocity snapshot |
| `TimestampedInput` | core | Data | — | Input + predicted state at sequence number |
| `InputBuffer` | core | Data | — | 128-slot ring buffer for prediction |
| `PredictionComponent` | core | Component | — | Local player prediction state |
| `ClientPredictionSystem` | core | System | 0 | Captures input + predicted state each frame |
| `ReconciliationSystem` | core | System | 1 | Compares server state, corrects, applies smoothing |
| `EntitySnapshot` | core | Data | — | Remote entity state at a tick |
| `SnapshotBuffer` | core | Data | — | 4-slot buffer with bracketing queries |
| `InterpolationComponent` | core | Component | — | Remote entity interpolation state |
| `InterpolationSystem` | core | System | 55 | Lerps/slerps remote entities 100ms behind |
| `ClientNetworkSystem` | core | System | 60 | Wires spawn/destroy/batch to ECS (updated) |
