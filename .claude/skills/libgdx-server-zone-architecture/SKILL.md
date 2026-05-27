---
name: libgdx-server-zone-architecture
description: >
  How to architect SpaceGame's server infrastructure for MMO-scale (1000+ concurrent players)
  across a galaxy-sized world. Covers spatial zone partitioning, zone server instances, seamless
  zone transitions with entity handoff, gateway/lobby routing, cross-zone visibility for border
  entities, load balancing and zone splitting, galaxy-scale NPC simulation tiers, persistence
  with PostgreSQL and Redis, and horizontal scaling patterns. Use this skill whenever the user
  wants to: partition the galaxy across multiple servers, handle zone transitions or boundary
  crossings, implement server clustering, set up a gateway/lobby server, manage cross-zone
  entity visibility, add PostgreSQL persistence for world state, use Redis for session state
  or pub/sub, scale the server horizontally, handle player connection routing, implement world
  persistence and saving, or design the server deployment topology. Also trigger for "zone
  server", "shard", "server cluster", "horizontal scaling", "zone boundary", "handoff",
  "load balancing", "server architecture", "persistence", "database", "world save",
  "too many players for one server", or "galaxy partitioning".
---

# Server Zone Architecture & Horizontal Scaling

This skill covers partitioning SpaceGame's galaxy across multiple server instances so no single server bears the full simulation load. At MMO scale with 1000+ players spread across a galaxy, a single server process cannot simulate every entity — you need spatial partitioning into zones, each owned by a server instance, with seamless handoff when players cross boundaries.

The architecture follows an industry pattern: a stateless **Gateway** routes players to the correct **Zone Server**, which owns a spatial region of the galaxy. Zone servers communicate via **Redis pub/sub** for cross-zone events, and persist world state to **PostgreSQL**. The key challenge is making zone boundaries invisible to players — no loading screens, no visible seams.

## Architecture Overview

```
                    ┌─────────────┐
                    │   Gateway   │ (stateless, load-balanced)
                    │   Server    │ Player auth, zone routing
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────┴─────┐┌────┴────┐┌─────┴─────┐
        │  Zone A   ││ Zone B  ││  Zone C   │
        │  Server   ││ Server  ││  Server   │
        │ (Sector 1)││(Sector 2)││(Sector 3) │
        └─────┬─────┘└────┬────┘└─────┬─────┘
              │            │            │
        ┌─────┴────────────┴────────────┴─────┐
        │          Redis (pub/sub + cache)      │
        └─────────────────┬────────────────────┘
                          │
        ┌─────────────────┴────────────────────┐
        │      PostgreSQL (persistent state)    │
        └──────────────────────────────────────┘
```

## Zone Partitioning Strategy

The galaxy is divided into **sectors** using the existing star system layout. Each sector is a cuboid region of galaxy-space (double-precision coordinates). One zone server owns one or more sectors.

```
core/src/main/java/com/galacticodyssey/networking/zone/
  ZoneDefinition.java         Spatial bounds of a zone (galaxy-space doubles)
  ZoneRegistry.java           Maps galaxy coordinates to zone IDs
  ZoneAssignment.java         Which zone server owns which zones

server/src/main/java/com/galacticodyssey/server/zone/
  ZoneServer.java             Main loop for a zone server instance
  ZoneSimulation.java         Runs Ashley ECS for entities in this zone
  ZoneBoundaryMonitor.java    Detects entities approaching zone edges
  ZoneHandoffManager.java     Transfers entities between zone servers
  CrossZoneEventBus.java      Redis-backed event bus for inter-zone events

server/src/main/java/com/galacticodyssey/server/gateway/
  GatewayServer.java          Player authentication and zone routing
  PlayerSessionManager.java   Tracks active sessions, reconnection tokens
  ZoneLoadBalancer.java       Assigns zones to server instances based on load

server/src/main/java/com/galacticodyssey/server/persistence/
  WorldPersistenceService.java   Saves/loads world state to PostgreSQL
  PlayerDataService.java         Player-specific persistence (inventory, wallet, etc.)
  EntitySerializer.java          Converts ECS entities to/from database rows
```

### ZoneDefinition

```java
public class ZoneDefinition {
    public final int zoneId;
    public final String name;

    // Galaxy-space bounds (64-bit doubles for precision)
    public final double minX, minY, minZ;
    public final double maxX, maxY, maxZ;

    // Adjacent zone IDs for handoff routing
    public final int[] adjacentZones;

    // Boundary overlap — entities in this margin are visible from adjacent zones
    public final double boundaryOverlap = 1000.0;

    public boolean containsPoint(double gx, double gy, double gz) {
        return gx >= minX && gx <= maxX
            && gy >= minY && gy <= maxY
            && gz >= minZ && gz <= maxZ;
    }

    public boolean isInBoundaryMargin(double gx, double gy, double gz) {
        return containsPoint(gx, gy, gz) && (
            gx - minX < boundaryOverlap || maxX - gx < boundaryOverlap ||
            gy - minY < boundaryOverlap || maxY - gy < boundaryOverlap ||
            gz - minZ < boundaryOverlap || maxZ - gz < boundaryOverlap
        );
    }
}
```

### ZoneRegistry

```java
public class ZoneRegistry {
    private final List<ZoneDefinition> zones = new ArrayList<>();

    public ZoneDefinition getZoneForPosition(double gx, double gy, double gz) {
        for (ZoneDefinition zone : zones) {
            if (zone.containsPoint(gx, gy, gz)) return zone;
        }
        return null;  // in deep space between sectors — should not happen
    }

    public List<ZoneDefinition> getAdjacentZones(int zoneId) {
        ZoneDefinition zone = getZoneById(zoneId);
        List<ZoneDefinition> result = new ArrayList<>();
        for (int adjId : zone.adjacentZones) {
            result.add(getZoneById(adjId));
        }
        return result;
    }
}
```

Zone definitions are loaded from a data file (`data/zones/zone_definitions.json`) — consistent with the data-driven content principle in CLAUDE.md.

## Gateway Server

The gateway is the player's first point of contact. It handles authentication, determines which zone the player belongs to, and routes them to the correct zone server.

```java
public class GatewayServer {
    private final PlayerSessionManager sessions;
    private final ZoneLoadBalancer loadBalancer;
    private final ZoneRegistry zoneRegistry;

    public void onPlayerConnect(Connection connection, LoginRequest request) {
        // 1. Authenticate (validate credentials against database)
        PlayerData player = authenticate(request.username, request.token);
        if (player == null) {
            connection.sendTCP(new LoginResponse(false, "Invalid credentials"));
            return;
        }

        // 2. Determine which zone the player's last known position belongs to
        ZoneDefinition zone = zoneRegistry.getZoneForPosition(
            player.lastGalaxyX, player.lastGalaxyY, player.lastGalaxyZ);

        // 3. Find which server instance owns that zone
        String zoneServerAddress = loadBalancer.getServerForZone(zone.zoneId);

        // 4. Create a session token and send redirect
        String sessionToken = sessions.createSession(player.playerId, zone.zoneId);
        connection.sendTCP(new LoginResponse(true, zoneServerAddress, sessionToken));
    }
}
```

The gateway is **stateless** — session tokens are stored in Redis, so multiple gateway instances can run behind a load balancer for redundancy.

### PlayerSessionManager

```java
public class PlayerSessionManager {
    private final RedisClient redis;

    public String createSession(int playerId, int zoneId) {
        String token = UUID.randomUUID().toString();
        // Store in Redis with TTL — if the player disconnects, session expires
        redis.setex("session:" + token, 300,
            playerId + ":" + zoneId);
        return token;
    }

    public PlayerSession validateSession(String token) {
        String data = redis.get("session:" + token);
        if (data == null) return null;
        String[] parts = data.split(":");
        return new PlayerSession(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    public void refreshSession(String token) {
        redis.expire("session:" + token, 300);
    }
}
```

## Zone Server

Each zone server runs a full Ashley ECS simulation for the entities in its spatial region. It's essentially the current `ServerLauncher` extended with zone awareness.

```java
public class ZoneServer {
    private final int zoneId;
    private final ZoneDefinition zoneDef;
    private final GameWorld gameWorld;
    private final ZoneSimulation simulation;
    private final ZoneBoundaryMonitor boundaryMonitor;
    private final ZoneHandoffManager handoffManager;
    private final CrossZoneEventBus crossZoneBus;
    private final ServerReplicationSystem replication;

    public void start() {
        // Load zone definition and entities from database
        zoneDef = zoneRegistry.getZoneById(zoneId);
        List<EntityData> entities = persistence.loadEntitiesInZone(zoneId);

        // Bootstrap ECS world with zone's entities
        gameWorld = new GameWorld();
        for (EntityData data : entities) {
            gameWorld.spawnEntity(data);
        }

        // Add zone-specific systems
        gameWorld.getEngine().addSystem(boundaryMonitor);
        gameWorld.getEngine().addSystem(replication);

        // Subscribe to cross-zone events from Redis
        crossZoneBus.subscribe(zoneId);
    }

    public void onPlayerJoin(Connection connection, String sessionToken) {
        PlayerSession session = sessionManager.validateSession(sessionToken);
        if (session == null || session.zoneId != zoneId) {
            connection.close();
            return;
        }

        // Load player data from database
        PlayerData playerData = playerDataService.loadPlayer(session.playerId);

        // Create player entity in ECS
        Entity playerEntity = spawnPlayerEntity(playerData);

        // Send world snapshot for entities in player's interest sphere
        SnapshotBuilder snapshot = new SnapshotBuilder(interestManager, serializer);
        connection.sendTCP(snapshot.buildJoinSnapshot(
            session.playerId, playerData.getPosition()));

        // Register for replication updates
        replication.addClient(session.playerId, connection);
    }
}
```

### ZoneSimulation

The zone simulation runs the standard game loop but only for entities within the zone's spatial bounds:

```java
public class ZoneSimulation {
    private static final float TICK_RATE = 1f / 20f;  // 20 Hz server tick
    private final GameWorld gameWorld;
    private float accumulator;

    public void update(float realDelta) {
        accumulator += realDelta;
        while (accumulator >= TICK_RATE) {
            gameWorld.getEngine().update(TICK_RATE);
            accumulator -= TICK_RATE;
        }
    }
}
```

Fixed timestep is essential for deterministic simulation — variable timestep causes client-server divergence.

## Zone Boundary Handoff

The most complex part: transferring an entity from one zone server to another without the player noticing.

### ZoneBoundaryMonitor

Detects entities approaching the zone boundary and initiates handoff:

```java
public class ZoneBoundaryMonitor extends IntervalSystem {
    private static final float CHECK_INTERVAL = 0.5f;  // check every 500ms
    private static final double HANDOFF_TRIGGER_DISTANCE = 500.0;

    private final ZoneDefinition zoneDef;
    private final ZoneHandoffManager handoffManager;

    public ZoneBoundaryMonitor(ZoneDefinition zoneDef, ZoneHandoffManager handoffManager) {
        super(CHECK_INTERVAL, /* priority */ 45);
        this.zoneDef = zoneDef;
        this.handoffManager = handoffManager;
    }

    @Override
    protected void updateInterval() {
        for (Entity entity : getNetworkedEntities()) {
            double[] galaxyPos = getGalaxyPosition(entity);
            double gx = galaxyPos[0], gy = galaxyPos[1], gz = galaxyPos[2];

            if (!zoneDef.containsPoint(gx, gy, gz)) {
                // Entity has fully crossed — initiate handoff now
                ZoneDefinition targetZone = zoneRegistry.getZoneForPosition(gx, gy, gz);
                if (targetZone != null) {
                    handoffManager.initiateHandoff(entity, targetZone.zoneId);
                }
            } else if (zoneDef.isInBoundaryMargin(gx, gy, gz)) {
                // Entity is near boundary — start sharing with adjacent zone
                handoffManager.startBorderSharing(entity, getAdjacentZoneId(gx, gy, gz));
            }
        }
    }
}
```

### ZoneHandoffManager

The handoff is a multi-step protocol to prevent the entity from "blinking":

```java
public class ZoneHandoffManager {
    private final CrossZoneEventBus crossZoneBus;
    private final WorldPersistenceService persistence;

    /**
     * Handoff protocol:
     * 1. PREPARE: Serialize entity state, notify target zone via Redis
     * 2. TRANSFER: Target zone creates entity from serialized state
     * 3. CONFIRM: Target zone confirms entity is live
     * 4. RELEASE: Source zone removes entity from its simulation
     *
     * During steps 1-3, the entity exists in BOTH zones briefly.
     * The source zone stops processing inputs for the entity but
     * continues sending its last known state to connected clients.
     */

    public void initiateHandoff(Entity entity, int targetZoneId) {
        int networkId = getNetworkId(entity);

        // Step 1: Serialize complete entity state
        EntityTransferData transferData = serializeEntity(entity);
        transferData.sourceZoneId = this.zoneId;
        transferData.targetZoneId = targetZoneId;

        // Publish to Redis — target zone is listening
        crossZoneBus.publish("zone.handoff.prepare." + targetZoneId, transferData);

        // Mark entity as "handing off" — stop processing inputs, keep sending state
        markEntityHandingOff(entity);
    }

    // Called when target zone confirms it has the entity
    public void onHandoffConfirmed(int networkId, int targetZoneId) {
        Entity entity = getEntityByNetworkId(networkId);
        if (entity == null) return;

        // For player entities: redirect the client connection to target zone
        if (isPlayer(entity)) {
            int clientId = getOwnerClientId(entity);
            String targetAddress = loadBalancer.getServerForZone(targetZoneId);
            sendRedirect(clientId, targetAddress, networkId);
        }

        // Remove entity from this zone's simulation
        getEngine().removeEntity(entity);
    }
}
```

### Client-Side Zone Transition

From the client's perspective, a zone transition should be invisible. The client receives a redirect message with the new zone server address:

```java
// Client-side handler:
public void onZoneRedirect(ZoneRedirectMessage msg) {
    // 1. Keep rendering the current scene — don't show a loading screen
    // 2. Connect to new zone server in the background
    newConnection = kryoClient.connect(msg.newServerAddress, msg.newServerPort);
    newConnection.sendTCP(new ZoneJoinRequest(msg.sessionToken, msg.networkId));

    // 3. When new zone sends first state update, switch over
    // The InterpolationSystem smooths the transition
    // 4. Disconnect from old zone server
}
```

The trick: connect to the new zone *before* disconnecting from the old one. During the brief overlap, the client receives state from both servers. The old server's updates are gradually phased out as the new server's updates take over.

## Cross-Zone Entity Visibility

Entities near a zone boundary must be visible to players in the adjacent zone. Without this, ships would pop in and out at zone edges.

### Border Sharing

When an entity enters the boundary margin (1000 units from the edge), its state is broadcast to adjacent zone servers via Redis:

```java
public class CrossZoneEventBus {
    private final JedisPubSub pubSub;
    private final Jedis publisher;

    public void publishEntityState(int targetZoneId, int networkId,
                                    EntityStateSnapshot state) {
        String channel = "zone.border." + targetZoneId;
        byte[] data = serializeState(state);
        publisher.publish(channel.getBytes(), data);
    }

    public void subscribe(int zoneId) {
        String channel = "zone.border." + zoneId;
        new Thread(() -> {
            pubSub.subscribe(channel);
        }).start();
    }

    // Received by the adjacent zone server:
    public void onBorderEntityState(int networkId, EntityStateSnapshot state) {
        // Create or update a "ghost" entity — visible but not simulated
        Entity ghost = getOrCreateGhost(networkId);
        updateGhostState(ghost, state);
    }
}
```

**Ghost entities** are read-only representations of entities owned by an adjacent zone. They appear in the local zone's interest management and are replicated to clients, but the local zone doesn't simulate them — it just mirrors the state received from the owning zone.

## Galaxy-Scale NPC Simulation

At MMO scale, most of the galaxy is empty of players but still needs to "live" — factions battle, trade routes operate, economies fluctuate. This connects to the `spacegame-combat-lod-ai` skill's DORMANT tier.

### Simulation Tiers Across Zones

| Tier | Where | Simulated by | Granularity |
|---|---|---|---|
| Full ECS | Player-occupied zones | Zone server | Every tick (20 Hz) |
| Statistical | Adjacent zones, no players | Background worker | Every 10 seconds |
| Aggregate | Distant sectors | Galaxy simulation | Every 5 minutes |

The **Galaxy Simulation Worker** is a separate process (not a zone server) that runs aggregate simulation for sectors no zone server currently owns:

```java
public class GalaxySimulationWorker {
    private final WorldPersistenceService persistence;
    private final GalaxyEconomyEngine economy;

    // Runs on a timer, not at game tick rate
    public void simulateInactiveSectors() {
        List<SectorData> inactiveSectors = persistence.getSectorsWithoutZoneServer();

        for (SectorData sector : inactiveSectors) {
            // Statistical combat outcomes for dormant NPCs
            sector.applyStatisticalCombat();

            // Economy simulation — resource production, trade flow
            economy.simulateSector(sector);

            // Faction territory changes
            sector.applyFactionDynamics();

            // Persist results
            persistence.saveSectorData(sector);
        }
    }
}
```

When a player enters a previously inactive sector, a zone server spins up, loads the sector from the database (including the results of statistical simulation), and promotes dormant entities to full ECS.

## Persistence Layer

### PostgreSQL Schema (Key Tables)

```sql
-- Zone ownership and server assignments
CREATE TABLE zone_assignments (
    zone_id INT PRIMARY KEY,
    server_instance_id VARCHAR(64),
    assigned_at TIMESTAMP DEFAULT NOW(),
    entity_count INT DEFAULT 0,
    player_count INT DEFAULT 0
);

-- Persistent entity state (serialized components)
CREATE TABLE entities (
    network_id INT PRIMARY KEY,
    zone_id INT REFERENCES zone_assignments(zone_id),
    entity_type VARCHAR(32),
    galaxy_x DOUBLE PRECISION,
    galaxy_y DOUBLE PRECISION,
    galaxy_z DOUBLE PRECISION,
    component_data JSONB,  -- serialized component state
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Player-specific data
CREATE TABLE players (
    player_id INT PRIMARY KEY,
    username VARCHAR(64) UNIQUE,
    last_zone_id INT,
    last_galaxy_x DOUBLE PRECISION,
    last_galaxy_y DOUBLE PRECISION,
    last_galaxy_z DOUBLE PRECISION,
    inventory JSONB,
    wallet_credits BIGINT DEFAULT 0,
    last_login TIMESTAMP
);

-- Sector aggregate state for galaxy simulation
CREATE TABLE sector_state (
    sector_id INT PRIMARY KEY,
    faction_control JSONB,  -- {factionId: controlPercentage}
    resource_stockpiles JSONB,
    population INT,
    last_simulated_at TIMESTAMP
);
```

Use `JSONB` for component data — it's queryable, indexable, and avoids schema changes when new components are added. The `component_data` column stores a JSON object keyed by component type name.

### Persistence Cadence

| Data | Save frequency | Strategy |
|---|---|---|
| Player position | Every 30 seconds | Batch update |
| Player inventory | On change | Immediate write |
| Entity positions | Every 60 seconds | Batch upsert |
| Economy state | Every 5 minutes | Sector-level aggregate |
| Combat outcomes | On resolution | Event-driven write |

Batch writes to avoid overwhelming the database. Use PostgreSQL's `ON CONFLICT DO UPDATE` (upsert) for entity position saves.

### Redis Usage

| Key pattern | Purpose | TTL |
|---|---|---|
| `session:{token}` | Player session data | 5 min (refreshed on activity) |
| `zone:{zoneId}:load` | Current entity/player count | 10 sec |
| `player:{id}:zone` | Which zone a player is in | Session lifetime |

Redis pub/sub channels:
- `zone.border.{zoneId}` — Border entity state updates
- `zone.handoff.prepare.{zoneId}` — Handoff initiation
- `zone.handoff.confirm.{zoneId}` — Handoff confirmation
- `galaxy.event` — Galaxy-wide events (faction wars, economy shifts)

## Load Balancing

### ZoneLoadBalancer

Assigns zones to server instances and rebalances when load is uneven:

```java
public class ZoneLoadBalancer {
    private final RedisClient redis;
    private final Map<String, List<Integer>> serverToZones = new HashMap<>();

    public String getServerForZone(int zoneId) {
        // Read from Redis — updated by each zone server heartbeat
        return redis.get("zone:" + zoneId + ":server");
    }

    public void rebalance() {
        // Read load metrics from all zone servers
        Map<String, ZoneLoad> loads = getAllZoneLoads();

        // Find overloaded servers (>80% capacity)
        for (var entry : loads.entrySet()) {
            if (entry.getValue().cpuPercent > 80 || entry.getValue().playerCount > 200) {
                // Split: move some zones to a less loaded server
                int zoneToMove = findLeastLoadedZone(entry.getKey());
                String targetServer = findLeastLoadedServer();
                migrateZone(zoneToMove, entry.getKey(), targetServer);
            }
        }
    }

    public void migrateZone(int zoneId, String fromServer, String toServer) {
        // 1. Spin up zone on target server (loads from database)
        // 2. Transfer all player connections
        // 3. Shut down zone on source server
        // This is a "cold" migration — brief interruption is acceptable
        // for non-player-occupied zones
    }
}
```

### Dynamic Zone Splitting

When a single zone gets too crowded (e.g., a large fleet battle draws hundreds of players to one sector), the zone can be dynamically split:

```java
public void splitZone(int zoneId) {
    ZoneDefinition original = zoneRegistry.getZoneById(zoneId);

    // Split along the longest axis
    double midpoint;
    if (original.maxX - original.minX >= original.maxY - original.minY
        && original.maxX - original.minX >= original.maxZ - original.minZ) {
        midpoint = (original.minX + original.maxX) / 2.0;
        // Create two new zones: [minX, midpoint] and [midpoint, maxX]
    }
    // ... similar for Y and Z axes

    // Assign new zones to different server instances
    // Entities are partitioned by which half they fall in
    // Players in the split zone receive redirect to their new zone server
}
```

Zone splitting is expensive and should be rare — it's a last resort for extreme load. The default zone sizing should handle 200+ players comfortably.

## Server Deployment

A minimal deployment for development/testing:

```
1x Gateway Server (can run on same machine as a zone server)
1x Zone Server (handles all zones — single-server mode for development)
1x PostgreSQL instance
1x Redis instance
```

Production deployment for 1000+ players:

```
2x Gateway Servers (behind load balancer)
10-20x Zone Servers (each owning 5-10 sectors)
1x Galaxy Simulation Worker
1x PostgreSQL cluster (primary + read replica)
1x Redis cluster (3-node for HA)
```

## Testing

```java
@Test
void zoneRegistryFindsCorrectZone() {
    ZoneRegistry registry = new ZoneRegistry();
    registry.addZone(new ZoneDefinition(1, "Alpha",
        0, 0, 0, 1000, 1000, 1000, new int[]{2}));
    registry.addZone(new ZoneDefinition(2, "Beta",
        1000, 0, 0, 2000, 1000, 1000, new int[]{1}));

    assertEquals(1, registry.getZoneForPosition(500, 500, 500).zoneId);
    assertEquals(2, registry.getZoneForPosition(1500, 500, 500).zoneId);
}

@Test
void boundaryMarginDetectsNearEdgeEntities() {
    ZoneDefinition zone = new ZoneDefinition(1, "Test",
        0, 0, 0, 10000, 10000, 10000, new int[]{}, 1000);

    assertTrue(zone.isInBoundaryMargin(500, 5000, 5000));  // near minX
    assertTrue(zone.isInBoundaryMargin(9500, 5000, 5000));  // near maxX
    assertFalse(zone.isInBoundaryMargin(5000, 5000, 5000));  // center
}

@Test
void handoffTransfersEntityBetweenZones() {
    // Setup: entity at zone boundary
    // Action: entity crosses into zone 2
    // Verify: entity removed from zone 1 engine, created in zone 2 engine
    // Verify: networkId preserved across transfer
    // Verify: client receives redirect to zone 2 server
}

@Test
void ghostEntitiesAreReadOnly() {
    // Setup: create ghost entity from border sharing
    // Verify: ghost has TransformComponent but no CombatAIComponent
    // Verify: ghost is included in interest management queries
    // Verify: ghost is not processed by CombatAISystem
}
```

## Integration with Existing Skills

- **libgdx-network-replication** — Each zone server runs its own `ReplicationManager` instance. The replication skill handles per-client state sync within a zone; this skill handles the zone-level orchestration above it.
- **libgdx-client-prediction** — Zone transitions require clearing prediction history and re-bootstrapping from the new zone server's authoritative state. The prediction skill's `ReconciliationSystem` must handle the zone-change case.
- **libgdx-network-protocol** — Zone servers and gateway use the same KryoNet message types. The protocol skill defines the message format; this skill defines the server topology that routes those messages.
- **spacegame-combat-lod-ai** — The DORMANT NPC system maps to this skill's galaxy simulation worker. Dormant entities live in the database as `sector_state`; when a zone server spins up for a sector, it promotes them to ECS entities.
- **economy-creation** — The economy system's tiered simulation (galactic → sector → planetary) maps to this skill's zone hierarchy. Galaxy-wide economy runs on the simulation worker; local economy runs on zone servers.

## Common Pitfalls

1. **Zone boundaries at popular locations.** If a zone boundary cuts through a space station or popular trading hub, entities will constantly hand off back and forth. Place boundaries in empty space between star systems, not through them.

2. **Clock skew between zone servers.** Server tick counters will drift. Use a shared monotonic clock (Redis `TIME` or NTP-synced) for cross-zone event ordering.

3. **Database as bottleneck.** With 20+ zone servers writing entity positions every 60 seconds, PostgreSQL can become the bottleneck. Use batch upserts, connection pooling, and consider partitioning the `entities` table by `zone_id`.

4. **Redis pub/sub message ordering.** Redis pub/sub does not guarantee ordering across channels. The handoff protocol uses a sequence (prepare → confirm → release) that must be handled idempotently — a late "prepare" arriving after "confirm" should be ignored.

5. **Single point of failure.** The gateway, Redis, and PostgreSQL are all SPOFs in the basic deployment. For production: run multiple gateway instances behind a load balancer, use Redis Sentinel or Cluster, and configure PostgreSQL streaming replication.
