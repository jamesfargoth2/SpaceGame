---
name: libgdx-multiplayer-networking
description: >
  Enforces correct server-authoritative networking architecture, KryoNet
  transport, client-side prediction, entity interpolation, spatial interest
  management, and state replication for a libGDX 3D space game. Use this skill
  whenever writing or modifying: network message definitions, KryoNet server/client
  setup, client prediction and reconciliation, entity state synchronization,
  spatial partitioning for network relevance, delta compression, bandwidth
  optimization, or multiplayer session management. Also triggers when adding
  PvP mechanics, shared world persistence, multiplayer economy interactions,
  or any code that must handle network latency, packet loss, or authoritative
  validation.
---

# libGDX Multiplayer Networking

## Core Architecture

Server-authoritative model. The server owns all game state. Never trust the client.

## KryoNet Transport

Register all types in identical order on both client and server:

```java
public class NetworkRegistration {
    public static void register(Kryo kryo) {
        kryo.register(Vector3.class);
        kryo.register(Quaternion.class);
        kryo.register(float[].class);
        kryo.register(InputMessage.class);
        kryo.register(StateSnapshot.class);
        kryo.register(EntitySpawn.class);
        kryo.register(EntityDespawn.class);
    }
}
```

## Network Messages

```java
public class InputMessage {
    public int sequenceNumber;
    public float forward, strafe, pitch, yaw;
    public boolean firing;
    public long timestamp;
}

public class StateSnapshot {
    public int tick;
    public int lastProcessedInput;
    public Array<EntityState> entities;
}

public class EntityState {
    public int entityId;
    public Vector3 position;
    public Quaternion rotation;
    public Vector3 velocity;
    public float health;
    public byte flags;
}
```

## Client-Side Prediction

Predict own movement locally, reconcile on server confirmation by replaying unconfirmed inputs:

```java
public class ClientPrediction {
    private Array<InputMessage> unconfirmedInputs = new Array<>();
    private Vector3 predictedPosition = new Vector3();

    public void onServerSnapshot(StateSnapshot snapshot) {
        Iterator<InputMessage> it = unconfirmedInputs.iterator();
        while (it.hasNext()) {
            if (it.next().sequenceNumber <= snapshot.lastProcessedInput) it.remove();
        }
        EntityState myState = findMyEntity(snapshot);
        predictedPosition.set(myState.position);
        for (InputMessage input : unconfirmedInputs) applyInput(predictedPosition, input);
    }
}
```

## Entity Interpolation

Remote entities render between two known server positions. Introduces one-tick delay for smooth visuals.

## Spatial Interest Management

Only send entities within relevance radius (default 5000m). Use spatial hash grid or octree for many entities.

## Delta Compression

Track previous state per client; only send changed fields.

## Server Tick Loop

Fixed-rate 20Hz simulation with accumulator pattern. Validate all client inputs server-side with 10% speed tolerance.

## Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| Server tick rate | 20 Hz | Simulation frequency |
| Relevance radius | 5000m | Spatial interest cutoff |
| Interpolation delay | 1 tick (50ms) | Smoothing buffer |
| Position epsilon | 0.01m | Delta compression threshold |
| Speed tolerance | 10% | Anti-cheat margin |
| KryoNet buffer | 65536 bytes | Read/write buffer |

## Common Mistakes

| Mistake | Fix |
|---|---|
| Trusting client position | Server validates and clamps all inputs |
| Sending full state every tick | Use delta compression |
| Sending all entities to all clients | Use spatial interest management |
| Kryo registration order mismatch | Identical order on client and server |
| Interpolating own player | Only interpolate remote entities |
| Fixed tick without accumulator | Always use accumulator pattern |
