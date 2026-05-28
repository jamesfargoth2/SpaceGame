# VFX & Audio Systems

These two packages handle all sensory feedback for gameplay events: particle effects for combat and environmental visuals, and 3D positional audio with dynamic music.

---

## VFX System

Located in `com.galacticodyssey.vfx`.

### Particle Pipeline

**`ParticleSpawnSystem`** (priority 12)

Subscribes to combat and environmental events and maps them to particle effects via `VFXEventBindings`. On receipt of a triggering event:
1. Looks up the `ParticleEffectDefinition` by effect key.
2. Grabs an `ActiveEmitter` from `ParticlePoolComponent` (object pool; avoids per-shot allocations).
3. Positions the emitter at the hit point or entity origin.
4. Sets burst parameters from the definition (count, speed, spread, colour, lifetime).

**`ParticleUpdateSystem`**

Each frame advances all live `Particle` objects: integrates velocity, decrements lifetime, interpolates colour from the effect's gradient. Dead particles are returned to the pool.

**`ParticleRenderSystem`**

Batches live particles and submits them to the renderer. Particles use billboarded quads drawn in a single instanced draw call per effect type.

### Event → Effect Bindings

**`VFXEventBindings`**

Loaded from `data/vfx/bindings.json`. Maps event class names to effect keys, with optional sub-routing on event fields:

| Event | Condition | Effect key |
|---|---|---|
| `HitscanHitEvent` | target has `ArmorComponent` (metal) | `ImpactMetal` |
| `HitscanHitEvent` | target has `HealthComponent` (flesh) | `ImpactFlesh` |
| `WeaponFiredEvent` | — | `MuzzleFlash` |
| `ShieldAbsorbEvent` | — | `ShieldRipple` |
| `EntityKilledEvent` | — | `DeathExplosion` |
| `ProjectileHitEvent` | — | `ProjectileImpact` |

### Data

**`VFXRegistry`** — loads all `ParticleEffectDefinition` objects from `data/vfx/effects/`.

**`ParticleEffectDefinition`** — configures a single effect type:
- `burstCount` — particles per burst
- `speed` — initial particle speed (min/max range)
- `spread` — cone half-angle in degrees
- `colorStart` / `colorEnd` — gradient endpoints
- `lifetime` — particle life in seconds
- `textureRegion` — atlas region name

### Components

| Component | Purpose |
|---|---|
| `ParticleEmitterComponent` | Emitter position, currently active definition, burst state |
| `ParticlePoolComponent` | Pool of pre-allocated `Particle` and `ActiveEmitter` objects |

---

## Audio System

Located in `com.galacticodyssey.audio` and `com.galacticodyssey.core`.

### Playback Architecture

**`AudioManager`** (in `core/`)

Central playback hub. All other audio systems route through it:
- Maintains libGDX `Sound` and `Music` handles.
- Applies per-category volume (`AudioCategory`: MUSIC, SFX, AMBIENT, DIALOGUE, UI).
- For 3D sounds: passes listener position/orientation and source position to the OpenAL backend for distance attenuation and panning.
- Implements voice limiting (max concurrent SFX instances per effect).

**`AudioSystem`**

Subscribes to combat and gameplay events on the event bus. On each relevant event, looks up the sound ID from `SoundBindings`, computes the source world position, and calls `AudioManager.play3D(soundId, position)`.

Maintains the listener position by reading `FPSCameraComponent` (or the ship's position in piloting mode) each frame.

**`SoundBindings`**

Loaded from `data/audio/bindings.json`. Maps event class names to sound asset IDs, with optional distance-based muffling flags.

### Music

**`MusicManager`**

Dynamic music system. Maintains a current music state (combat, exploration, station, menu) and transitions between states with crossfade. State is driven by game events (entering combat triggers combat music; combat ends → fade back to exploration theme).

**`AmbientManager`**

Manages layered ambient soundscapes. Multiple ambient loops (space ambience, engine hum, wind, water) are blended by mixing their volumes. Transitions between locations fade layers independently for smooth continuity.

---

## Audio Categories

| Category | Examples |
|---|---|
| `MUSIC` | Dynamic music tracks |
| `SFX` | Weapon fire, impacts, footsteps, explosions |
| `AMBIENT` | Space ambience, wind, ocean, machinery hum |
| `DIALOGUE` | NPC speech lines |
| `UI` | Button clicks, menu transitions, notifications |

Volume for each category is saved in `GamePreferences` and exposed in the settings screen.
