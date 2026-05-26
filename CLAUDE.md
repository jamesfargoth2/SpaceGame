# Project Galactic Odyssey

Java/libGDX space game (FPS + 6DOF ship piloting, galaxy-scale). The full design document lives at [docs/DESIGN.md](docs/DESIGN.md) — consult it for system scope, feature definitions, and game design before designing new systems.

## Stack

- **Engine:** libGDX 1.13+ with LWJGL3 backend
- **Rendering:** Custom deferred rendering pipeline with PBR shaders (OpenGL 4.x / Vulkan via LWJGL)
- **ECS:** Ashley (libGDX's built-in ECS) for ships, NPCs, projectiles
- **Physics:** gdx-bullet (Bullet physics wrapper) + custom spatial partitioning
- **UI:** Scene2D.UI (libGDX) for menus and HUD
- **Input:** libGDX InputProcessor + gdx-controllers for gamepad/HOTAS
- **Networking:** Server-authoritative; KryoNet for transport, clients predict + interpolate
- **Persistence:** JSON/YAML data files (client), PostgreSQL + Redis (server)
- **Build:** Gradle multi-module project (core/desktop/server)
- **Testing:** JUnit 5 + Mockito

## Architectural rules

1. **Floating origin is non-negotiable.** Use 64-bit doubles for galaxy/sector coordinates; convert to 32-bit floats only for the active local scene. The player stays near `(0,0,0)` — the universe moves around them. Never write code that assumes world-space floats are valid at >10km from origin.
2. **Data-driven content.** Ship stats, weapons, species, factions, resources, missions — all defined in JSON or YAML data files loaded at runtime. Never hardcode game content.
3. **Event-driven communication.** Systems publish events via a central event bus; UI/audio/VFX subscribe independently. Avoid direct cross-system references.
4. **Server-authoritative.** Server owns all game state. Never trust the client.
5. **Modular, isolated testability.** Each system (economy, combat, crew, crafting) must function in an isolated test before integration. Systems must not depend on the rendering context for logic.
6. **Separate physics worlds for ship interiors.** Interiors run in their own Bullet `btDynamicsWorld` attached to a moving ship — don't simulate interior physics in the parent world.

## Folder layout

```
core/src/main/java/com/galacticodyssey/
  core/          Floating origin, coordinate manager, ECS world bootstrap, event bus
  ship/          Ship data, hulls, modules, interior layout
  combat/        Unified damage model, weapons, shields
  player/        FPS controller, ship pilot controller, state machine
  economy/       Tiered simulation (galactic → sector → planetary → local)
  npc/           Crew, AI, behavior trees, utility AI
  ui/            Scene2D panels, HUD
  networking/    Transport, replication, prediction
  data/          Data loaders, content registry, config models

core/src/main/resources/
  data/          JSON/YAML content definitions (ships, weapons, factions, etc.)
  shaders/       GLSL vertex/fragment shaders
  models/        3D model assets (.g3db, .obj, .gltf)
  textures/      Texture atlases, PBR maps
  audio/         Sound effects, music
  scenes/        Scene definition files

desktop/         LWJGL3 desktop launcher
server/          Dedicated server module (headless, no rendering)
```

When creating new Java classes, place them in the matching subpackage. Don't introduce new top-level packages without checking the design doc first.

## Conventions

- Prefer ECS components + systems (Ashley) over inheritance hierarchies for anything that scales (ships, projectiles, NPCs). Use plain objects or controller classes only for scene-bound glue (input, camera, UI bridges).
- Keep simulation logic out of rendering/audio code paths. Systems must be testable without a GL context.
- LOD AI: full behavior near the player, simplified at distance. Don't tick every NPC in the galaxy every frame.
- When touching coordinates, ask whether the value is galaxy-space (`double`) or local-space (`float`) and convert explicitly at the boundary.
- Use `final` on fields where possible. Prefer composition over inheritance.
- All game loop logic goes through ECS systems or explicit update methods — never rely on anonymous threads for game state mutation.
- Dispose all libGDX resources (`Texture`, `Model`, `SpriteBatch`, `ShaderProgram`, etc.) properly. Implement `Disposable` where applicable.
- Use libGDX `Pool<T>` for frequently allocated objects (vectors, matrices, bullets, particles) to minimize GC pressure.
