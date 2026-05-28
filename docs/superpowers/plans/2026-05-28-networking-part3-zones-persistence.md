# Networking Part 3: Zone Architecture & Persistence

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the zone server architecture (gateway authentication, zone boundary monitoring, cross-zone handoff), PostgreSQL/Redis persistence layer, and Docker Compose infrastructure for local development.

**Architecture:** Gateway server authenticates clients and routes them to zone servers. Each zone server owns an axis-aligned cuboid of galaxy-space. Entities near zone boundaries are shared as ghost entities via Redis pub/sub. PostgreSQL stores player/entity/sector state; Redis handles sessions and inter-zone messaging. HikariCP for connection pooling, Jedis for Redis.

**Tech Stack:** Java 21+, KryoNet 2.22.0-RC1, Ashley ECS 1.7.4, PostgreSQL 16, Redis 7, HikariCP 6.x, Jedis 5.x, Docker Compose, JUnit 5

**Deferred to a future plan:** Galaxy simulation worker, zone load balancer, zone splitting, LagCompensationSystem. These require the core zone infrastructure built here.

---

## File Structure

### New files — `common/` module (protocol messages)

| File | Responsibility |
|------|---------------|
| `common/src/main/java/com/galacticodyssey/common/protocol/ZoneJoinRequest.java` | Client→zone: session token + zone ID |
| `common/src/main/java/com/galacticodyssey/common/protocol/ZoneJoinResponse.java` | Zone→client: success/fail + world snapshot data |
| `common/src/main/java/com/galacticodyssey/common/protocol/ZoneRedirect.java` | Zone→client: redirect to new zone during handoff |
| `common/src/main/java/com/galacticodyssey/common/protocol/HandoffPrepare.java` | Source zone→target zone via Redis: serialized entity data |
| `common/src/main/java/com/galacticodyssey/common/protocol/HandoffTransferAck.java` | Target zone→source zone via Redis: acknowledgment |

### New files — `core/` module (components)

| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/galacticodyssey/networking/components/GhostComponent.java` | Marks entity as read-only ghost from another zone |

### New files — `gateway/` module (entire new module)

| File | Responsibility |
|------|---------------|
| `gateway/build.gradle.kts` | Gradle config: KryoNet, Jedis, PostgreSQL JDBC, HikariCP |
| `gateway/src/main/java/com/galacticodyssey/gateway/GatewayServer.java` | Main entry: accepts client connections, authenticates, routes to zones |
| `gateway/src/main/java/com/galacticodyssey/gateway/GatewayNetworkListener.java` | KryoNet listener: handles LoginRequest, responds with zone routing |
| `gateway/src/main/java/com/galacticodyssey/gateway/SessionManager.java` | Redis-backed session store: create, validate, refresh, expire |
| `gateway/src/main/java/com/galacticodyssey/gateway/ZoneRouter.java` | Resolves player position → zone server address via PostgreSQL |

### New files — `server/` module (zone + persistence)

| File | Responsibility |
|------|---------------|
| `server/src/main/java/com/galacticodyssey/server/zone/ZoneDefinition.java` | Immutable zone metadata: ID, cuboid bounds, adjacent zones, overlap |
| `server/src/main/java/com/galacticodyssey/server/zone/ZoneBoundaryMonitor.java` | Checks player positions against zone boundaries every 500ms |
| `server/src/main/java/com/galacticodyssey/server/zone/ZoneHandoffManager.java` | Orchestrates 4-phase zone handoff via Redis pub/sub |
| `server/src/main/java/com/galacticodyssey/server/persistence/DatabaseManager.java` | HikariCP connection pool lifecycle: init, getConnection, shutdown |
| `server/src/main/java/com/galacticodyssey/server/persistence/PlayerPersistenceService.java` | CRUD for players table: load/save position, inventory, wallet |
| `server/src/main/java/com/galacticodyssey/server/persistence/EntityPersistenceService.java` | CRUD for entities table: batch upsert, load by zone |
| `server/src/main/java/com/galacticodyssey/server/persistence/SectorStateService.java` | CRUD for sector_state table: faction control, resources, trade |
| `server/src/main/java/com/galacticodyssey/server/persistence/RedisManager.java` | Jedis pool lifecycle + pub/sub helpers |

### New files — infrastructure

| File | Responsibility |
|------|---------------|
| `docker-compose.yml` | PostgreSQL 16 + Redis 7 for local dev |
| `sql/init.sql` | DDL: zone_assignments, players, entities, sector_state tables |

### Modified files

| File | Change |
|------|--------|
| `settings.gradle.kts` | Add `include("gateway")` |
| `server/build.gradle.kts` | Add HikariCP, PostgreSQL JDBC, Jedis dependencies |
| `gradle.properties` | Add `hikariVersion`, `jedisVersion`, `postgresVersion` |
| `common/src/main/java/com/galacticodyssey/common/serialization/NetworkKryoRegistrar.java` | Register zone protocol messages (IDs 127–133) |
| `server/src/main/java/com/galacticodyssey/server/network/PlayerSession.java` | Add `zoneId` field for zone tracking |

---

### Task 1: Zone Protocol Messages

**Files:**
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/ZoneJoinRequest.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/ZoneJoinResponse.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/ZoneRedirect.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/HandoffPrepare.java`
- Create: `common/src/main/java/com/galacticodyssey/common/protocol/HandoffTransferAck.java`
- Modify: `common/src/main/java/com/galacticodyssey/common/serialization/NetworkKryoRegistrar.java`
- Test: `common/src/test/java/com/galacticodyssey/common/protocol/ZoneProtocolMessageTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.common.protocol;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.galacticodyssey.common.serialization.NetworkKryoRegistrar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneProtocolMessageTest {

    private Kryo kryo;

    @BeforeEach
    void setUp() {
        kryo = new Kryo();
        NetworkKryoRegistrar.register(kryo);
    }

    @Test
    void zoneJoinRequestRoundTrip() {
        ZoneJoinRequest msg = new ZoneJoinRequest();
        msg.sessionToken = "tok-abc";
        msg.zoneId = UUID.randomUUID();

        ZoneJoinRequest result = roundTrip(msg, ZoneJoinRequest.class);
        assertEquals("tok-abc", result.sessionToken);
        assertEquals(msg.zoneId, result.zoneId);
    }

    @Test
    void zoneJoinResponseRoundTrip() {
        ZoneJoinResponse msg = new ZoneJoinResponse();
        msg.success = true;
        msg.worldSnapshotData = new byte[]{1, 2, 3};

        ZoneJoinResponse result = roundTrip(msg, ZoneJoinResponse.class);
        assertTrue(result.success);
        assertArrayEquals(new byte[]{1, 2, 3}, result.worldSnapshotData);
    }

    @Test
    void zoneRedirectRoundTrip() {
        ZoneRedirect msg = new ZoneRedirect();
        msg.newZoneAddress = "10.0.0.5:7100";
        msg.handoffToken = "handoff-xyz";
        msg.targetZoneId = UUID.randomUUID();

        ZoneRedirect result = roundTrip(msg, ZoneRedirect.class);
        assertEquals("10.0.0.5:7100", result.newZoneAddress);
        assertEquals("handoff-xyz", result.handoffToken);
        assertEquals(msg.targetZoneId, result.targetZoneId);
    }

    @Test
    void handoffPrepareRoundTrip() {
        HandoffPrepare msg = new HandoffPrepare();
        msg.entityNetworkId = 42;
        msg.sourceZoneId = UUID.randomUUID();
        msg.targetZoneId = UUID.randomUUID();
        msg.serializedEntityData = new byte[]{10, 20, 30};
        msg.playerSessionToken = "tok-player";

        HandoffPrepare result = roundTrip(msg, HandoffPrepare.class);
        assertEquals(42, result.entityNetworkId);
        assertEquals(msg.sourceZoneId, result.sourceZoneId);
        assertEquals(msg.targetZoneId, result.targetZoneId);
        assertArrayEquals(new byte[]{10, 20, 30}, result.serializedEntityData);
        assertEquals("tok-player", result.playerSessionToken);
    }

    @Test
    void handoffTransferAckRoundTrip() {
        HandoffTransferAck msg = new HandoffTransferAck();
        msg.entityNetworkId = 42;
        msg.sourceZoneId = UUID.randomUUID();
        msg.targetZoneId = UUID.randomUUID();
        msg.success = true;

        HandoffTransferAck result = roundTrip(msg, HandoffTransferAck.class);
        assertEquals(42, result.entityNetworkId);
        assertEquals(msg.sourceZoneId, result.sourceZoneId);
        assertEquals(msg.targetZoneId, result.targetZoneId);
        assertTrue(result.success);
    }

    @SuppressWarnings("unchecked")
    private <T> T roundTrip(T obj, Class<T> type) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeObject(output, obj);
        output.close();
        Input input = new Input(new ByteArrayInputStream(baos.toByteArray()));
        return kryo.readObject(input, type);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :common:test --tests "com.galacticodyssey.common.protocol.ZoneProtocolMessageTest" -v`
Expected: FAIL — classes don't exist yet.

- [ ] **Step 3: Create the protocol message classes**

`ZoneJoinRequest.java`:
```java
package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class ZoneJoinRequest extends NetworkMessage {
    public String sessionToken;
    public UUID zoneId;
}
```

`ZoneJoinResponse.java`:
```java
package com.galacticodyssey.common.protocol;

public class ZoneJoinResponse extends NetworkMessage {
    public boolean success;
    public String failureReason;
    public byte[] worldSnapshotData;
}
```

`ZoneRedirect.java`:
```java
package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class ZoneRedirect extends NetworkMessage {
    public String newZoneAddress;
    public String handoffToken;
    public UUID targetZoneId;
}
```

`HandoffPrepare.java`:
```java
package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class HandoffPrepare extends NetworkMessage {
    public int entityNetworkId;
    public UUID sourceZoneId;
    public UUID targetZoneId;
    public byte[] serializedEntityData;
    public String playerSessionToken;
}
```

`HandoffTransferAck.java`:
```java
package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class HandoffTransferAck extends NetworkMessage {
    public int entityNetworkId;
    public UUID sourceZoneId;
    public UUID targetZoneId;
    public boolean success;
}
```

- [ ] **Step 4: Register in NetworkKryoRegistrar**

Add these registrations after the existing `byte[].class` / `String.class` guards (after ID 126):

```java
// --- Zone protocol messages (127–133) ---
kryo.register(ZoneJoinRequest.class, 127);
kryo.register(ZoneJoinResponse.class, 128);
kryo.register(ZoneRedirect.class, 129);
kryo.register(HandoffPrepare.class, 130);
kryo.register(HandoffTransferAck.class, 131);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `gradlew.bat :common:test --tests "com.galacticodyssey.common.protocol.ZoneProtocolMessageTest" -v`
Expected: PASS (5 tests)

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/com/galacticodyssey/common/protocol/ZoneJoinRequest.java common/src/main/java/com/galacticodyssey/common/protocol/ZoneJoinResponse.java common/src/main/java/com/galacticodyssey/common/protocol/ZoneRedirect.java common/src/main/java/com/galacticodyssey/common/protocol/HandoffPrepare.java common/src/main/java/com/galacticodyssey/common/protocol/HandoffTransferAck.java common/src/main/java/com/galacticodyssey/common/serialization/NetworkKryoRegistrar.java common/src/test/java/com/galacticodyssey/common/protocol/ZoneProtocolMessageTest.java
git commit -m "feat(net): add zone protocol messages and Kryo registration"
```

---

### Task 2: GhostComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/networking/components/GhostComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/networking/components/GhostComponentTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.networking.components;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GhostComponentTest {

    @Test
    void storesOwningZoneId() {
        GhostComponent ghost = new GhostComponent();
        UUID zoneId = UUID.randomUUID();
        ghost.owningZoneId = zoneId;
        assertEquals(zoneId, ghost.owningZoneId);
    }

    @Test
    void defaultsToNullOwningZone() {
        GhostComponent ghost = new GhostComponent();
        assertNull(ghost.owningZoneId);
        assertFalse(ghost.readOnly);
    }

    @Test
    void readOnlyFlagCanBeSet() {
        GhostComponent ghost = new GhostComponent();
        ghost.readOnly = true;
        assertTrue(ghost.readOnly);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.components.GhostComponentTest" -v`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Create GhostComponent**

```java
package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Component;

import java.util.UUID;

public class GhostComponent implements Component {
    public UUID owningZoneId;
    public boolean readOnly;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.networking.components.GhostComponentTest" -v`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/networking/components/GhostComponent.java core/src/test/java/com/galacticodyssey/networking/components/GhostComponentTest.java
git commit -m "feat(net): add GhostComponent for cross-zone entity shadows"
```

---

### Task 3: ZoneDefinition

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/zone/ZoneDefinition.java`
- Test: `server/src/test/java/com/galacticodyssey/server/zone/ZoneDefinitionTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.server.zone;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneDefinitionTest {

    @Test
    void containsPointInsideBounds() {
        ZoneDefinition zone = new ZoneDefinition(
                UUID.randomUUID(), "zone-alpha",
                0, 0, 0, 1000, 1000, 1000,
                List.of(), 100.0
        );
        assertTrue(zone.containsPoint(500, 500, 500));
    }

    @Test
    void rejectsPointOutsideBounds() {
        ZoneDefinition zone = new ZoneDefinition(
                UUID.randomUUID(), "zone-alpha",
                0, 0, 0, 1000, 1000, 1000,
                List.of(), 100.0
        );
        assertFalse(zone.containsPoint(1500, 500, 500));
    }

    @Test
    void pointInBoundaryOverlap() {
        ZoneDefinition zone = new ZoneDefinition(
                UUID.randomUUID(), "zone-alpha",
                0, 0, 0, 1000, 1000, 1000,
                List.of(), 100.0
        );
        // 950 is within 100 units of the max boundary (1000)
        assertTrue(zone.isInBoundaryOverlap(950, 500, 500));
        // 500 is not near any boundary
        assertFalse(zone.isInBoundaryOverlap(500, 500, 500));
    }

    @Test
    void pointNearMinBoundaryIsOverlap() {
        ZoneDefinition zone = new ZoneDefinition(
                UUID.randomUUID(), "zone-alpha",
                1000, 1000, 1000, 2000, 2000, 2000,
                List.of(), 200.0
        );
        // 1100 is within 200 units of the min boundary (1000)
        assertTrue(zone.isInBoundaryOverlap(1100, 1500, 1500));
    }

    @Test
    void adjacentZonesStored() {
        UUID adj1 = UUID.randomUUID();
        UUID adj2 = UUID.randomUUID();
        ZoneDefinition zone = new ZoneDefinition(
                UUID.randomUUID(), "zone-alpha",
                0, 0, 0, 1000, 1000, 1000,
                List.of(adj1, adj2), 100.0
        );
        assertEquals(2, zone.adjacentZoneIds().size());
        assertTrue(zone.adjacentZoneIds().contains(adj1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.zone.ZoneDefinitionTest" -v`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Create ZoneDefinition**

```java
package com.galacticodyssey.server.zone;

import java.util.List;
import java.util.UUID;

public record ZoneDefinition(
        UUID zoneId,
        String name,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        List<UUID> adjacentZoneIds,
        double boundaryOverlap
) {
    public boolean containsPoint(double x, double y, double z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean isInBoundaryOverlap(double x, double y, double z) {
        if (!containsPoint(x, y, z)) return false;
        return (x - minX) < boundaryOverlap || (maxX - x) < boundaryOverlap
                || (y - minY) < boundaryOverlap || (maxY - y) < boundaryOverlap
                || (z - minZ) < boundaryOverlap || (maxZ - z) < boundaryOverlap;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.zone.ZoneDefinitionTest" -v`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/galacticodyssey/server/zone/ZoneDefinition.java server/src/test/java/com/galacticodyssey/server/zone/ZoneDefinitionTest.java
git commit -m "feat(net): add ZoneDefinition with boundary and overlap checks"
```

---

### Task 4: ZoneBoundaryMonitor

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/zone/ZoneBoundaryMonitor.java`
- Test: `server/src/test/java/com/galacticodyssey/server/zone/ZoneBoundaryMonitorTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.server.zone;

import com.galacticodyssey.server.network.PlayerSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneBoundaryMonitorTest {

    private ZoneDefinition makeZone(double minX, double maxX) {
        return new ZoneDefinition(
                UUID.randomUUID(), "test-zone",
                minX, 0, 0, maxX, 1000, 1000,
                List.of(), 100.0
        );
    }

    @Test
    void detectsPlayerInBoundaryOverlap() {
        ZoneDefinition zone = makeZone(0, 1000);
        ZoneBoundaryMonitor monitor = new ZoneBoundaryMonitor(zone);

        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setGalaxyPosition(950, 500, 500); // within 100 of maxX

        List<ZoneBoundaryMonitor.BoundaryEvent> events = monitor.check(List.of(session));
        assertEquals(1, events.size());
        assertEquals(ZoneBoundaryMonitor.BoundaryEventType.ENTERED_OVERLAP, events.get(0).type());
    }

    @Test
    void noEventWhenPlayerInCenter() {
        ZoneDefinition zone = makeZone(0, 1000);
        ZoneBoundaryMonitor monitor = new ZoneBoundaryMonitor(zone);

        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setGalaxyPosition(500, 500, 500);

        List<ZoneBoundaryMonitor.BoundaryEvent> events = monitor.check(List.of(session));
        assertTrue(events.isEmpty());
    }

    @Test
    void detectsPlayerLeftOverlap() {
        ZoneDefinition zone = makeZone(0, 1000);
        ZoneBoundaryMonitor monitor = new ZoneBoundaryMonitor(zone);

        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setGalaxyPosition(950, 500, 500);
        monitor.check(List.of(session)); // enters overlap

        session.setGalaxyPosition(500, 500, 500); // moves to center
        List<ZoneBoundaryMonitor.BoundaryEvent> events = monitor.check(List.of(session));
        assertEquals(1, events.size());
        assertEquals(ZoneBoundaryMonitor.BoundaryEventType.LEFT_OVERLAP, events.get(0).type());
    }

    @Test
    void detectsPlayerExitedZone() {
        ZoneDefinition zone = makeZone(0, 1000);
        ZoneBoundaryMonitor monitor = new ZoneBoundaryMonitor(zone);

        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.setGalaxyPosition(1100, 500, 500); // outside zone

        List<ZoneBoundaryMonitor.BoundaryEvent> events = monitor.check(List.of(session));
        assertEquals(1, events.size());
        assertEquals(ZoneBoundaryMonitor.BoundaryEventType.EXITED_ZONE, events.get(0).type());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.zone.ZoneBoundaryMonitorTest" -v`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Create ZoneBoundaryMonitor**

```java
package com.galacticodyssey.server.zone;

import com.galacticodyssey.server.network.PlayerSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZoneBoundaryMonitor {

    public enum BoundaryEventType {
        ENTERED_OVERLAP,
        LEFT_OVERLAP,
        EXITED_ZONE
    }

    public record BoundaryEvent(int connectionId, BoundaryEventType type) {}

    private final ZoneDefinition zone;
    private final Map<Integer, Boolean> previouslyInOverlap = new HashMap<>();

    public ZoneBoundaryMonitor(ZoneDefinition zone) {
        this.zone = zone;
    }

    public List<BoundaryEvent> check(List<PlayerSession> sessions) {
        List<BoundaryEvent> events = new ArrayList<>();

        for (PlayerSession session : sessions) {
            double x = session.getGalaxyX();
            double y = session.getGalaxyY();
            double z = session.getGalaxyZ();
            int connId = session.getConnectionId();

            boolean inZone = zone.containsPoint(x, y, z);
            boolean inOverlap = inZone && zone.isInBoundaryOverlap(x, y, z);
            boolean wasInOverlap = previouslyInOverlap.getOrDefault(connId, false);

            if (!inZone) {
                events.add(new BoundaryEvent(connId, BoundaryEventType.EXITED_ZONE));
                previouslyInOverlap.remove(connId);
            } else if (inOverlap && !wasInOverlap) {
                events.add(new BoundaryEvent(connId, BoundaryEventType.ENTERED_OVERLAP));
                previouslyInOverlap.put(connId, true);
            } else if (!inOverlap && wasInOverlap) {
                events.add(new BoundaryEvent(connId, BoundaryEventType.LEFT_OVERLAP));
                previouslyInOverlap.put(connId, false);
            }
        }
        return events;
    }

    public void removePlayer(int connectionId) {
        previouslyInOverlap.remove(connectionId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.zone.ZoneBoundaryMonitorTest" -v`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/galacticodyssey/server/zone/ZoneBoundaryMonitor.java server/src/test/java/com/galacticodyssey/server/zone/ZoneBoundaryMonitorTest.java
git commit -m "feat(net): add ZoneBoundaryMonitor for player boundary detection"
```

---

### Task 5: Docker Compose + SQL Schema

**Files:**
- Create: `docker-compose.yml`
- Create: `sql/init.sql`
- Test: manual — `docker compose up -d` then `docker compose ps`

- [ ] **Step 1: Create docker-compose.yml**

```yaml
services:
  postgres:
    image: postgres:16
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: galactic_odyssey
      POSTGRES_USER: galactic
      POSTGRES_PASSWORD: dev_only
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U galactic -d galactic_odyssey"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

- [ ] **Step 2: Create sql/init.sql**

```sql
-- Zone assignments: each zone owns a cuboid of galaxy-space
CREATE TABLE IF NOT EXISTS zone_assignments (
    zone_id         UUID PRIMARY KEY,
    zone_name       VARCHAR(255) NOT NULL,
    sector_min_x    DOUBLE PRECISION NOT NULL,
    sector_min_y    DOUBLE PRECISION NOT NULL,
    sector_min_z    DOUBLE PRECISION NOT NULL,
    sector_max_x    DOUBLE PRECISION NOT NULL,
    sector_max_y    DOUBLE PRECISION NOT NULL,
    sector_max_z    DOUBLE PRECISION NOT NULL,
    server_instance VARCHAR(255),
    adjacent_zones  UUID[] DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'DORMANT',
    boundary_overlap DOUBLE PRECISION NOT NULL DEFAULT 1000.0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'DORMANT', 'MIGRATING'))
);

CREATE INDEX IF NOT EXISTS idx_zone_assignments_status ON zone_assignments(status);

-- Players: persistent player data
CREATE TABLE IF NOT EXISTS players (
    player_id       UUID PRIMARY KEY,
    username        VARCHAR(255) NOT NULL UNIQUE,
    last_zone_id    UUID REFERENCES zone_assignments(zone_id),
    last_galaxy_x   DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_galaxy_y   DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_galaxy_z   DOUBLE PRECISION NOT NULL DEFAULT 0,
    inventory       JSONB DEFAULT '{}',
    wallet          JSONB DEFAULT '{"credits": 1000}',
    player_state    JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_players_username ON players(username);
CREATE INDEX IF NOT EXISTS idx_players_last_zone ON players(last_zone_id);

-- Entities: persistent world entities (ships, stations, NPCs)
CREATE TABLE IF NOT EXISTS entities (
    entity_id       UUID PRIMARY KEY,
    zone_id         UUID REFERENCES zone_assignments(zone_id),
    entity_type     VARCHAR(100) NOT NULL,
    galaxy_x        DOUBLE PRECISION NOT NULL DEFAULT 0,
    galaxy_y        DOUBLE PRECISION NOT NULL DEFAULT 0,
    galaxy_z        DOUBLE PRECISION NOT NULL DEFAULT 0,
    component_state JSONB DEFAULT '{}',
    is_active       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_entities_zone ON entities(zone_id);
CREATE INDEX IF NOT EXISTS idx_entities_type ON entities(entity_type);
CREATE INDEX IF NOT EXISTS idx_entities_active ON entities(is_active);

-- Sector state: galaxy simulation state per zone/sector
CREATE TABLE IF NOT EXISTS sector_state (
    sector_id       UUID PRIMARY KEY REFERENCES zone_assignments(zone_id),
    faction_control JSONB DEFAULT '{}',
    resource_levels JSONB DEFAULT '{}',
    population      BIGINT NOT NULL DEFAULT 0,
    trade_demand    JSONB DEFAULT '{}',
    trade_supply    JSONB DEFAULT '{}',
    simulated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed a default zone for local development
INSERT INTO zone_assignments (zone_id, zone_name, sector_min_x, sector_min_y, sector_min_z, sector_max_x, sector_max_y, sector_max_z, server_instance, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'local-dev-zone', -50000, -50000, -50000, 50000, 50000, 50000, 'localhost:7100', 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO sector_state (sector_id)
VALUES ('00000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;
```

- [ ] **Step 3: Verify Docker Compose starts**

Run: `docker compose up -d && docker compose ps`
Expected: Both postgres and redis containers running and healthy.

Run: `docker compose exec postgres psql -U galactic -d galactic_odyssey -c "\dt"`
Expected: Lists 4 tables: zone_assignments, players, entities, sector_state.

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml sql/init.sql
git commit -m "feat(infra): add Docker Compose for PostgreSQL and Redis"
```

---

### Task 6: Build Dependencies (server + gateway module)

**Files:**
- Modify: `settings.gradle.kts` — add `include("gateway")`
- Modify: `gradle.properties` — add dependency versions
- Modify: `server/build.gradle.kts` — add HikariCP, PostgreSQL JDBC, Jedis
- Create: `gateway/build.gradle.kts` — KryoNet, HikariCP, PostgreSQL JDBC, Jedis

- [ ] **Step 1: Update gradle.properties**

Add these lines:

```properties
hikariVersion=6.2.1
jedisVersion=5.2.0
postgresVersion=42.7.4
```

- [ ] **Step 2: Update settings.gradle.kts**

Change to:
```kotlin
rootProject.name = "SpaceGame"

include("common")
include("core")
include("desktop")
include("server")
include("gateway")
```

- [ ] **Step 3: Update server/build.gradle.kts**

Add persistence dependencies:

```kotlin
val gdxVersion: String by project
val ashleyVersion: String by project
val junitVersion: String by project
val hikariVersion: String by project
val jedisVersion: String by project
val postgresVersion: String by project

plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("redis.clients:jedis:$jedisVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.galacticodyssey.server.ServerLauncher")
}
```

- [ ] **Step 4: Create gateway/build.gradle.kts**

```kotlin
val junitVersion: String by project
val hikariVersion: String by project
val jedisVersion: String by project
val postgresVersion: String by project

plugins {
    application
}

dependencies {
    implementation(project(":common"))

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("redis.clients:jedis:$jedisVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.galacticodyssey.gateway.GatewayServer")
}
```

- [ ] **Step 5: Verify Gradle sync**

Run: `gradlew.bat :server:dependencies --configuration runtimeClasspath | findstr "hikari\|jedis\|postgres"`
Expected: Shows HikariCP, Jedis, and PostgreSQL JDBC driver resolved.

Run: `gradlew.bat :gateway:dependencies --configuration runtimeClasspath | findstr "hikari\|jedis\|postgres"`
Expected: Shows same dependencies for gateway module.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts gradle.properties server/build.gradle.kts gateway/build.gradle.kts
git commit -m "feat(infra): add gateway module and persistence dependencies"
```

---

### Task 7: DatabaseManager (HikariCP Connection Pool)

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/persistence/DatabaseManager.java`
- Test: `server/src/test/java/com/galacticodyssey/server/persistence/DatabaseManagerTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.server.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @Test
    void configHasDefaults() {
        DatabaseManager.Config config = new DatabaseManager.Config();
        assertEquals("localhost", config.host);
        assertEquals(5432, config.port);
        assertEquals("galactic_odyssey", config.database);
        assertEquals("galactic", config.username);
        assertEquals("dev_only", config.password);
        assertEquals(10, config.maxPoolSize);
    }

    @Test
    void buildJdbcUrl() {
        DatabaseManager.Config config = new DatabaseManager.Config();
        config.host = "db.example.com";
        config.port = 5433;
        config.database = "test_db";
        assertEquals("jdbc:postgresql://db.example.com:5433/test_db", config.getJdbcUrl());
    }

    @Test
    void createManagerDoesNotThrowBeforeInit() {
        DatabaseManager.Config config = new DatabaseManager.Config();
        DatabaseManager manager = new DatabaseManager(config);
        assertNotNull(manager);
        assertFalse(manager.isRunning());
    }

    @Test
    void shutdownWithoutInitIsNoOp() {
        DatabaseManager manager = new DatabaseManager(new DatabaseManager.Config());
        assertDoesNotThrow(manager::shutdown);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.persistence.DatabaseManagerTest" -v`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Create DatabaseManager**

```java
package com.galacticodyssey.server.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    public static class Config {
        public String host = "localhost";
        public int port = 5432;
        public String database = "galactic_odyssey";
        public String username = "galactic";
        public String password = "dev_only";
        public int maxPoolSize = 10;

        public String getJdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }
    }

    private final Config config;
    private HikariDataSource dataSource;

    public DatabaseManager(Config config) {
        this.config = config;
    }

    public void init() {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getJdbcUrl());
        hikari.setUsername(config.username);
        hikari.setPassword(config.password);
        hikari.setMaximumPoolSize(config.maxPoolSize);
        hikari.setPoolName("galactic-odyssey-pool");
        dataSource = new HikariDataSource(hikari);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DatabaseManager not initialized — call init() first");
        }
        return dataSource.getConnection();
    }

    public boolean isRunning() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.persistence.DatabaseManagerTest" -v`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/galacticodyssey/server/persistence/DatabaseManager.java server/src/test/java/com/galacticodyssey/server/persistence/DatabaseManagerTest.java
git commit -m "feat(net): add DatabaseManager with HikariCP connection pool"
```

---

### Task 8: RedisManager (Jedis Pool + Pub/Sub)

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/persistence/RedisManager.java`
- Test: `server/src/test/java/com/galacticodyssey/server/persistence/RedisManagerTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.server.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisManagerTest {

    @Test
    void configHasDefaults() {
        RedisManager.Config config = new RedisManager.Config();
        assertEquals("localhost", config.host);
        assertEquals(6379, config.port);
        assertEquals(10, config.maxPoolSize);
    }

    @Test
    void createManagerDoesNotThrow() {
        RedisManager manager = new RedisManager(new RedisManager.Config());
        assertNotNull(manager);
        assertFalse(manager.isRunning());
    }

    @Test
    void shutdownWithoutInitIsNoOp() {
        RedisManager manager = new RedisManager(new RedisManager.Config());
        assertDoesNotThrow(manager::shutdown);
    }

    @Test
    void sessionKeyFormat() {
        assertEquals("session:tok-abc", RedisManager.sessionKey("tok-abc"));
    }

    @Test
    void zoneLoadKeyFormat() {
        assertEquals("zone:zone-1:load", RedisManager.zoneLoadKey("zone-1"));
    }

    @Test
    void zoneBorderChannelFormat() {
        assertEquals("zone.border.zone-1", RedisManager.zoneBorderChannel("zone-1"));
    }

    @Test
    void handoffPrepareChannelFormat() {
        assertEquals("zone.handoff.prepare.zone-1", RedisManager.handoffPrepareChannel("zone-1"));
    }

    @Test
    void handoffAckChannelFormat() {
        assertEquals("zone.handoff.ack.zone-1", RedisManager.handoffAckChannel("zone-1"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.persistence.RedisManagerTest" -v`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Create RedisManager**

```java
package com.galacticodyssey.server.persistence;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;

public class RedisManager {

    public static class Config {
        public String host = "localhost";
        public int port = 6379;
        public int maxPoolSize = 10;
    }

    private final Config config;
    private JedisPool pool;

    public RedisManager(Config config) {
        this.config = config;
    }

    public void init() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.maxPoolSize);
        pool = new JedisPool(poolConfig, config.host, config.port);
    }

    public Jedis getResource() {
        if (pool == null) {
            throw new IllegalStateException("RedisManager not initialized — call init() first");
        }
        return pool.getResource();
    }

    public boolean isRunning() {
        return pool != null && !pool.isClosed();
    }

    public void shutdown() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    public static String sessionKey(String token) {
        return "session:" + token;
    }

    public static String zoneLoadKey(String zoneId) {
        return "zone:" + zoneId + ":load";
    }

    public static String zoneBorderChannel(String zoneId) {
        return "zone.border." + zoneId;
    }

    public static String handoffPrepareChannel(String zoneId) {
        return "zone.handoff.prepare." + zoneId;
    }

    public static String handoffAckChannel(String zoneId) {
        return "zone.handoff.ack." + zoneId;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.persistence.RedisManagerTest" -v`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/galacticodyssey/server/persistence/RedisManager.java server/src/test/java/com/galacticodyssey/server/persistence/RedisManagerTest.java
git commit -m "feat(net): add RedisManager with Jedis pool and key/channel helpers"
```

---

### Task 9: PlayerPersistenceService

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/persistence/PlayerPersistenceService.java`
- Test: `server/src/test/java/com/galacticodyssey/server/persistence/PlayerPersistenceServiceTest.java`

Note: Tests use a mock `DatabaseManager` approach — we pass a `Connection` supplier so tests can provide a fake or real connection. Integration tests against real PostgreSQL are in a separate task.

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.server.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerPersistenceServiceTest {

    @Test
    void playerDataHoldsFields() {
        UUID playerId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        PlayerPersistenceService.PlayerData data = new PlayerPersistenceService.PlayerData(
                playerId, "testuser", zoneId, 100.0, 200.0, 300.0, "{}", "{\"credits\":500}", "{}"
        );
        assertEquals(playerId, data.playerId());
        assertEquals("testuser", data.username());
        assertEquals(zoneId, data.lastZoneId());
        assertEquals(100.0, data.lastGalaxyX());
        assertEquals(200.0, data.lastGalaxyY());
        assertEquals(300.0, data.lastGalaxyZ());
    }

    @Test
    void upsertSqlIsValid() {
        String sql = PlayerPersistenceService.UPSERT_POSITION_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("INSERT INTO players"));
        assertTrue(sql.contains("ON CONFLICT"));
    }

    @Test
    void loadSqlIsValid() {
        String sql = PlayerPersistenceService.LOAD_BY_USERNAME_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("SELECT"));
        assertTrue(sql.contains("FROM players"));
    }

    @Test
    void updatePositionSqlIsValid() {
        String sql = PlayerPersistenceService.UPDATE_POSITION_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("UPDATE players"));
        assertTrue(sql.contains("last_galaxy_x"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.persistence.PlayerPersistenceServiceTest" -v`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Create PlayerPersistenceService**

```java
package com.galacticodyssey.server.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerPersistenceService {

    public record PlayerData(
            UUID playerId,
            String username,
            UUID lastZoneId,
            double lastGalaxyX,
            double lastGalaxyY,
            double lastGalaxyZ,
            String inventoryJson,
            String walletJson,
            String playerStateJson
    ) {}

    static final String UPSERT_POSITION_SQL =
            "INSERT INTO players (player_id, username, last_zone_id, last_galaxy_x, last_galaxy_y, last_galaxy_z) " +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (player_id) DO UPDATE SET " +
            "last_zone_id = EXCLUDED.last_zone_id, " +
            "last_galaxy_x = EXCLUDED.last_galaxy_x, " +
            "last_galaxy_y = EXCLUDED.last_galaxy_y, " +
            "last_galaxy_z = EXCLUDED.last_galaxy_z, " +
            "last_login = NOW()";

    static final String LOAD_BY_USERNAME_SQL =
            "SELECT player_id, username, last_zone_id, last_galaxy_x, last_galaxy_y, last_galaxy_z, " +
            "inventory::text, wallet::text, player_state::text FROM players WHERE username = ?";

    static final String UPDATE_POSITION_SQL =
            "UPDATE players SET last_zone_id = ?, last_galaxy_x = ?, last_galaxy_y = ?, last_galaxy_z = ?, " +
            "last_login = NOW() WHERE player_id = ?";

    static final String SAVE_INVENTORY_SQL =
            "UPDATE players SET inventory = ?::jsonb WHERE player_id = ?";

    static final String SAVE_WALLET_SQL =
            "UPDATE players SET wallet = ?::jsonb WHERE player_id = ?";

    private final DatabaseManager db;

    public PlayerPersistenceService(DatabaseManager db) {
        this.db = db;
    }

    public PlayerData loadByUsername(String username) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOAD_BY_USERNAME_SQL)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new PlayerData(
                        rs.getObject("player_id", UUID.class),
                        rs.getString("username"),
                        rs.getObject("last_zone_id", UUID.class),
                        rs.getDouble("last_galaxy_x"),
                        rs.getDouble("last_galaxy_y"),
                        rs.getDouble("last_galaxy_z"),
                        rs.getString(7),
                        rs.getString(8),
                        rs.getString(9)
                );
            }
        }
    }

    public void upsertPosition(UUID playerId, String username, UUID zoneId,
                               double x, double y, double z) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_POSITION_SQL)) {
            ps.setObject(1, playerId);
            ps.setString(2, username);
            ps.setObject(3, zoneId);
            ps.setDouble(4, x);
            ps.setDouble(5, y);
            ps.setDouble(6, z);
            ps.executeUpdate();
        }
    }

    public void updatePosition(UUID playerId, UUID zoneId, double x, double y, double z) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_POSITION_SQL)) {
            ps.setObject(1, zoneId);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.setObject(5, playerId);
            ps.executeUpdate();
        }
    }

    public void saveInventory(UUID playerId, String inventoryJson) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SAVE_INVENTORY_SQL)) {
            ps.setString(1, inventoryJson);
            ps.setObject(2, playerId);
            ps.executeUpdate();
        }
    }

    public void saveWallet(UUID playerId, String walletJson) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SAVE_WALLET_SQL)) {
            ps.setString(1, walletJson);
            ps.setObject(2, playerId);
            ps.executeUpdate();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.persistence.PlayerPersistenceServiceTest" -v`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/galacticodyssey/server/persistence/PlayerPersistenceService.java server/src/test/java/com/galacticodyssey/server/persistence/PlayerPersistenceServiceTest.java
git commit -m "feat(net): add PlayerPersistenceService for PostgreSQL player storage"
```

---

### Task 10: EntityPersistenceService

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/persistence/EntityPersistenceService.java`
- Test: `server/src/test/java/com/galacticodyssey/server/persistence/EntityPersistenceServiceTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.server.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EntityPersistenceServiceTest {

    @Test
    void entityDataHoldsFields() {
        UUID entityId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        EntityPersistenceService.EntityData data = new EntityPersistenceService.EntityData(
                entityId, zoneId, "ship", 10.0, 20.0, 30.0, "{\"health\":100}", true
        );
        assertEquals(entityId, data.entityId());
        assertEquals(zoneId, data.zoneId());
        assertEquals("ship", data.entityType());
        assertEquals(10.0, data.galaxyX());
        assertTrue(data.isActive());
    }

    @Test
    void batchUpsertSqlIsValid() {
        String sql = EntityPersistenceService.UPSERT_ENTITY_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("INSERT INTO entities"));
        assertTrue(sql.contains("ON CONFLICT"));
    }

    @Test
    void loadByZoneSqlIsValid() {
        String sql = EntityPersistenceService.LOAD_BY_ZONE_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("SELECT"));
        assertTrue(sql.contains("zone_id"));
    }

    @Test
    void updatePositionBatchSqlIsValid() {
        String sql = EntityPersistenceService.UPDATE_POSITION_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("UPDATE entities"));
        assertTrue(sql.contains("galaxy_x"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.persistence.EntityPersistenceServiceTest" -v`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Create EntityPersistenceService**

```java
package com.galacticodyssey.server.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EntityPersistenceService {

    public record EntityData(
            UUID entityId,
            UUID zoneId,
            String entityType,
            double galaxyX,
            double galaxyY,
            double galaxyZ,
            String componentStateJson,
            boolean isActive
    ) {}

    static final String UPSERT_ENTITY_SQL =
            "INSERT INTO entities (entity_id, zone_id, entity_type, galaxy_x, galaxy_y, galaxy_z, component_state, is_active) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?) " +
            "ON CONFLICT (entity_id) DO UPDATE SET " +
            "zone_id = EXCLUDED.zone_id, " +
            "galaxy_x = EXCLUDED.galaxy_x, " +
            "galaxy_y = EXCLUDED.galaxy_y, " +
            "galaxy_z = EXCLUDED.galaxy_z, " +
            "component_state = EXCLUDED.component_state, " +
            "is_active = EXCLUDED.is_active, " +
            "updated_at = NOW()";

    static final String LOAD_BY_ZONE_SQL =
            "SELECT entity_id, zone_id, entity_type, galaxy_x, galaxy_y, galaxy_z, " +
            "component_state::text, is_active FROM entities WHERE zone_id = ?";

    static final String UPDATE_POSITION_SQL =
            "UPDATE entities SET galaxy_x = ?, galaxy_y = ?, galaxy_z = ?, updated_at = NOW() " +
            "WHERE entity_id = ?";

    static final String DELETE_ENTITY_SQL =
            "DELETE FROM entities WHERE entity_id = ?";

    private final DatabaseManager db;

    public EntityPersistenceService(DatabaseManager db) {
        this.db = db;
    }

    public List<EntityData> loadByZone(UUID zoneId) throws SQLException {
        List<EntityData> results = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOAD_BY_ZONE_SQL)) {
            ps.setObject(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new EntityData(
                            rs.getObject("entity_id", UUID.class),
                            rs.getObject("zone_id", UUID.class),
                            rs.getString("entity_type"),
                            rs.getDouble("galaxy_x"),
                            rs.getDouble("galaxy_y"),
                            rs.getDouble("galaxy_z"),
                            rs.getString(7),
                            rs.getBoolean("is_active")
                    ));
                }
            }
        }
        return results;
    }

    public void upsertEntity(EntityData data) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_ENTITY_SQL)) {
            ps.setObject(1, data.entityId());
            ps.setObject(2, data.zoneId());
            ps.setString(3, data.entityType());
            ps.setDouble(4, data.galaxyX());
            ps.setDouble(5, data.galaxyY());
            ps.setDouble(6, data.galaxyZ());
            ps.setString(7, data.componentStateJson());
            ps.setBoolean(8, data.isActive());
            ps.executeUpdate();
        }
    }

    public void batchUpsert(List<EntityData> entities) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_ENTITY_SQL)) {
            conn.setAutoCommit(false);
            for (EntityData data : entities) {
                ps.setObject(1, data.entityId());
                ps.setObject(2, data.zoneId());
                ps.setString(3, data.entityType());
                ps.setDouble(4, data.galaxyX());
                ps.setDouble(5, data.galaxyY());
                ps.setDouble(6, data.galaxyZ());
                ps.setString(7, data.componentStateJson());
                ps.setBoolean(8, data.isActive());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    public void batchUpdatePositions(List<EntityData> entities) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_POSITION_SQL)) {
            conn.setAutoCommit(false);
            for (EntityData data : entities) {
                ps.setDouble(1, data.galaxyX());
                ps.setDouble(2, data.galaxyY());
                ps.setDouble(3, data.galaxyZ());
                ps.setObject(4, data.entityId());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    public void deleteEntity(UUID entityId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_ENTITY_SQL)) {
            ps.setObject(1, entityId);
            ps.executeUpdate();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.persistence.EntityPersistenceServiceTest" -v`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/galacticodyssey/server/persistence/EntityPersistenceService.java server/src/test/java/com/galacticodyssey/server/persistence/EntityPersistenceServiceTest.java
git commit -m "feat(net): add EntityPersistenceService for PostgreSQL entity storage"
```

---

### Task 11: SectorStateService

**Files:**
- Create: `server/src/main/java/com/galacticodyssey/server/persistence/SectorStateService.java`
- Test: `server/src/test/java/com/galacticodyssey/server/persistence/SectorStateServiceTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.server.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SectorStateServiceTest {

    @Test
    void sectorDataHoldsFields() {
        UUID sectorId = UUID.randomUUID();
        SectorStateService.SectorData data = new SectorStateService.SectorData(
                sectorId, "{\"federation\":60}", "{\"iron\":1000}", 50000,
                "{\"fuel\":100}", "{\"fuel\":80}"
        );
        assertEquals(sectorId, data.sectorId());
        assertEquals(50000, data.population());
    }

    @Test
    void upsertSqlIsValid() {
        String sql = SectorStateService.UPSERT_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("INSERT INTO sector_state"));
        assertTrue(sql.contains("ON CONFLICT"));
    }

    @Test
    void loadSqlIsValid() {
        String sql = SectorStateService.LOAD_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("SELECT"));
        assertTrue(sql.contains("sector_state"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.persistence.SectorStateServiceTest" -v`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Create SectorStateService**

```java
package com.galacticodyssey.server.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SectorStateService {

    public record SectorData(
            UUID sectorId,
            String factionControlJson,
            String resourceLevelsJson,
            long population,
            String tradeDemandJson,
            String tradeSupplyJson
    ) {}

    static final String UPSERT_SQL =
            "INSERT INTO sector_state (sector_id, faction_control, resource_levels, population, trade_demand, trade_supply, simulated_at) " +
            "VALUES (?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb, NOW()) " +
            "ON CONFLICT (sector_id) DO UPDATE SET " +
            "faction_control = EXCLUDED.faction_control, " +
            "resource_levels = EXCLUDED.resource_levels, " +
            "population = EXCLUDED.population, " +
            "trade_demand = EXCLUDED.trade_demand, " +
            "trade_supply = EXCLUDED.trade_supply, " +
            "simulated_at = NOW()";

    static final String LOAD_SQL =
            "SELECT sector_id, faction_control::text, resource_levels::text, population, " +
            "trade_demand::text, trade_supply::text FROM sector_state WHERE sector_id = ?";

    private final DatabaseManager db;

    public SectorStateService(DatabaseManager db) {
        this.db = db;
    }

    public SectorData load(UUID sectorId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOAD_SQL)) {
            ps.setObject(1, sectorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new SectorData(
                        rs.getObject("sector_id", UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getLong("population"),
                        rs.getString(5),
                        rs.getString(6)
                );
            }
        }
    }

    public void upsert(SectorData data) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setObject(1, data.sectorId());
            ps.setString(2, data.factionControlJson());
            ps.setString(3, data.resourceLevelsJson());
            ps.setLong(4, data.population());
            ps.setString(5, data.tradeDemandJson());
            ps.setString(6, data.tradeSupplyJson());
            ps.executeUpdate();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.persistence.SectorStateServiceTest" -v`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/galacticodyssey/server/persistence/SectorStateService.java server/src/test/java/com/galacticodyssey/server/persistence/SectorStateServiceTest.java
git commit -m "feat(net): add SectorStateService for galaxy simulation persistence"
```

---

### Task 12: SessionManager (Redis-backed, for Gateway)

**Files:**
- Create: `gateway/src/main/java/com/galacticodyssey/gateway/SessionManager.java`
- Test: `gateway/src/test/java/com/galacticodyssey/gateway/SessionManagerTest.java`

Note: The SessionManager wraps Redis operations for session creation/validation. Tests verify the logic without a live Redis (using a `RedisAdapter` interface for testability).

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.gateway;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @Test
    void createSessionGeneratesToken() {
        FakeRedisAdapter redis = new FakeRedisAdapter();
        SessionManager manager = new SessionManager(redis, 300);

        UUID playerId = UUID.randomUUID();
        String token = manager.createSession(playerId, "zone-1");

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void validateSessionReturnsData() {
        FakeRedisAdapter redis = new FakeRedisAdapter();
        SessionManager manager = new SessionManager(redis, 300);

        UUID playerId = UUID.randomUUID();
        String token = manager.createSession(playerId, "zone-1");

        SessionManager.SessionData data = manager.validateSession(token);
        assertNotNull(data);
        assertEquals(playerId, data.playerId());
        assertEquals("zone-1", data.zoneId());
    }

    @Test
    void invalidTokenReturnsNull() {
        FakeRedisAdapter redis = new FakeRedisAdapter();
        SessionManager manager = new SessionManager(redis, 300);

        assertNull(manager.validateSession("bogus-token"));
    }

    @Test
    void refreshSessionUpdatesExpiry() {
        FakeRedisAdapter redis = new FakeRedisAdapter();
        SessionManager manager = new SessionManager(redis, 300);

        UUID playerId = UUID.randomUUID();
        String token = manager.createSession(playerId, "zone-1");

        assertTrue(manager.refreshSession(token));
        assertFalse(manager.refreshSession("bogus"));
    }

    @Test
    void destroySessionRemovesIt() {
        FakeRedisAdapter redis = new FakeRedisAdapter();
        SessionManager manager = new SessionManager(redis, 300);

        UUID playerId = UUID.randomUUID();
        String token = manager.createSession(playerId, "zone-1");

        manager.destroySession(token);
        assertNull(manager.validateSession(token));
    }

    /**
     * In-memory Redis adapter for testing without a live Redis server.
     */
    static class FakeRedisAdapter implements SessionManager.RedisAdapter {
        private final Map<String, Map<String, String>> hashes = new HashMap<>();

        @Override
        public void hset(String key, Map<String, String> fields, int ttlSeconds) {
            hashes.put(key, new HashMap<>(fields));
        }

        @Override
        public Map<String, String> hgetAll(String key) {
            return hashes.getOrDefault(key, Map.of());
        }

        @Override
        public boolean exists(String key) {
            return hashes.containsKey(key);
        }

        @Override
        public void expire(String key, int seconds) {
            // no-op in fake
        }

        @Override
        public void del(String key) {
            hashes.remove(key);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :gateway:test --tests "com.galacticodyssey.gateway.SessionManagerTest" -v`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Create SessionManager**

```java
package com.galacticodyssey.gateway;

import java.util.Map;
import java.util.UUID;

public class SessionManager {

    public record SessionData(UUID playerId, String zoneId) {}

    public interface RedisAdapter {
        void hset(String key, Map<String, String> fields, int ttlSeconds);
        Map<String, String> hgetAll(String key);
        boolean exists(String key);
        void expire(String key, int seconds);
        void del(String key);
    }

    private final RedisAdapter redis;
    private final int sessionTtlSeconds;

    public SessionManager(RedisAdapter redis, int sessionTtlSeconds) {
        this.redis = redis;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    public String createSession(UUID playerId, String zoneId) {
        String token = UUID.randomUUID().toString();
        String key = "session:" + token;
        redis.hset(key, Map.of(
                "playerId", playerId.toString(),
                "zoneId", zoneId
        ), sessionTtlSeconds);
        return token;
    }

    public SessionData validateSession(String token) {
        String key = "session:" + token;
        Map<String, String> fields = redis.hgetAll(key);
        if (fields == null || fields.isEmpty() || !fields.containsKey("playerId")) {
            return null;
        }
        return new SessionData(
                UUID.fromString(fields.get("playerId")),
                fields.get("zoneId")
        );
    }

    public boolean refreshSession(String token) {
        String key = "session:" + token;
        if (!redis.exists(key)) return false;
        redis.expire(key, sessionTtlSeconds);
        return true;
    }

    public void destroySession(String token) {
        redis.del("session:" + token);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :gateway:test --tests "com.galacticodyssey.gateway.SessionManagerTest" -v`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add gateway/src/main/java/com/galacticodyssey/gateway/SessionManager.java gateway/src/test/java/com/galacticodyssey/gateway/SessionManagerTest.java
git commit -m "feat(net): add Redis-backed SessionManager for gateway authentication"
```

---

### Task 13: ZoneRouter (PostgreSQL-backed)

**Files:**
- Create: `gateway/src/main/java/com/galacticodyssey/gateway/ZoneRouter.java`
- Test: `gateway/src/test/java/com/galacticodyssey/gateway/ZoneRouterTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.gateway;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneRouterTest {

    @Test
    void routeInfoHoldsFields() {
        UUID zoneId = UUID.randomUUID();
        ZoneRouter.RouteInfo info = new ZoneRouter.RouteInfo(zoneId, "10.0.0.5:7100");
        assertEquals(zoneId, info.zoneId());
        assertEquals("10.0.0.5:7100", info.serverAddress());
    }

    @Test
    void resolveByPositionSqlIsValid() {
        String sql = ZoneRouter.RESOLVE_BY_POSITION_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("zone_assignments"));
        assertTrue(sql.contains("sector_min_x"));
        assertTrue(sql.contains("ACTIVE"));
    }

    @Test
    void resolveByZoneIdSqlIsValid() {
        String sql = ZoneRouter.RESOLVE_BY_ZONE_ID_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("zone_assignments"));
        assertTrue(sql.contains("zone_id"));
    }

    @Test
    void resolveByPlayerSqlIsValid() {
        String sql = ZoneRouter.RESOLVE_BY_PLAYER_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("players"));
        assertTrue(sql.contains("zone_assignments"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :gateway:test --tests "com.galacticodyssey.gateway.ZoneRouterTest" -v`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Create ZoneRouter**

```java
package com.galacticodyssey.gateway;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;

public class ZoneRouter {

    public record RouteInfo(UUID zoneId, String serverAddress) {}

    static final String RESOLVE_BY_POSITION_SQL =
            "SELECT zone_id, server_instance FROM zone_assignments " +
            "WHERE status = 'ACTIVE' " +
            "AND sector_min_x <= ? AND sector_max_x >= ? " +
            "AND sector_min_y <= ? AND sector_max_y >= ? " +
            "AND sector_min_z <= ? AND sector_max_z >= ? " +
            "LIMIT 1";

    static final String RESOLVE_BY_ZONE_ID_SQL =
            "SELECT zone_id, server_instance FROM zone_assignments " +
            "WHERE zone_id = ? AND status = 'ACTIVE'";

    static final String RESOLVE_BY_PLAYER_SQL =
            "SELECT z.zone_id, z.server_instance FROM players p " +
            "JOIN zone_assignments z ON p.last_zone_id = z.zone_id " +
            "WHERE p.username = ? AND z.status = 'ACTIVE'";

    private final Supplier<Connection> connectionSupplier;

    public ZoneRouter(Supplier<Connection> connectionSupplier) {
        this.connectionSupplier = connectionSupplier;
    }

    public RouteInfo resolveByPosition(double x, double y, double z) throws SQLException {
        try (Connection conn = connectionSupplier.get();
             PreparedStatement ps = conn.prepareStatement(RESOLVE_BY_POSITION_SQL)) {
            ps.setDouble(1, x);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.setDouble(6, z);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RouteInfo(
                        rs.getObject("zone_id", UUID.class),
                        rs.getString("server_instance")
                );
            }
        }
    }

    public RouteInfo resolveByZoneId(UUID zoneId) throws SQLException {
        try (Connection conn = connectionSupplier.get();
             PreparedStatement ps = conn.prepareStatement(RESOLVE_BY_ZONE_ID_SQL)) {
            ps.setObject(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RouteInfo(
                        rs.getObject("zone_id", UUID.class),
                        rs.getString("server_instance")
                );
            }
        }
    }

    public RouteInfo resolveByPlayer(String username) throws SQLException {
        try (Connection conn = connectionSupplier.get();
             PreparedStatement ps = conn.prepareStatement(RESOLVE_BY_PLAYER_SQL)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RouteInfo(
                        rs.getObject("zone_id", UUID.class),
                        rs.getString("server_instance")
                );
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :gateway:test --tests "com.galacticodyssey.gateway.ZoneRouterTest" -v`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add gateway/src/main/java/com/galacticodyssey/gateway/ZoneRouter.java gateway/src/test/java/com/galacticodyssey/gateway/ZoneRouterTest.java
git commit -m "feat(net): add ZoneRouter for player-to-zone routing via PostgreSQL"
```

---

### Task 14: GatewayServer + GatewayNetworkListener

**Files:**
- Create: `gateway/src/main/java/com/galacticodyssey/gateway/GatewayServer.java`
- Create: `gateway/src/main/java/com/galacticodyssey/gateway/GatewayNetworkListener.java`
- Test: `gateway/src/test/java/com/galacticodyssey/gateway/GatewayNetworkListenerTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.gateway;

import com.galacticodyssey.common.protocol.LoginRequest;
import com.galacticodyssey.common.protocol.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
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
    void loginWithRouterResolvesZone() {
        // ZoneRouter is null in this test, so response should still succeed
        // with fallback behavior (no zone address)
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
        // No crash, session cleaned up
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :gateway:test --tests "com.galacticodyssey.gateway.GatewayNetworkListenerTest" -v`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Create GatewayNetworkListener**

```java
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

    public void simulateConnected(int connectionId) {
        // No-op until login
    }

    public void simulateReceived(int connectionId, Object message) {
        if (message instanceof LoginRequest login) {
            mainThreadPoster.accept(() -> handleLogin(connectionId, login));
        }
    }

    public void simulateDisconnected(int connectionId) {
        mainThreadPoster.accept(() -> {
            String token = connectionTokens.remove(connectionId);
            if (token != null) {
                sessionManager.destroySession(token);
            }
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
                // Fall through — route with default zone
            }
        }

        if (zoneId == null) {
            zoneId = "00000000-0000-0000-0000-000000000001";
        }

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
```

- [ ] **Step 4: Add `zoneServerAddress` field to LoginResponse**

The existing `LoginResponse` class needs a `zoneServerAddress` field. Add it:

```java
// In LoginResponse.java, add field:
public String zoneServerAddress;
```

- [ ] **Step 5: Create GatewayServer**

```java
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

    public void start() throws IOException {
        kryoServer = new Server(131072, 16384);
        NetworkKryoRegistrar.register(kryoServer.getKryo());

        kryoServer.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                if (networkListener != null) {
                    networkListener.simulateConnected(connection.getID());
                }
            }

            @Override
            public void received(Connection connection, Object object) {
                if (networkListener != null) {
                    networkListener.simulateReceived(connection.getID(), object);
                }
            }

            @Override
            public void disconnected(Connection connection) {
                if (networkListener != null) {
                    networkListener.simulateDisconnected(connection.getID());
                }
            }
        });

        kryoServer.bind(config.tcpPort);
        kryoServer.start();
    }

    public void setNetworkListener(GatewayNetworkListener listener) {
        this.networkListener = listener;
    }

    public void stop() {
        if (kryoServer != null) {
            kryoServer.stop();
            kryoServer.close();
        }
    }

    public Config getConfig() {
        return config;
    }

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        GatewayServer server = new GatewayServer(config);
        server.start();
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `gradlew.bat :gateway:test --tests "com.galacticodyssey.gateway.GatewayNetworkListenerTest" -v`
Expected: PASS (4 tests)

- [ ] **Step 7: Commit**

```bash
git add gateway/src/main/java/com/galacticodyssey/gateway/GatewayServer.java gateway/src/main/java/com/galacticodyssey/gateway/GatewayNetworkListener.java gateway/src/test/java/com/galacticodyssey/gateway/GatewayNetworkListenerTest.java common/src/main/java/com/galacticodyssey/common/protocol/LoginResponse.java
git commit -m "feat(net): add GatewayServer and GatewayNetworkListener for client routing"
```

---

### Task 15: PlayerSession Zone Tracking + ZoneHandoffManager

**Files:**
- Modify: `server/src/main/java/com/galacticodyssey/server/network/PlayerSession.java` — add zoneId field
- Create: `server/src/main/java/com/galacticodyssey/server/zone/ZoneHandoffManager.java`
- Test: `server/src/test/java/com/galacticodyssey/server/zone/ZoneHandoffManagerTest.java`

- [ ] **Step 1: Add zoneId to PlayerSession**

Add to `PlayerSession.java`:

```java
private UUID zoneId;

public UUID getZoneId() { return zoneId; }
public void setZoneId(UUID zoneId) { this.zoneId = zoneId; }
```

- [ ] **Step 2: Write the test**

```java
package com.galacticodyssey.server.zone;

import com.galacticodyssey.common.protocol.HandoffPrepare;
import com.galacticodyssey.common.protocol.HandoffTransferAck;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneHandoffManagerTest {

    @Test
    void initiateHandoffCreatesPrepareMessage() {
        List<HandoffPrepare> published = new ArrayList<>();
        ZoneHandoffManager manager = new ZoneHandoffManager(published::add);

        UUID sourceZone = UUID.randomUUID();
        UUID targetZone = UUID.randomUUID();
        byte[] entityData = new byte[]{1, 2, 3};

        manager.initiateHandoff(42, sourceZone, targetZone, entityData, "tok-player");

        assertEquals(1, published.size());
        HandoffPrepare prep = published.get(0);
        assertEquals(42, prep.entityNetworkId);
        assertEquals(sourceZone, prep.sourceZoneId);
        assertEquals(targetZone, prep.targetZoneId);
        assertArrayEquals(entityData, prep.serializedEntityData);
        assertEquals("tok-player", prep.playerSessionToken);
    }

    @Test
    void tracksPendingHandoff() {
        ZoneHandoffManager manager = new ZoneHandoffManager(p -> {});
        UUID sourceZone = UUID.randomUUID();
        UUID targetZone = UUID.randomUUID();

        manager.initiateHandoff(42, sourceZone, targetZone, new byte[0], "tok");
        assertTrue(manager.isPending(42));
        assertFalse(manager.isPending(99));
    }

    @Test
    void acknowledgeHandoffCompletesIt() {
        ZoneHandoffManager manager = new ZoneHandoffManager(p -> {});
        UUID sourceZone = UUID.randomUUID();
        UUID targetZone = UUID.randomUUID();

        manager.initiateHandoff(42, sourceZone, targetZone, new byte[0], "tok");

        HandoffTransferAck ack = new HandoffTransferAck();
        ack.entityNetworkId = 42;
        ack.sourceZoneId = sourceZone;
        ack.targetZoneId = targetZone;
        ack.success = true;

        ZoneHandoffManager.HandoffResult result = manager.acknowledgeHandoff(ack);
        assertNotNull(result);
        assertTrue(result.success());
        assertEquals("tok", result.playerSessionToken());
        assertFalse(manager.isPending(42));
    }

    @Test
    void acknowledgeUnknownHandoffReturnsNull() {
        ZoneHandoffManager manager = new ZoneHandoffManager(p -> {});

        HandoffTransferAck ack = new HandoffTransferAck();
        ack.entityNetworkId = 99;
        ack.success = true;

        assertNull(manager.acknowledgeHandoff(ack));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.zone.ZoneHandoffManagerTest" -v`
Expected: FAIL — class doesn't exist.

- [ ] **Step 4: Create ZoneHandoffManager**

```java
package com.galacticodyssey.server.zone;

import com.galacticodyssey.common.protocol.HandoffPrepare;
import com.galacticodyssey.common.protocol.HandoffTransferAck;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ZoneHandoffManager {

    public record HandoffResult(boolean success, String playerSessionToken, UUID targetZoneId) {}

    private record PendingHandoff(UUID sourceZoneId, UUID targetZoneId, String playerSessionToken) {}

    private final Consumer<HandoffPrepare> publishCallback;
    private final Map<Integer, PendingHandoff> pendingHandoffs = new HashMap<>();

    public ZoneHandoffManager(Consumer<HandoffPrepare> publishCallback) {
        this.publishCallback = publishCallback;
    }

    public void initiateHandoff(int entityNetworkId, UUID sourceZoneId, UUID targetZoneId,
                                byte[] serializedEntityData, String playerSessionToken) {
        HandoffPrepare prepare = new HandoffPrepare();
        prepare.entityNetworkId = entityNetworkId;
        prepare.sourceZoneId = sourceZoneId;
        prepare.targetZoneId = targetZoneId;
        prepare.serializedEntityData = serializedEntityData;
        prepare.playerSessionToken = playerSessionToken;

        pendingHandoffs.put(entityNetworkId, new PendingHandoff(sourceZoneId, targetZoneId, playerSessionToken));
        publishCallback.accept(prepare);
    }

    public boolean isPending(int entityNetworkId) {
        return pendingHandoffs.containsKey(entityNetworkId);
    }

    public HandoffResult acknowledgeHandoff(HandoffTransferAck ack) {
        PendingHandoff pending = pendingHandoffs.remove(ack.entityNetworkId);
        if (pending == null) return null;

        return new HandoffResult(ack.success, pending.playerSessionToken, pending.targetZoneId);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `gradlew.bat :server:test --tests "com.galacticodyssey.server.zone.ZoneHandoffManagerTest" -v`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add server/src/main/java/com/galacticodyssey/server/network/PlayerSession.java server/src/main/java/com/galacticodyssey/server/zone/ZoneHandoffManager.java server/src/test/java/com/galacticodyssey/server/zone/ZoneHandoffManagerTest.java
git commit -m "feat(net): add ZoneHandoffManager for 4-phase zone transfers"
```

---

### Task 16: Verify Full Build

- [ ] **Step 1: Run full build**

Run: `gradlew.bat clean build -x :desktop:dist`
Expected: All modules compile. All tests pass (except known pre-existing failures in core).

- [ ] **Step 2: Verify test counts per module**

Run: `gradlew.bat :common:test :server:test :gateway:test :core:test --tests "com.galacticodyssey.networking.*"`
Expected:
- `common:test` — PASS (includes zone protocol tests)
- `server:test` — PASS (includes zone + persistence tests)
- `gateway:test` — PASS (session manager + zone router + gateway listener tests)
- `core:test` networking — PASS (includes GhostComponent + all prior networking tests)

- [ ] **Step 3: Commit verification note (if needed)**

No commit needed if everything passes. If minor fixes were needed, commit them.
