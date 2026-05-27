# Procedural Planet Generation in GameScreen

**Date:** 2026-05-26
**Approach:** ECS Systems in GameWorld, Thin GameScreen (Approach B)

## Overview

Replace the flat terrain in `GameScreen` with a procedurally generated spherical planet, supporting both FPS surface walking and ship orbital flight, with radial gravity and full Bullet physics collision on the sphere.

The existing planet generation pipeline (PlanetGenerator, AtmosphereGenerator, BiomeMapper, PlanetTerrainSystem) is fully built but not yet wired into the game scene. This design connects it.

## Constraints

- Hardcoded Terran planet seed (bypasses galaxy → star → orbital chain for now)
- Clean scene: no scatter boxes or statically-placed ships from the old flat world
- Planet at origin `(0,0,0)` — player stays near surface, universe moves around them (floating origin)
- No atmosphere re-entry physics for V1
- No atmospheric scattering shader for V1 (simple altitude-based clear color)

---

## 1. Planet Generation Pipeline (GameScreen)

`GameScreen.initializeWorld()` replaces the flat terrain setup with a procgen pipeline:

1. Create a `Planet` directly with hardcoded seed `42L`, `PlanetType.TERRAN`, radius `1.0f`, mass `1.0f`, day length `24f`, axial tilt `23.4f`, not tidally locked
2. Run `AtmosphereGenerator.generate()` using a minimal stub `StarSystem` with `luminosity = 1.0f`, `spectralClass = G`, and a single `OrbitalSlot` at `orbitalRadius = 1.0f` in the `HABITABLE` zone. This produces a breathable Earth-like atmosphere
3. Run `BiomeMapper.generate()` to produce the `BiomeMap`
4. Pass the `Planet` + `BiomeMap` to `GameWorld`, which feeds them to `PlanetTerrainSystem`

**Planet scale:** With `radius = 1.0f`, `PlanetTerrainSystem.loadPlanet()` computes `radiusKm = 1.0 * 6371 = 6371` km. This is Earth-scale. All terrain chunk positions, collision bodies, and spawn calculations use this radius.

**Removed from GameScreen:**
- `TerrainGenerator` heightmap generation
- `createTerrainMesh()` — flat terrain mesh construction
- `createTerrainPhysics()` — flat terrain Bullet collision
- `createScatterBoxes()` — random physics boxes
- `renderTerrain()` — flat terrain rendering
- All flat-terrain fields: `heightmap`, `terrainMesh`, `TERRAIN_VERTS_X/Z`, `TERRAIN_WIDTH/DEPTH`

## 2. PlanetTerrainSystem Enhancements

The existing `PlanetTerrainSystem` manages quadtree LOD but currently discards mesh data. Changes:

### Mesh Creation
When `generateMesh()` builds a chunk's `MeshData`, create a libGDX `Mesh` from the vertices/indices and store it on `TerrainChunk.mesh` (the field already exists but is never populated). Dispose it when chunks merge or the planet unloads.

### Bullet Collision Per Chunk
For each chunk that has a mesh, build a `btBvhTriangleMeshShape` from its vertex positions and add a static `btRigidBody` to the dynamics world. When a chunk splits into children, remove the parent's collision body and create new ones for the children. When children merge back, remove theirs and recreate the parent's.

### Chunk Lifecycle
`PlanetTerrainSystem` needs a reference to `btDiscreteDynamicsWorld` (injected at construction or via a setter) to add/remove rigid bodies. Each `TerrainChunk` gains a `btRigidBody` field alongside the existing `Mesh` field, both disposed together.

### Rendering Access
`PlanetTerrainSystem` exposes `getVisibleLeaves()` (already exists on `TerrainQuadtree`) so the render side can iterate over chunks with live meshes. The system itself does not render — it manages data only.

## 3. RadialGravitySystem

New Ashley `EntitySystem` that replaces Bullet's built-in world gravity with per-entity radial gravity toward the planet center.

### RadialGravitySystem (new)
- Processes all entities with `PhysicsBodyComponent` + `TransformComponent`
- Each tick: `gravityDir = normalize(planetCenter - entityPosition)`, apply force `gravityDir * GRAVITY * mass`
- Disable Bullet world gravity: `dynamicsWorld.setGravity(0,0,0)`
- Planet center is `(0,0,0)` for now
- Skip entities in `PILOTING` mode (check `PlayerStateComponent` if present) — ships handle their own thrust
- Non-player entities (boxes, NPCs) always receive gravity

### PlayerMovementSystem Changes
Replace all hardcoded `Vector3.Y` references with a dynamic `localUp` vector (`normalize(playerPosition - planetCenter)`):

| Current (flat)                              | New (spherical)                                      |
|---------------------------------------------|------------------------------------------------------|
| Ground ray: `-Y` direction                  | Ground ray: `-localUp` direction                     |
| Jump impulse: `(0, JUMP_IMPULSE, 0)`       | Jump impulse: `localUp * JUMP_IMPULSE`               |
| Slope angle vs `Vector3.Y`                  | Slope angle vs `localUp`                             |
| Yaw rotation around `Vector3.Y`            | Yaw rotation around `localUp`                        |
| Forward/right from yaw around Y             | Forward/right in tangent plane to sphere at player    |

### Player Capsule Orientation
The capsule's world transform rotation must be updated each frame so its long axis aligns with `localUp`. As the player walks around the sphere, their "up" gradually rotates.

## 4. Planet Rendering

### New `renderPlanetTerrain()` in GameScreen
- Gets visible leaf chunks from `PlanetTerrainSystem.getVisibleLeaves()`
- Binds the existing terrain shader (vertex format matches: position(3) + normal(3) + color(4) = stride 10)
- For each chunk with a non-null `Mesh`, sets world transform (identity — planet at origin) and calls `mesh.render(shader, GL20.GL_TRIANGLES)`
- Existing shader handles ambient + directional lighting with vertex colors

### Camera Far Plane
Set dynamically each frame based on player altitude above the surface:
- On surface (altitude < 10 km): `camera.far = 500f` (horizon ~100 km at eye height, 500 is generous)
- Mid altitude (10–500 km): `camera.far = altitude * 10f`
- Orbital (altitude > 500 km): `camera.far = planetRadius * 4f` (~25,000 km — sees full planet)
- `camera.near` stays at `0.1f` on surface, increases to `1f` above 10 km to preserve depth buffer precision

### Sky / Background Color
Replace flat `ScreenUtils.clear(0.1f, 0.1f, 0.15f)` with altitude-based clear color:
- Below 100 km (atmosphere threshold): lerp from `(0.4, 0.6, 0.9)` (Terran sky blue at surface) toward `(0.05, 0.05, 0.1)` (near-black at 100 km)
- Above 100 km: `(0.02, 0.02, 0.04)` (deep space)

Proper atmospheric scattering is a future enhancement.

### Shader Reuse
The existing terrain shader works as-is. Both old flat terrain and new spherical chunks use identical vertex attributes. No new shaders needed for V1.

## 5. Player Spawning on the Sphere

### Surface Spawn Point
1. Pick a spawn direction (e.g. `CubeSphere.toSphere(POS_Z, 0.5f, 0.5f)` — center of one face)
2. Query `TerrainNoiseStack.heightAt()` for elevation at that direction
3. Compute world position: `spawnDir * (planetRadius + height * planetRadius * 0.01f + capsuleOffset)`
4. Orient player capsule so its up axis aligns with `spawnDir`

### Ocean Avoidance
Check `BiomeMap.getBiome()` at the spawn point. If `OCEAN` or `ICE_SHEET`, offset the spawn direction and retry up to a limit. Fall back to original point if no land found.

### Ship Spawn
Place a single ship near the player's spawn point:
- Offset along the surface tangent by a small distance
- Query terrain height at that offset
- Orient the ship to sit on the surface
- Provides a boardable ship for the orbital flight test

### Camera Initial Orientation and CameraSystem Changes
`FPSCameraComponent` gains a `localUp` vector field (initialized to the spawn direction, updated each frame by `PlayerMovementSystem`).

`CameraSystem` changes:
- Build the view matrix using `localUp` instead of `Vector3.Y` for the camera's up direction
- Compute camera position as: `playerPosition + localUp * eyeHeight`
- Look direction derived from yaw/pitch rotated around `localUp` (not `Vector3.Y`)
- On the first frame after spawn, `localUp` is already set from the spawn direction — no special-case needed

## 6. Orbital Flight and Surface-to-Orbit Transition

### Surface to Orbit (boarding ship)
- Existing `InteractionSystem` + `PlayerStateComponent` mode switch handles entering a ship
- Mode switches to `PILOTING`, `ShipFlightSystem` takes over
- `RadialGravitySystem` skips entities in `PILOTING` mode
- `ShipCameraSystem` handles piloting camera — works as-is

### Orbit to Surface (leaving ship)
- Player exits near surface, re-enters FPS mode
- `PlayerMovementSystem` resumes, `RadialGravitySystem` applies
- Player capsule orientation snaps to `localUp` at exit position
- Ship remains where it was left

### LOD During Flight
- `PlanetTerrainSystem` already updates LOD based on camera position
- During flight: distant chunks merge (coarse), nearby chunks split (detailed)
- Bullet collision bodies follow the same lifecycle — only current LOD leaf chunks have collision
- From orbit: few coarse chunks, minimal collision. Approaching surface: chunks refine, collision detail increases

### Not in V1
- Atmosphere re-entry physics (heating, drag)
- Atmospheric scattering shader
- Re-entry visual effects

## 7. Disposal and Cleanup

### GameScreen Disposal Changes
- Remove disposal of `terrainMesh`, `heightmap`, flat terrain physics — no longer exist
- `PlanetTerrainSystem.dispose()` handles all chunk meshes and collision bodies
- Ship factory disposal stays for the single spawn ship
- Shader disposal stays (reused for planet chunks)

### GameWorld Disposal
- Call `PlanetTerrainSystem.dispose()` explicitly (Ashley `Engine` does not auto-dispose systems)
- `RadialGravitySystem` has no resources to dispose

### Chunk Collision Body Lifecycle
- Each `TerrainChunk` disposes its own `btRigidBody` and `btBvhTriangleMeshShape` when disposed
- Chunk removes its body from the dynamics world before disposing
- Dynamics world reference stored on chunk or passed through quadtree

---

## Files Modified

| File | Change |
|------|--------|
| `ui/GameScreen.java` | Remove flat terrain; add planet procgen pipeline, `renderPlanetTerrain()` |
| `core/GameWorld.java` | Add `PlanetTerrainSystem`, `RadialGravitySystem`; accept planet + biome map |
| `planet/terrain/PlanetTerrainSystem.java` | Create libGDX Meshes + Bullet collision from chunk data |
| `planet/terrain/TerrainQuadtree.java` | Pass dynamics world for collision body management |
| `planet/terrain/TerrainChunk.java` | Add `btRigidBody` field, dispose collision alongside mesh |
| `player/systems/PlayerMovementSystem.java` | Replace hardcoded Y-up with dynamic `localUp` from planet center |
| `player/components/FPSCameraComponent.java` | Add `localUp` vector field |
| `player/systems/CameraSystem.java` | Use `localUp` for view matrix orientation |

## New Files

| File | Purpose |
|------|---------|
| `core/systems/RadialGravitySystem.java` | Per-entity gravity toward planet center |
