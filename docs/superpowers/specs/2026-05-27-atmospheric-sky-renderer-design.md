# Atmospheric Sky Renderer — Design Spec

**Date:** 2026-05-27  
**Status:** Draft  
**Approach:** Single-pass analytic Rayleigh+Mie scattering with integrated volumetric clouds  
**Replaces:** `SkyRenderer.java` (fullscreen gradient + sun disc)  
**Fixes:** Sky/horizon bleed through terrain at low camera angles  

---

## Problem Statement

The current `SkyRenderer` uses a static gradient with hardcoded horizon/zenith colors and a sun disc. The `FogShaderProvider` uses a fixed fog color `(0.6, 0.55, 0.45)` that doesn't match the sky at all angles, causing the terrain to blend into a color that doesn't match the actual sky — creating the illusion that "sky and clouds are below the mountains."

There are no clouds at all in the current system.

The atmosphere does not vary per planet despite the existing `AtmosphereGenerator` computing gas composition, pressure, and temperature for every planet.

---

## Design

### 1. ScatteringParams — Deriving Physics from Atmosphere

`ScatteringParams` is a pure-data class that converts `Atmosphere` (gas composition, pressure, temperature) and `Planet` (radius, day length, axial tilt, tidal locking) into physical scattering coefficients the shader consumes.

**Derivation rules:**

| Source Data | Derived Parameter | Logic |
|---|---|---|
| Gas composition (N2, O2 fractions) | `rayleighCoeff` (vec3) | Wavelength-dependent: `baseCoeff * (pressure / 1.0) * molecularFactor`. N2/O2-heavy → blue scattering. CO2-heavy → shifts toward amber. |
| Pressure | `scaleHeightRayleigh` | Higher pressure → lower scale height (denser, more opaque atmosphere). Range: 4km (crushing) to 12km (thin). |
| Aerosol proxy (planet type) | `mieCoeff` (float) | ARID/DESERT → high (0.02–0.04), OCEAN → low (0.003–0.006), TOXIC → very high (0.05+). |
| Planet type | `mieG` (float) | Mie asymmetry parameter. 0.76 for Earth-like, 0.85 for dusty, 0.6 for thick haze. |
| Surface temperature | `sunIntensity` (float) | Derived from star luminosity reaching the planet. Hotter equilibrium → brighter sun. |
| Planet radius | `planetRadius` (float) | In render-space units (matching the terrain coordinate system). For the flat test terrain, defaults to 6371 (Earth radius in km scaled to match the 500-unit terrain as a local patch). Used for atmosphere shell geometry in the scattering integral. |
| Pressure + radius | `atmosphereRadius` (float) | `planetRadius + scaleHeight * 6`. Thin atmo → small shell, dense → large shell. |
| Moisture/climate data | `cloudCoverage` (float) | 0.0 (no clouds) to 1.0 (overcast). OCEAN → 0.6–0.8, ARID → 0.05–0.15, ICE → 0.3–0.5. |
| Pressure + temperature | `cloudBase`, `cloudTop` (float) | Altitude of cloud layer. Higher pressure → lower cloud base. Range: 1–4km base, 2–8km top. |
| Gas composition | `absorptionCoeff` (vec3) | Ozone-like absorption for O2-rich atmospheres. Shifts sunset toward deeper reds. SO2-heavy → yellowish absorption. |

**Hazard-driven visual overrides:**
- `VACUUM` (pressure < 0.01): All scattering near zero. Black sky, no fog, no clouds. Stars always visible.
- `CRUSHING` (pressure > 10): Very dense atmosphere. High Rayleigh, high Mie, thick fog, low visibility.
- `TOXIC` (SO2 > 5%): Yellow-brown tint added to Rayleigh coefficients.
- `CORROSIVE`: Purple-brown tint, very high Mie (acid haze).

**For the current flat test terrain** (no Planet object): `ScatteringParams.earthLike()` provides sensible defaults matching a habitable world.

### 2. AtmosphericSkyRenderer — The Shader

Replaces `SkyRenderer`. Same architecture: fullscreen quad rendered before all geometry with depth test disabled. But the fragment shader computes physically-based scattering instead of a gradient.

**Vertex shader:** Identical to current — pass-through fullscreen quad, compute ray direction from inverse view-projection.

**Fragment shader algorithm:**

```
For each pixel:
  1. Compute view ray direction from inverse VP matrix
  2. Intersect ray with atmosphere sphere (inner = planetRadius, outer = atmosphereRadius)
  3. If ray hits planet surface (below horizon): use ground-level scattering
  4. March along ray through atmosphere (8–16 steps):
     a. At each step, compute altitude above surface
     b. Look up atmospheric density at altitude: exp(-altitude / scaleHeight)
     c. Accumulate optical depth for Rayleigh and Mie
     d. March toward sun (4 steps) to compute light reaching this point
     e. Accumulate in-scattered light: Rayleigh phase * rayleighCoeff + Mie phase * mieCoeff
  5. If ray passes through cloud layer (cloudBase to cloudTop):
     a. Sample 3D FBM noise at ray position (4 octaves + domain warp)
     b. Modulate by cloudCoverage (2D noise field)
     c. Accumulate cloud opacity and lighting (beer-powder approximation)
     d. Cloud color = sunColor * transmittance-to-sun * phase
  6. Composite: sky scattering behind clouds, blended by cloud opacity
  7. Add sun disc (physical angular size from star data)
  8. Output final color
```

**Phase functions:**
- Rayleigh: `(3/16π) * (1 + cos²θ)` where θ is angle between view and sun
- Mie (Henyey-Greenstein): `(1 - g²) / (4π * (1 + g² - 2g·cosθ)^1.5)`

**Cloud noise:**
- 3D Simplex noise with 4 octaves, persistence 0.5, lacunarity 2.0
- Domain warped by a second 2D noise field for shape variety
- Altitude ramp: density peaks at `cloudBase + (cloudTop - cloudBase) * 0.3`, fades to zero at both edges
- Animated by adding `time * windSpeed` to noise coordinates

**Uniforms:**

```glsl
// Atmosphere geometry
uniform float u_planetRadius;
uniform float u_atmosphereRadius;

// Scattering
uniform vec3 u_rayleighCoeff;
uniform float u_mieCoeff;
uniform float u_mieG;
uniform vec3 u_absorptionCoeff;
uniform float u_scaleHeightRayleigh;
uniform float u_scaleHeightMie;

// Sun
uniform vec3 u_sunDirection;
uniform float u_sunIntensity;
uniform float u_sunAngularRadius;

// Clouds
uniform float u_cloudBase;
uniform float u_cloudTop;
uniform float u_cloudCoverage;
uniform float u_time;
uniform vec2 u_windDirection;

// Camera
uniform mat4 u_invViewProj;
uniform vec3 u_cameraPos;
```

**Public API:**

```java
public class AtmosphericSkyRenderer implements Disposable {
    public AtmosphericSkyRenderer();

    // Configure from planet data
    public void setScatteringParams(ScatteringParams params);

    // Update sun position (called each frame by DayNightCycle)
    public void setSunDirection(Vector3 dir);
    public void setTime(float elapsedSeconds);

    // Render the sky (replaces SkyRenderer.render)
    public void render(PerspectiveCamera camera);

    // Query for fog integration — computed from last render's scattering
    public Vector3 getHorizonColor();
    public float getFogDensity();
    public Vector3 getSunDirection();
    public float getAmbientIntensity();
}
```

`getHorizonColor()` computes what the sky scattering produces at the horizon for the current sun angle. This is evaluated once per frame (sample a ray at `y ≈ 0`) and cached. The terrain/ship fog shaders read this value instead of a hardcoded constant.

### 3. DayNightCycle — Sun Rotation

Tracks elapsed game time and derives the sun direction from planet rotation parameters.

```java
public class DayNightCycle {
    public DayNightCycle(float dayLengthSeconds, float axialTiltDegrees,
                         boolean tidallyLocked);

    // Call each frame
    public void update(float delta);

    // Current sun state
    public Vector3 getSunDirection();    // normalized
    public float getSunAltitude();       // -1 (nadir) to +1 (zenith)
    public float getTimeOfDay();         // 0.0 = midnight, 0.5 = noon
    public boolean isNight();            // sun below horizon

    // Lighting multipliers
    public float getSunIntensity();      // 0 at night, 1 at noon, smooth transition
    public float getAmbientIntensity();  // low at night, moderate at day
    public float getStarVisibility();    // 1 at night, 0 at day, fade at twilight
}
```

**Sun path:** The sun orbits in a great circle perpendicular to the planet's rotational axis. With axial tilt, the sun's maximum elevation varies by "season" (though we don't simulate full orbital seasons, the tilt affects the sun arc geometry).

**Tidally locked:** Sun direction is fixed based on the player's position relative to the sub-stellar point. The terminator zone gets perpetual twilight scattering.

**Transition smoothing:** Sun intensity and ambient use `smoothstep` over a ±6° band around the horizon to prevent popping.

### 4. Fog Integration — Fixing the Bug

The core fix is simple: fog color is no longer hardcoded. Each frame:

1. `AtmosphericSkyRenderer` renders the sky and caches `horizonColor` (scattering result at horizon angle).
2. `GameScreen` reads `atmosphericSkyRenderer.getHorizonColor()` and `getFogDensity()`.
3. These values are passed to `FogShaderProvider.setFogParams()` and to the terrain/ship shader uniforms.
4. All fog now exactly matches the sky at the horizon — no mismatch, no bleed.

`FogShaderProvider` API is unchanged — `setFogParams(float density, Vector3 color)` — but the *values* it receives are now dynamic per-frame instead of static.

Additionally, `fogDensity` is now derived from atmosphere pressure:
- Vacuum: `0.0` (infinite visibility)
- Thin: `0.001`
- Earth-like: `0.004` (current value preserved)
- Dense: `0.008–0.015`
- Crushing: `0.02+`

### 5. Render Order

Updated render order in `GameScreen.render()`:

```
1. ScreenUtils.clear()
2. atmosphericSkyRenderer.render(camera)     // sky + clouds, depth off
3. renderTerrain()                            // fog uses dynamic horizon color
4. renderBoxes()                              // fog uses dynamic horizon color
5. renderWorldObjects()                       // fog uses dynamic horizon color
6. renderShips()                              // fog uses dynamic horizon color
7. renderWater()                              // transparency pass
8. HUD / UI overlays
```

Identical to current order — `AtmosphericSkyRenderer` is a drop-in replacement for `SkyRenderer`.

---

## File Map

```
NEW FILES:
  core/src/main/java/com/galacticodyssey/planet/ScatteringParams.java
  core/src/main/java/com/galacticodyssey/ui/AtmosphericSkyRenderer.java
  core/src/main/java/com/galacticodyssey/ui/DayNightCycle.java
  core/src/test/java/com/galacticodyssey/planet/ScatteringParamsTest.java
  core/src/test/java/com/galacticodyssey/ui/DayNightCycleTest.java

MODIFIED FILES:
  core/src/main/java/com/galacticodyssey/ui/GameScreen.java
    — Replace SkyRenderer with AtmosphericSkyRenderer
    — Add DayNightCycle
    — Feed dynamic fog color/density each frame
  core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java
    — No API changes, just receives dynamic values now

DELETED FILES:
  (none — SkyRenderer.java kept for reference but unused)
```

---

## Testing Strategy

**ScatteringParamsTest:**
- Earth-like atmosphere produces blue-ish Rayleigh coefficients (blue channel > red channel)
- CO2-heavy atmosphere shifts Rayleigh toward amber (red > blue)
- Vacuum atmosphere produces near-zero coefficients
- High pressure → lower scale height, higher fog density
- Cloud coverage scales with moisture

**DayNightCycleTest:**
- Sun direction rotates over one full day cycle
- `getTimeOfDay()` advances from 0 to 1 over `dayLengthSeconds`
- `isNight()` returns true when sun is below horizon
- Tidally locked: sun direction doesn't change with time
- Sun intensity is 0 at night, 1 at noon
- Star visibility inversely tracks sun intensity

**Visual verification:**
- Run the game and observe: sky is blue at noon, orange/red at sunset, dark at night
- Fog matches sky at all times of day — no horizon bleed
- Clouds visible in cloud layer, not below terrain
- Different planet atmospheres produce visually distinct skies

---

## Constraints & Decisions

1. **Single pass over two passes**: Clouds are integrated into the sky shader rather than a separate pass. This means clouds can't be rendered at lower resolution independently, but avoids compositing complexity and the cloud layer is thin enough that the extra samples are cheap.

2. **Analytic over LUT**: We use the Nishita analytic integral rather than precomputed Bruneton LUTs. Slightly less accurate at extreme grazing angles but avoids compute shader dependencies and per-planet precomputation cost.

3. **Flat terrain approximation**: The scattering model uses a spherical atmosphere, but the current game terrain is a flat heightmap (not a sphere). The shader treats the camera as being on the surface of a sphere with the configured `planetRadius`. This is correct for the player's local view — the curvature only matters at >10km distances which are fogged out anyway.

4. **No star field yet**: Night sky shows a dark gradient. Star rendering is a separate feature that can be added later as a particle layer behind the atmosphere.

5. **Cloud shadow on terrain**: Not included in this spec. Cloud shadows require projecting the cloud density field onto the terrain in the terrain shader — a natural follow-up but out of scope here.

6. **Shader stays in Java strings**: Following the existing pattern in `SkyRenderer`, `FogShaderProvider`, and the terrain/ship shaders, the GLSL is embedded as Java string constants rather than external `.glsl` files. This keeps the build simple and matches codebase conventions. If the shader grows too large, it can be extracted to resource files in a future refactor.
