# Networking Part 1: Transport, Server Tick & Replication

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the core multiplayer networking stack — KryoNet transport, message protocol, server-authoritative tick loop, and entity replication with interest management — so two clients can connect and see each other's entities move.

**Architecture:** Server-authoritative with KryoNet (TCP+UDP). A new `common/` module holds protocol messages and Kryo registration. The `server/` module gets a dedicated server with a fixed 20Hz tick loop running Ashley ECS. Entity state is replicated to clients via delta-compressed `EntityBatchUpdate` packets, filtered by distance-based interest tiers (NEAR/MID/FAR). Client prediction and interpolation are deferred to Part 2.

**Tech Stack:** Java 21, KryoNet 2.22.9, Kryo 5.6.0, Ashley ECS 1.7.4, libGDX 1.13.5, JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-28-networking-system-design.md` (sections 1–3)

**Follow-up plans:**
- Part 2: Client Prediction & Interpolation (spec sections 4–5)
- Part 3: Zone Architecture & Persistence (spec sections 6–8)

---

### Task 1: Create `common/` Gradle Module

**Files:**
- Create: `common/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `core/build.gradle.kts`
- Modify: `server/build.gradle.kts`

- [ ] **Step 1: Add `common` to settings.gradle.kts**

```kotlin
rootProject.name = "SpaceGame"

include("common")
include("core")
include("desktop")
include("server")
```

- [ ] **Step 2: Create `common/build.gradle.kts`**

```kotlin
plugins {
    `java-library`
}

dependencies {
    api("com.esotericsoftware:kryo:5.6.0")
    api("com.esotericsoftware:kryonet:2.22.9")

    testImplementation("org.junit.jupiter:junit-jupiter:${project.property("junitVersion")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

- [ ] **Step 3: Add `common` dependency to `core/build.gradle.kts`**

Add at the top of the `dependencies` block:

```kotlin
api(project(":common"))
```

- [ ] **Step 4: Add KryoNet dependency to `server/build.gradle.kts`**

The server already depends on `:core` which transitively includes `:common`. No change needed — verify by running:

Run: `gradlew.bat :server:dependencies --configuration runtimeClasspath`
Expected: output includes `+--- project :common` and kryonet-2.22.9

- [ ] **Step 5: Verify the build compiles**

Run: `gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
git add settings.gradle.kts common/build.gradle.kts core/build.gradle.kts
git commit -m "feat(net): add common/ module with KryoNet dependency"
```

---

### Task 2: Define Network Message POJOs

**Files:**
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/NetworkMessage.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/LoginRequest.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/LoginResponse.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/Heartbeat.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/HeartbeatAck.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/Disconnect.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/InputPacket.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/PlayerInput.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/EntityStateUpdate.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/EntityBatchUpdate.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/EntitySpawnMessage.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/EntityDestroyMessage.java`
- Test: `common/src/test/java/com/galacticodyssey/common/protocol/NetworkMessageTest.java`

All messages follow the project's snapshot POJO pattern: public fields, no-arg constructor, no logic.

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMessageTest {

    @Test
    void loginRequestHasPublicFields() {
        LoginRequest msg = new LoginRequest();
        msg.username = "player1";
        msg.clientVersion = "0.1.0";
        assertEquals("player1", msg.username);
        assertEquals("0.1.0", msg.clientVersion);
    }

    @Test
    void loginResponseHasPublicFields() {
        LoginResponse msg = new LoginResponse();
        msg.sessionToken = "abc-123";
        msg.zoneServerHost = "localhost";
        msg.zoneServerTcpPort = 7100;
        msg.zoneServerUdpPort = 7101;
        msg.playerId = java.util.UUID.randomUUID();
        assertNotNull(msg.playerId);
    }

    @Test
    void inputPacketBundlesThreeInputs() {
        InputPacket packet = new InputPacket();
        packet.inputs = new PlayerInput[3];
        packet.inputs[0] = new PlayerInput();
        packet.inputs[0].moveForward = 1.0f;
        packet.inputs[0].sequenceNumber = 42;
        assertEquals(3, packet.inputs.length);
        assertEquals(1.0f, packet.inputs[0].moveForward, 1e-5f);
        assertEquals(42, packet.inputs[0].sequenceNumber);
    }

    @Test
    void entityStateUpdateHasDirtyMask() {
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = 7;
        update.serverTick = 100;
        update.dirtyMask = 0b0011;
        update.payload = new byte[]{1, 2, 3};
        assertEquals(7, update.networkId);
        assertEquals(0b0011, update.dirtyMask);
    }

    @Test
    void entityBatchUpdateWrapsMultipleUpdates() {
        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 200;
        batch.lastProcessedInputSequence = 50;
        batch.updates = new EntityStateUpdate[2];
        batch.updates[0] = new EntityStateUpdate();
        batch.updates[1] = new EntityStateUpdate();
        assertEquals(2, batch.updates.length);
        assertEquals(50, batch.lastProcessedInputSequence);
    }

    @Test
    void disconnectHasReasonEnum() {
        Disconnect msg = new Disconnect();
        msg.reason = Disconnect.Reason.TIMEOUT;
        assertEquals(Disconnect.Reason.TIMEOUT, msg.reason);
    }

    @Test
    void entitySpawnMessageHasFullPayload() {
        EntitySpawnMessage msg = new EntitySpawnMessage();
        msg.networkId = 1;
        msg.entityType = "ship";
        msg.persistenceId = java.util.UUID.randomUUID();
        msg.componentData = new byte[]{10, 20, 30};
        assertEquals("ship", msg.entityType);
        assertNotNull(msg.persistenceId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :common:test --tests "com.galacticodyssey.common.protocol.NetworkMessageTest" --info`
Expected: FAIL — classes not found

- [ ] **Step 3: Create the base class and all message POJOs**

`NetworkMessage.java`:
```java
package com.galacticodyssey.common.protocol;

public abstract class NetworkMessage {
}
```

`LoginRequest.java`:
```java
package com.galacticodyssey.common.protocol;

public class LoginRequest extends NetworkMessage {
    public String username;
    public String clientVersion;

    public LoginRequest() {}
}
```

`LoginResponse.java`:
```java
package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class LoginResponse extends NetworkMessage {
    public boolean success;
    public String sessionToken;
    public String zoneServerHost;
    public int zoneServerTcpPort;
    public int zoneServerUdpPort;
    public UUID playerId;
    public String failureReason;

    public LoginResponse() {}
}
```

`Heartbeat.java`:
```java
package com.galacticodyssey.common.protocol;

public class Heartbeat extends NetworkMessage {
    public long clientTimestamp;

    public Heartbeat() {}
}
```

`HeartbeatAck.java`:
```java
package com.galacticodyssey.common.protocol;

public class HeartbeatAck extends NetworkMessage {
    public long clientTimestamp;
    public long serverTimestamp;

    public HeartbeatAck() {}
}
```

`Disconnect.java`:
```java
package com.galacticodyssey.common.protocol;

public class Disconnect extends NetworkMessage {
    public enum Reason {
        CLIENT_QUIT, TIMEOUT, KICKED, SERVER_SHUTDOWN, ZONE_REDIRECT
    }

    public Reason reason;

    public Disconnect() {}
}
```

`PlayerInput.java`:
```java
package com.galacticodyssey.common.protocol;

public class PlayerInput {
    public int sequenceNumber;

    // FPS inputs
    public float moveForward;
    public float moveStrafe;
    public float mouseDeltaX;
    public float mouseDeltaY;
    public boolean jump;
    public boolean sprint;
    public boolean crouch;
    public boolean fire;
    public boolean ads;

    // Ship inputs
    public float throttle;
    public float pitchInput;
    public float yawInput;
    public float rollInput;
    public float strafe;
    public float verticalThrust;
    public boolean[] fireGroup;

    // Mode
    public int playerMode;

    public PlayerInput() {
        fireGroup = new boolean[4];
    }
}
```

`InputPacket.java`:
```java
package com.galacticodyssey.common.protocol;

public class InputPacket extends NetworkMessage {
    public PlayerInput[] inputs;
    public PlayerInput[] redundantInputs;

    public InputPacket() {}
}
```

`EntityStateUpdate.java`:
```java
package com.galacticodyssey.common.protocol;

public class EntityStateUpdate {
    public int networkId;
    public int serverTick;
    public long dirtyMask;
    public byte[] payload;

    public EntityStateUpdate() {}
}
```

`EntityBatchUpdate.java`:
```java
package com.galacticodyssey.common.protocol;

public class EntityBatchUpdate extends NetworkMessage {
    public int serverTick;
    public int lastProcessedInputSequence;
    public EntityStateUpdate[] updates;

    public EntityBatchUpdate() {}
}
```

`EntitySpawnMessage.java`:
```java
package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class EntitySpawnMessage extends NetworkMessage {
    public int networkId;
    public String entityType;
    public UUID persistenceId;
    public byte[] componentData;

    public EntitySpawnMessage() {}
}
```

`EntityDestroyMessage.java`:
```java
package com.galacticodyssey.common.protocol;

public class EntityDestroyMessage extends NetworkMessage {
    public int networkId;

    public EntityDestroyMessage() {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :common:test --tests "com.galacticodyssey.common.protocol.NetworkMessageTest" --info`
Expected: PASS — all 7 tests green

- [ ] **Step 5: Commit**

```
git add common/src/
git commit -m "feat(net): add network message protocol POJOs"
```

---

### Task 3: Kryo Registration for Network Messages

**Files:**
- Create: `common/src/main/java/com/galacticodyssey/common/serialization/NetworkKryoRegistrar.java`
- Test: `common/src/test/java/com/galacticodyssey/common/serialization/NetworkKryoRegistrarTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.common.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.galacticodyssey.common.protocol.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NetworkKryoRegistrarTest {

    private Kryo kryo;

    @BeforeEach
    void setUp() {
        kryo = new Kryo();
        NetworkKryoRegistrar.register(kryo);
    }

    @Test
    void roundTripLoginRequest() {
        LoginRequest original = new LoginRequest();
        original.username = "testplayer";
        original.clientVersion = "0.1.0";

        LoginRequest restored = roundTrip(original, LoginRequest.class);

        assertEquals("testplayer", restored.username);
        assertEquals("0.1.0", restored.clientVersion);
    }

    @Test
    void roundTripLoginResponse() {
        LoginResponse original = new LoginResponse();
        original.success = true;
        original.sessionToken = "token-abc";
        original.zoneServerHost = "localhost";
        original.zoneServerTcpPort = 7100;
        original.zoneServerUdpPort = 7101;
        original.playerId = UUID.randomUUID();

        LoginResponse restored = roundTrip(original, LoginResponse.class);

        assertTrue(restored.success);
        assertEquals("token-abc", restored.sessionToken);
        assertEquals(original.playerId, restored.playerId);
    }

    @Test
    void roundTripInputPacket() {
        InputPacket original = new InputPacket();
        original.inputs = new PlayerInput[3];
        for (int i = 0; i < 3; i++) {
            original.inputs[i] = new PlayerInput();
            original.inputs[i].sequenceNumber = 100 + i;
            original.inputs[i].moveForward = 0.5f * i;
        }
        original.redundantInputs = new PlayerInput[0];

        InputPacket restored = roundTrip(original, InputPacket.class);

        assertEquals(3, restored.inputs.length);
        assertEquals(101, restored.inputs[1].sequenceNumber);
        assertEquals(0.5f, restored.inputs[1].moveForward, 1e-5f);
    }

    @Test
    void roundTripEntityBatchUpdate() {
        EntityBatchUpdate original = new EntityBatchUpdate();
        original.serverTick = 500;
        original.lastProcessedInputSequence = 120;
        original.updates = new EntityStateUpdate[1];
        original.updates[0] = new EntityStateUpdate();
        original.updates[0].networkId = 42;
        original.updates[0].serverTick = 500;
        original.updates[0].dirtyMask = 0b101;
        original.updates[0].payload = new byte[]{10, 20, 30};

        EntityBatchUpdate restored = roundTrip(original, EntityBatchUpdate.class);

        assertEquals(500, restored.serverTick);
        assertEquals(120, restored.lastProcessedInputSequence);
        assertEquals(42, restored.updates[0].networkId);
        assertArrayEquals(new byte[]{10, 20, 30}, restored.updates[0].payload);
    }

    @Test
    void roundTripEntitySpawnMessage() {
        EntitySpawnMessage original = new EntitySpawnMessage();
        original.networkId = 1;
        original.entityType = "player";
        original.persistenceId = UUID.randomUUID();
        original.componentData = new byte[]{1, 2, 3, 4};

        EntitySpawnMessage restored = roundTrip(original, EntitySpawnMessage.class);

        assertEquals(1, restored.networkId);
        assertEquals("player", restored.entityType);
        assertEquals(original.persistenceId, restored.persistenceId);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, restored.componentData);
    }

    @Test
    void roundTripDisconnect() {
        Disconnect original = new Disconnect();
        original.reason = Disconnect.Reason.TIMEOUT;

        Disconnect restored = roundTrip(original, Disconnect.class);

        assertEquals(Disconnect.Reason.TIMEOUT, restored.reason);
    }

    private byte[] serialize(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Output output = new Output(baos)) {
            kryo.writeObject(output, obj);
        }
        return baos.toByteArray();
    }

    private <T> T deserialize(byte[] bytes, Class<T> type) {
        try (Input input = new Input(new ByteArrayInputStream(bytes))) {
            return kryo.readObject(input, type);
        }
    }

    private <T> T roundTrip(T obj, Class<T> type) {
        return deserialize(serialize(obj), type);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :common:test --tests "com.galacticodyssey.common.serialization.NetworkKryoRegistrarTest" --info`
Expected: FAIL — NetworkKryoRegistrar not found

- [ ] **Step 3: Implement NetworkKryoRegistrar**

```java
package com.galacticodyssey.common.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import com.galacticodyssey.common.protocol.*;

import java.util.UUID;

/**
 * Kryo type registration for network messages. IDs 110–149, appended to the
 * persistence range (10–109) defined in {@code KryoRegistrar}. Append only —
 * never reorder or reuse IDs.
 */
public final class NetworkKryoRegistrar {

    private NetworkKryoRegistrar() {}

    public static void register(Kryo kryo) {
        kryo.setDefaultSerializer(CompatibleFieldSerializer.class);
        kryo.setRegistrationRequired(true);

        // UUID needed if not already registered (standalone use without KryoRegistrar)
        if (kryo.getClassResolver().getRegistration(UUID.class) == null) {
            kryo.register(UUID.class, new DefaultSerializers.UUIDSerializer(), 10);
        }

        // --- Network protocol messages (110–149) ---
        kryo.register(LoginRequest.class, 110);
        kryo.register(LoginResponse.class, 111);
        kryo.register(Heartbeat.class, 112);
        kryo.register(HeartbeatAck.class, 113);
        kryo.register(Disconnect.class, 114);
        kryo.register(Disconnect.Reason.class, 115);
        kryo.register(PlayerInput.class, 116);
        kryo.register(PlayerInput[].class, 117);
        kryo.register(boolean[].class, 118);
        kryo.register(InputPacket.class, 119);
        kryo.register(EntityStateUpdate.class, 120);
        kryo.register(EntityStateUpdate[].class, 121);
        kryo.register(EntityBatchUpdate.class, 122);
        kryo.register(EntitySpawnMessage.class, 123);
        kryo.register(EntityDestroyMessage.class, 124);
        kryo.register(byte[].class, 125);
        kryo.register(String.class, 126);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :common:test --tests "com.galacticodyssey.common.serialization.NetworkKryoRegistrarTest" --info`
Expected: PASS — all 6 tests green

- [ ] **Step 5: Commit**

```
git add common/src/
git commit -m "feat(net): add NetworkKryoRegistrar for protocol message serialization"
```

---

### Task 4: NetworkIdComponent and AuthorityComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/networking/components/NetworkIdComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/networking/components/AuthorityComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/components/NetworkIdComponentTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkIdComponentTest {

    @Test
    void networkIdComponentStoresId() {
        NetworkIdComponent net = new NetworkIdComponent(42);
        assertEquals(42, net.networkId);
    }

    @Test
    void authorityComponentDefaultsToServer() {
        AuthorityComponent auth = new AuthorityComponent();
        assertEquals(AuthorityComponent.Owner.SERVER, auth.owner);
    }

    @Test
    void authorityComponentTracksZoneId() {
        AuthorityComponent auth = new AuthorityComponent();
        auth.owner = AuthorityComponent.Owner.ZONE_SERVER;
        auth.ownerZoneId = "zone-alpha";
        assertEquals("zone-alpha", auth.ownerZoneId);
    }

    @Test
    void ashleyFamilyMatchesNetworkedEntities() {
        Engine engine = new Engine();
        Entity e = new Entity();
        e.add(new NetworkIdComponent(1));
        e.add(new AuthorityComponent());
        engine.addEntity(e);

        var family = Family.all(NetworkIdComponent.class, AuthorityComponent.class).get();
        assertEquals(1, engine.getEntitiesFor(family).size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.components.NetworkIdComponentTest" --info`
Expected: FAIL — classes not found

- [ ] **Step 3: Implement NetworkIdComponent**

```java
package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Component;

public class NetworkIdComponent implements Component {
    public final int networkId;

    public NetworkIdComponent() {
        this.networkId = -1;
    }

    public NetworkIdComponent(int networkId) {
        this.networkId = networkId;
    }
}
```

- [ ] **Step 4: Implement AuthorityComponent**

```java
package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Component;

public class AuthorityComponent implements Component {
    public enum Owner {
        SERVER,
        ZONE_SERVER,
        LOCAL_PREDICTED
    }

    public Owner owner = Owner.SERVER;
    public String ownerZoneId;

    public AuthorityComponent() {}
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.components.NetworkIdComponentTest" --info`
Expected: PASS — all 4 tests green

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/networking/ core/src/test/java/com/galacticodyssey/networking/
git commit -m "feat(net): add NetworkIdComponent and AuthorityComponent"
```

---

### Task 5: DirtyTracker — Per-Component Bitfield Change Detection

**Files:**
- Create: `common/src/main/java/com/galacticodyssey/common/serialization/DirtyTracker.java`
- Test: `common/src/test/java/com/galacticodyssey/common/serialization/DirtyTrackerTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.common.serialization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirtyTrackerTest {

    @Test
    void initiallyAllClean() {
        DirtyTracker tracker = new DirtyTracker(8);
        assertEquals(0L, tracker.getDirtyMask());
        assertFalse(tracker.isDirty());
    }

    @Test
    void markDirtySetsCorrectBit() {
        DirtyTracker tracker = new DirtyTracker(8);
        tracker.markDirty(0);
        tracker.markDirty(3);
        assertEquals(0b1001L, tracker.getDirtyMask());
        assertTrue(tracker.isDirty());
    }

    @Test
    void clearResetsAllBits() {
        DirtyTracker tracker = new DirtyTracker(8);
        tracker.markDirty(0);
        tracker.markDirty(7);
        tracker.clear();
        assertEquals(0L, tracker.getDirtyMask());
        assertFalse(tracker.isDirty());
    }

    @Test
    void isBitDirtyChecksSpecificBit() {
        DirtyTracker tracker = new DirtyTracker(8);
        tracker.markDirty(5);
        assertTrue(tracker.isBitDirty(5));
        assertFalse(tracker.isBitDirty(4));
    }

    @Test
    void markAllDirtySetsAllBitsUpToFieldCount() {
        DirtyTracker tracker = new DirtyTracker(4);
        tracker.markAllDirty();
        assertEquals(0b1111L, tracker.getDirtyMask());
    }

    @Test
    void supports64Fields() {
        DirtyTracker tracker = new DirtyTracker(64);
        tracker.markDirty(63);
        assertTrue(tracker.isBitDirty(63));
        assertFalse(tracker.isBitDirty(62));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :common:test --tests "com.galacticodyssey.common.serialization.DirtyTrackerTest" --info`
Expected: FAIL — DirtyTracker not found

- [ ] **Step 3: Implement DirtyTracker**

```java
package com.galacticodyssey.common.serialization;

public class DirtyTracker {
    private final int fieldCount;
    private long mask;

    public DirtyTracker(int fieldCount) {
        if (fieldCount < 1 || fieldCount > 64) {
            throw new IllegalArgumentException("fieldCount must be 1–64, got " + fieldCount);
        }
        this.fieldCount = fieldCount;
    }

    public void markDirty(int fieldIndex) {
        mask |= (1L << fieldIndex);
    }

    public void markAllDirty() {
        mask = fieldCount == 64 ? -1L : (1L << fieldCount) - 1;
    }

    public void clear() {
        mask = 0L;
    }

    public boolean isDirty() {
        return mask != 0L;
    }

    public boolean isBitDirty(int fieldIndex) {
        return (mask & (1L << fieldIndex)) != 0;
    }

    public long getDirtyMask() {
        return mask;
    }

    public int getFieldCount() {
        return fieldCount;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :common:test --tests "com.galacticodyssey.common.serialization.DirtyTrackerTest" --info`
Expected: PASS — all 6 tests green

- [ ] **Step 5: Commit**

```
git add common/src/
git commit -m "feat(net): add DirtyTracker for per-component change detection"
```

---

### Task 6: InterestManager — Distance-Based Entity Tier Assignment

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/replication/InterestTier.java`
- Create: `server/src/main/java/com/galacticodyssey/server/replication/InterestManager.java`
- Test: `server/src/test/java/com/galacticodyssey/server/replication/InterestManagerTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.server.replication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterestManagerTest {

    @Test
    void entityAtOriginIsNear() {
        InterestManager manager = new InterestManager();
        InterestTier tier = manager.computeTier(0, 0, 0, 100, 0, 0);
        assertEquals(InterestTier.NEAR, tier);
    }

    @Test
    void entityAt1000mIsMid() {
        InterestManager manager = new InterestManager();
        InterestTier tier = manager.computeTier(0, 0, 0, 1000, 0, 0);
        assertEquals(InterestTier.MID, tier);
    }

    @Test
    void entityAt5000mIsFar() {
        InterestManager manager = new InterestManager();
        InterestTier tier = manager.computeTier(0, 0, 0, 5000, 0, 0);
        assertEquals(InterestTier.FAR, tier);
    }

    @Test
    void entityBeyond10kmIsNone() {
        InterestManager manager = new InterestManager();
        InterestTier tier = manager.computeTier(0, 0, 0, 15000, 0, 0);
        assertEquals(InterestTier.NONE, tier);
    }

    @Test
    void tierBoundariesAreInclusive() {
        InterestManager manager = new InterestManager();
        assertEquals(InterestTier.NEAR, manager.computeTier(0, 0, 0, 500, 0, 0));
        assertEquals(InterestTier.MID, manager.computeTier(0, 0, 0, 500.01, 0, 0));
        assertEquals(InterestTier.MID, manager.computeTier(0, 0, 0, 2000, 0, 0));
        assertEquals(InterestTier.FAR, manager.computeTier(0, 0, 0, 2000.01, 0, 0));
        assertEquals(InterestTier.FAR, manager.computeTier(0, 0, 0, 10000, 0, 0));
        assertEquals(InterestTier.NONE, manager.computeTier(0, 0, 0, 10000.01, 0, 0));
    }

    @Test
    void shouldSendThisTickNearEveryTick() {
        InterestManager manager = new InterestManager();
        for (int tick = 0; tick < 20; tick++) {
            assertTrue(manager.shouldSendThisTick(InterestTier.NEAR, tick));
        }
    }

    @Test
    void shouldSendThisTickMidEvery4th() {
        InterestManager manager = new InterestManager();
        assertTrue(manager.shouldSendThisTick(InterestTier.MID, 0));
        assertFalse(manager.shouldSendThisTick(InterestTier.MID, 1));
        assertFalse(manager.shouldSendThisTick(InterestTier.MID, 2));
        assertFalse(manager.shouldSendThisTick(InterestTier.MID, 3));
        assertTrue(manager.shouldSendThisTick(InterestTier.MID, 4));
    }

    @Test
    void shouldSendThisTickFarEvery10th() {
        InterestManager manager = new InterestManager();
        assertTrue(manager.shouldSendThisTick(InterestTier.FAR, 0));
        assertFalse(manager.shouldSendThisTick(InterestTier.FAR, 1));
        assertTrue(manager.shouldSendThisTick(InterestTier.FAR, 10));
    }

    @Test
    void shouldNeverSendForNone() {
        InterestManager manager = new InterestManager();
        for (int tick = 0; tick < 100; tick++) {
            assertFalse(manager.shouldSendThisTick(InterestTier.NONE, tick));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.replication.InterestManagerTest" --info`
Expected: FAIL — classes not found

- [ ] **Step 3: Implement InterestTier enum**

```java
package com.galacticodyssey.server.replication;

public enum InterestTier {
    NEAR,
    MID,
    FAR,
    NONE
}
```

- [ ] **Step 4: Implement InterestManager**

```java
package com.galacticodyssey.server.replication;

public class InterestManager {

    private static final double NEAR_RADIUS = 500.0;
    private static final double MID_RADIUS = 2000.0;
    private static final double FAR_RADIUS = 10000.0;

    private static final double NEAR_SQ = NEAR_RADIUS * NEAR_RADIUS;
    private static final double MID_SQ = MID_RADIUS * MID_RADIUS;
    private static final double FAR_SQ = FAR_RADIUS * FAR_RADIUS;

    public InterestTier computeTier(double playerX, double playerY, double playerZ,
                                     double entityX, double entityY, double entityZ) {
        double dx = entityX - playerX;
        double dy = entityY - playerY;
        double dz = entityZ - playerZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq <= NEAR_SQ) return InterestTier.NEAR;
        if (distSq <= MID_SQ) return InterestTier.MID;
        if (distSq <= FAR_SQ) return InterestTier.FAR;
        return InterestTier.NONE;
    }

    public boolean shouldSendThisTick(InterestTier tier, int currentTick) {
        return switch (tier) {
            case NEAR -> true;
            case MID -> currentTick % 4 == 0;
            case FAR -> currentTick % 10 == 0;
            case NONE -> false;
        };
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.replication.InterestManagerTest" --info`
Expected: PASS — all 9 tests green

- [ ] **Step 6: Commit**

```
git add server/src/
git commit -m "feat(net): add InterestManager with NEAR/MID/FAR tier logic"
```

---

### Task 7: ReplicationState — Per-Client Per-Entity Tracking

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/replication/ReplicationState.java`
- Test: `server/src/test/java/com/galacticodyssey/server/replication/ReplicationStateTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.server.replication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplicationStateTest {

    @Test
    void newStateIsUninitialized() {
        ReplicationState state = new ReplicationState(42);
        assertEquals(42, state.getNetworkId());
        assertFalse(state.hasBeenSent());
        assertEquals(InterestTier.NONE, state.getCurrentTier());
    }

    @Test
    void markSentUpdatesState() {
        ReplicationState state = new ReplicationState(1);
        state.setCurrentTier(InterestTier.NEAR);
        state.markSent(100);
        assertTrue(state.hasBeenSent());
        assertEquals(100, state.getLastSentTick());
    }

    @Test
    void tierChangeDetection() {
        ReplicationState state = new ReplicationState(1);
        state.setCurrentTier(InterestTier.NEAR);
        assertFalse(state.tierChangedFrom(InterestTier.NEAR));
        assertTrue(state.tierChangedFrom(InterestTier.MID));
    }

    @Test
    void lastSentSnapshotStorage() {
        ReplicationState state = new ReplicationState(1);
        byte[] snapshot = new byte[]{1, 2, 3};
        state.setLastSentSnapshot(snapshot);
        assertArrayEquals(new byte[]{1, 2, 3}, state.getLastSentSnapshot());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.replication.ReplicationStateTest" --info`
Expected: FAIL — ReplicationState not found

- [ ] **Step 3: Implement ReplicationState**

```java
package com.galacticodyssey.server.replication;

public class ReplicationState {
    private final int networkId;
    private InterestTier currentTier = InterestTier.NONE;
    private int lastSentTick = -1;
    private byte[] lastSentSnapshot;

    public ReplicationState(int networkId) {
        this.networkId = networkId;
    }

    public int getNetworkId() {
        return networkId;
    }

    public InterestTier getCurrentTier() {
        return currentTier;
    }

    public void setCurrentTier(InterestTier tier) {
        this.currentTier = tier;
    }

    public boolean hasBeenSent() {
        return lastSentTick >= 0;
    }

    public int getLastSentTick() {
        return lastSentTick;
    }

    public void markSent(int tick) {
        this.lastSentTick = tick;
    }

    public boolean tierChangedFrom(InterestTier previousTier) {
        return currentTier != previousTier;
    }

    public byte[] getLastSentSnapshot() {
        return lastSentSnapshot;
    }

    public void setLastSentSnapshot(byte[] snapshot) {
        this.lastSentSnapshot = snapshot;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.replication.ReplicationStateTest" --info`
Expected: PASS — all 4 tests green

- [ ] **Step 5: Commit**

```
git add server/src/
git commit -m "feat(net): add ReplicationState for per-client per-entity tracking"
```

---

### Task 8: PlayerSession — Server-Side Connection Tracking

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/network/PlayerSession.java`
- Test: `server/src/test/java/com/galacticodyssey/server/network/PlayerSessionTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.server.network;

import com.galacticodyssey.common.protocol.PlayerInput;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSessionTest {

    @Test
    void sessionStoresPlayerInfo() {
        UUID playerId = UUID.randomUUID();
        PlayerSession session = new PlayerSession(7, playerId, "token-abc");

        assertEquals(7, session.getConnectionId());
        assertEquals(playerId, session.getPlayerId());
        assertEquals("token-abc", session.getSessionToken());
    }

    @Test
    void inputQueueDrainsInOrder() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");

        PlayerInput i1 = new PlayerInput();
        i1.sequenceNumber = 1;
        PlayerInput i2 = new PlayerInput();
        i2.sequenceNumber = 2;

        session.enqueueInput(i1);
        session.enqueueInput(i2);

        var drained = session.drainInputs();
        assertEquals(2, drained.size());
        assertEquals(1, drained.get(0).sequenceNumber);
        assertEquals(2, drained.get(1).sequenceNumber);
    }

    @Test
    void drainInputsClearsQueue() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.enqueueInput(new PlayerInput());
        session.drainInputs();

        var second = session.drainInputs();
        assertTrue(second.isEmpty());
    }

    @Test
    void lastProcessedInputSequenceTracking() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        assertEquals(-1, session.getLastProcessedInputSequence());

        session.setLastProcessedInputSequence(50);
        assertEquals(50, session.getLastProcessedInputSequence());
    }

    @Test
    void networkIdAssignment() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        assertEquals(-1, session.getPlayerNetworkId());

        session.setPlayerNetworkId(10);
        assertEquals(10, session.getPlayerNetworkId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.network.PlayerSessionTest" --info`
Expected: FAIL — PlayerSession not found

- [ ] **Step 3: Implement PlayerSession**

```java
package com.galacticodyssey.server.network;

import com.galacticodyssey.common.protocol.PlayerInput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PlayerSession {
    private final int connectionId;
    private final UUID playerId;
    private final String sessionToken;
    private final ConcurrentLinkedQueue<PlayerInput> inputQueue = new ConcurrentLinkedQueue<>();

    private int lastProcessedInputSequence = -1;
    private int playerNetworkId = -1;
    private double galaxyX, galaxyY, galaxyZ;

    public PlayerSession(int connectionId, UUID playerId, String sessionToken) {
        this.connectionId = connectionId;
        this.playerId = playerId;
        this.sessionToken = sessionToken;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void enqueueInput(PlayerInput input) {
        inputQueue.add(input);
    }

    public List<PlayerInput> drainInputs() {
        List<PlayerInput> result = new ArrayList<>();
        PlayerInput input;
        while ((input = inputQueue.poll()) != null) {
            result.add(input);
        }
        return result;
    }

    public int getLastProcessedInputSequence() {
        return lastProcessedInputSequence;
    }

    public void setLastProcessedInputSequence(int seq) {
        this.lastProcessedInputSequence = seq;
    }

    public int getPlayerNetworkId() {
        return playerNetworkId;
    }

    public void setPlayerNetworkId(int id) {
        this.playerNetworkId = id;
    }

    public void setGalaxyPosition(double x, double y, double z) {
        this.galaxyX = x;
        this.galaxyY = y;
        this.galaxyZ = z;
    }

    public double getGalaxyX() { return galaxyX; }
    public double getGalaxyY() { return galaxyY; }
    public double getGalaxyZ() { return galaxyZ; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.network.PlayerSessionTest" --info`
Expected: PASS — all 5 tests green

- [ ] **Step 5: Commit**

```
git add server/src/
git commit -m "feat(net): add PlayerSession with input queue and connection tracking"
```

---

### Task 9: ServerNetworkListener — KryoNet Message Handler

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/network/ServerNetworkListener.java`
- Test: `server/src/test/java/com/galacticodyssey/server/network/ServerNetworkListenerTest.java`

- [ ] **Step 1: Write the test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.network.ServerNetworkListenerTest" --info`
Expected: FAIL — ServerNetworkListener not found

- [ ] **Step 3: Implement ServerNetworkListener**

```java
package com.galacticodyssey.server.network;

import com.galacticodyssey.common.protocol.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ServerNetworkListener {
    private final Consumer<Runnable> mainThreadPoster;
    private final Map<Integer, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final Map<Integer, LoginResponse> pendingResponses = new ConcurrentHashMap<>();

    public ServerNetworkListener(Consumer<Runnable> mainThreadPoster) {
        this.mainThreadPoster = mainThreadPoster;
    }

    public void simulateConnected(int connectionId) {
        // Connection established but no session yet — waiting for LoginRequest
    }

    public void simulateReceived(int connectionId, Object message) {
        if (message instanceof LoginRequest login) {
            mainThreadPoster.accept(() -> handleLogin(connectionId, login));
        } else if (message instanceof InputPacket packet) {
            mainThreadPoster.accept(() -> handleInput(connectionId, packet));
        } else if (message instanceof Heartbeat hb) {
            mainThreadPoster.accept(() -> handleHeartbeat(connectionId, hb));
        }
    }

    public void simulateDisconnected(int connectionId) {
        mainThreadPoster.accept(() -> sessions.remove(connectionId));
    }

    private void handleLogin(int connectionId, LoginRequest request) {
        UUID playerId = UUID.nameUUIDFromBytes(request.username.getBytes());
        String token = UUID.randomUUID().toString();
        PlayerSession session = new PlayerSession(connectionId, playerId, token);
        sessions.put(connectionId, session);
    }

    private void handleInput(int connectionId, InputPacket packet) {
        PlayerSession session = sessions.get(connectionId);
        if (session == null) return;

        if (packet.inputs != null) {
            for (PlayerInput input : packet.inputs) {
                if (input != null) {
                    session.enqueueInput(input);
                }
            }
        }
    }

    private void handleHeartbeat(int connectionId, Heartbeat hb) {
        // Will be used for RTT estimation — placeholder for now
    }

    public PlayerSession getSession(int connectionId) {
        return sessions.get(connectionId);
    }

    public Collection<PlayerSession> getAllSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public void removeSession(int connectionId) {
        sessions.remove(connectionId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.network.ServerNetworkListenerTest" --info`
Expected: PASS — all 4 tests green

- [ ] **Step 5: Commit**

```
git add server/src/
git commit -m "feat(net): add ServerNetworkListener with login/input/disconnect handling"
```

---

### Task 10: ServerReplicationSystem — Build and Send Entity Updates

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/replication/ServerReplicationSystem.java`
- Test: `server/src/test/java/com/galacticodyssey/server/replication/ServerReplicationSystemTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.server.replication;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.common.protocol.EntityBatchUpdate;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.server.network.PlayerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ServerReplicationSystemTest {

    private Engine engine;
    private ServerReplicationSystem replicationSystem;
    private List<ServerReplicationSystem.SentPacket> sentPackets;

    @BeforeEach
    void setUp() {
        sentPackets = new ArrayList<>();
        replicationSystem = new ServerReplicationSystem(sentPackets::add);
        engine = new Engine();
        engine.addSystem(replicationSystem);
    }

    @Test
    void sendsSpawnMessageWhenEntityEntersInterest() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setPlayerNetworkId(100);
        session.setGalaxyPosition(0, 0, 0);
        replicationSystem.addSession(session);

        Entity e = new Entity();
        e.add(new NetworkIdComponent(1));
        TransformComponent tc = new TransformComponent();
        tc.position.set(100, 0, 0);
        e.add(tc);
        e.add(new HealthComponent());
        engine.addEntity(e);

        replicationSystem.setServerTick(0);
        replicationSystem.setOriginOffset(0, 0, 0);
        engine.update(0.05f);

        boolean hasSpawn = sentPackets.stream()
            .anyMatch(p -> p.message() instanceof EntitySpawnMessage);
        assertTrue(hasSpawn, "Expected EntitySpawnMessage for new entity in interest");
    }

    @Test
    void sendsDestroyMessageWhenEntityLeavesInterest() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setPlayerNetworkId(100);
        session.setGalaxyPosition(0, 0, 0);
        replicationSystem.addSession(session);

        Entity e = new Entity();
        e.add(new NetworkIdComponent(1));
        TransformComponent tc = new TransformComponent();
        tc.position.set(100, 0, 0);
        e.add(tc);
        engine.addEntity(e);

        replicationSystem.setServerTick(0);
        replicationSystem.setOriginOffset(0, 0, 0);
        engine.update(0.05f);
        sentPackets.clear();

        tc.position.set(15000, 0, 0);
        replicationSystem.setServerTick(1);
        engine.update(0.05f);

        boolean hasDestroy = sentPackets.stream()
            .anyMatch(p -> p.message() instanceof EntityDestroyMessage);
        assertTrue(hasDestroy, "Expected EntityDestroyMessage for entity leaving interest");
    }

    @Test
    void doesNotReplicatePlayerEntityToItself() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setPlayerNetworkId(5);
        session.setGalaxyPosition(0, 0, 0);
        replicationSystem.addSession(session);

        Entity playerEntity = new Entity();
        playerEntity.add(new NetworkIdComponent(5));
        TransformComponent tc = new TransformComponent();
        tc.position.set(0, 0, 0);
        playerEntity.add(tc);
        engine.addEntity(playerEntity);

        replicationSystem.setServerTick(0);
        replicationSystem.setOriginOffset(0, 0, 0);
        engine.update(0.05f);

        boolean sentToSelf = sentPackets.stream()
            .filter(p -> p.connectionId() == 1)
            .anyMatch(p -> {
                if (p.message() instanceof EntitySpawnMessage spawn) {
                    return spawn.networkId == 5;
                }
                return false;
            });
        assertFalse(sentToSelf, "Should not replicate player entity to itself");
    }

    @Test
    void sendsBatchUpdateForKnownEntities() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setPlayerNetworkId(100);
        session.setGalaxyPosition(0, 0, 0);
        session.setLastProcessedInputSequence(10);
        replicationSystem.addSession(session);

        Entity e = new Entity();
        e.add(new NetworkIdComponent(1));
        TransformComponent tc = new TransformComponent();
        tc.position.set(100, 0, 0);
        e.add(tc);
        engine.addEntity(e);

        replicationSystem.setServerTick(0);
        replicationSystem.setOriginOffset(0, 0, 0);
        engine.update(0.05f);
        sentPackets.clear();

        tc.position.set(110, 0, 0);
        replicationSystem.setServerTick(1);
        engine.update(0.05f);

        boolean hasBatch = sentPackets.stream()
            .anyMatch(p -> p.message() instanceof EntityBatchUpdate);
        assertTrue(hasBatch, "Expected EntityBatchUpdate for existing entity");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.replication.ServerReplicationSystemTest" --info`
Expected: FAIL — ServerReplicationSystem not found

- [ ] **Step 3: Implement ServerReplicationSystem**

```java
package com.galacticodyssey.server.replication;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.common.protocol.*;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.server.network.PlayerSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ServerReplicationSystem extends EntitySystem {

    @FunctionalInterface
    public interface PacketSender {
        void accept(SentPacket packet);
    }

    public record SentPacket(int connectionId, Object message) {}

    private final PacketSender packetSender;
    private final InterestManager interestManager = new InterestManager();
    private final Map<Integer, PlayerSession> sessions = new ConcurrentHashMap<>();

    // connectionId -> (networkId -> ReplicationState)
    private final Map<Integer, Map<Integer, ReplicationState>> clientStates = new HashMap<>();

    private final ComponentMapper<NetworkIdComponent> netMapper =
        ComponentMapper.getFor(NetworkIdComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private ImmutableArray<Entity> networkedEntities;

    private int serverTick;
    private double originOffsetX, originOffsetY, originOffsetZ;

    public ServerReplicationSystem(Consumer<SentPacket> sender) {
        super(50);
        this.packetSender = sender::accept;
    }

    public ServerReplicationSystem(PacketSender sender) {
        super(50);
        this.packetSender = sender;
    }

    public void addSession(PlayerSession session) {
        sessions.put(session.getConnectionId(), session);
        clientStates.put(session.getConnectionId(), new HashMap<>());
    }

    public void removeSession(int connectionId) {
        sessions.remove(connectionId);
        clientStates.remove(connectionId);
    }

    public void setServerTick(int tick) {
        this.serverTick = tick;
    }

    public void setOriginOffset(double x, double y, double z) {
        this.originOffsetX = x;
        this.originOffsetY = y;
        this.originOffsetZ = z;
    }

    @Override
    public void addedToEngine(Engine engine) {
        networkedEntities = engine.getEntitiesFor(
            Family.all(NetworkIdComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (PlayerSession session : sessions.values()) {
            int connId = session.getConnectionId();
            Map<Integer, ReplicationState> states = clientStates.get(connId);
            if (states == null) continue;

            double px = session.getGalaxyX();
            double py = session.getGalaxyY();
            double pz = session.getGalaxyZ();

            Set<Integer> seenThisTick = new HashSet<>();
            List<EntityStateUpdate> updates = new ArrayList<>();

            for (int i = 0; i < networkedEntities.size(); i++) {
                Entity entity = networkedEntities.get(i);
                NetworkIdComponent netId = netMapper.get(entity);
                TransformComponent transform = transformMapper.get(entity);

                // Don't replicate player to itself
                if (netId.networkId == session.getPlayerNetworkId()) continue;

                double ex = transform.position.x + originOffsetX;
                double ey = transform.position.y + originOffsetY;
                double ez = transform.position.z + originOffsetZ;

                InterestTier tier = interestManager.computeTier(px, py, pz, ex, ey, ez);
                seenThisTick.add(netId.networkId);

                ReplicationState repState = states.get(netId.networkId);

                if (tier == InterestTier.NONE) {
                    if (repState != null) {
                        // Entity left interest — send destroy
                        EntityDestroyMessage destroy = new EntityDestroyMessage();
                        destroy.networkId = netId.networkId;
                        packetSender.accept(new SentPacket(connId, destroy));
                        states.remove(netId.networkId);
                    }
                    continue;
                }

                if (repState == null) {
                    // Entity entered interest — send spawn
                    repState = new ReplicationState(netId.networkId);
                    repState.setCurrentTier(tier);
                    states.put(netId.networkId, repState);

                    EntitySpawnMessage spawn = new EntitySpawnMessage();
                    spawn.networkId = netId.networkId;
                    spawn.entityType = "entity";
                    spawn.componentData = new byte[0];
                    packetSender.accept(new SentPacket(connId, spawn));
                    repState.markSent(serverTick);
                    continue;
                }

                repState.setCurrentTier(tier);

                if (interestManager.shouldSendThisTick(tier, serverTick)) {
                    EntityStateUpdate update = new EntityStateUpdate();
                    update.networkId = netId.networkId;
                    update.serverTick = serverTick;
                    update.dirtyMask = 0b1;
                    update.payload = new byte[0];
                    updates.add(update);
                    repState.markSent(serverTick);
                }
            }

            // Check for entities that disappeared from the engine
            Set<Integer> toRemove = new HashSet<>();
            for (int networkId : states.keySet()) {
                if (!seenThisTick.contains(networkId)) {
                    EntityDestroyMessage destroy = new EntityDestroyMessage();
                    destroy.networkId = networkId;
                    packetSender.accept(new SentPacket(connId, destroy));
                    toRemove.add(networkId);
                }
            }
            toRemove.forEach(states::remove);

            // Send batch update
            if (!updates.isEmpty()) {
                EntityBatchUpdate batch = new EntityBatchUpdate();
                batch.serverTick = serverTick;
                batch.lastProcessedInputSequence = session.getLastProcessedInputSequence();
                batch.updates = updates.toArray(new EntityStateUpdate[0]);
                packetSender.accept(new SentPacket(connId, batch));
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.replication.ServerReplicationSystemTest" --info`
Expected: PASS — all 4 tests green

- [ ] **Step 5: Commit**

```
git add server/src/
git commit -m "feat(net): add ServerReplicationSystem with interest-based entity replication"
```

---

### Task 11: DedicatedServer — Headless Server With 20Hz Tick Loop

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/DedicatedServer.java`
- Modify: `server/src/main/java/com/galacticodyssey/server/ServerLauncher.java`
- Test: `server/src/test/java/com/galacticodyssey/server/DedicatedServerTest.java`

- [ ] **Step 1: Write the test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.DedicatedServerTest" --info`
Expected: FAIL — DedicatedServer not found

- [ ] **Step 3: Implement DedicatedServer**

```java
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

public class DedicatedServer extends ApplicationAdapter {

    public static class Config {
        public int tcpPort = 7100;
        public int udpPort = 7101;
        public int tickRate = 20;

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

    public DedicatedServer(Config config) {
        this.config = config;
    }

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

        // Network listener
        networkListener = new ServerNetworkListener(runnable ->
            com.badlogic.gdx.Gdx.app.postRunnable(runnable));

        // Start KryoNet server
        kryoServer = new Server(131072, 16384);
        KryoRegistrar.register(kryoServer.getKryo());
        NetworkKryoRegistrar.register(kryoServer.getKryo());

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
                networkListener.simulateDisconnected(connection.getID());
                replicationSystem.removeSession(connection.getID());
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

    @Override
    public void render() {
        float tickInterval = config.getTickInterval();

        // Process inputs from network listener into ECS
        for (PlayerSession session : networkListener.getAllSessions()) {
            List<PlayerInput> inputs = session.drainInputs();
            for (PlayerInput input : inputs) {
                session.setLastProcessedInputSequence(input.sequenceNumber);
            }
        }

        // Step simulation
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

    public Engine getEngine() {
        return engine;
    }

    public int getCurrentTick() {
        return currentTick;
    }
}
```

- [ ] **Step 4: Update ServerLauncher to use DedicatedServer**

Replace the contents of `server/src/main/java/com/galacticodyssey/server/ServerLauncher.java`:

```java
package com.galacticodyssey.server;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

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
```

- [ ] **Step 5: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.DedicatedServerTest" --info`
Expected: PASS — all 3 tests green

- [ ] **Step 6: Verify full build compiles**

Run: `gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```
git add server/src/
git commit -m "feat(net): add DedicatedServer with 20Hz tick loop and KryoNet transport"
```

---

### Task 12: ClientNetworkSystem — Client-Side Connection and Message Handling

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/networking/systems/ClientNetworkSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/systems/ClientNetworkSystemTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.networking.systems;

import com.galacticodyssey.common.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientNetworkSystemTest {

    @Test
    void connectionStateStartsDisconnected() {
        ClientNetworkSystem system = new ClientNetworkSystem();
        assertEquals(ClientNetworkSystem.ConnectionState.DISCONNECTED, system.getConnectionState());
    }

    @Test
    void processLoginResponseUpdatesState() {
        ClientNetworkSystem system = new ClientNetworkSystem();

        LoginResponse response = new LoginResponse();
        response.success = true;
        response.sessionToken = "tok-123";
        response.playerId = UUID.randomUUID();
        system.handleLoginResponse(response);

        assertEquals(ClientNetworkSystem.ConnectionState.CONNECTED, system.getConnectionState());
        assertEquals("tok-123", system.getSessionToken());
    }

    @Test
    void processFailedLoginStaysDisconnected() {
        ClientNetworkSystem system = new ClientNetworkSystem();

        LoginResponse response = new LoginResponse();
        response.success = false;
        response.failureReason = "Invalid version";
        system.handleLoginResponse(response);

        assertEquals(ClientNetworkSystem.ConnectionState.DISCONNECTED, system.getConnectionState());
    }

    @Test
    void entityBatchUpdateIsQueued() {
        ClientNetworkSystem system = new ClientNetworkSystem();

        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 10;
        batch.lastProcessedInputSequence = 5;
        batch.updates = new EntityStateUpdate[0];
        system.handleEntityBatchUpdate(batch);

        var queued = system.drainBatchUpdates();
        assertEquals(1, queued.size());
        assertEquals(10, queued.get(0).serverTick);
    }

    @Test
    void entitySpawnMessageIsQueued() {
        ClientNetworkSystem system = new ClientNetworkSystem();

        EntitySpawnMessage spawn = new EntitySpawnMessage();
        spawn.networkId = 42;
        spawn.entityType = "ship";
        system.handleEntitySpawn(spawn);

        var queued = system.drainSpawnMessages();
        assertEquals(1, queued.size());
        assertEquals(42, queued.get(0).networkId);
    }

    @Test
    void entityDestroyMessageIsQueued() {
        ClientNetworkSystem system = new ClientNetworkSystem();

        EntityDestroyMessage destroy = new EntityDestroyMessage();
        destroy.networkId = 42;
        system.handleEntityDestroy(destroy);

        var queued = system.drainDestroyMessages();
        assertEquals(1, queued.size());
        assertEquals(42, queued.get(0).networkId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.systems.ClientNetworkSystemTest" --info`
Expected: FAIL — ClientNetworkSystem not found

- [ ] **Step 3: Implement ClientNetworkSystem**

```java
package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.common.protocol.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

    private final ConcurrentLinkedQueue<EntityBatchUpdate> batchQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<EntitySpawnMessage> spawnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<EntityDestroyMessage> destroyQueue = new ConcurrentLinkedQueue<>();

    public ClientNetworkSystem() {
        super(60);
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
        // Process queued messages into ECS — will be expanded in Part 2 (prediction/interpolation)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.systems.ClientNetworkSystemTest" --info`
Expected: PASS — all 6 tests green

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/networking/systems/ core/src/test/java/com/galacticodyssey/networking/systems/
git commit -m "feat(net): add ClientNetworkSystem for receiving server state updates"
```

---

### Task 13: Integration Test — Full Client-Server Round Trip

**Files:**
- Test: `server/src/test/java/com/galacticodyssey/server/NetworkIntegrationTest.java`

This test verifies the complete flow: Kryo registration, message serialization, login, entity spawn, and batch update delivery — all in-process without launching KryoNet servers.

- [ ] **Step 1: Write the integration test**

```java
package com.galacticodyssey.server;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.common.protocol.*;
import com.galacticodyssey.common.serialization.NetworkKryoRegistrar;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.NetworkIdComponent;
import com.galacticodyssey.networking.systems.ClientNetworkSystem;
import com.galacticodyssey.persistence.KryoRegistrar;
import com.galacticodyssey.server.network.PlayerSession;
import com.galacticodyssey.server.replication.ServerReplicationSystem;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NetworkIntegrationTest {

    private Engine serverEngine;
    private ServerReplicationSystem replicationSystem;
    private ClientNetworkSystem clientSystem;
    private Kryo kryo;
    private List<ServerReplicationSystem.SentPacket> serverPackets;

    @BeforeEach
    void setUp() {
        serverPackets = new ArrayList<>();
        replicationSystem = new ServerReplicationSystem(serverPackets::add);
        serverEngine = new Engine();
        serverEngine.addSystem(replicationSystem);

        clientSystem = new ClientNetworkSystem();

        kryo = new Kryo();
        KryoRegistrar.register(kryo);
        NetworkKryoRegistrar.register(kryo);
    }

    @Test
    void fullRoundTrip_loginInputSpawnUpdate() {
        // 1. Client sends LoginRequest
        LoginRequest loginReq = new LoginRequest();
        loginReq.username = "testplayer";
        loginReq.clientVersion = "0.1.0";

        // Serialize and deserialize (simulates network transport)
        LoginRequest receivedLogin = roundTrip(loginReq, LoginRequest.class);
        assertEquals("testplayer", receivedLogin.username);

        // 2. Server creates session
        UUID playerId = UUID.randomUUID();
        PlayerSession session = new PlayerSession(1, playerId, "session-token");
        session.setPlayerNetworkId(100);
        session.setGalaxyPosition(0, 0, 0);
        replicationSystem.addSession(session);

        // Server sends LoginResponse
        LoginResponse resp = new LoginResponse();
        resp.success = true;
        resp.sessionToken = "session-token";
        resp.playerId = playerId;

        LoginResponse receivedResp = roundTrip(resp, LoginResponse.class);
        clientSystem.handleLoginResponse(receivedResp);
        assertEquals(ClientNetworkSystem.ConnectionState.CONNECTED, clientSystem.getConnectionState());

        // 3. Create a world entity on the server
        Entity npc = new Entity();
        npc.add(new NetworkIdComponent(1));
        TransformComponent tc = new TransformComponent();
        tc.position.set(200, 0, 0);
        npc.add(tc);
        npc.add(new HealthComponent());
        serverEngine.addEntity(npc);

        // 4. Server ticks — should produce EntitySpawnMessage
        replicationSystem.setServerTick(0);
        replicationSystem.setOriginOffset(0, 0, 0);
        serverEngine.update(0.05f);

        boolean foundSpawn = false;
        for (var packet : serverPackets) {
            if (packet.message() instanceof EntitySpawnMessage spawn) {
                EntitySpawnMessage receivedSpawn = roundTrip(spawn, EntitySpawnMessage.class);
                clientSystem.handleEntitySpawn(receivedSpawn);
                foundSpawn = true;
            }
        }
        assertTrue(foundSpawn, "Server should have sent spawn");
        assertEquals(1, clientSystem.drainSpawnMessages().size());

        // 5. Server ticks again — should produce EntityBatchUpdate
        serverPackets.clear();
        tc.position.set(210, 0, 0);
        replicationSystem.setServerTick(1);
        serverEngine.update(0.05f);

        for (var packet : serverPackets) {
            if (packet.message() instanceof EntityBatchUpdate batch) {
                EntityBatchUpdate receivedBatch = roundTrip(batch, EntityBatchUpdate.class);
                clientSystem.handleEntityBatchUpdate(receivedBatch);
            }
        }
        assertEquals(1, clientSystem.drainBatchUpdates().size());

        // 6. Client sends InputPacket
        InputPacket inputPkt = new InputPacket();
        inputPkt.inputs = new PlayerInput[3];
        for (int i = 0; i < 3; i++) {
            inputPkt.inputs[i] = new PlayerInput();
            inputPkt.inputs[i].sequenceNumber = i;
            inputPkt.inputs[i].moveForward = 1.0f;
        }
        inputPkt.redundantInputs = new PlayerInput[0];

        InputPacket receivedInput = roundTrip(inputPkt, InputPacket.class);
        assertEquals(3, receivedInput.inputs.length);
        assertEquals(1.0f, receivedInput.inputs[0].moveForward, 1e-5f);
    }

    private byte[] serialize(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Output output = new Output(baos)) {
            kryo.writeObject(output, obj);
        }
        return baos.toByteArray();
    }

    private <T> T deserialize(byte[] bytes, Class<T> type) {
        try (Input input = new Input(new ByteArrayInputStream(bytes))) {
            return kryo.readObject(input, type);
        }
    }

    private <T> T roundTrip(T obj, Class<T> type) {
        return deserialize(serialize(obj), type);
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.NetworkIntegrationTest" --info`
Expected: PASS — integration test green

- [ ] **Step 3: Run all tests to check for regressions**

Run: `gradlew.bat test`
Expected: BUILD SUCCESSFUL — all existing tests still pass, all new tests pass

- [ ] **Step 4: Commit**

```
git add server/src/test/
git commit -m "test(net): add integration test for full client-server message round trip"
```

---

### Task 14: Verify Full Build and Run All Tests

- [ ] **Step 1: Full clean build**

Run: `gradlew.bat clean compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests across all modules**

Run: `gradlew.bat test`
Expected: BUILD SUCCESSFUL — no regressions

- [ ] **Step 3: Verify module dependency graph**

Run: `gradlew.bat :core:dependencies --configuration compileClasspath`
Expected: shows `+--- project :common` with kryonet transitive dependency

Run: `gradlew.bat :server:dependencies --configuration compileClasspath`
Expected: shows both `+--- project :core` and transitive `+--- project :common`

- [ ] **Step 4: Final commit with all changes**

If any uncommitted changes remain:

```
git add -A
git commit -m "chore(net): verify full build and test suite for networking Part 1"
```
