# Procgen Visual Foundation — Design Spec

**Date:** 2026-05-27
**Scope:** Sky rendering, distance fog, terrain color variation
**Approach:** Shader-first — enhance existing forward pipeline, no FBO/deferred changes

## Problem

The procedural game world looks like a prototype: flat dark void behind terrain, no atmospheric depth cues, and each biome is a single solid color. These three gaps are the biggest visual quality blockers.

## 1. Procedural Sky

A fullscreen sky shader rendered behind all geometry. Provides a sense of atmosphere and place.

### Rendering Order

1. Clear color + depth buffer
2. Render sky quad (depth write OFF, drawn at far plane)
3. Render terrain, world objects, ships (depth write ON — overwrites sky)
4. Render debug HUD (depth test OFF)

### Sky Shader

**Vertex shader:** Pass-through for a fullscreen quad (two triangles covering NDC -1..1). Compute view-space ray direction from the quad position using the inverse view-projection matrix.

**Fragment shader:**
- **Gradient:** Lerp between zenith color (dark blue) and horizon color (warm haze) based on `ray.y` (altitude above horizon). Use `smoothstep` for a natural falloff.
- **Sun disc:** Compute `dot(normalize(rayDir), sunDirection)`. Apply a tight `pow()` falloff for the bright disc, plus a wider soft glow.
- **Horizon haze:** Additional warm band at `ray.y ≈ 0` to bridge sky and fog color.

**Uniforms:**
- `u_invViewProj` (mat4) — inverse view-projection for ray reconstruction
- `u_sunDirection` (vec3) — normalized light direction
- `u_zenithColor` (vec3) — top-of-sky color, default `(0.05, 0.1, 0.3)`
- `u_horizonColor` (vec3) — horizon color, default `(0.6, 0.55, 0.45)` — shared with fog
- `u_sunColor` (vec3) — sun disc tint, default `(1.0, 0.9, 0.7)`

### New Class: `SkyRenderer`

- **Package:** `com.galacticodyssey.ui`
- **Owns:** fullscreen quad `Mesh` (4 verts, 6 indices), `ShaderProgram`
- **API:** `render(PerspectiveCamera camera, Vector3 sunDirection)`, `resize(w, h)`, `dispose()`
- **Implements:** `Disposable`

## 2. Distance Fog

Exponential squared fog applied to terrain, ship, and ModelBatch shaders. Blends distant geometry smoothly into the sky's horizon color.

### Formula

```
float dist = length(worldPos - cameraPos);
float fogFactor = exp(-fogDensity * dist * fogDensity * dist);
fogFactor = clamp(fogFactor, 0.0, 1.0);
finalColor = mix(fogColor, litColor, fogFactor);
```

- `fogFactor = 1.0` → fully visible (close)
- `fogFactor = 0.0` → fully fogged (distant)

### Fog Color = Horizon Color

The fog color must match the sky's horizon color exactly. This creates a seamless transition — distant terrain fades into the same color the sky shows at the horizon, so there is never a visible seam.

### Shader Changes

**Terrain shader:**
- Add `uniform vec3 u_cameraPos`, `uniform float u_fogDensity`, `uniform vec3 u_fogColor`
- Compute world position in vertex shader, pass as `varying`
- Apply fog formula after lighting in fragment shader

**Ship shader:**
- Same three uniforms, same fog formula in fragment shader
- World position derived from `u_worldTrans * a_position`

**ModelBatch objects (trees, rocks, grass, animals):**
- Create `FogShaderProvider` extending `DefaultShaderProvider`
- Override `createShader()` to return a custom `DefaultShader` subclass that injects fog uniforms and applies the fog formula
- Set this provider on the `ModelBatch` in GameScreen

### Uniforms (shared across all shaders)

- `u_fogDensity` (float) — default `0.004`, tunable
- `u_fogColor` (vec3) — same as sky horizon color
- `u_cameraPos` (vec3) — camera world position

## 3. Terrain Color Variation

Four layers of visual detail computed at terrain mesh build time, all in vertex colors. No shader changes required — the terrain shader already reads `a_color`.

### Layer 1: Noise Variation

Sample simplex noise at each vertex's world position (using the terrain seed) and perturb the biome base color by ±15% in RGB. This breaks up the flat single-color-per-biome look. Use two noise octaves at different frequencies for natural-looking variation.

### Layer 2: Biome Edge Blending

At each vertex, sample the biome grid at the 4 neighboring cells. If any neighbor has a different biome, lerp between the two biome colors based on a noise-perturbed distance factor. Creates soft ~10m transition zones instead of hard pixel-level edges between biomes.

### Layer 3: Slope-Driven Rock

Where `normal.y < 0.7` (steep slopes > ~45°), blend the vertex color toward a grey-brown rock color `(0.42, 0.38, 0.32)`. Use `smoothstep(0.5, 0.75, normal.y)` as the blend factor so the transition is gradual. Cliffs and mountainsides show exposed rock regardless of biome.

### Layer 4: Altitude Snow

Above a snow line threshold (computed as `75th percentile of height range`, perturbed by noise), blend vertex color toward white `(0.92, 0.93, 0.95)`. Use `smoothstep` over a noise-varied range so the snow line isn't a hard horizontal band. Mountain peaks get natural snow caps.

### Implementation Location

Enhance `WorldPopulator.biomeColor()` signature to:

```java
public static Color biomeColor(BiomeType biome, float heightFrac, float slope,
                                float worldX, float worldZ, float height,
                                float minH, float maxH, int[] noisePerm,
                                BiomeType[] biomeGrid, int vertsX, int vertsZ,
                                int gridX, int gridZ)
```

All four layers computed per-vertex during `GameScreen.createTerrainMesh()`. The noise permutation table is derived from the terrain seed for determinism.

## Integration Points

### GameScreen.render() Order (Updated)

```
1. ScreenUtils.clear(...)
2. skyRenderer.render(camera, sunDirection)      // NEW — sky behind everything
3. gameWorld.update(clampedDelta)                 // ECS update (no HUD draw)
4. updateAnimals(...)
5. syncBoxTransforms()
6. renderTerrain()                                // fog applied in shader
7. renderBoxes()                                  // fog via FogShaderProvider
8. renderWorldObjects()                           // fog via FogShaderProvider
9. renderShips()                                  // fog applied in shader
10. debugHudSystem.render(delta)                  // HUD on top, no depth test
11. pauseStage (if paused)
```

### Shared State

Sky horizon color and fog color must be the same `Vector3` instance (or at minimum set to the same value). This is owned by GameScreen and passed to both `SkyRenderer` and all fog-enabled shaders each frame.

### New Files

| File | Purpose |
|------|---------|
| `ui/SkyRenderer.java` | Sky quad mesh + shader, render method |
| `ui/FogShaderProvider.java` | ModelBatch shader provider that injects fog |

### Modified Files

| File | Change |
|------|--------|
| `ui/GameScreen.java` | Add SkyRenderer, update render order, set fog uniforms on terrain/ship shaders, use FogShaderProvider for ModelBatch |
| `data/WorldPopulator.java` | Enhance `biomeColor()` with noise variation, blending, slope rock, altitude snow |

## Out of Scope (Future Work)

- FBO/post-processing pipeline (bloom, tone mapping, FXAA)
- Shadow mapping
- Water wave animation and fresnel
- Vegetation quality improvements (better geometry, wind sway, LOD)
- Cloud layer in sky shader
- Time-of-day cycle
