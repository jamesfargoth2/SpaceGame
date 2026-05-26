# Phase 1: Seed Infrastructure + Galaxy Layout — Design Spec

## Overview

Phase 1 of the procedural generation pipeline builds the foundation: a deterministic seed hierarchy and a chunk-streamed galaxy of ~1,000,000 stars arranged in a spiral pattern. The galaxy map is presented as a 2D top-down projection; local space (star systems, flight) remains full 3D.

A new `GalaxyManager` sits above the existing `CoordinateManager`, owning the galaxy-scale data (star catalog, nebulae, regions) while `CoordinateManager` continues to handle float-precision conversion for the active local scene.

### Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Galaxy type | Spiral (default) | Iconic, creates natural gameplay regions (arms vs inter-arm) |
| Star count | ~1,000,000 | Massive feel; requires lazy chunk generation |
| Map projection | 2D top-down | Easier navigation, cleaner UI; local space is 3D |
| Generation model | Chunk-first lazy pipeline | Only way to handle 1M stars without holding all in memory |
| Integration | New GalaxyManager layer | Clean separation from existing CoordinateManager |

### Phase Scope

**In scope:** SeedDeriver, GalaxyConfig (JSON-driven), GalaxyNoise, GalaxyDensityField, GalaxyChunkManager, per-chunk star placement, nebula placement, galaxy region classification, GalaxyManager coordinator, unit tests.

**Out of scope (later phases):** Star system internals (Phase 2), planet terrain/atmosphere/biomes (Phase 3), asteroids/stations (Phase 4), factions/names (Phase 5).

---

## 1. Package Structure

```
core/src/main/java/com/galacticodyssey/galaxy/
    SeedDeriver.java
    RngUtil.java
    GalaxyConfig.java
    GalaxyType.java
    GalaxyNoise.java
    GalaxyDensityField.java
    GalaxyChunkManager.java
    GalaxyChunk.java
    ChunkKey.java
    StarPosition.java
    NebulaPlacer.java
    NebulaRegion.java
    NebulaType.java
    GalaxyRegionClassifier.java
    GalaxyRegion.java
    GalaxyManager.java

core/src/main/resources/data/galaxy/
    galaxy_config.json

core/src/test/java/com/galacticodyssey/galaxy/
    SeedDeriverTest.java
    GalaxyDensityFieldTest.java
    GalaxyChunkManagerTest.java
    GalaxyManagerIntegrationTest.java
```

---

## 2. SeedDeriver — Deterministic Seed Hierarchy

All procedural generation flows from a single galaxy seed. `SeedDeriver` produces child seeds for every layer and object using XOR with domain constants and a splitmix64 finaliser. This guarantees:

- Same seed → identical galaxy across sessions, platforms, and generation order.
- Each object gets its own `new Random(derivedSeed)` — no shared RNG state.
- Chunk generation order doesn't matter — each chunk's seed depends only on its coordinates.

```java
public final class SeedDeriver {
    // Domain constants — one per generation layer, must never change
    private static final long GALAXY_DOMAIN     = 0x9E3779B97F4A7C15L;
    private static final long STAR_DOMAIN       = 0x6C62272E07BB0142L;
    private static final long PLANET_DOMAIN     = 0x517CC1B727220A95L;
    private static final long MOON_DOMAIN       = 0xBF58476D1CE4E5B9L;
    private static final long TERRAIN_DOMAIN    = 0x94D049BB133111EBL;
    private static final long ATMOSPHERE_DOMAIN = 0xC4CEB9FE1A85EC53L;
    private static final long BIOME_DOMAIN      = 0xD2A98B26625EEE7BL;
    private static final long STATION_DOMAIN    = 0x3C79AC492BA7B653L;
    private static final long INTERIOR_DOMAIN   = 0xE7037ED1A0B428DBL;
    private static final long FACTION_DOMAIN    = 0x4F6CDD1CB33DA28DL;
    private static final long NAME_DOMAIN       = 0x8C4F9B29D25B9E63L;
    private static final long NEBULA_DOMAIN     = 0xA2F9836E4E441529L;

    public static long domain(long parentSeed, long domainConstant) {
        return mix(parentSeed ^ domainConstant);
    }

    public static long forId(long domainSeed, long id) {
        return mix(domainSeed ^ id);
    }

    public static long forChunk(long domainSeed, int cx, int cy) {
        long h = domainSeed;
        h ^= ((long) cx) * 0x9E3779B97F4A7C15L;
        h ^= ((long) cy) * 0x6C62272E07BB0142L;
        return mix(h);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
```

**Critical invariants:**
- Domain constants are final and must never be changed after release (breaks all existing saves).
- Every generation call creates `new Random(derivedSeed)`. Never reuse a Random instance across objects.
- Never use `Math.random()`, `MathUtils.random()`, or `System.currentTimeMillis()` in generation code.
- When iterating collections during generation, sort by deterministic ID first (HashMap iteration order is not stable across JVM runs).

### Seed Usage Pattern

```
galaxySeed (user-provided)
  ├── domain(galaxySeed, STAR_DOMAIN) → starDomainSeed
  │     └── forChunk(starDomainSeed, cx, cy) → per-chunk star RNG
  ├── domain(galaxySeed, NEBULA_DOMAIN) → nebulaDomainSeed
  │     └── forId(nebulaDomainSeed, nebulaIndex) → per-nebula RNG
  ├── domain(galaxySeed, FACTION_DOMAIN) → factionDomainSeed  (Phase 5)
  └── ... (one domain per generation layer)
```

---

## 3. GalaxyConfig — Data-Driven Configuration

All galaxy parameters are loaded from JSON at runtime. Never hardcode galaxy content.

```java
public class GalaxyConfig {
    public GalaxyType type;               // SPIRAL (default)
    public int        targetStarCount;    // ~1,000,000
    public float      radiusLY;           // galaxy radius in light-years (e.g. 50,000)
    public int        armCount;           // spiral arms (default: 4)
    public float      armWindingAngle;    // radians total winding (default: 4.0)
    public float      armWidth;           // normalised 0–1 (default: 0.15)
    public float      coreDensityFactor;  // core brightening multiplier (default: 3.0)
    public int        nebulaCount;        // number of nebula regions (default: 200)
    public float      chunkSizeLY;        // light-years per chunk side (default: 100)
    public int        maxLoadedChunks;    // memory cap (default: 512)
}
```

### Default JSON

```json
// core/src/main/resources/data/galaxy/galaxy_config.json
{
    "type": "SPIRAL",
    "targetStarCount": 1000000,
    "radiusLY": 50000.0,
    "armCount": 4,
    "armWindingAngle": 4.0,
    "armWidth": 0.15,
    "coreDensityFactor": 3.0,
    "nebulaCount": 200,
    "chunkSizeLY": 100.0,
    "maxLoadedChunks": 512
}
```

The seed is not in `GalaxyConfig` or the JSON — it's provided separately at world creation by the player or randomly generated. `GalaxyManager` takes the seed as a constructor parameter alongside the config.

### Utility: Seeded Random Helpers

libGDX's `MathUtils.random()` uses a global Random and is not safe for deterministic generation. All procgen code must use explicit `Random` instances. Convenience helper:

```java
public final class RngUtil {
    public static float range(Random rng, float min, float max) {
        return min + rng.nextFloat() * (max - min);
    }

    public static int range(Random rng, int min, int maxExclusive) {
        return min + rng.nextInt(maxExclusive - min);
    }
}
```

Use `RngUtil.range(rng, min, max)` everywhere instead of `MathUtils.random()`.

---

## 4. GalaxyNoise — Noise Foundation

Wraps SimplexNoise with multi-octave fBm and domain warping. Stateless after construction (thread-safe if SimplexNoise is stateless).

```java
public class GalaxyNoise {
    private final SimplexNoise simplex;

    public GalaxyNoise(long seed) {
        this.simplex = new SimplexNoise(seed);
    }

    public float fbm(float x, float y, int octaves, float persistence, float lacunarity) {
        float value = 0f, amplitude = 1f, frequency = 1f, maxValue = 0f;
        for (int i = 0; i < octaves; i++) {
            value    += simplex.noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return value / maxValue; // approximately [-1, 1]
    }

    public float warpedNoise(float x, float y, float warpStrength) {
        float warpX = fbm(x + 1.7f, y + 9.2f, 4, 0.5f, 2.0f);
        float warpY = fbm(x + 8.3f, y + 2.8f, 4, 0.5f, 2.0f);
        return fbm(x + warpStrength * warpX, y + warpStrength * warpY, 6, 0.5f, 2.0f);
    }
}
```

**Note:** The existing `TerrainGenerator` in `data/` has its own Simplex implementation. Phase 1 uses a new `GalaxyNoise` wrapper. In a later cleanup, we may extract a shared `SimplexNoise` utility, but for now each system owns its noise to avoid coupling.

---

## 5. GalaxyDensityField — Star Density Function

A pure function: given normalised galaxy-plane coordinates `(nx, ny)` where `|n| <= 1`, returns a density value in `[0, 1]`. No stored state — can be evaluated anywhere, anytime.

```java
public class GalaxyDensityField {

    public float density(float nx, float ny, GalaxyConfig cfg, GalaxyNoise noise) {
        float r     = (float) Math.sqrt(nx * nx + ny * ny); // 0=centre, 1=rim
        float angle = MathUtils.atan2(ny, nx);
        float d     = spiralDensity(r, angle, cfg);

        // Core bulge: Sersic-like exponential falloff
        float coreBulge = cfg.coreDensityFactor * (float) Math.exp(-r * 4f);
        d = Math.max(0f, d + coreBulge);

        // Large-scale density clumping
        d *= 0.85f + 0.3f * noise.fbm(nx * 3f, ny * 3f, 3, 0.5f, 2f);

        return MathUtils.clamp(d, 0f, 1f);
    }

    private float spiralDensity(float r, float angle, GalaxyConfig cfg) {
        if (r > 1f) return 0f;
        float maxDensity = 0f;
        for (int arm = 0; arm < cfg.armCount; arm++) {
            float armOffset   = arm * (MathUtils.PI2 / cfg.armCount);
            float spiralAngle = cfg.armWindingAngle * (float) Math.log(r + 0.1f) + armOffset;
            float angleDiff   = Math.abs(normaliseAngle(angle - spiralAngle));
            float armDensity  = (float) Math.exp(
                -angleDiff * angleDiff / (2f * cfg.armWidth * cfg.armWidth));
            armDensity *= (1f - r * 0.7f); // density falls off toward rim
            maxDensity  = Math.max(maxDensity, armDensity);
        }
        return maxDensity;
    }

    private float normaliseAngle(float a) {
        while (a >  MathUtils.PI) a -= MathUtils.PI2;
        while (a < -MathUtils.PI) a += MathUtils.PI2;
        return a;
    }
}
```

The logarithmic spiral formula `angle = winding * log(r) + offset` produces arms that wind tighter toward the core — matching real spiral galaxies.

---

## 6. GalaxyChunkManager — Lazy 2D Chunk Grid

The galaxy is divided into a 2D grid of chunks, each `chunkSizeLY × chunkSizeLY` (default 100 LY). Stars are generated per-chunk on demand and discarded when distant.

### ChunkKey

```java
public final class ChunkKey {
    public final int cx, cy;
    // equals() and hashCode() based on cx, cy
}
```

### GalaxyChunk

```java
public class GalaxyChunk {
    public final ChunkKey          key;
    public final Array<StarPosition> stars;
    public final double            centreX, centreY; // galaxy-space LY
    public final float             averageDensity;
}
```

### GalaxyChunkManager

```java
public class GalaxyChunkManager {
    private final Map<ChunkKey, GalaxyChunk> loaded = new LinkedHashMap<>();
    private final GalaxyConfig              config;
    private final GalaxyDensityField        densityField;
    private final long                      starDomainSeed;

    public GalaxyChunk getOrGenerate(int cx, int cy) {
        ChunkKey key = new ChunkKey(cx, cy);
        GalaxyChunk cached = loaded.get(key);
        if (cached != null) return cached;

        GalaxyChunk chunk = generateChunk(cx, cy);
        loaded.put(key, chunk);
        evictIfOverCapacity();
        return chunk;
    }

    private GalaxyChunk generateChunk(int cx, int cy) {
        long chunkSeed  = SeedDeriver.forChunk(starDomainSeed, cx, cy);
        Random rng      = new Random(chunkSeed);
        GalaxyNoise noise = new GalaxyNoise(chunkSeed);

        float chunkWorldX = cx * config.chunkSizeLY;
        float chunkWorldY = cy * config.chunkSizeLY;

        // Estimate star count for this chunk based on average density
        float centreNX = (chunkWorldX + config.chunkSizeLY * 0.5f) / config.radiusLY;
        float centreNY = (chunkWorldY + config.chunkSizeLY * 0.5f) / config.radiusLY;
        float avgDensity = densityField.density(centreNX, centreNY, config, noise);

        float chunkArea    = config.chunkSizeLY * config.chunkSizeLY;
        float galaxyArea   = MathUtils.PI * config.radiusLY * config.radiusLY;
        int expectedStars  = (int)(config.targetStarCount * (chunkArea / galaxyArea) * avgDensity * 4f);
        // The *4 factor compensates for density being < 1 on average; actual count
        // is controlled by rejection sampling below.

        Array<StarPosition> stars = new Array<>();
        int maxAttempts = expectedStars * 5;
        int attempts = 0;

        while (stars.size < expectedStars && attempts < maxAttempts) {
            attempts++;
            float localX = rng.nextFloat() * config.chunkSizeLY;
            float localY = rng.nextFloat() * config.chunkSizeLY;

            float worldX = chunkWorldX + localX;
            float worldY = chunkWorldY + localY;
            float nx     = worldX / config.radiusLY;
            float ny     = worldY / config.radiusLY;

            if (nx * nx + ny * ny > 1f) continue; // outside galaxy disk

            float d = densityField.density(nx, ny, config, noise);
            if (rng.nextFloat() > d) continue; // rejection sample

            // Z-height: thin disk, thicker toward core
            float r      = (float) Math.sqrt(nx * nx + ny * ny);
            float zScale = 0.02f + 0.01f * (1f - r);
            float z      = (float)(rng.nextGaussian() * zScale) * config.radiusLY;

            StarPosition star = new StarPosition();
            star.uniqueId     = SeedDeriver.forId(chunkSeed, stars.size);
            star.x            = worldX;
            star.y            = worldY;
            star.z            = z;
            star.localDensity = d;
            stars.add(star);
        }

        return new GalaxyChunk(new ChunkKey(cx, cy), stars,
            chunkWorldX + config.chunkSizeLY * 0.5,
            chunkWorldY + config.chunkSizeLY * 0.5,
            avgDensity);
    }

    public void loadChunksAround(double viewCentreX, double viewCentreY, float radiusLY) {
        int minCX = (int) Math.floor((viewCentreX - radiusLY) / config.chunkSizeLY);
        int maxCX = (int) Math.ceil((viewCentreX + radiusLY) / config.chunkSizeLY);
        int minCY = (int) Math.floor((viewCentreY - radiusLY) / config.chunkSizeLY);
        int maxCY = (int) Math.ceil((viewCentreY + radiusLY) / config.chunkSizeLY);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cy = minCY; cy <= maxCY; cy++) {
                getOrGenerate(cx, cy);
            }
        }
    }

    public void unloadDistantChunks(double viewCentreX, double viewCentreY, float unloadRadiusLY) {
        loaded.entrySet().removeIf(e -> {
            GalaxyChunk c = e.getValue();
            double dx = c.centreX - viewCentreX;
            double dy = c.centreY - viewCentreY;
            return Math.sqrt(dx * dx + dy * dy) > unloadRadiusLY;
        });
    }

    private void evictIfOverCapacity() {
        while (loaded.size() > config.maxLoadedChunks) {
            Iterator<ChunkKey> it = loaded.keySet().iterator();
            if (it.hasNext()) { it.next(); it.remove(); } // LRU via LinkedHashMap
        }
    }
}
```

### Memory Budget

At 100 LY chunks with ~1M stars across a 50,000 LY radius galaxy:
- Galaxy disk area ≈ π × 50,000² ≈ 7.85 × 10⁹ LY²
- Chunk area = 10,000 LY²
- Total chunks ≈ 785,000 (but most are partially/fully outside the disk)
- Stars per chunk varies widely: 0 (void/rim) to 50–200 (dense core/arm)
- Galaxy-wide average ≈ 1.3 stars/chunk, but loaded chunks are biased toward populated areas
- Worst case (viewing core): 512 chunks × ~100 stars × ~40 bytes per StarPosition ≈ 2 MB
- Typical case (viewing arm): 512 chunks × ~10 stars × ~40 bytes ≈ 200 KB
- Well within budget even at worst case.

---

## 7. StarPosition — Per-Star Data

```java
public class StarPosition {
    public long   uniqueId;       // deterministic, derived from chunk seed + index
    public double x, y, z;        // galaxy-space coordinates in light-years (64-bit)
    public float  localDensity;   // density at this position (for region tagging)
}
```

`StarPosition` is intentionally lightweight — it's just a location in the galaxy catalog. The full star data (spectral class, luminosity, planets) is generated lazily in Phase 2 when the player enters that star's system.

The `uniqueId` is deterministic: `SeedDeriver.forId(chunkSeed, indexWithinChunk)`. This means the same star always gets the same ID regardless of which chunks were loaded first.

---

## 8. NebulaPlacer — Nebula Seeding

Nebulae are anchored to high-density star regions. They're generated once at galaxy creation (not per-chunk) since there are only ~200.

```java
public class NebulaPlacer {

    public Array<NebulaRegion> place(GalaxyConfig cfg, long galaxySeed) {
        long nebulaSeed = SeedDeriver.domain(galaxySeed, NEBULA_DOMAIN);
        Random rng      = new Random(nebulaSeed);
        GalaxyNoise noise = new GalaxyNoise(nebulaSeed);
        GalaxyDensityField density = new GalaxyDensityField();

        Array<NebulaRegion> nebulae = new Array<>();

        for (int i = 0; i < cfg.nebulaCount; i++) {
            // Find a high-density point via rejection sampling
            float nx, ny;
            int attempts = 0;
            do {
                nx = rng.nextFloat() * 2f - 1f;
                ny = rng.nextFloat() * 2f - 1f;
                attempts++;
            } while (density.density(nx, ny, cfg, noise) < 0.4f && attempts < 100);

            NebulaRegion n = new NebulaRegion();
            n.centreX      = nx * cfg.radiusLY;
            n.centreY      = ny * cfg.radiusLY;
            n.radiusLY     = cfg.radiusLY * RngUtil.range(rng, 0.02f, 0.08f);
            n.type         = NebulaType.values()[rng.nextInt(NebulaType.values().length)];
            n.colour       = nebulaColour(n.type, rng);
            nebulae.add(n);
        }
        return nebulae;
    }

    private Color nebulaColour(NebulaType type, Random rng) {
        switch (type) {
            case EMISSION:   return new Color(1f, 0.2f + rng.nextFloat() * 0.3f, 0.1f, 0.6f);
            case REFLECTION: return new Color(0.2f, 0.4f, 1f, 0.5f);
            case DARK:       return new Color(0.05f, 0.05f, 0.05f, 0.8f);
            case PLANETARY:  return new Color(0.3f, 1f, 0.5f, 0.4f);
            default:         return new Color(0.8f, 0.8f, 0.8f, 0.3f);
        }
    }
}
```

### NebulaType

```java
public enum NebulaType {
    EMISSION,    // red/pink — hydrogen gas ionised by nearby hot stars
    REFLECTION,  // blue — scattering starlight
    DARK,        // opaque — blocks background stars
    PLANETARY    // green/teal — ejected shell from dying star
}
```

### NebulaRegion

```java
public class NebulaRegion {
    public double     centreX, centreY;  // galaxy-space LY
    public float      radiusLY;
    public NebulaType type;
    public Color      colour;            // RGBA for map rendering
}
```

Nebulae affect gameplay in later phases: sensor disruption in DARK nebulae, rare gas harvesting in EMISSION nebulae, hidden pirate bases (as noted in DESIGN.md section 4.13).

---

## 9. GalaxyRegionClassifier — Core/Rim/Void Tagging

Classifies any galaxy-space position into a region for gameplay purposes (e.g., core has rarer resources, voids have fewer patrols).

```java
public class GalaxyRegionClassifier {

    public GalaxyRegion classify(double x, double y, GalaxyConfig cfg) {
        float nx = (float)(x / cfg.radiusLY);
        float ny = (float)(y / cfg.radiusLY);
        float r  = (float) Math.sqrt(nx * nx + ny * ny);

        if (r > 1.0f) return GalaxyRegion.VOID;
        if (r < 0.1f) return GalaxyRegion.CORE;
        if (r < 0.4f) return GalaxyRegion.INNER_RIM;
        return GalaxyRegion.OUTER_RIM;
    }
}
```

```java
public enum GalaxyRegion {
    CORE,       // dense, dangerous, rare resources, ancient civilisations
    INNER_RIM,  // well-patrolled, established factions, trade hubs
    OUTER_RIM,  // frontier, pirates, independent settlements
    VOID        // empty space between arms or beyond galaxy edge
}
```

Region thresholds (0.1, 0.4) are initial values that can be tuned. The classifier is used by Phase 5 (faction territory) and gameplay systems (encounter difficulty scaling, resource rarity).

---

## 10. GalaxyManager — Top-Level Coordinator

Owns all galaxy-scale state and provides the public API that other systems use.

```java
public class GalaxyManager implements Disposable {
    private final GalaxyConfig            config;
    private final long                    galaxySeed;
    private final GalaxyChunkManager      chunkManager;
    private final NebulaPlacer            nebulaPlacer;
    private final GalaxyRegionClassifier  regionClassifier;
    private final Array<NebulaRegion>     nebulae;

    public GalaxyManager(long galaxySeed, GalaxyConfig config) {
        this.galaxySeed       = galaxySeed;
        this.config           = config;
        long starDomain       = SeedDeriver.domain(galaxySeed, STAR_DOMAIN);
        this.chunkManager     = new GalaxyChunkManager(config, starDomain);
        this.nebulaPlacer     = new NebulaPlacer();
        this.regionClassifier = new GalaxyRegionClassifier();
        this.nebulae          = nebulaPlacer.place(config, galaxySeed);
    }

    /** Load/unload chunks around the player's galaxy-map view. */
    public void updateView(double viewCentreX, double viewCentreY, float viewRadiusLY) {
        chunkManager.loadChunksAround(viewCentreX, viewCentreY, viewRadiusLY);
        chunkManager.unloadDistantChunks(viewCentreX, viewCentreY, viewRadiusLY * 2f);
    }

    /** Get all currently loaded stars (for rendering the galaxy map). */
    public Iterable<StarPosition> getLoadedStars() {
        // Flatten all loaded chunks into a single iterable
    }

    /** Find the nearest star to a galaxy-space position. Searches loaded chunks + neighbours. */
    public StarPosition findNearestStar(double x, double y) {
        // Check chunk at (x,y) plus 8 adjacent chunks
    }

    /** Get the galaxy region at a position. */
    public GalaxyRegion getRegion(double x, double y) {
        return regionClassifier.classify(x, y, config);
    }

    /** Get all nebulae (small fixed list, always in memory). */
    public Array<NebulaRegion> getNebulae() {
        return nebulae;
    }

    /** Get the galaxy seed (for save/load — only the seed needs to be persisted). */
    public long getGalaxySeed() {
        return galaxySeed;
    }

    public GalaxyConfig getConfig() {
        return config;
    }

    @Override
    public void dispose() {
        // No GL resources to dispose; chunk data is pure Java objects
    }
}
```

### Integration with Existing Systems

- **CoordinateManager:** `GalaxyManager` provides galaxy-space (double) coordinates. When the player enters a star system, `CoordinateManager` converts the star's galaxy-space position into the local floating-origin reference frame.
- **GameWorld:** `GalaxyManager` is created during world initialisation and registered as a system-level service. It does NOT participate in the Ashley ECS tick — it's queried on demand.
- **EventBus:** `GalaxyManager` publishes `ChunkLoadedEvent` and `ChunkUnloadedEvent` so the galaxy map renderer can update.
- **Save/Load:** Only `galaxySeed` and player modifications are persisted. The entire galaxy is regenerated from seed on load. See seed-reproducibility rules in Section 2.

---

## 11. Save/Load Contract

Generated galaxy data is never serialised. Only persist:

1. `galaxySeed` (the root seed)
2. `GalaxyConfig` (in case the player modified defaults)
3. Player-discovered star IDs (so names generated in Phase 5 stay consistent)
4. Player modifications (mined-out asteroids, destroyed stations, etc. — later phases)

On load: recreate `GalaxyManager(savedSeed, savedConfig)` → galaxy is identical.

---

## 12. Testing Strategy

### Unit Tests

**SeedDeriverTest:**
- Same inputs → same output (determinism)
- Different chunk coordinates → different seeds (distribution)
- Different domains → different seeds (no collisions)
- Bit distribution: verify mix() produces well-distributed bits (chi-squared on lower 16 bits)

**GalaxyDensityFieldTest:**
- Core density > rim density
- Density at galaxy centre > 0.5
- Density outside disk radius = 0
- Density along spiral arms > density between arms (sample specific points)
- Density is deterministic (same config + noise → same values)

**GalaxyChunkManagerTest:**
- Same chunk coordinates → same stars (reproducibility)
- Different chunk coordinates → different stars
- Stars are within chunk bounds
- Star z-values follow Gaussian distribution (thicker at core)
- Chunk load/unload lifecycle: load, verify present, unload, verify absent
- LRU eviction: load maxChunks+1 → first loaded chunk evicted
- Chunks outside galaxy disk have 0 stars

### Integration Test

**GalaxyManagerIntegrationTest:**
- Create GalaxyManager with a fixed seed
- Load chunks around (0,0), verify stars exist in core
- Load chunks at rim, verify fewer stars
- Verify nebulae are in high-density regions
- Verify region classification (core/inner/outer/void)
- Destroy and recreate with same seed → identical star positions

---

## 13. Performance Considerations

| Concern | Mitigation |
|---------|------------|
| Chunk generation latency | Generation is ~1ms per chunk (noise sampling + rejection). Acceptable for synchronous load on main thread. If profiling shows issues, move to background thread with `Future<GalaxyChunk>`. |
| Memory (loaded chunks) | 512 chunk cap × ~10 stars × 64 bytes = ~320 KB. Negligible. |
| Galaxy map rendering (many stars) | Loaded stars are rendered as point sprites. At 512 chunks × 10 avg = ~5,000 points — trivial for GPU. Zoomed-in views load more chunks with fewer stars each. |
| Cross-chunk nearest-star queries | Check the target chunk + 8 adjacent chunks. Max 9 chunks queried = ~90 stars to search. Fast enough for O(n) linear scan. |
| SimplexNoise thread safety | `GalaxyNoise` creates its own `SimplexNoise` per instance. If chunk generation moves to background threads, each thread gets its own `GalaxyNoise` from its chunk seed. No shared mutable state. |

---

## 14. Future Phase Hooks

Phase 1 establishes these extension points for later phases:

| Hook | Used By |
|------|---------|
| `StarPosition.uniqueId` | Phase 2: derive star system seed from `SeedDeriver.forId(STAR_DOMAIN, uniqueId)` |
| `StarPosition.localDensity` | Phase 5: faction territory assignment weights |
| `GalaxyRegion` | Phase 5: region affects faction behaviour, resource rarity |
| `NebulaRegion` | Phase 4: nebulae contain harvestable gas, affect sensors |
| `SeedDeriver` domains | All phases: PLANET_DOMAIN, TERRAIN_DOMAIN, etc. already defined |
| `GalaxyManager.getGalaxySeed()` | Save system: only value needed to recreate galaxy |
