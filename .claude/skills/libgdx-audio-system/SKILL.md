---
name: libgdx-audio-system
description: >
  Enforces correct audio architecture including 3D positional audio via OpenAL,
  streaming music management, sound effect pooling, volume mixing channels,
  event-driven audio triggers, and spatial audio propagation for a libGDX 3D
  space game. Use this skill whenever writing or modifying: 3D audio source
  positioning, music track management, sound effect triggering from game events,
  volume channel mixing (master, music, effects, dialogue), audio resource
  lifecycle, or spatial audio effects. Also triggers when adding audio variants,
  voice acting, or environmental soundscapes. See libgdx-audio-resource-lifecycle
  and era-thematic-audio-variant-matching for related skills.
---

# libGDX Audio System

## Architecture

Event-driven. Game systems post AudioEvents; AudioManager routes to subsystems:

```
Game Systems -> AudioEvent -> AudioManager
    |- MusicManager (streaming, crossfading)
    |- SFXManager (pooled one-shots)
    |- AmbientManager (environmental loops)
    +- DialogueManager (voice line queue)
```

## Audio Event

```java
public class AudioEvent {
    public AudioCategory category; // MUSIC, SFX, AMBIENT, DIALOGUE, UI
    public String soundId;
    public Vector3 worldPosition;  // null for non-positional
    public float volume = 1f;
    public float pitch = 1f;
    public boolean loop;
    public Entity attachTo;
}
```

## Music Management

Streaming with crossfade (default 2s). Priority tiers: Combat (3) > Exploration (2) > Ambient (1).

## Sound Effect Pooling

Max 32 simultaneous. 3D positioning with inverse-square attenuation. Max audible distance 5000m.

```java
public void play3D(String soundId, Vector3 sourcePos, Vector3 listenerPos,
                    Vector3 listenerForward, float volume) {
    float dist = sourcePos.dst(listenerPos);
    if (dist > MAX_AUDIBLE_DISTANCE) return;
    float attenuation = 1f / (1f + dist * dist * 0.001f);
    float pan = computePan(sourcePos, listenerPos, listenerForward);
    getOrLoad(soundId).play(volume * attenuation, 1f, pan);
}
```

## Environmental Soundscapes

| Environment | Layers |
|---|---|
| Deep Space | Low drone, distant star hum |
| Planet Surface | Wind, wildlife, weather (biome-specific) |
| Ship Interior | Engine hum, life support, console beeps |
| Station | Crowd murmur, PA, machinery |

Multiple ambient loops blend. Fade on environment change.

## Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| Max simultaneous SFX | 32 | Prevent overload |
| Max audible distance | 5000m | Sound culling |
| Crossfade duration | 2s | Music transition |
| Master volume | 1.0 | Global volume |
| Music volume | 0.7 | Music channel |

## Common Mistakes

| Mistake | Fix |
|---|---|
| Playing sounds directly | Post AudioEvent; let AudioManager route |
| Sounds not disposed | Track and dispose in dispose() |
| Music not crossfading | Always crossfade |
| No listener updates | Update from camera every frame |
| Too many simultaneous | Enforce max; drop lowest priority |
| Ambient not disposed on scene change | Clear layers when leaving |
