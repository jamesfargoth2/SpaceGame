# Networking System Design

Full multi-zone, server-authoritative multiplayer networking for Galactic Odyssey. Built bottom-up in layers: transport, server tick, replication, prediction, interpolation, zone architecture, persistence.

## 1. Transport Layer & Message Protocol

### Dependencies

KryoNet 2.22.9 added to `common/build.gradle.kts` (transitively available to core, server, and gateway). New `common/` Gradle module holds shared protocol classes (pure Java, Kryo + KryoNet only — no libGDX rendering).

### Module Dependency Graph

```
common  ← core ← desktop (client)
  ↑        ↑
  |      server (zone server)
  |
gateway
```

### Message Hierarchy

All network messages extend a base `NetworkMessage` class.

#### TCP (reliable, ordered)

Connection lifecycle:
- `LoginRequest` — username, clientVersion
- `LoginResponse` — sessionToken, zoneServerAddress, playerId
- `Heartbeat` / `HeartbeatAck` — timestamps for RTT calculation
- `Disconnect` — reason enum

Zone management:
- `ZoneJoinRequest` — sessionToken, zoneId
- `ZoneJoinResponse` — worldSnapshot (full state of interest sphere)
- `ZoneRedirect` — newZoneAddress, handoffToken
- `OriginRebaseMessage` — offsetX/Y/Z as doubles

Entity lifecycle:
- `EntitySpawnMessage` — full snapshot of all replicated components + networkId + entityType
- `EntityDestroyMessage` — networkId

Replicated game events:
- `DamageEvent`, `EntityKilledEvent`, `WeaponFiredEvent`, `ShieldAbsorbEvent`

Economy:
- `TradeCompleted`, `WalletUpdate`, `CargoUpdate`

Chat:
- `ChatMessage`

#### UDP (lossy, high-frequency)

- `InputPacket` — bundles 3 client inputs per server tick (20Hz). Each input: moveForward, moveStrafe, mouseDeltaX/Y, jump, sprint, crouch, fire, ads, throttle, pitch, yaw, roll, playerMode. Includes sequence number. Redundant sending: current 3 + previous 3 inputs (~4.8 KB/s).
- `EntityStateUpdate` — single entity delta: entityId (int), tick number, dirty mask (bitfield), only changed fields serialized.
- `EntityBatchUpdate` — wraps multiple `EntityStateUpdate` + `lastProcessedInputSequence` for reconciliation.

### Kryo Registration

New network message IDs in range 110–149, appended to `KryoRegistrar`. Registration order is a versioning contract — append only, never reorder.

### KryoNet Configuration

- Write buffer: 128 KB
- Object buffer: 16 KB
- Heartbeat interval: 2 seconds
- Timeout threshold: 10 seconds
- Reconnection grace period: 30 seconds

### Thread Safety

KryoNet listeners run on a background thread. All game state access dispatched to main thread via `Gdx.app.postRunnable()`.

---

## 2. Server Tick Loop & Authoritative Simulation

### Server Architecture

New `DedicatedServer` class replaces the current `ServerLauncher`'s direct use of `GalacticOdyssey`. Runs a headless Ashley ECS engine at a fixed 20Hz tick rate (50ms per tick).

### Tick Loop (per tick)

1. **Receive inputs** — drain queued `InputPacket` messages from KryoNet listener thread (posted via `postRunnable`).
2. **Apply inputs** — map each player's inputs to their entity's `PlayerInputComponent` (FPS mode) or `ShipFlightComponent` (piloting mode). Process 3 bundled inputs per tick.
3. **Simulate** — step the Ashley engine. All existing game systems run identically to the client, minus rendering/audio/UI systems.
4. **Replication** — `ServerReplicationSystem` (priority 50, after all game logic) builds `EntityBatchUpdate` packets per connected player based on their interest sphere.
5. **Send state** — dispatch UDP state updates and any queued TCP events to each client.

### Shared Code Principle

`PlayerMovementSystem` and `ShipFlightSystem` live in `core/` and run identically on client and server. The server instantiates them with its own `btDiscreteDynamicsWorld`. Movement must be deterministic — same inputs + same state = same output. Fixed timestep (50ms), not variable `deltaTime`.

### New Classes — Server Module

- `DedicatedServer` — headless application, owns the tick loop.
- `ZoneServer` — manages one zone's ECS engine, player connections, interest management.
- `PlayerSession` — tracks connection, authenticated player ID, session token, last input sequence, zone assignment.
- `ServerNetworkListener` — KryoNet `Listener` implementation, queues messages for main thread.

### New Classes — Core Module (shared)

- `NetworkIdComponent` — compact integer ID for entities (not UUID — too expensive for per-tick replication). Server assigns these; maps to/from `PersistenceIdComponent` UUIDs for save/load.
- `AuthorityComponent` — marks who owns an entity (server, or which zone server in multi-zone).
- `ReplicatedComponent` — marker interface for components that should be network-synced.

### System Priority Order on Server

| Priority | System |
|----------|--------|
| 1 | PlayerMovementSystem |
| 2 | ShipFlightSystem |
| 3 | BulletPhysicsSystem |
| 4 | PhysicsBodySystem |
| 10 | CombatAISystem |
| 11 | WeaponSystem / HitscanSystem / ProjectileSystem |
| 12 | DamageSystem |
| 20 | StatusEffectSystem |
| 30 | EconomySystem |
| 50 | ServerReplicationSystem |

---

## 3. Entity Replication & Interest Management

### Authority Model

Server owns all entity state. Clients send only `InputPacket` messages — never entity state.

### Interest Tiers

| Tier | Range | Update Rate | Use Case |
|------|-------|-------------|----------|
| NEAR | 0–500m | Every tick (20Hz) | Combat range |
| MID | 500–2000m | Every 4th tick (5Hz) | Visible ships |
| FAR | 2000–10000m | Every 10th tick (2Hz) | Distant objects |
| NONE | >10000m | Not replicated | Entity doesn't exist on client |

Tier assignment recalculated every 500ms per client using squared distance checks against the player's galaxy-space position.

### Replicated Components

| Replicated | Not Replicated |
|---|---|
| TransformComponent | PlayerInputComponent |
| HealthComponent | FPSCameraComponent |
| ShieldComponent | PhysicsBodyComponent (rebuilt client-side) |
| ShipFlightComponent | CombatAIComponent (server-only) |
| ShipDataComponent | CargoBayComponent (owner-only, via TCP) |
| WeaponInventoryComponent | PlayerWalletComponent (owner-only, via TCP) |
| MovementStateComponent | CrosshairComponent |
| ArmorComponent | RecoilComponent |
| StatusEffectsComponent | ScreenShakeComponent, ADSComponent |

### Delta Compression

Each replicated component tracks a dirty bitfield. `ServerReplicationSystem` compares current tick state against the last-sent state per client. Only changed fields are serialized.

Sizes:
- Full snapshot (entity enters interest sphere): 80–200 bytes
- Delta, position only: 16 bytes
- Delta, position + rotation: 32 bytes

### Entity Lifecycle (TCP)

- `EntitySpawnMessage` — sent when entity enters interest sphere. Full snapshot + networkId + entityType.
- `EntityDestroyMessage` — sent when entity leaves interest sphere or is destroyed.

### Floating Origin

All replication packets use galaxy-space doubles (from `TransformSnapshot`). Clients convert to local-space floats relative to their `CoordinateManager` origin.

### New Classes

- `ReplicationState` — per-client, per-entity: last sent snapshot, current interest tier, tick counter for tier-based throttling.
- `InterestManager` — spatial queries for per-client entity visibility, assigns tiers.
- `DirtyTracker` — per-component bitfield tracking changed fields since last replication.
- `EntityStateSerializer` — reads dirty mask, serializes only changed fields via Kryo.

---

## 4. Client Prediction & Server Reconciliation

### Prediction Pipeline (60Hz, every client frame)

1. Sample input from `PlayerInputComponent` (FPS) or ship controls.
2. Apply movement locally using the same `PlayerMovementSystem` / `ShipFlightSystem` the server runs — identical code, fixed timestep.
3. Store input + predicted state in a ring buffer (128 slots, ~2 seconds at 60fps).
4. Send `InputPacket` to server (bundled 3 per tick at 20Hz, redundant previous 3).

### Reconciliation (on receiving `EntityBatchUpdate`)

1. Read `lastProcessedInputSequence` from response.
2. Discard buffered inputs with sequence <= that number.
3. Compare server's authoritative position to predicted position at that sequence.
4. **Within 0.01m:** Accept prediction. No correction.
5. **Between 0.01m and 5.0m:** Snap physics to server state, replay all unacknowledged inputs. Apply visual smoothing offset decaying over 10 frames.
6. **Beyond 5.0m:** Hard snap (teleport). Covers zone transitions, origin rebases, respawns.

### Visual Smoothing

Physics/logic position is always authoritative. A separate `predictionOffset` vector tracks the visual difference and lerps toward zero over 10 frames. Rendering uses `logicPosition + predictionOffset`.

### Both Movement Modes

- **FPS on-foot:** Predict `PlayerMovementSystem`. Inputs: moveForward, moveStrafe, jump, sprint, crouch. Reconcile position + velocity.
- **Ship piloting:** Predict `ShipFlightSystem`. Inputs: throttle, pitch, yaw, roll, strafe. Reconcile position + rotation + velocity + angular velocity.
- `PlayerStateComponent.currentMode` determines which path runs. Mode transitions are server-authoritative (TCP).

### Lag Compensation for Combat (Server-Side)

- Server maintains 15-tick position history per entity (~750ms at 20Hz).
- On hitscan fire: rewind entity positions to the tick the client fired at (derived from input sequence + estimated latency).
- Hit detection runs against historical hitbox positions.
- Implemented as `LagCompensationSystem` on server, wrapping `HitscanSystem`.

### New Classes

- `PredictionComponent` — local player entity. Input ring buffer, sequence number, prediction offset, smoothing counter.
- `ClientPredictionSystem` — client-only. Samples input, predicts, stores in buffer. Priority 0.
- `ReconciliationSystem` — client-only. Processes server updates, compares, replays. Runs after network receive.
- `InputBuffer` — ring buffer of `TimestampedInput` (sequence number, input snapshot, predicted state).

---

## 5. Remote Entity Interpolation

### Strategy — Render 100ms Behind

Remote entities maintain a buffer of recent server states. Rendering interpolates at a point 100ms (one server tick) in the past, guaranteeing two states to interpolate between despite packet jitter.

### Interpolation Buffer

- 4 `EntityStateUpdate` snapshots per remote entity (tick number + state).
- Rendering picks two snapshots straddling `currentServerTick - 2`.
- Interpolation factor `t` derived from elapsed local time between the two snapshots.

### What Gets Interpolated

| Interpolated (continuous) | Not Interpolated (discrete) |
|---|---|
| Position (lerp, galaxy doubles → local float) | Health, shield (snap to latest) |
| Rotation (slerp quaternion) | Status effects (TCP events) |
| Ship throttle/velocity (lerp) | Equipment changes (TCP events) |

### Extrapolation Fallback

- Late packet: extrapolate using last known velocity for up to 500ms.
- After 500ms without update: freeze at last extrapolated position.
- Next packet arrives: blend from frozen to new interpolation target over 5 frames.

### Floating Origin Integration

On `OriginRebaseMessage`, all positions in every remote entity's interpolation buffer are offset by the rebase delta. `InterpolationSystem` subscribes to `OriginRebaseEvent` on the `EventBus`.

### Bandwidth Budget

| Tier | Entities | Bytes/update | Rate | Bandwidth |
|------|----------|-------------|------|-----------|
| NEAR | 200 | 16 avg | 20/s | 64 KB/s |
| MID | 100 | 32 avg | 5/s | 16 KB/s |
| FAR | 50 | 32 avg | 2/s | 3.2 KB/s |
| **Total** | | | | **~83 KB/s** |

### New Classes

- `InterpolationComponent` — per remote entity. Snapshot ring buffer (4 slots), interpolation time offset, extrapolation timer.
- `InterpolationSystem` — client-only, priority 55 (after game logic, before rendering). Lerps/slerps between bracketing snapshots.
- `SnapshotBuffer` — generic timestamped ring buffer. Handles out-of-order packet dropping and bracketing queries.

---

## 6. Zone Architecture

### Gateway Server (`gateway/` module)

Stateless authentication and routing. Multiple instances in production, one locally.

1. Client sends `LoginRequest` (TCP) with username.
2. Gateway generates session token → stores in Redis (`session:{token}`, 5-min TTL).
3. Looks up player's last known position in PostgreSQL → resolves owning zone server.
4. Returns `LoginResponse` with session token + zone server address.
5. Client disconnects from gateway, connects to zone server.

### Zone Server (`server/` module)

Each zone server owns one or more sectors defined by `ZoneDefinition`:
- Axis-aligned cuboid in galaxy-space doubles (minX/maxX, minY/maxY, minZ/maxZ)
- List of adjacent zone IDs
- Boundary overlap: 1000 units for ghost entity sharing

On `ZoneJoinRequest`: validates session token against Redis, spawns player entity, sends `WorldSnapshot`.

`ZoneBoundaryMonitor` checks player positions against zone boundaries every 500ms.

### Zone Boundary Handoff (4-phase)

| Phase | Action | Channel |
|-------|--------|---------|
| PREPARE | Source serializes entity, publishes to target zone | Redis `zone.handoff.prepare.{targetZoneId}` |
| TRANSFER | Target creates entity, sends ack | Redis `zone.handoff.ack.{sourceZoneId}` |
| CONFIRM | Target processes inputs. Source sends `ZoneRedirect` to client | KryoNet TCP |
| RELEASE | Client connects to new zone. Source removes entity | KryoNet + ECS |

During handoff (~200ms), entity exists in both zones. Source stops processing inputs but continues sending state. Client sees no loading screen — prediction continues during redirect.

### Ghost Entities

- Entities within 1000m of zone boundary publish replicated state to Redis `zone.border.{zoneId}` every tick.
- Adjacent zone servers create read-only ghost entities (`GhostComponent` marking owning zone).
- Visible in interest management, rendered on clients, not simulated locally.
- Combat targeting a ghost: zone server forwards the action to the owning zone via Redis.
- Removed when source entity moves >1200m from boundary (200m hysteresis).

### Galaxy Simulation Worker (separate process)

For sectors with no active zone server:
- Statistical simulation: faction control, economy supply/demand, NPC fleet movement.
- Cadence: every 10 seconds for adjacent-to-active sectors, every 5 minutes for distant.
- On player entry: gateway spins up zone server, worker hands off state, entities promoted to full ECS.
- Communicates via Redis pub/sub and PostgreSQL.

### Load Balancing

- `ZoneLoadBalancer` reads `zone:{zoneId}:load` from Redis.
- Rebalance trigger: >80% CPU or >200 players on a zone server.
- Zone splitting: split along longest axis.
- Cold migration acceptable for non-player zones.

---

## 7. Persistence

### PostgreSQL Schema

#### `zone_assignments`

| Column | Type | Notes |
|--------|------|-------|
| zone_id | UUID | PK |
| sector_min_x/y/z | double precision | Cuboid bounds |
| sector_max_x/y/z | double precision | |
| server_instance | varchar | hostname:port |
| adjacent_zones | UUID[] | |
| status | enum | ACTIVE, DORMANT, MIGRATING |

#### `players`

| Column | Type | Notes |
|--------|------|-------|
| player_id | UUID | PK |
| username | varchar | Unique |
| last_zone_id | UUID | FK → zone_assignments |
| last_galaxy_x/y/z | double precision | |
| inventory | JSONB | Serialized InventorySnapshot |
| wallet | JSONB | Serialized PlayerWalletSnapshot |
| player_state | JSONB | Serialized PlayerStateSnapshot |
| created_at | timestamptz | |
| last_login | timestamptz | |

#### `entities`

| Column | Type | Notes |
|--------|------|-------|
| entity_id | UUID | PK |
| zone_id | UUID | FK → zone_assignments |
| entity_type | varchar | ship, station, npc, etc. |
| galaxy_x/y/z | double precision | |
| component_state | JSONB | Serialized component snapshots |
| is_active | boolean | In active zone server ECS |
| updated_at | timestamptz | |

#### `sector_state`

| Column | Type | Notes |
|--------|------|-------|
| sector_id | UUID | PK, FK → zone_assignments |
| faction_control | JSONB | faction → percentage |
| resource_levels | JSONB | resource → quantity |
| population | bigint | |
| trade_demand | JSONB | |
| trade_supply | JSONB | |
| simulated_at | timestamptz | |

### Save Cadence

| Data | Frequency | Method |
|------|-----------|--------|
| Player position | Every 30 seconds | Batch upsert |
| Inventory/wallet | On change | Immediate write |
| Entity positions | Every 60 seconds | Batch upsert |
| Economy/faction | Every 5 minutes | Sector aggregate |
| Combat outcomes | On resolution | Event-driven |

Zone servers buffer dirty entities and flush on cadence.

### Redis Usage

| Key/Channel | Purpose | TTL |
|-------------|---------|-----|
| `session:{token}` | Player session (playerId, zoneId) | 5 min, refreshed on heartbeat |
| `zone:{zoneId}:load` | Zone server load metrics | 10 sec |
| `zone.border.{zoneId}` | Ghost entity state (pub/sub) | N/A |
| `zone.handoff.prepare.{zoneId}` | Handoff initiation (pub/sub) | N/A |
| `zone.handoff.ack.{zoneId}` | Handoff confirmation (pub/sub) | N/A |
| `zone.command.{zoneId}` | Admin commands (pub/sub) | N/A |
| `galaxy.simulation` | Worker coordination (pub/sub) | N/A |

### Docker Compose (local dev)

```yaml
services:
  postgres:
    image: postgres:16
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: galactic_odyssey
      POSTGRES_USER: galactic
      POSTGRES_PASSWORD: dev_only
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
volumes:
  pgdata:
```

`sql/init.sql` creates all four tables, indexes on spatial columns, and foreign keys.

### Server Dependencies

- `server/build.gradle.kts`: HikariCP (connection pool), PostgreSQL JDBC driver, Jedis (Redis client)
- `gateway/build.gradle.kts`: same Redis + PostgreSQL dependencies
- `common/`: no database dependencies

---

## 8. Deployment Configurations

### Local Development

- 1x Gateway (localhost:7000)
- 1x Zone Server (localhost:7100)
- 1x PostgreSQL (Docker, localhost:5432)
- 1x Redis (Docker, localhost:6379)
- Galaxy simulation worker optional (can run in-process with zone server for dev)

### Production (1000+ players)

- 2x Gateway (behind load balancer)
- 10–20x Zone Servers (5–10 sectors each)
- 1x Galaxy Simulation Worker
- PostgreSQL cluster (primary + replica)
- Redis cluster (3-node HA)

---

## 9. New File Summary

### `common/` module (new)

```
common/src/main/java/com/galacticodyssey/common/
  protocol/
    NetworkMessage.java
    LoginRequest.java, LoginResponse.java
    Heartbeat.java, HeartbeatAck.java
    Disconnect.java
    ZoneJoinRequest.java, ZoneJoinResponse.java
    ZoneRedirect.java
    OriginRebaseMessage.java
    InputPacket.java, TimestampedInput.java
    EntitySpawnMessage.java, EntityDestroyMessage.java
    EntityStateUpdate.java, EntityBatchUpdate.java
    DamageEvent.java, EntityKilledEvent.java
    WeaponFiredEvent.java, ShieldAbsorbEvent.java
    TradeCompleted.java, WalletUpdate.java, CargoUpdate.java
    ChatMessage.java
    HandoffPrepare.java, HandoffTransferAck.java
  serialization/
    NetworkKryoRegistrar.java
    EntityStateSerializer.java
    DirtyMask.java
```

### `core/` module (additions)

```
core/src/main/java/com/galacticodyssey/networking/
  components/
    NetworkIdComponent.java
    AuthorityComponent.java
    ReplicatedComponent.java  (marker interface)
    PredictionComponent.java
    InterpolationComponent.java
    GhostComponent.java
  systems/
    ClientPredictionSystem.java
    ReconciliationSystem.java
    InterpolationSystem.java
    ClientNetworkSystem.java  (sends/receives via KryoNet)
  util/
    InputBuffer.java
    SnapshotBuffer.java
    DirtyTracker.java
```

### `server/` module (additions)

```
server/src/main/java/com/galacticodyssey/server/
  DedicatedServer.java
  zone/
    ZoneServer.java
    ZoneDefinition.java
    ZoneBoundaryMonitor.java
    ZoneLoadBalancer.java
  network/
    ServerNetworkListener.java
    PlayerSession.java
  replication/
    ServerReplicationSystem.java
    ReplicationState.java
    InterestManager.java
    LagCompensationSystem.java
  persistence/
    DatabaseManager.java
    EntityPersistenceService.java
    PlayerPersistenceService.java
    SectorStateService.java
  simulation/
    GalaxySimulationWorker.java
    StatisticalSimulator.java
```

### `gateway/` module (new)

```
gateway/src/main/java/com/galacticodyssey/gateway/
  GatewayServer.java
  GatewayNetworkListener.java
  SessionManager.java
  ZoneRouter.java
```

### Infrastructure files

```
docker-compose.yml
sql/init.sql
```
