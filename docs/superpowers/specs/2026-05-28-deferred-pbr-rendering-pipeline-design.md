# Deferred PBR Rendering Pipeline — Design Spec

**Date:** 2026-05-28
**Status:** Approved
**Replaces:** Default libGDX forward renderer with diffuse+ambient shading

## Overview

Replace the current forward renderer (diffuse+ambient lighting, inline Java shaders, no framebuffers) with a full deferred PBR pipeline: G-buffer geometry pass, Cook-Torrance BRDF lighting, and a post-processing chain (SSAO, SSR, bloom, ACES tone mapping, FXAA). The pipeline supports texture-based PBR materials, dozens of dynamic lights (16–64), and integrates with existing systems (atmospheric sky, particles, HUD) without modifying them.

## Scope

**In scope:**
- G-buffer with 3 color render targets + depth/stencil
- Cook-Torrance PBR BRDF (GGX + Smith-Schlick + Fresnel-Schlick) matching glTF 2.0 spec
- Directional, point, and spot lights via light volumes
- PBR material system: JSON-defined materials, texture maps (albedo, normal, metallic-roughness, emissive, AO)
- Shader variants for textured PBR and vertex-color PBR (with/without emissive)
- Post-processing: SSAO, SSR, bloom, ACES tone mapping, FXAA 3.11
- External GLSL shader files with `#include` preprocessor
- Shader hot-reload in debug mode
- Integration with existing AtmosphericSkyRenderer, ParticleRenderSystem, CockpitHUDSystem

**Out of scope (future work):**
- Shadow mapping (directional, point, cascaded)
- Image-Based Lighting (IBL) with environment cubemaps
- Temporal anti-aliasing (TAA)
- Auto-exposure (luminance histogram)
- Volumetric lighting/fog
- Tiled/clustered deferred (not needed for 16–64 lights)

---

## 1. G-Buffer Layout

Three color attachments plus a shared depth/stencil buffer, written by the geometry pass.

| Render Target | R | G | B | A | Format | Notes |
|---|---|---|---|---|---|---|
| **RT0** | Albedo R | Albedo G | Albedo B | Metallic | RGBA8 | Base color + metallic. 8-bit metallic is sufficient. |
| **RT1** | Normal X | Normal Y | Roughness | AO | RGBA16F | View-space normals, octahedral encoded into RG (Z reconstructed). 16F avoids banding. AO from texture maps. |
| **RT2** | Emissive R | Emissive G | Emissive B | — | RGB16F | HDR emissive. Drives bloom. Ship windows, engines, weapons, station lights. |
| **Depth** | 24-bit depth | | | 8-bit stencil | D24S8 | Stencil=1 for geometry, stencil=0 for sky. Used by SSAO, SSR, lighting for position reconstruction and sky masking. |

**Key decisions:**
- **View-space normals** — cheaper position reconstruction from depth, avoids world-space precision issues at distance (floating-origin friendly).
- **Octahedral normal encoding** — packs 3-component normal into 2 channels with better quality than spheremap encoding. Z reconstructed in the lighting shader.
- **No position RT** — position reconstructed from depth via inverse projection. Saves 12–16 bytes/pixel.
- **Stencil for sky masking** — lighting, SSAO, and SSR skip pixels where stencil=0.

**Bandwidth:** 22 bytes/pixel → ~45 MB at 1080p, ~180 MB at 4K.

---

## 2. Render Pass Structure

Eight passes per frame, executed in order:

### Pass 1 — G-Buffer (Geometry)
- **FBO:** gBuffer (3 color attachments + depth/stencil)
- **Renders:** All opaque geometry — terrain, ships, world objects (trees, rocks, buildings), FPS weapon
- **Shader:** `gbuffer.vert` / `gbuffer.frag` with `#define` variants per material type
- **FPS weapon:** Rendered last with depth clear + near-plane 0.01 override (same trick as current code)
- **Stencil:** Writes 1 for all geometry fragments

### Pass 2 — SSAO
- **FBO:** ssaoBuffer (R8, half resolution)
- **Inputs:** RT1 normals, depth buffer, 4×4 noise texture, hemisphere kernel samples
- **Output:** Single-channel AO texture
- **Then:** Two-pass bilateral blur (horizontal + vertical) to smooth noise while preserving edges
- **Upscaled** before use in lighting pass

### Pass 3 — Deferred Lighting
- **FBO:** hdrBuffer (RGBA16F)
- **Inputs:** All G-Buffer RTs, SSAO texture
- **Shader:** `lighting_directional.frag`, `lighting_point.frag`, `lighting_spot.frag`
- **Steps:**
  1. Reconstruct view-space position from depth
  2. Read surface properties from G-Buffer
  3. Directional light (sun): fullscreen quad, Cook-Torrance BRDF
  4. Point lights: additive blending, sphere light volumes with frustum culling
  5. Spot lights: additive blending, cone light volumes
  6. Ambient term: ambientColor × ambientIntensity × SSAO × AO_map
  7. Add emissive from RT2
- **Stencil test:** Only shade pixels where stencil=1

### Pass 4 — Sky + Forward Transparents
- **FBO:** hdrBuffer (continued), using G-Buffer depth for depth testing
- **Sky:** AtmosphericSkyRenderer fullscreen quad where stencil=0 (existing code, unchanged)
- **Water:** Forward-rendered with PBR, alpha blending, wave displacement, depth read only
- **Particles:** Billboard + mesh particles (existing ParticleRenderSystem), forward-lit or unlit+emissive
- **Sorting:** Transparent objects sorted back-to-front

### Pass 5 — Screen-Space Reflections
- **FBO:** ssrBuffer (RGBA16F)
- **Inputs:** HDR lit buffer, RT1 normals, depth, RT0 metallic+roughness
- **Algorithm:** 16-step linear ray march + 4-step binary refinement in screen space
- **Confidence mask:** Fades at screen edges, for large step distances, and for rays pointing away from camera
- **Roughness cutoff:** Skip if roughness > 0.7
- **Compositing:** `finalColor = mix(litColor, ssrColor, confidence × fresnel × (1 - roughness))`
- **Max distance:** 50 view-space units

### Pass 6 — Bloom
- **FBOs:** bloomDown[0–5] (RGBA16F at decreasing resolutions), bloomUp[0–4]
- **Downsample:** Brightness threshold extraction with soft knee, then 6 levels of progressive downsample using 13-tap filter
- **Upsample:** Walk back up the chain, blending each level with the one above using 9-tap tent filter
- **Composite:** Additive blend into HDR buffer
- **Parameters:** Threshold=1.0, soft knee=0.5, intensity=0.3

### Pass 7 — Tone Mapping + FXAA
- **Tone mapping:** ACES filmic curve. Exposure driven by DayNightCycle sun intensity. sRGB gamma correction.
- **FXAA:** FXAA 3.11, quality preset 12, edge threshold 0.166. Tone mapping writes to ldrBuffer (RGBA8). FXAA reads ldrBuffer, writes to the default framebuffer (screen). Two separate fullscreen passes.

### Pass 8 — HUD / UI Overlay
- **FBO:** Default framebuffer (screen)
- **Renders:** CockpitHUD, DebugHUD, DialogHUD, HackingOverlay, PauseMenu
- **Unchanged** from current implementation — Scene2D stages drawn on top of the resolved 3D image

**Total FBOs per frame:**
- gBuffer: 3 color + depth/stencil (persistent)
- ssaoBuffer: 1×R8 + 1×R8 blur temp (persistent)
- hdrBuffer: 1×RGBA16F (persistent)
- ssrBuffer: 1×RGBA16F (persistent)
- bloomDown[0–5]: 6×RGBA16F at decreasing res (persistent)
- bloomUp[0–4]: 5×RGBA16F at increasing res (can reuse down FBOs)
- ldrBuffer: 1×RGBA8 for FXAA input (persistent)
- **Total VRAM at 1080p:** ~85–100 MB

---

## 3. PBR Material System

### Material Data Model

JSON-defined assets in `data/materials/`, loaded by `MaterialDataRegistry`. Pattern follows existing `WeaponDataRegistry` and `CombatDataRegistry`.

```json
{
  "name": "hull_titanium",
  "albedoMap": "textures/pbr/hull_titanium_albedo.png",
  "normalMap": "textures/pbr/hull_titanium_normal.png",
  "metallicRoughnessMap": "textures/pbr/hull_titanium_mr.png",
  "emissiveMap": null,
  "aoMap": "textures/pbr/hull_titanium_ao.png",
  "tilingX": 2.0,
  "tilingY": 2.0,
  "albedoTint": [1.0, 1.0, 1.0, 1.0],
  "metallicScale": 1.0,
  "roughnessScale": 1.0,
  "emissiveIntensity": 0.0
}
```

**Texture conventions:**
- Metallic-Roughness map: G=roughness, B=metallic (glTF convention, compatible with gdx-gltf)
- Normal map: Tangent-space, OpenGL convention (Y+ up)
- AO map: Single-channel baked ambient occlusion (multiplied with SSAO)
- Emissive map: RGB color × emissiveIntensity = HDR emissive output

### MaterialComponent (Ashley ECS)

```java
public class MaterialComponent implements Component {
    public Texture albedoMap;
    public Texture normalMap;
    public Texture metallicRoughnessMap;
    public Texture emissiveMap;         // nullable
    public Texture aoMap;               // nullable
    public float tilingX = 1f, tilingY = 1f;
    public Color albedoTint = Color.WHITE;
    public float metallicScale = 1f;
    public float roughnessScale = 1f;
    public float emissiveIntensity = 0f;
}
```

### G-Buffer Shader Variants

One base shader with `#define` flags, compiled into variants at startup (not runtime branching):

| Variant | Defines | Used By |
|---|---|---|
| Textured PBR | `HAS_ALBEDO_MAP`, `HAS_NORMAL_MAP`, `HAS_MR_MAP` | World objects, imported glTF, buildings |
| Textured + Emissive | + `HAS_EMISSIVE_MAP` | Ship windows, station lights, consoles |
| Vertex Color PBR | `HAS_VERTEX_COLOR` | Terrain (biome colors), procgen ships |
| Vertex Color + Emissive | `HAS_VERTEX_COLOR`, `HAS_EMISSIVE_ATTRIB` | Procgen ships with emissive windows/engines |

### Fallback Strategy

Missing textures fall back gracefully — no runtime errors, just reduced visual quality:

| If missing... | Fallback |
|---|---|
| Albedo map | Use `albedoTint` color (or vertex color if `HAS_VERTEX_COLOR`) |
| Normal map | Use interpolated vertex normal |
| Metallic-Roughness map | Use `metallicScale` / `roughnessScale` as uniform values |
| AO map | AO = 1.0 (rely on SSAO only) |
| Emissive map | Emissive = vec3(0) (or vertex emissive attribute) |

Existing terrain and procgen ships work immediately — vertex colors become albedo, uniform metallic/roughness give reasonable defaults.

---

## 4. Lighting Model

### Cook-Torrance PBR BRDF

Industry-standard microfacet model matching glTF 2.0 / Khronos PBR reference:

```
f(l, v) = f_diffuse + f_specular

f_diffuse  = (1 - metallic) × albedo / π                    (Lambert)
f_specular = D(h) × G(l, v) × F(v, h) / (4 × (n·l) × (n·v))  (Cook-Torrance)
```

- **D — GGX/Trowbridge-Reitz:** Normal distribution function. α = roughness².
- **G — Smith-Schlick (height-correlated):** Geometric shadowing/masking. k = (roughness+1)²/8.
- **F — Fresnel-Schlick:** F₀ = mix(0.04, albedo, metallic). Dielectrics reflect ~4% head-on; metals use albedo as F₀.

### Light Types

| Type | Rendering | Attenuation | Use Cases |
|---|---|---|---|
| Directional | Fullscreen quad, single draw call | None (infinite distance) | Sun, star light |
| Point | Sphere mesh, inside-out culling trick | 1/(1 + d/r)² with smooth falloff | Thrusters, explosions, station lamps, muzzle flash |
| Spot | Cone mesh, same inside-out trick | Point attenuation × angular smoothstep | Ship headlights, hangar spots, flashlight |

### LightComponent (Ashley ECS)

```java
public class LightComponent implements Component {
    public enum Type { DIRECTIONAL, POINT, SPOT }
    public Type type = Type.POINT;
    public final Color color = new Color(1, 1, 1, 1);
    public float intensity = 1f;
    public float radius = 10f;
    public float innerCone = 30f;
    public float outerCone = 45f;
    public boolean castShadows = false;
}
```

### Light Volume Rendering

For each point/spot light:
1. Frustum-cull the light's bounding sphere — skip if offscreen
2. If camera is **outside** volume: render front faces, depth test LESS
3. If camera is **inside** volume: render back faces, depth test GREATER
4. Additive blend into HDR buffer
5. Stencil test: skip pixels where stencil ≠ 1

### Ambient Lighting

**This implementation:** Flat ambient driven by DayNightCycle. `ambient = ambientColor × ambientIntensity × SSAO × AO_map`.

**Future:** IBL with environment cubemaps (diffuse irradiance + specular prefiltered environment + BRDF LUT). The lighting shader has a clean hook point for swapping in IBL later.

---

## 5. Post-Processing Effects

### SSAO (Screen-Space Ambient Occlusion)

- **Method:** John Chapman hemisphere sampling
- **Samples:** 32 (quality) / 16 (performance), configurable
- **Radius:** 0.5 units (adjustable per-scene)
- **Bias:** 0.025 (prevents self-occlusion)
- **Noise:** 4×4 random rotation texture, tiled across screen
- **Resolution:** Half resolution for performance (960×540 at 1080p)
- **Blur:** 4×4 bilateral blur (2 passes: H + V), edge-aware to preserve creases
- **Output:** R8 AO texture, upscaled before use in lighting pass

### SSR (Screen-Space Reflections)

- **Method:** Linear ray march + binary refinement in screen space
- **Steps:** 16 linear + 4 binary refinement
- **Max distance:** 50 view-space units
- **Thickness:** 0.1 units (depth comparison tolerance)
- **Edge fade:** Fade out in outer 10% of screen
- **Roughness cutoff:** Skip if roughness > 0.7
- **Compositing:** `mix(litColor, ssrColor, confidence × fresnel × (1 - roughness))`
- **Fallback:** Ambient term for misses
- **Target surfaces:** Ship hulls, water, station floors

### Bloom

- **Method:** Progressive downsample/upsample (dual Kawase style)
- **Threshold extraction:** Soft-knee: `bright = max(color - threshold, 0) × color / (color + 0.001)`
- **Downsample:** 6 levels (½ → 1/32 res), 13-tap filter
- **Upsample:** Walk back up, 9-tap tent filter blending each level with the one above
- **Composite:** Additive blend into HDR buffer
- **Parameters:** Threshold=1.0, soft knee=0.5, intensity=0.3

### Tone Mapping

- **Curve:** ACES filmic (Academy Color Encoding System fitted curve)
- **Exposure:** Driven by `DayNightCycle.getSunIntensity()`, adjustable via debug slider
- **Gamma:** sRGB output via `pow(color, 1/2.2)` or `GL_SRGB` framebuffer
- **Future:** Auto-exposure via luminance histogram

### FXAA 3.11

- **Quality preset:** FXAA_QUALITY_PRESET 12
- **Edge threshold:** 0.166 (medium sensitivity)
- **Cost:** <1ms at 1080p on modern GPU
- **Applied:** Final fullscreen pass on LDR image after tone mapping
- **Future upgrade path:** TAA with motion vectors

---

## 6. Architecture — Class Structure

### New Package: `com.galacticodyssey.rendering`

```
rendering/
├── DeferredRenderer.java           // Pipeline orchestrator — owns all FBOs, runs all passes
├── GBuffer.java                    // Creates/manages 3 RTs + depth/stencil FBO
├── LightingPass.java               // Deferred lighting resolve
├── ForwardPass.java                // Sky, water, particles into HDR buffer
│
├── materials/
│   ├── MaterialComponent.java      // Ashley component: texture refs + PBR scalars
│   ├── MaterialDataRegistry.java   // Loads material JSON, manages textures
│   └── GBufferShaderProvider.java  // Builds #define shader variants
│
├── lighting/
│   ├── LightComponent.java         // Ashley component: type, color, intensity, radius
│   ├── LightVolumeMesh.java        // Generates sphere/cone meshes
│   └── LightingSystem.java         // Ashley system: collects lights for LightingPass
│
├── postfx/
│   ├── PostProcessingPipeline.java // Chains effects, manages intermediate FBOs
│   ├── SSAOEffect.java
│   ├── SSREffect.java
│   ├── BloomEffect.java
│   ├── ToneMappingEffect.java
│   └── FXAAEffect.java
│
└── shaders/
    ├── ShaderCache.java            // Compiles, caches, disposes ShaderPrograms
    └── ShaderUtils.java            // #include preprocessor, uniform helpers
```

### External Shader Files: `resources/shaders/`

```
shaders/
├── gbuffer.vert / gbuffer.frag
├── lighting_directional.vert / lighting_directional.frag
├── lighting_point.vert / lighting_point.frag
├── lighting_spot.frag
├── ssao.frag
├── blur_bilateral.frag
├── ssr.frag
├── bloom_downsample.frag / bloom_upsample.frag
├── tonemap.frag
├── fxaa.frag
├── fullscreen.vert
├── sky_atmospheric.vert / sky_atmospheric.frag (extracted from Java)
├── forward_transparent.vert / forward_transparent.frag
└── include/
    ├── pbr_common.glsl
    ├── normal_encoding.glsl
    └── depth_reconstruct.glsl
```

`include/` files injected via a simple preprocessor in `ShaderUtils` that replaces `#include "file"` directives before compilation.

### DeferredRenderer — The Orchestrator

```java
public class DeferredRenderer implements Disposable {
    private final GBuffer gBuffer;
    private final LightingPass lightingPass;
    private final ForwardPass forwardPass;
    private final PostProcessingPipeline postFX;
    private final ShaderCache shaderCache;

    public void render(PerspectiveCamera camera, RenderContext ctx) {
        gBuffer.begin();
        ctx.renderOpaque(gBuffer.getShaderProvider());
        ctx.renderFirstPersonWeapon(gBuffer.getShaderProvider());
        gBuffer.end();

        postFX.applySSAO(gBuffer);
        lightingPass.resolve(gBuffer, postFX.getSSAOTexture(), camera);

        forwardPass.render(lightingPass.getHDRBuffer(), gBuffer.getDepth(),
                           ctx.getSky(), ctx.getWater(), ctx.getParticles());

        postFX.apply(lightingPass.getHDRBuffer(), gBuffer);
    }

    public void resize(int width, int height) { /* recreate all FBOs */ }
    public void dispose() { /* dispose all FBOs, shaders, meshes */ }
}
```

### GameScreen Integration

GameScreen creates a `DeferredRenderer` and a `RenderContext`. Current render methods become data providers:

| Current Method | Becomes |
|---|---|
| `renderTerrain()` | Opaque renderable in RenderContext (terrain Mesh + MaterialComponent) |
| `renderBoxes()` | Opaque renderables (ModelInstance + MaterialComponent per entity) |
| `renderWorldObjects()` | Opaque renderables (trees, rocks, buildings with material data) |
| `renderShips()` | Opaque renderables (ShipMeshComponent + MaterialComponent) |
| `atmosphericSkyRenderer` | Passed to ForwardPass (renders into HDR where stencil=0) |
| Water rendering | Forward transparent renderable |
| ParticleRenderSystem | Passed to ForwardPass |
| HUD / UI stages | **Unchanged** — rendered to screen after pipeline completes |

### Removed

- **FogShaderProvider** — fog computed from depth in the lighting pass
- **Inline shader strings** — replaced by external GLSL files
- **fogModelBatch** — replaced by DeferredRenderer G-Buffer pass
- **SkyRenderer.java** — already unused, deleted
- **Scattered GL state management** — moved into pass classes

### Lifecycle

- **Resize:** `GameScreen.resize()` → `deferredRenderer.resize(w, h)`. All FBOs recreated. Bloom mip chain recalculated.
- **Dispose:** `DeferredRenderer.dispose()` called from `GameScreen.dispose()`. Disposes all FBOs, shaders, light volume meshes, fullscreen quad. `MaterialDataRegistry` disposes cached textures separately.
- **Hot reload (debug):** `ShaderCache` watches file timestamps. F5 recompiles all shaders from disk. Errors logged to DebugHUD; last working shader stays active.
