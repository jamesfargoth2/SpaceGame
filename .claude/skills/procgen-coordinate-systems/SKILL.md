---
name: procgen-coordinate-systems
description: >
  Enforces the four-layer floating-origin coordinate system of Galactic Odyssey:
  GALAXY_SPACE (double, light-years), SYSTEM_SPACE (double, AU), PLANET_SPACE
  (double, km), and LOCAL_SCENE (float, metres). Defines the coordinate
  transform contracts between layers, float32 precision limits and when they
  are violated, how the player-centred floating origin is maintained, how
  chunks stream in and out at each scale, and how procgen seeds are derived
  from multi-layer coordinates. Use this skill BEFORE writing any code that
  stores, converts, or passes coordinates between systems, samples positions at
  galaxy or system scale, places objects on a planet surface, transitions the
  player between coordinate layers, or calls any procgen function that needs a
  position. Also use when debugging position jitter, objects snapping to wrong
  locations, or NaN/Inf coordinate errors. CLAUDE.md rule 1 ("Floating origin
  is non-negotiable") is implemented through this skill.
---

# Coordinate System Architecture

## The Four Layers

```
GALAXY_SPACE   double[3]  light-years  origin = galaxy centre
      │
      ▼  sector lookup, then system spawn
SYSTEM_SPACE   double[3]  AU           origin = parent star
      │
      ▼  planet lookup, then surface descent
PLANET_SPACE   double[3]  km           origin = planet centre
      │
      ▼  floating-origin camera clamp
LOCAL_SCENE    float[3]   metres       origin = player position (moves with player)
```

**The player is always at (0,0,0) in LOCAL_SCENE.**
The universe moves around the player. This is not optional — see CLAUDE.md rule 1.

---

## Precision Analysis

```
Coordinate       Typical max value    double precision at max    Result
─────────────────────────────────────────────────────────────────────────
GALAXY_SPACE     25,000 LY           25000 × 2⁻⁵²  = 5.5e-12 LY    0.05 m  ✓
SYSTEM_SPACE       100 AU            100 × 2⁻⁵²    = 2.2e-14 AU     3.3 mm  ✓
PLANET_SPACE   100,000 km            1e5 × 2⁻⁵²   = 2.2e-11 km     22 nm   ✓
LOCAL_SCENE      5,000 m             5000 × 2⁻²³   = 0.0006 m       0.6 mm  ✓ (barely)

float at 10,000 m:   10000 × 2⁻²³ = 0.0012 m = 1.2 mm   ⚠ marginal
float at 100,000 m:  1e5 × 2⁻²³   = 0.012 m  = 1.2 cm   ✗ visible jitter
float at 1,000,000 m: 1e6 × 2⁻²³  = 0.12 m              ✗ catastrophic
```

**Enforce the float limit:** Never allow LOCAL_SCENE coordinates to drift beyond ±5,000 m from origin. Reset the origin (shift the universe) before that boundary is reached.

---

## Coordinate Type Declarations

```java
// Always annotate coordinates with their space using these types.
// Never store a "position" as a raw float[] or Vector3 without knowing which layer it lives in.

/** Galaxy-space position in light-years. Never cast to float without converting to LOCAL. */
public record GalaxyCoordsLY(double x, double y, double z) {
    public static final GalaxyCoordsLY ORIGIN = new GalaxyCoordsLY(0, 0, 0);
}

/** System-space position in AU, relative to the parent star. */
public record SystemCoordsAU(double x, double y, double z) {}

/** Planet-space position in km, relative to planet centre. */
public record PlanetCoordsKM(double x, double y, double z) {}

/** Local-scene position in metres, relative to current player position. Always float. */
public record LocalCoordsM(float x, float y, float z) {
    public Vector3 toVector3() { return new Vector3(x, y, z); }
}
```

---

## Unit Conversion Constants

```java
public final class CoordConvert {

    // ── BETWEEN LAYERS ─────────────────────────────────────────────────────
    public static final double LY_TO_AU  = 63_241.0;
    public static final double AU_TO_LY  = 1.0 / 63_241.0;
    public static final double AU_TO_KM  = 1.496e8;
    public static final double KM_TO_AU  = 1.0 / 1.496e8;
    public static final double KM_TO_M   = 1_000.0;
    public static final double M_TO_KM   = 0.001;

    // ── GALAXY ↔ SYSTEM ────────────────────────────────────────────────────
    public static GalaxyCoordsLY systemToGalaxy(SystemCoordsAU sysPos,
                                                  GalaxyCoordsLY starPos) {
        return new GalaxyCoordsLY(
            starPos.x() + sysPos.x() * AU_TO_LY,
            starPos.y() + sysPos.y() * AU_TO_LY,
            starPos.z() + sysPos.z() * AU_TO_LY
        );
    }

    public static SystemCoordsAU galaxyToSystem(GalaxyCoordsLY worldPos,
                                                  GalaxyCoordsLY starPos) {
        return new SystemCoordsAU(
            (worldPos.x() - starPos.x()) * LY_TO_AU,
            (worldPos.y() - starPos.y()) * LY_TO_AU,
            (worldPos.z() - starPos.z()) * LY_TO_AU
        );
    }

    // ── SYSTEM ↔ PLANET ────────────────────────────────────────────────────
    /** Convert a system-space position to planet-centred km. */
    public static PlanetCoordsKM systemToPlanet(SystemCoordsAU sysPos,
                                                  SystemCoordsAU planetPos) {
        return new PlanetCoordsKM(
            (sysPos.x() - planetPos.x()) * AU_TO_KM,
            (sysPos.y() - planetPos.y()) * AU_TO_KM,
            (sysPos.z() - planetPos.z()) * AU_TO_KM
        );
    }

    public static SystemCoordsAU planetToSystem(PlanetCoordsKM planetPos,
                                                  SystemCoordsAU planetOrigin) {
        return new SystemCoordsAU(
            planetOrigin.x() + planetPos.x() * KM_TO_AU,
            planetOrigin.y() + planetPos.y() * KM_TO_AU,
            planetOrigin.z() + planetPos.z() * KM_TO_AU
        );
    }

    // ── PLANET ↔ LOCAL ─────────────────────────────────────────────────────
    /**
     * Convert a planet-space position to LOCAL_SCENE float, given the player's
     * current planet-space position. This is the critical floating-origin step.
     * MUST be called with player's EXACT double-precision planet position.
     */
    public static LocalCoordsM planetToLocal(PlanetCoordsKM worldPos,
                                              PlanetCoordsKM playerPos) {
        // Subtract in double BEFORE casting to float — never subtract floats
        double dx = (worldPos.x() - playerPos.x()) * KM_TO_M;
        double dy = (worldPos.y() - playerPos.y()) * KM_TO_M;
        double dz = (worldPos.z() - playerPos.z()) * KM_TO_M;
        return new LocalCoordsM((float) dx, (float) dy, (float) dz);
    }

    public static PlanetCoordsKM localToPlanet(LocalCoordsM localPos,
                                                PlanetCoordsKM playerPos) {
        return new PlanetCoordsKM(
            playerPos.x() + localPos.x() * M_TO_KM,
            playerPos.y() + localPos.y() * M_TO_KM,
            playerPos.z() + localPos.z() * M_TO_KM
        );
    }
}
```

---

## The Floating-Origin Manager

```java
/**
 * Manages the player's authoritative position across coordinate layers
 * and triggers the origin-reset when LOCAL_SCENE drift exceeds the limit.
 *
 * Only this class may update playerPlanetPos and playerSystemPos.
 * All other systems read the LOCAL offset from CoordConvert.
 */
public class FloatingOriginManager {

    private static final float RESET_THRESHOLD_M = 4_000f; // reset before hitting 5 km

    // Player's authoritative position in the active layer (double precision)
    private PlanetCoordsKM  playerPlanetPos;
    private SystemCoordsAU  playerSystemPos;
    private GalaxyCoordsLY  playerGalaxyPos;
    private CoordLayer       activeLayer;

    /**
     * Call every frame. Accumulate the player's LOCAL_SCENE movement
     * into the double-precision layer position, then reset LOCAL to (0,0,0).
     */
    public void update(float localDeltaX, float localDeltaY, float localDeltaZ) {
        double dm = CoordConvert.M_TO_KM;
        switch (activeLayer) {
            case PLANET -> playerPlanetPos = new PlanetCoordsKM(
                playerPlanetPos.x() + localDeltaX * dm,
                playerPlanetPos.y() + localDeltaY * dm,
                playerPlanetPos.z() + localDeltaZ * dm
            );
            case SYSTEM -> playerSystemPos = new SystemCoordsAU(
                playerSystemPos.x() + localDeltaX * dm * CoordConvert.KM_TO_AU,
                playerSystemPos.y() + localDeltaY * dm * CoordConvert.KM_TO_AU,
                playerSystemPos.z() + localDeltaZ * dm * CoordConvert.KM_TO_AU
            );
        }
    }

    /**
     * Call after update(). If the accumulated LOCAL drift (non-reset player
     * motion) would exceed RESET_THRESHOLD, broadcast a scene-wide reset event.
     * All renderable entities must re-derive their LOCAL position from the
     * event's new reference position.
     */
    public void checkAndReset(float accumulatedDrift) {
        if (Math.abs(accumulatedDrift) > RESET_THRESHOLD_M) {
            // Broadcast to all systems: re-transform world from double coords
            EventBus.post(new FloatingOriginResetEvent(playerPlanetPos));
        }
    }

    /** Every system that places objects in LOCAL_SCENE must listen for this. */
    public record FloatingOriginResetEvent(PlanetCoordsKM newPlayerPos) {}
}
```

---

## Procgen Seed Derivation from Coordinates

```java
/**
 * All procgen seeds must be derived deterministically from coordinates.
 * The same coordinate always produces the same seed, which produces the same content.
 * Never use System.currentTimeMillis() or Math.random() as a seed source.
 */
public class CoordSeedDeriver {

    /**
     * Derive a 64-bit seed for a galaxy chunk from its chunk indices.
     * Chunk index = floor(coord_LY / CHUNK_SIZE_LY).
     */
    public static long chunkSeed(long masterSeed, long chunkX, long chunkY, long chunkZ) {
        // Use Wang hash to avoid correlations between adjacent chunks
        long h = masterSeed;
        h = wangHash(h ^ chunkX);
        h = wangHash(h ^ chunkY);
        h = wangHash(h ^ chunkZ);
        return h;
    }

    /**
     * Derive a star seed from its galaxy-space chunk + local index within chunk.
     */
    public static long starSeed(long chunkSeed, int starIndexInChunk) {
        return wangHash(chunkSeed ^ starIndexInChunk);
    }

    /**
     * Derive a planet seed from its parent star seed + orbital slot index.
     */
    public static long planetSeed(long starSeed, int orbitalSlot) {
        return wangHash(starSeed ^ (long) orbitalSlot * 0x9E3779B97F4A7C15L);
    }

    /**
     * Derive a surface chunk seed for terrain/vegetation generation.
     * Uses planet seed + cubemap face + face-local chunk XY.
     */
    public static long surfaceChunkSeed(long planetSeed, int face, int faceX, int faceY) {
        long h = wangHash(planetSeed ^ face);
        h = wangHash(h ^ faceX);
        h = wangHash(h ^ faceY);
        return h;
    }

    private static long wangHash(long n) {
        n = (n ^ 61L) ^ (n >>> 16);
        n = n + (n << 3);
        n = n ^ (n >>> 4);
        n = n * 0x27D4EB2F165667C5L;
        n = n ^ (n >>> 15);
        return n;
    }
}
```

---

## Spherical ↔ Cartesian on a Planet

```java
/**
 * Planet surface positions must be converted to/from spherical coordinates
 * for terrain sampling. All geodetic coordinates use PLANET_SPACE km.
 */
public class PlanetarySurfaceCoords {

    /**
     * Convert geodetic (lat, lon, altitude) to planet-centred Cartesian km.
     * @param latDeg    latitude in degrees (-90 south, +90 north)
     * @param lonDeg    longitude in degrees (-180 to +180)
     * @param altKm     altitude above mean radius in km
     * @param radiusKm  planet mean radius in km
     */
    public static PlanetCoordsKM toCartesian(float latDeg, float lonDeg,
                                              float altKm, float radiusKm) {
        double latRad = Math.toRadians(latDeg);
        double lonRad = Math.toRadians(lonDeg);
        double r = radiusKm + altKm;
        return new PlanetCoordsKM(
            r * Math.cos(latRad) * Math.cos(lonRad),
            r * Math.sin(latRad),
            r * Math.cos(latRad) * Math.sin(lonRad)
        );
    }

    /**
     * Convert planet-centred Cartesian km to geodetic (lat, lon, alt).
     */
    public static float[] toGeodetic(PlanetCoordsKM pos, float radiusKm) {
        double r   = Math.sqrt(pos.x()*pos.x() + pos.y()*pos.y() + pos.z()*pos.z());
        float lat  = (float) Math.toDegrees(Math.asin(pos.y() / r));
        float lon  = (float) Math.toDegrees(Math.atan2(pos.z(), pos.x()));
        float alt  = (float)(r - radiusKm);
        return new float[]{ lat, lon, alt };
    }

    /**
     * "Up" vector in LOCAL_SCENE: always points away from planet centre.
     * Every LOCAL_SCENE position must know its "up" to orient objects correctly.
     */
    public static Vector3 surfaceUpLocal(PlanetCoordsKM playerPos) {
        // Player's position relative to planet centre, normalised = "up" direction
        double r = Math.sqrt(playerPos.x()*playerPos.x()
                           + playerPos.y()*playerPos.y()
                           + playerPos.z()*playerPos.z());
        return new Vector3(
            (float)(playerPos.x() / r),
            (float)(playerPos.y() / r),
            (float)(playerPos.z() / r)
        );
    }
}
```

---

## Layer Transition Rules

```java
/**
 * When the player crosses a layer boundary, the active coordinate layer changes.
 * The transition is always smooth — no teleports, no seams.
 */
public class LayerTransitionRules {

    // ── SYSTEM → PLANET ────────────────────────────────────────────────────
    // Enter PLANET layer when altitude above surface < DESCENT_TRIGGER_KM
    public static final float DESCENT_TRIGGER_KM     = 2_000f;  // 2,000 km (2× Moon distance to surface roughly)
    // Exit PLANET layer (re-enter SYSTEM) when altitude > EXIT_ALTITUDE_KM
    public static final float EXIT_ALTITUDE_KM       = 3_000f;  // hysteresis band above entry

    // ── GALAXY → SYSTEM ────────────────────────────────────────────────────
    // Enter SYSTEM layer when within SYSTEM_ENTRY_AU of the star
    public static final float SYSTEM_ENTRY_AU        = 0.5f;    // 0.5 AU from star (inside Mercury's orbit)
    // Exit SYSTEM layer when beyond SYSTEM_EXIT_AU
    public static final float SYSTEM_EXIT_AU         = 110f;    // just beyond system max radius

    /**
     * During a SYSTEM → PLANET descent, gravity and atmospheric physics
     * transition. The physics skill reads altitude from PlanetCoordsKM
     * to determine drag, not from LOCAL_SCENE (which would lose precision
     * at the 5,000 m LOCAL limit at orbital altitude).
     *
     * Rule: atmospheric physics always use PLANET_SPACE double altitude.
     *       Only rendering and collision use LOCAL_SCENE float positions.
     */
}
```

---

## Orbital Position at Time

```java
/**
 * Planets orbit their star in SYSTEM_SPACE (AU).
 * Orbital state is computed deterministically from a reference time.
 * Never store planet positions directly — always compute from time.
 */
public class OrbitalPositionComputer {

    /**
     * Compute planet's SYSTEM_SPACE position at a given game time.
     * @param semiMajorAU  orbital semi-major axis in AU
     * @param eccentricity orbital eccentricity (0 = circle)
     * @param periodDays   orbital period in Earth days
     * @param phaseRad     epoch phase at time=0 (from planet seed)
     * @param timeDays     current game time in Earth days
     */
    public static SystemCoordsAU position(float semiMajorAU, float eccentricity,
                                           float periodDays, float phaseRad,
                                           double timeDays) {
        // Mean anomaly: M = 2π × (t / T) + phase
        double M = MathUtils.PI2 * (timeDays / periodDays) + phaseRad;
        // Solve Kepler's equation for eccentric anomaly E via Newton iteration
        double E = solveKepler(M, eccentricity);
        // True anomaly ν from E
        double sinE = Math.sin(E);
        double cosE = Math.cos(E);
        double nu   = Math.atan2(Math.sqrt(1 - eccentricity * eccentricity) * sinE,
                                  cosE - eccentricity);
        // Radius in AU
        double r    = semiMajorAU * (1 - eccentricity * cosE);
        // Position in orbital plane (XZ, Y=0 for equatorial orbits)
        return new SystemCoordsAU(r * Math.cos(nu), 0, r * Math.sin(nu));
    }

    private static double solveKepler(double M, double e) {
        double E = M;
        for (int i = 0; i < 8; i++) {
            E = E - (E - e * Math.sin(E) - M) / (1 - e * Math.cos(E));
        }
        return E;
    }
}
```

---

## Chunk Streaming Architecture

```java
/**
 * The game streams content at each scale layer.
 * Chunk keys are always integer indices derived from double-precision coords.
 */
public class ChunkKeySystem {

    // ── GALAXY LAYER (100 LY chunks) ───────────────────────────────────────
    public static long[] galaxyChunkKey(GalaxyCoordsLY pos) {
        return new long[]{
            (long) Math.floor(pos.x() / 100.0),
            (long) Math.floor(pos.y() / 100.0),
            (long) Math.floor(pos.z() / 100.0),
        };
    }

    // ── PLANET SURFACE (terrain tiles: 10 km per tile at LOD0) ────────────
    // The cubemap face divides the sphere into 6 faces × N² tiles
    // At LOD0: tile covers 10 km × 10 km of surface area
    // Tile count per face = circumference / (4 × tile_km) = 2πR / (4 × 10)
    public static int surfaceTilesPerFaceAxis(float radiusKm) {
        float circumferenceKm = 2f * MathUtils.PI * radiusKm;
        return Math.max(4, (int) Math.ceil(circumferenceKm / 40f));
    }
    // Earth-like (6371 km): 2π×6371 / 40 ≈ 1000 tiles per axis per face
    // Dwarf (1000 km): 2π×1000 / 40 ≈ 157 tiles per axis
    // Moonlet (300 km): 47 tiles per axis (very small; reasonable)

    // ── LOAD RADIUS (how many chunks to keep resident) ────────────────────
    public static final int GALAXY_LOAD_RADIUS_CHUNKS  = 3;   // ±300 LY around player
    public static final int SURFACE_LOAD_RADIUS_TILES  = 5;   // ±5 tiles (±50 km)
}
```

---

## Rules That Must Never Be Broken

```java
// Enforced by code review; violation causes position jitter or overflow:

// 1. SUBTRACT IN DOUBLE, CAST AFTER
//    ✓  float local = (float)(doubleA - doubleB);
//    ✗  float local = (float)doubleA - (float)doubleB;   // catastrophic cancellation

// 2. NEVER STORE WORLD POSITION AS FLOAT
//    ✓  PlanetCoordsKM position;
//    ✗  Vector3 position;   // for anything not in LOCAL_SCENE

// 3. PLAYER IS ALWAYS AT LOCAL (0,0,0)
//    ✓  playerLocalPos == Vector3.Zero  (enforced by FloatingOriginManager)
//    ✗  moving the player's local position and not resetting

// 4. PROCGEN SEEDS FROM COORDINATES, NOT TIME
//    ✓  long seed = CoordSeedDeriver.planetSeed(starSeed, slotIndex);
//    ✗  long seed = System.nanoTime();

// 5. ORBITAL POSITIONS COMPUTED FROM TIME, NOT STORED
//    ✓  pos = OrbitalPositionComputer.position(..., currentTimeDays);
//    ✗  planet.position += planet.velocity * dt;  // drift over long sessions

// 6. GRAVITY USES PLANET_SPACE ALTITUDE, NOT LOCAL
//    ✓  float altKm = (float)(planetCoordsKM.length() - radiusKm);
//    ✗  float altKm = playerLocalPos.y / 1000f;  // wrong at orbital altitude
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| `Vector3 pos = worldPos.toFloat3()` without subtracting player | Always subtract player's double position before casting: `CoordConvert.planetToLocal(worldPos, playerPos)` |
| Storing a planet's orbit position and updating it each frame | Compute from Kepler's equation and `currentTimeDays`; drift is guaranteed otherwise |
| Using `Random(System.currentTimeMillis())` in procgen | Always use `CoordSeedDeriver.*` methods; same coord must produce same content |
| Planet surface tile size independent of radius | Tile count scales with radius: `surfaceTilesPerFaceAxis(radiusKm)` |
| Float subtraction of two large world positions | Subtract first in double, then cast: `(float)(a_d - b_d)`, never `(float)a - (float)b` |
| "Up" direction hardcoded to Vector3.Y | On a spherical planet, "up" is the surface normal at the player's position, not Y |
| Atmospheric physics using LOCAL altitude | Use `PlanetCoordsKM.length() - radiusKm` for altitude; LOCAL altitude is unreliable at orbital height |
| Chunk key from float position | Chunk key must be derived from double coords; float → int conversion at large distances is wrong |
