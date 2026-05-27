---
name: libgdx-scene-world-streaming
description: >
  Enforces correct floating origin implementation, additive scene management,
  LOD streaming for geometry/physics/AI, multi-scale coordinate handling
  (64-bit galaxy to 32-bit local), and interior physics isolation for a libGDX
  3D space game. Use this skill whenever writing or modifying: floating origin
  recentering, scene loading/unloading, coordinate conversion between galaxy
  (double) and local (float) space, LOD transitions for planets/ships/NPCs,
  async asset streaming, multiple btDynamicsWorld management for ship interiors,
  or the SceneManager that controls active scenes. Also triggers when adding
  new scene types, optimizing draw calls, or debugging precision issues.
---

# libGDX Scene / World Streaming

## Three-Level Coordinate Hierarchy

```
Galaxy Map   (64-bit doubles, light-year scale)
  | convert at sector boundary
Sector       (64-bit doubles, AU scale)
  | convert at scene load
Local Space  (32-bit floats, player near origin, <10km radius)
```

Never use 32-bit floats for positions more than 10km from the player.

```java
public class CoordinateManager {
    public double galaxyX, galaxyY, galaxyZ;
    public final Vector3 localOffset = new Vector3();

    public void galaxyToLocal(double gx, double gy, double gz, Vector3 out) {
        out.set((float)(gx - galaxyX), (float)(gy - galaxyY), (float)(gz - galaxyZ));
    }
}
```

## Floating Origin

Player stays near (0,0,0). Recenter when drifting past 1000m:

```java
public class FloatingOriginSystem extends EntitySystem {
    private static final float RECENTER_THRESHOLD = 1000f;

    @Override public void update(float dt) {
        Vector3 playerPos = getPlayerPosition();
        if (playerPos.len() > RECENTER_THRESHOLD) recenter(playerPos);
    }

    private void recenter(Vector3 offset) {
        coordManager.galaxyX += offset.x;
        coordManager.galaxyY += offset.y;
        coordManager.galaxyZ += offset.z;
        for (Entity e : getEngine().getEntitiesFor(Family.all(TransformComponent.class).get())) {
            Mappers.transform.get(e).position.sub(offset);
        }
        shiftPhysicsWorld(localDynamicsWorld, offset);
        EventBus.post(new OriginShiftEvent(offset));
    }
}
```

Every system with cached positions must subscribe to OriginShiftEvent.

## Scene Manager

Additive loading, max 2-3 scenes active. Scene types: Deep Space, Orbital Space, Planet Surface, Ship Interior (separate btDynamicsWorld per ship), Station Interior, Asteroid Field.

## Interior Physics Isolation

Ship interiors run in their own Bullet dynamics world:

```java
public class ShipInteriorWorld {
    public btDynamicsWorld interiorWorld;

    public Vector3 worldToInterior(Vector3 worldPos) {
        TransformComponent ship = Mappers.transform.get(parentShip);
        return worldPos.cpy().sub(ship.position).mul(ship.rotation.conjugate());
    }
}
```

## LOD System

FULL (0-500m), HIGH (500-2000m), MEDIUM (2000-5000m), LOW (5000-20000m), NONE (20000m+). Add hysteresis to prevent pop-in.

## Async Asset Loading

Use libGDX AssetManager with reference counting. Never block render thread. Disguise transitions with gameplay animations.

## Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| Recenter threshold | 1000m | Floating origin trigger |
| Max simultaneous scenes | 3 | Memory budget |
| LOD distances | 500/2000/5000/20000m | Per-LOD bands |
| Asset loading budget | 8ms per frame | Async time slice |

## Common Mistakes

| Mistake | Fix |
|---|---|
| Float positions beyond 10km | Use 64-bit doubles for galaxy/sector |
| Forgetting OriginShiftEvent listeners | Every system with cached positions must subscribe |
| Loading scenes synchronously | Always async with transition disguises |
| Single physics world for interiors | Each ship needs its own btDynamicsWorld |
| LOD pop-in | Add hysteresis to transition thresholds |
| Unloading referenced assets | Use reference counting |
