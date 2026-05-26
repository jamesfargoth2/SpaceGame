---
name: libgdx-network-replication
description: >
  How to implement entity state replication and interest management for SpaceGame's MMO-scale
  multiplayer using Ashley ECS and KryoNet. Covers component serialization, dirty tracking,
  priority-based update scheduling, spatial interest management, delta compression, and snapshot
  delivery for joining players. Use this skill whenever the user wants to: sync entity state
  across server and clients, implement network replication, add interest management or relevance
  filtering, serialize Ashley components for network transport, track dirty components, send
  delta updates, handle player joins with world snapshots, replicate ships/NPCs/projectiles,
  decide what to sync and how often, or optimize bandwidth for entity updates. Also trigger for
  "entity sync", "replicate state", "network updates", "what does each client see",
  "bandwidth optimization", "dirty flags", "component serialization", or "world snapshot".
---

# Network Replication & Interest Management

This skill covers the central problem of MMO-scale networking: getting the right entity state to the right clients at the right frequency without drowning in bandwidth. At galaxy scale with 1000+ concurrent players, you cannot send every entity update to every client — you need spatial interest management, priority-based scheduling, and delta compression working together.

The architecture builds on SpaceGame's existing Ashley ECS. Each networked entity gets a `NetworkIdentityComponent` that assigns a stable network ID. The server tracks which components are dirty and which clients care about each entity. Clients receive only the entities within their interest radius, with update frequency proportional to distance and gameplay importance.

## Core Concepts

**Authority model:** The server owns all entity state (CLAUDE.md rule 4). Clients send inputs, never state. The server simulates, then replicates results back. A client's own player entity gets updates at full tick rate; distant entities get updates less frequently.

**Interest management:** Each client has a spherical interest region centered on their player entity. Entities outside this sphere are invisible to that client. The sphere has tiers — close entities update every tick, mid-range entities every 4th tick, far entities every 10th tick.

**Delta compression:** Only send component fields that actually changed since the last acknowledged update for that client. A full snapshot is sent only when a client first connects or an entity enters their interest sphere.

## New Files

```
core/src/main/java/com/galacticodyssey/networking/replication/
  NetworkIdentityComponent.java     Stable network ID, authority info
  ReplicatedComponent.java          Interface marking a component as network-synced
  DirtyTracker.java                 Tracks which fields changed per component per entity
  ComponentSerializer.java          Serializes/deserializes component state to byte buffers
  InterestManager.java              Determines which entities each client sees
  ReplicationManager.java           Orchestrates: dirty check → interest filter → prioritize → send
  ClientReplicationState.java       Per-client tracking: last acked snapshot, pending deltas
  ReplicationPriority.java          Enum: CRITICAL, HIGH, MEDIUM, LOW
  SnapshotBuilder.java              Creates full world snapshots for joining players

server/src/main/java/com/galacticodyssey/server/replication/
  ServerReplicationSystem.java      Ashley system that runs the replication loop each server tick
```

## NetworkIdentityComponent

Every entity that exists on the network gets this component. It's assigned server-side at entity creation and never changes.

```java
package com.galacticodyssey.networking.replication;

import com.badlogic.ashley.core.Component;

public class NetworkIdentityComponent implements Component {
    public int networkId;
    public EntityType entityType;
    public int ownerClientId = -1;  // -1 = server-owned (NPCs, projectiles)

    public enum EntityType {
        PLAYER, SHIP, NPC, PROJECTILE, STATION, ASTEROID, LOOT
    }
}
```

The `networkId` is a monotonically increasing integer assigned by the server. It must be stable across the entity's lifetime — clients reference entities by this ID, so it cannot change even if the Ashley `Entity` object is recycled.

## ReplicatedComponent Interface

Not every component needs network sync. `TransformComponent` does; `FPSCameraComponent` doesn't (it's client-local). Mark syncable components:

```java
public interface ReplicatedComponent {
    void writeState(ByteBuffer buffer);
    void readState(ByteBuffer buffer);
    boolean isDirty();
    void clearDirty();
    int getReplicationPriority();  // higher = sent more reliably
}
```

Components implement this interface to participate in replication. The `isDirty()` / `clearDirty()` pattern uses per-field flags so the serializer knows which fields changed.

### Which Components to Replicate

| Component | Replicate? | Priority | Frequency | Notes |
|---|---|---|---|---|
| TransformComponent | Yes | CRITICAL | Every tick (near), 4th tick (mid), 10th (far) | Position, rotation, velocity |
| HealthComponent | Yes | HIGH | On change only | HP matters for combat UI |
| ShieldComponent | Yes | HIGH | On change only | Shield state |
| ShipFlightComponent | Yes | HIGH | Every tick (near) | Attitude, thrust, angular vel |
| ShipDataComponent | Yes | LOW | On change only | Hull type rarely changes |
| WeaponInventoryComponent | Yes | MEDIUM | On change only | Weapon loadout |
| CargoBayComponent | No | — | — | Private to owner |
| PlayerWalletComponent | No | — | — | Private to owner, sent via RPC |
| CombatAIComponent | No | — | — | Server-only |
| PlayerInputComponent | No | — | — | Client-to-server only |
| FPSCameraComponent | No | — | — | Client-local rendering |
| PhysicsBodyComponent | No | — | — | Derived from transform on client |

The key principle: replicate *observable state* (what other players can see or be affected by), not *internal state* (AI decisions, input bindings, wallet balances).

## DirtyTracker

Rather than polling every component every tick, use a dirty-flag approach. When a system modifies a replicated component, it marks the field dirty:

```java
public class DirtyTracker {
    private final Map<Integer, long[]> dirtyBits = new HashMap<>();
    // Key: networkId, Value: bitmask per component type

    private static final int MAX_COMPONENT_TYPES = 32;

    public void markDirty(int networkId, int componentTypeIndex, int fieldIndex) {
        long[] bits = dirtyBits.computeIfAbsent(networkId,
            k -> new long[MAX_COMPONENT_TYPES]);
        bits[componentTypeIndex] |= (1L << fieldIndex);
    }

    public boolean isDirty(int networkId, int componentTypeIndex) {
        long[] bits = dirtyBits.get(networkId);
        return bits != null && bits[componentTypeIndex] != 0;
    }

    public long getDirtyFields(int networkId, int componentTypeIndex) {
        long[] bits = dirtyBits.get(networkId);
        return bits == null ? 0 : bits[componentTypeIndex];
    }

    public void clearAll() {
        dirtyBits.clear();
    }

    public void clearEntity(int networkId) {
        dirtyBits.remove(networkId);
    }
}
```

Each replicated component type is assigned an index (0–31). Each field within that component is a bit in the bitmask. This supports up to 64 fields per component and 32 component types — more than enough for SpaceGame.

### Integrating Dirty Tracking Into Systems

Systems that modify replicated state need to call `markDirty`. The cleanest pattern is a helper on the component itself:

```java
// In a component implementing ReplicatedComponent:
public class TransformReplication implements ReplicatedComponent {
    private static final int FIELD_POSITION = 0;
    private static final int FIELD_ROTATION = 1;
    private static final int FIELD_VELOCITY = 2;

    private long dirtyMask;

    public void setPosition(float x, float y, float z) {
        // actual position set on TransformComponent
        dirtyMask |= (1L << FIELD_POSITION);
    }

    @Override
    public boolean isDirty() { return dirtyMask != 0; }

    @Override
    public void clearDirty() { dirtyMask = 0; }

    @Override
    public void writeState(ByteBuffer buffer) {
        buffer.putLong(dirtyMask);
        if ((dirtyMask & (1L << FIELD_POSITION)) != 0) {
            buffer.putFloat(position.x);
            buffer.putFloat(position.y);
            buffer.putFloat(position.z);
        }
        if ((dirtyMask & (1L << FIELD_ROTATION)) != 0) {
            buffer.putFloat(rotation.x);
            buffer.putFloat(rotation.y);
            buffer.putFloat(rotation.z);
            buffer.putFloat(rotation.w);
        }
        if ((dirtyMask & (1L << FIELD_VELOCITY)) != 0) {
            buffer.putFloat(velocity.x);
            buffer.putFloat(velocity.y);
            buffer.putFloat(velocity.z);
        }
    }
}
```

Only the fields whose bits are set get serialized — this is the delta compression. A position-only change sends 16 bytes (mask + 3 floats) instead of 40+ bytes for the full component.

## InterestManager

The interest manager answers: "which entities should client C receive updates about?" It uses spatial queries and the floating-origin coordinate system.

```java
public class InterestManager {
    private static final float INTEREST_RADIUS_NEAR = 500f;
    private static final float INTEREST_RADIUS_MID = 2000f;
    private static final float INTEREST_RADIUS_FAR = 10000f;

    private final SpatialGrid entityGrid;  // from spacegame-combat-spatial
    private final Map<Integer, Set<Integer>> clientInterestSets = new HashMap<>();

    public InterestManager(SpatialGrid entityGrid) {
        this.entityGrid = entityGrid;
    }

    public InterestResult computeInterest(int clientId, Vector3 clientPosition) {
        InterestResult result = new InterestResult();

        List<Entity> nearby = new ArrayList<>();

        nearby.clear();
        entityGrid.query(clientPosition, INTEREST_RADIUS_NEAR, nearby);
        for (Entity e : nearby) {
            result.nearEntities.add(getNetworkId(e));
        }

        nearby.clear();
        entityGrid.query(clientPosition, INTEREST_RADIUS_MID, nearby);
        for (Entity e : nearby) {
            int nid = getNetworkId(e);
            if (!result.nearEntities.contains(nid)) {
                result.midEntities.add(nid);
            }
        }

        nearby.clear();
        entityGrid.query(clientPosition, INTEREST_RADIUS_FAR, nearby);
        for (Entity e : nearby) {
            int nid = getNetworkId(e);
            if (!result.nearEntities.contains(nid) && !result.midEntities.contains(nid)) {
                result.farEntities.add(nid);
            }
        }

        // Detect entities that left interest entirely
        Set<Integer> previousInterest = clientInterestSets.getOrDefault(clientId, Set.of());
        Set<Integer> currentInterest = new HashSet<>();
        currentInterest.addAll(result.nearEntities);
        currentInterest.addAll(result.midEntities);
        currentInterest.addAll(result.farEntities);

        for (int nid : previousInterest) {
            if (!currentInterest.contains(nid)) {
                result.exitedEntities.add(nid);
            }
        }
        for (int nid : currentInterest) {
            if (!previousInterest.contains(nid)) {
                result.enteredEntities.add(nid);
            }
        }

        clientInterestSets.put(clientId, currentInterest);
        return result;
    }

    public static class InterestResult {
        public final Set<Integer> nearEntities = new HashSet<>();   // every tick
        public final Set<Integer> midEntities = new HashSet<>();    // every 4th tick
        public final Set<Integer> farEntities = new HashSet<>();    // every 10th tick
        public final Set<Integer> enteredEntities = new HashSet<>(); // need full snapshot
        public final Set<Integer> exitedEntities = new HashSet<>();  // send destroy
    }
}
```

### Interest Tiers and Update Frequency

| Tier | Radius | Update every N ticks | Bandwidth share | Use case |
|---|---|---|---|---|
| NEAR | 0–500 units | 1 (every tick) | ~60% | Combat range, visible ships |
| MID | 500–2000 | 4 | ~25% | Approaching ships, station traffic |
| FAR | 2000–10000 | 10 | ~15% | Distant fleet movements, navigation |

Entities beyond FAR radius are invisible to the client. Their state is not sent at all — they don't exist on that client's world.

### Floating Origin Considerations

Interest queries use **local-space** positions (floats relative to origin). But the interest radii are small enough (10km max) that float precision is fine. The server must compute interest relative to each client's local origin, which may differ if clients are in different sectors.

When computing interest for a client, convert entity positions to that client's local frame:

```java
Vector3 localPos = coordinateManager.toLocalSpace(
    entity.galaxyX, entity.galaxyY, entity.galaxyZ,
    clientOriginX, clientOriginY, clientOriginZ
);
```

## ClientReplicationState

The server maintains per-client state tracking what each client has acknowledged:

```java
public class ClientReplicationState {
    public final int clientId;
    public int lastAckedTick;
    public final Map<Integer, Integer> entityLastSentTick = new HashMap<>();
    public final Map<Integer, Long> entityLastSentDirtyMask = new HashMap<>();
    public final Set<Integer> knownEntities = new HashSet<>();  // entities client has received

    public ClientReplicationState(int clientId) {
        this.clientId = clientId;
    }

    public boolean clientKnowsEntity(int networkId) {
        return knownEntities.contains(networkId);
    }
}
```

When an entity enters a client's interest sphere for the first time, a full snapshot of that entity is sent (all replicated components, all fields). Subsequent updates are deltas only.

## ReplicationManager

The orchestrator that ties everything together. Runs once per server tick:

```java
public class ReplicationManager {
    private final InterestManager interestManager;
    private final DirtyTracker dirtyTracker;
    private final Map<Integer, ClientReplicationState> clientStates = new HashMap<>();
    private final ComponentSerializer serializer;
    private int currentTick;

    public List<ReplicationPacket> buildUpdates() {
        currentTick++;
        List<ReplicationPacket> packets = new ArrayList<>();

        for (var entry : clientStates.entrySet()) {
            int clientId = entry.getKey();
            ClientReplicationState state = entry.getValue();
            Vector3 clientPos = getClientPosition(clientId);

            InterestManager.InterestResult interest =
                interestManager.computeInterest(clientId, clientPos);

            ReplicationPacket packet = new ReplicationPacket();
            packet.serverTick = currentTick;
            packet.clientId = clientId;

            // Entities that left interest — tell client to destroy them
            for (int nid : interest.exitedEntities) {
                packet.destroyedEntities.add(nid);
                state.knownEntities.remove(nid);
            }

            // Entities that entered interest — send full snapshot
            for (int nid : interest.enteredEntities) {
                packet.entitySnapshots.add(serializer.fullSnapshot(nid));
                state.knownEntities.add(nid);
                state.entityLastSentTick.put(nid, currentTick);
            }

            // Near entities: delta every tick
            for (int nid : interest.nearEntities) {
                if (interest.enteredEntities.contains(nid)) continue;
                if (dirtyTracker.isDirty(nid, -1)) {
                    packet.entityDeltas.add(serializer.deltaSnapshot(nid, dirtyTracker));
                    state.entityLastSentTick.put(nid, currentTick);
                }
            }

            // Mid entities: delta every 4th tick
            for (int nid : interest.midEntities) {
                if ((currentTick + nid) % 4 != 0) continue;
                packet.entityDeltas.add(serializer.deltaSnapshot(nid, dirtyTracker));
                state.entityLastSentTick.put(nid, currentTick);
            }

            // Far entities: delta every 10th tick
            for (int nid : interest.farEntities) {
                if ((currentTick + nid) % 10 != 0) continue;
                packet.entityDeltas.add(serializer.deltaSnapshot(nid, dirtyTracker));
                state.entityLastSentTick.put(nid, currentTick);
            }

            packets.add(packet);
        }

        dirtyTracker.clearAll();
        return packets;
    }
}
```

The `+ nid` offset in the modulo distributes mid/far updates across ticks so they don't all batch onto the same frame. Without this, tick 4 would send updates for every mid-tier entity simultaneously — a bandwidth spike.

## SnapshotBuilder

When a player first connects (or reconnects after a long disconnect), they need a full world snapshot of everything in their interest sphere:

```java
public class SnapshotBuilder {
    private final InterestManager interestManager;
    private final ComponentSerializer serializer;

    public WorldSnapshot buildJoinSnapshot(int clientId, Vector3 spawnPosition) {
        WorldSnapshot snapshot = new WorldSnapshot();

        InterestManager.InterestResult interest =
            interestManager.computeInterest(clientId, spawnPosition);

        Set<Integer> allVisible = new HashSet<>();
        allVisible.addAll(interest.nearEntities);
        allVisible.addAll(interest.midEntities);
        allVisible.addAll(interest.farEntities);

        for (int nid : allVisible) {
            snapshot.entities.add(serializer.fullSnapshot(nid));
        }

        return snapshot;
    }
}
```

The join snapshot can be large (hundreds of entities × full component state). Send it as a reliable, ordered message — the client blocks on a loading screen until it arrives.

## ServerReplicationSystem

The Ashley system that drives replication on the server:

```java
public class ServerReplicationSystem extends EntitySystem {
    private final ReplicationManager replicationManager;
    private final NetworkTransport transport;  // KryoNet connection layer

    public ServerReplicationSystem(ReplicationManager replicationManager,
                                   NetworkTransport transport) {
        super(/* priority */ 50);  // after all game logic
        this.replicationManager = replicationManager;
        this.transport = transport;
    }

    @Override
    public void update(float delta) {
        List<ReplicationPacket> packets = replicationManager.buildUpdates();
        for (ReplicationPacket packet : packets) {
            if (packet.isEmpty()) continue;
            transport.sendToClient(packet.clientId, packet);
        }
    }
}
```

Priority 50 ensures all game systems (physics, combat, AI) have finished modifying state before replication reads it.

## Bandwidth Budget

At MMO scale, bandwidth is the primary constraint. Budget per client:

| Direction | Budget | Notes |
|---|---|---|
| Server → Client | ~50 KB/s | Position updates dominate |
| Client → Server | ~5 KB/s | Inputs only |

With 1000 clients at 50 KB/s each = 50 MB/s server egress. This is feasible on modern hardware but requires the interest management and delta compression described above.

**Per-entity cost estimate:**
- Full snapshot: ~80–200 bytes (depends on component count)
- Delta (position only): ~16 bytes (mask + xyz)
- Delta (position + rotation): ~32 bytes

With 200 near entities updating every tick (20 ticks/sec) at 16 bytes average:
200 × 16 × 20 = 64,000 bytes/sec = 62 KB/s — just over budget. This is why delta compression (only dirty fields) and interest tiering are essential.

## Testing

```java
@Test
void dirtyTrackerOnlySerializesDirtyFields() {
    DirtyTracker tracker = new DirtyTracker();
    tracker.markDirty(1, 0, 0);  // entity 1, component 0, field 0 (position)
    
    assertTrue(tracker.isDirty(1, 0));
    assertEquals(1L, tracker.getDirtyFields(1, 0));
    
    tracker.clearEntity(1);
    assertFalse(tracker.isDirty(1, 0));
}

@Test
void interestManagerDetectsEnterAndExit() {
    SpatialGrid grid = new SpatialGrid(64f);
    InterestManager im = new InterestManager(grid);
    
    Entity ship = createEntityAt(100, 0, 0, networkId: 42);
    grid.insert(ship, new Vector3(100, 0, 0));
    
    // First query — entity enters interest
    InterestManager.InterestResult r1 = im.computeInterest(1, new Vector3(0, 0, 0));
    assertTrue(r1.enteredEntities.contains(42));
    assertTrue(r1.nearEntities.contains(42));
    
    // Move entity far away
    grid.move(ship, new Vector3(50000, 0, 0));
    InterestManager.InterestResult r2 = im.computeInterest(1, new Vector3(0, 0, 0));
    assertTrue(r2.exitedEntities.contains(42));
}

@Test
void midTierEntitiesUpdateEveryFourthTick() {
    // Verify entity in mid tier only appears in delta list every 4 ticks
    int entityNid = 7;
    int updateCount = 0;
    for (int tick = 1; tick <= 20; tick++) {
        if ((tick + entityNid) % 4 == 0) updateCount++;
    }
    assertEquals(5, updateCount);  // 20 ticks / 4 = 5 updates
}

@Test
void joinSnapshotIncludesAllVisibleEntities() {
    // Populate grid with entities at various distances
    // Build join snapshot
    // Verify near + mid + far entities all included
    // Verify entities beyond FAR radius excluded
}
```

## Integration with Existing Skills

- **spacegame-combat-spatial** — InterestManager uses the same `SpatialGrid` for spatial queries. Share the grid instance or use a dedicated replication grid with cell size matching the interest radii.
- **spacegame-combat-lod-ai** — DORMANT tier NPCs (from LOD AI) are not Ashley entities, so they don't get replicated. They only become relevant for replication when promoted to ACTIVE/REDUCED and spawned as real entities by `DormantSpawnSystem`.
- **libgdx-client-prediction** — The replication system sends authoritative state; the prediction system on the client reconciles it against local predictions.
- **libgdx-server-zone-architecture** — Each zone server runs its own `ReplicationManager`. Cross-zone entity handoff means transferring `NetworkIdentityComponent` ownership between zone servers.
- **libgdx-network-protocol** — `ReplicationPacket` is serialized using the protocol skill's message format and sent via the channel system described there.

## Common Pitfalls

1. **Replicating too many components.** Every replicated component costs bandwidth. Start with TransformComponent and HealthComponent only — add more only when clients demonstrably need them.

2. **Forgetting to handle entity exit.** When an entity leaves a client's interest sphere, the client must be told to destroy it. Otherwise ghost entities accumulate and leak memory.

3. **Snapshot size on join.** A player joining a busy area might receive hundreds of full entity snapshots at once. Cap the join snapshot to the highest-priority entities and stream the rest over subsequent ticks.

4. **Dirty flags not cleared.** If `clearDirty()` is never called, every component looks dirty every tick — defeating delta compression entirely. The `ServerReplicationSystem` must clear after building all packets.

5. **Galaxy-space vs. local-space confusion.** Entity positions in replication packets should use galaxy-space doubles for correctness. The client converts to local-space floats relative to its own floating origin. Sending local-space floats assumes all clients share the same origin — they don't in a galaxy-scale game.
