---
name: procgen-vegetation
description: >
  Enforces procedural vegetation generation for planet surfaces in a libGDX 3D
  space game, covering flora archetype classification (trees, shrubs, grasses,
  ferns, mosses, aquatic plants, alien structures), parameterised branching tree
  growth using iterative space colonization, biome-correlated density fields,
  altitude and slope exclusion, ground cover vs canopy layer separation, vertex-
  color-only rendering (no UV maps; stride-11), LOD strategy (full geometry at
  close range, cross-billboard clusters at mid-range, density-texture impostors
  at far range), wind animation parameter seeding, alien biome variant flora
  (crystal formations, fungal towers, bioluminescent fronds), and vegetation
  placement integration with the planetary terrain and biome systems for a
  libGDX 3D space game. Use this skill whenever writing or modifying: surface
  vegetation spawning, flora mesh generation, tree branching algorithms, ground
  cover density, grass/shrub billboard systems, alien plant geometry, plant
  colour derivation from planet palette, vegetation LOD switching, wind shader
  parameters, or any code that places or renders plant-like objects on a planet
  surface. Read procgen-planet-biomes for biome classification inputs and
  procgen-planet-terrain for slope/altitude sampling.
---

# Procedural Vegetation Generation

## Flora Archetypes

```java
public enum FloraArchetype {
    // ── TREES ──────────────────────────────────────────────────────────────
    BROADLEAF_TREE,     // temperate/tropical; wide rounded canopy
    CONIFER_TREE,       // cold climates; conical silhouette, layered branches
    PALM_TREE,          // warm coastal; tall single trunk, crown of fronds
    DEAD_TREE,          // any biome; bare branches, no leaf geometry
    GIANT_TREE,         // rainforest emergent; > 30 m trunk, buttressed base
    MANGROVE_TREE,      // coastal; prop root tangle, canopy over water
    ALIEN_CANOPY,       // non-Earthlike tree; unusual branching topology
    CRYSTAL_TREE,       // alien crystal biome; faceted mineral branches
    FUNGAL_TOWER,       // alien fungal biome; capped pillar, spore pores
    BIOLUM_TREE,        // glowing alien; emissive fronds, dark substrate

    // ── SHRUBS & BUSHES ────────────────────────────────────────────────────
    ROUND_SHRUB,        // generic rounded bush
    SPINY_SHRUB,        // desert; minimal leaves, many thorns
    FLOWERING_SHRUB,    // temperate; accent colour blooms
    ALIEN_BULB,         // alien; inflated bulbous form
    BRAMBLE,            // thicket; tangled low shrub, difficult terrain

    // ── GROUND COVER ───────────────────────────────────────────────────────
    GRASS_TUFT,         // clumped grass; billboard strips
    GRASS_FIELD,        // flat continuous grass; density texture
    FERN,               // frond radiating from centre
    MOSS_PATCH,         // low flat coverage; hugs terrain contours
    LICHEN,             // flat crusty growth on rocks
    ALIEN_FROND,        // alien fern-like; often bioluminescent
    AQUATIC_REED,       // wetlands/coastal; tall hollow stems
    WATERLILY,          // floating leaf pad, optional bloom
    SUCCULENTS,         // desert; rosette or columnar; water-storing
    MUSHROOM_CLUSTER,   // fungal biome or forest floor; cap + stalk

    // ── ALIEN STRUCTURES ───────────────────────────────────────────────────
    CRYSTAL_SPIRE,      // alien crystal; faceted mineral column
    CRYSTAL_CLUSTER,    // alien crystal; grouped spires from common base
    TENDRIL_MASS,       // alien; writhing organic cables
    SPORE_PILLAR,       // alien fungal; tall column releasing particles
    MINERAL_BLOOM,      // alien; outward-growing mineral plates
    VOID_FLOWER,        // alien; dark inverted bloom shape, high emissive
}
```

---

## VegetationConfig

```java
public class VegetationConfig {
    public long         seed;
    public Biome        biome;
    public float        planetColorHueShift; // from PlanetConfig; shifts all plant colours
    public float        altitudeM;
    public float        slopeDeg;
    public boolean      hasWater;            // coastal/aquatic plants enabled
    public float        foodDensityFactor;   // from ecosystem; lush biome = denser veg
}
```

---

## Flora Selection by Biome

```java
public class FloraSelector {

    /**
     * Return a weighted pool of flora archetypes for a given biome and surface.
     * Altitude and slope constraints are applied by the placement system, not here.
     */
    public WeightedPool<FloraArchetype> poolFor(VegetationConfig cfg, Random rng) {
        WeightedPool<FloraArchetype> pool = new WeightedPool<>();

        switch (cfg.biome) {
            case TROPICAL_RAINFOREST:
                pool.add(FloraArchetype.GIANT_TREE,     20);
                pool.add(FloraArchetype.BROADLEAF_TREE, 35);
                pool.add(FloraArchetype.PALM_TREE,       8);
                pool.add(FloraArchetype.FERN,           20);
                pool.add(FloraArchetype.MOSS_PATCH,     12);
                pool.add(FloraArchetype.AQUATIC_REED,    5, cfg.hasWater);
                break;
            case TEMPERATE_DECIDUOUS:
                pool.add(FloraArchetype.BROADLEAF_TREE, 40);
                pool.add(FloraArchetype.ROUND_SHRUB,    20);
                pool.add(FloraArchetype.FLOWERING_SHRUB,10);
                pool.add(FloraArchetype.GRASS_TUFT,     20);
                pool.add(FloraArchetype.FERN,           10);
                break;
            case BOREAL_FOREST:
                pool.add(FloraArchetype.CONIFER_TREE,   60);
                pool.add(FloraArchetype.DEAD_TREE,      10);
                pool.add(FloraArchetype.MOSS_PATCH,     15);
                pool.add(FloraArchetype.MUSHROOM_CLUSTER,10);
                pool.add(FloraArchetype.LICHEN,          5);
                break;
            case TUNDRA:
                pool.add(FloraArchetype.GRASS_TUFT,     30);
                pool.add(FloraArchetype.MOSS_PATCH,     30);
                pool.add(FloraArchetype.LICHEN,         25);
                pool.add(FloraArchetype.DEAD_TREE,      15);
                break;
            case SAVANNA:
                pool.add(FloraArchetype.BROADLEAF_TREE, 15);
                pool.add(FloraArchetype.GRASS_FIELD,    50);
                pool.add(FloraArchetype.GRASS_TUFT,     25);
                pool.add(FloraArchetype.SPINY_SHRUB,    10);
                break;
            case HOT_DESERT:
                pool.add(FloraArchetype.SUCCULENTS,     45);
                pool.add(FloraArchetype.SPINY_SHRUB,    30);
                pool.add(FloraArchetype.DEAD_TREE,      15);
                pool.add(FloraArchetype.LICHEN,         10);
                break;
            case MANGROVE:
                pool.add(FloraArchetype.MANGROVE_TREE,  55);
                pool.add(FloraArchetype.AQUATIC_REED,   25);
                pool.add(FloraArchetype.WATERLILY,      20);
                break;
            case ALIEN_FUNGAL:
                pool.add(FloraArchetype.FUNGAL_TOWER,   30);
                pool.add(FloraArchetype.SPORE_PILLAR,   20);
                pool.add(FloraArchetype.MUSHROOM_CLUSTER,25);
                pool.add(FloraArchetype.BIOLUM_TREE,    15);
                pool.add(FloraArchetype.ALIEN_FROND,    10);
                break;
            case ALIEN_CRYSTAL:
                pool.add(FloraArchetype.CRYSTAL_TREE,   25);
                pool.add(FloraArchetype.CRYSTAL_SPIRE,  35);
                pool.add(FloraArchetype.CRYSTAL_CLUSTER,30);
                pool.add(FloraArchetype.MINERAL_BLOOM,  10);
                break;
            case ALIEN_LAVA_FIELDS:
                pool.add(FloraArchetype.LICHEN,         40); // heat-resistant extremophile
                pool.add(FloraArchetype.MINERAL_BLOOM,  35);
                pool.add(FloraArchetype.VOID_FLOWER,    25);
                break;
            default:
                pool.add(FloraArchetype.GRASS_TUFT,     50);
                pool.add(FloraArchetype.ROUND_SHRUB,    30);
                pool.add(FloraArchetype.BROADLEAF_TREE, 20);
        }
        return pool;
    }
}
```

---

## Tree Mesh Generation (Iterative Branch Growth)

```java
public class TreeMeshBuilder {

    /**
     * Grow a tree using iterative branching:
     * start from trunk, split into branches at each level,
     * taper branch radius and length with depth.
     */
    public MeshData build(FloraArchetype type, float heightM, long seed,
                           Color foliageColour, Color barkColour) {
        Random rng      = new Random(seed);
        MeshBuilder mb  = new MeshBuilder();

        TreeParams p    = paramsFor(type, heightM, rng);
        Array<Branch> branches = new Array<>();

        // Root trunk
        Branch trunk = new Branch(
            Vector3.Zero, new Vector3(0, 1, 0),
            heightM * p.trunkHeightFraction,
            p.trunkRadiusM, 0);
        branches.add(trunk);

        // Expand: each branch at depth < maxDepth spawns child branches
        for (int depth = 0; depth < p.maxDepth; depth++) {
            Array<Branch> nextGen = new Array<>();
            for (Branch b : branches) {
                if (b.depth != depth) continue;
                int childCount = p.branchingFactor + rng.nextInt(2);
                for (int c = 0; c < childCount; c++) {
                    Vector3 dir = branchDirection(b.direction, p, rng);
                    float   len = b.length * p.lengthDecay;
                    float   rad = b.radiusM * p.radiusDecay;
                    nextGen.add(new Branch(b.tip(), dir, len, rad, depth + 1));
                }
            }
            branches.addAll(nextGen);
        }

        // Tessellate each branch as a cylinder
        for (Branch b : branches) {
            Color c = b.depth == 0 ? barkColour : barkColour.cpy().lerp(foliageColour, b.depth * 0.25f);
            mb.addCylinder(b.origin, b.tip(), b.radiusM, 5, c, 0f);
        }

        // Add foliage geometry at leaf branches
        for (Branch b : branches) {
            if (b.depth == p.maxDepth - 1) {
                addFoliage(mb, b, type, foliageColour, rng);
            }
        }
        return mb.build();
    }

    private void addFoliage(MeshBuilder mb, Branch b, FloraArchetype type,
                              Color colour, Random rng) {
        switch (type) {
            case BROADLEAF_TREE:
            case GIANT_TREE:
            case ALIEN_CANOPY:
                // Sphere-cluster billboard proxy at branch tip
                float radius = b.length * 1.5f;
                mb.addSphere(b.tip(), radius, 6, colour, 0f);
                break;
            case CONIFER_TREE:
                // Stacked discs tapering toward top
                for (int l = 0; l < 5; l++) {
                    float t   = l / 4f;
                    float r   = b.length * (1f - t * 0.7f);
                    Vector3 c2 = b.tip().cpy().add(0, l * b.length * 0.3f, 0);
                    mb.addDisc(c2, r, 6, colour, 0f);
                }
                break;
            case CRYSTAL_TREE:
                // Faceted spire tips — no leaf, emissive
                mb.addPyramid(b.tip(), b.length * 0.8f, 4, colour, 0.4f);
                break;
            case BIOLUM_TREE:
            case FUNGAL_TOWER:
                mb.addSphere(b.tip(), b.length, 6, colour, 0.7f); // emissive
                break;
        }
    }

    private TreeParams paramsFor(FloraArchetype type, float heightM, Random rng) {
        return switch (type) {
            case BROADLEAF_TREE -> new TreeParams(
                0.55f, // trunkHeightFraction
                heightM * 0.04f, // trunkRadiusM
                3, // maxDepth
                3, // branchingFactor
                0.60f, // lengthDecay
                0.55f, // radiusDecay
                35f, // branchAngleDeg
                12f  // branchAngleVarianceDeg
            );
            case CONIFER_TREE -> new TreeParams(
                0.75f, heightM * 0.025f, 4, 2, 0.65f, 0.50f, 25f, 5f);
            case GIANT_TREE -> new TreeParams(
                0.60f, heightM * 0.07f, 3, 4, 0.55f, 0.50f, 40f, 15f);
            case PALM_TREE -> new TreeParams(
                0.90f, heightM * 0.035f, 1, 6, 0.80f, 0.40f, 70f, 10f);
            case ALIEN_CANOPY -> new TreeParams(
                0.50f, heightM * 0.05f, 3, rng.nextInt(3) + 2, 0.55f, 0.50f,
                45f + rng.nextFloat() * 30f, 20f);
            case CRYSTAL_TREE -> new TreeParams(
                0.70f, heightM * 0.04f, 3, 2, 0.70f, 0.60f, 30f, 5f);
            default -> new TreeParams(0.60f, heightM * 0.03f, 3, 3, 0.60f, 0.55f, 35f, 10f);
        };
    }

    private Vector3 branchDirection(Vector3 parent, TreeParams p, Random rng) {
        float pitch = p.branchAngleDeg + MathUtils.random(rng, -p.variance, p.variance);
        float yaw   = rng.nextFloat() * 360f;
        return parent.cpy()
            .rotate(parent.cpy().crs(Vector3.Y).nor(), pitch)
            .rotate(parent, yaw)
            .nor();
    }
}
```

---

## Ground Cover Generation

```java
public class GroundCoverBuilder {

    /**
     * Build a grass/moss/lichen mesh: vertical billboard strips arranged in a tuft.
     * All ground cover uses vertex colour only (stride-11, no UV).
     */
    public MeshData buildTuft(FloraArchetype type, float heightM, long seed,
                               Color baseColour, Color tipColour) {
        Random rng    = new Random(seed);
        MeshBuilder mb = new MeshBuilder();
        int blades     = tufBladesFor(type);

        for (int i = 0; i < blades; i++) {
            float angle  = rng.nextFloat() * MathUtils.PI2;
            float dist   = rng.nextFloat() * 0.3f;
            float h      = heightM * MathUtils.random(rng, 0.6f, 1.3f);
            float lean   = MathUtils.random(rng, -0.2f, 0.2f); // lean angle

            Vector3 base = new Vector3(
                MathUtils.cos(angle) * dist, 0,
                MathUtils.sin(angle) * dist);
            Vector3 tip  = base.cpy().add(lean, h, 0);

            // Gradient: dark base → bright tip
            mb.addBillboardBlade(base, tip, 0.04f, baseColour, tipColour);
        }
        return mb.build();
    }

    private int tufBladesFor(FloraArchetype t) {
        return switch (t) {
            case GRASS_TUFT  -> 8 + (int)(Math.random() * 8);
            case FERN        -> 5 + (int)(Math.random() * 4);
            case ALIEN_FROND -> 4 + (int)(Math.random() * 4);
            case AQUATIC_REED-> 3 + (int)(Math.random() * 3);
            default          -> 6;
        };
    }
}
```

---

## Alien Structure Meshes

```java
public class AlienFloraBuilder {

    public MeshData buildCrystalSpire(float heightM, float baseDiam,
                                       int facets, Color colour, long seed) {
        MeshBuilder mb = new MeshBuilder();
        Random rng     = new Random(seed);
        // Taper: hexagonal base → zero-area tip; emissive at tip
        for (int f = 0; f < facets; f++) {
            float a0 = f * MathUtils.PI2 / facets;
            float a1 = (f + 1) * MathUtils.PI2 / facets;
            Vector3 b0 = new Vector3(MathUtils.cos(a0) * baseDiam * 0.5f, 0,
                                      MathUtils.sin(a0) * baseDiam * 0.5f);
            Vector3 b1 = new Vector3(MathUtils.cos(a1) * baseDiam * 0.5f, 0,
                                      MathUtils.sin(a1) * baseDiam * 0.5f);
            Vector3 tip = new Vector3(0, heightM, 0);
            mb.triangle(b0, b1, tip, colour.cpy().mul(0.7f), colour.cpy().mul(0.7f), colour);
        }
        // Emissive glow at tip vertices
        mb.applyEmissivePatch(new Vector3(0, heightM, 0), 0.5f, colour, 0.8f);
        return mb.build();
    }

    public MeshData buildFungalTower(float heightM, float capRadiusM, long seed,
                                      Color stalkColour, Color capColour) {
        MeshBuilder mb = new MeshBuilder();
        Random rng     = new Random(seed);
        float stalkRadius = capRadiusM * 0.15f;

        mb.addCylinder(Vector3.Zero, new Vector3(0, heightM, 0),
                        stalkRadius, 6, stalkColour, 0f);

        // Cap: flattened hemi-sphere at top
        mb.addHemiSphere(new Vector3(0, heightM, 0),
                          capRadiusM, capRadiusM * 0.35f,
                          8, capColour, 0.1f); // slight emissive

        // Spore pores: dark dots baked on cap underside
        int pores = 6 + rng.nextInt(6);
        for (int p = 0; p < pores; p++) {
            float a = rng.nextFloat() * MathUtils.PI2;
            float r = rng.nextFloat() * capRadiusM * 0.7f;
            Vector3 porePos = new Vector3(MathUtils.cos(a) * r,
                                           heightM - 0.05f,
                                           MathUtils.sin(a) * r);
            mb.applyGaussianDarken2D(porePos, 0.08f, 0.7f);
        }
        return mb.build();
    }
}
```

---

## Vertex Colour Derivation

```java
public class VegetationColourDeriver {

    /**
     * All plant colours are derived from the biome palette + planet hue shift.
     * Never use hard-coded RGB for vegetation; always route through this.
     */
    public VegetationPalette derive(Biome biome, float planetHueShift, long seed) {
        Random rng       = new Random(seed);
        Color biomeLeaf  = biomeLeafColour(biome);
        Color biomeBark  = biomeBarkColour(biome);

        // Apply planet-wide hue shift (red plants on a red-star world, etc.)
        biomeLeaf = shiftHue(biomeLeaf, planetHueShift);
        biomeBark = shiftHue(biomeBark, planetHueShift * 0.3f); // bark shifts less

        return new VegetationPalette(
            biomeLeaf.cpy().mul(MathUtils.random(rng, 0.85f, 1.10f)),  // foliage base
            biomeLeaf.cpy().mul(MathUtils.random(rng, 1.10f, 1.30f)),  // foliage highlight
            biomeBark.cpy().mul(MathUtils.random(rng, 0.80f, 1.05f)),  // bark
            biomeLeaf.cpy().lerp(Color.WHITE, 0.4f)                     // blossom/tip
        );
    }

    private Color biomeLeafColour(Biome b) {
        return switch (b) {
            case TROPICAL_RAINFOREST,
                 TROPICAL_SEASONAL       -> Color.valueOf("0F6010");
            case TEMPERATE_DECIDUOUS     -> Color.valueOf("3A8030");
            case BOREAL_FOREST          -> Color.valueOf("1F5020");
            case SAVANNA, GRASSLAND     -> Color.valueOf("90B835");
            case TUNDRA                 -> Color.valueOf("708060");
            case HOT_DESERT             -> Color.valueOf("C8A840");
            case MANGROVE               -> Color.valueOf("1A7020");
            case ALIEN_FUNGAL           -> Color.valueOf("8020A0");
            case ALIEN_CRYSTAL          -> Color.valueOf("80D8E8");
            case ALIEN_LAVA_FIELDS      -> Color.valueOf("C83010");
            default                     -> Color.valueOf("508030");
        };
    }

    private Color biomeBarkColour(Biome b) {
        return switch (b) {
            case ALIEN_CRYSTAL          -> Color.valueOf("405080");
            case ALIEN_FUNGAL           -> Color.valueOf("301840");
            case ALIEN_LAVA_FIELDS      -> Color.valueOf("202020");
            default                     -> Color.valueOf("5A4030");
        };
    }
}
```

---

## Placement & Density

```java
public class VegetationPlacer {

    /**
     * Scatter vegetation instances across a terrain chunk.
     * Respects slope limits, altitude bands, water exclusion, and biome density.
     */
    public Array<FloraInstance> place(VegetationConfig cfg, TerrainChunk chunk,
                                       FloraSelector selector, Random rng) {
        Array<FloraInstance> instances = new Array<>();
        WeightedPool<FloraArchetype> pool = selector.poolFor(cfg, rng);
        float density = densityFor(cfg.biome) * cfg.foodDensityFactor;
        int   count   = (int)(chunk.sizeM * chunk.sizeM * density);

        for (int i = 0; i < count; i++) {
            float x = rng.nextFloat() * chunk.sizeM;
            float z = rng.nextFloat() * chunk.sizeM;
            float altitude = chunk.heightAt(x, z);
            float slope    = chunk.slopeAt(x, z);

            // Slope exclusion: no vegetation on > 45° slopes
            if (slope > 45f) continue;
            // Altitude exclusion: no trees above snow line
            if (altitude > cfg.altitudeM * 0.85f) continue;
            // Water exclusion: aquatic plants only at water's edge
            boolean atWater = chunk.isNearWater(x, z, 3f);
            FloraArchetype type = pool.roll(rng);
            if (isAquatic(type) && !atWater) continue;
            if (!isAquatic(type) && chunk.isUnderwater(x, z)) continue;

            float heightM   = floraHeightFor(type, rng);
            long  floraSeed = rng.nextLong();
            instances.add(new FloraInstance(type, new Vector3(x, altitude, z),
                                             heightM, floraSeed));
        }
        return instances;
    }

    private float densityFor(Biome b) {
        // instances per m²
        return switch (b) {
            case TROPICAL_RAINFOREST -> 0.08f;
            case TEMPERATE_DECIDUOUS,
                 BOREAL_FOREST       -> 0.05f;
            case SAVANNA             -> 0.03f;
            case GRASSLAND           -> 0.06f;
            case HOT_DESERT,
                 COLD_DESERT         -> 0.005f;
            case TUNDRA              -> 0.02f;
            case ALIEN_FUNGAL        -> 0.04f;
            case ALIEN_CRYSTAL       -> 0.02f;
            case ALIEN_LAVA_FIELDS   -> 0.003f;
            default                  -> 0.03f;
        };
    }

    private float floraHeightFor(FloraArchetype type, Random rng) {
        return switch (type) {
            case GIANT_TREE        -> 25f + rng.nextFloat() * 20f;
            case BROADLEAF_TREE    -> 6f  + rng.nextFloat() * 8f;
            case CONIFER_TREE      -> 8f  + rng.nextFloat() * 12f;
            case PALM_TREE         -> 10f + rng.nextFloat() * 8f;
            case FUNGAL_TOWER      -> 4f  + rng.nextFloat() * 8f;
            case CRYSTAL_SPIRE     -> 3f  + rng.nextFloat() * 10f;
            case ROUND_SHRUB       -> 0.8f + rng.nextFloat() * 1.2f;
            case GRASS_TUFT        -> 0.3f + rng.nextFloat() * 0.5f;
            case MUSHROOM_CLUSTER  -> 0.2f + rng.nextFloat() * 0.6f;
            default                -> 1.0f + rng.nextFloat() * 1.5f;
        };
    }
}
```

---

## LOD Strategy

```java
// LOD0  < 30 m:   full procedural mesh (branches, blades, crystal facets)
// LOD1  30–120 m: simplified mesh (trunk + foliage sphere; no branch detail)
// LOD2  120–400 m: cross-billboard (2 perpendicular quads; foliage colour only)
// LOD3  400–1500 m: density impostor texture patch (no individual instances)
// LOD4  > 1500 m:  vertex colour on terrain texture only; no instances

public class VegetationLODSystem {

    public void updateLODs(Array<FloraInstance> instances, Vector3 cameraPos) {
        for (FloraInstance fi : instances) {
            float dist = fi.position.dst(cameraPos);
            fi.activeLOD = dist < 30f   ? 0
                         : dist < 120f  ? 1
                         : dist < 400f  ? 2
                         : dist < 1500f ? 3
                         :                4;
        }
    }

    /** For LOD2: build a cross-billboard mesh from two perpendicular quads. */
    public Mesh buildCrossBillboard(FloraInstance fi, VegetationPalette pal) {
        MeshBuilder mb = new MeshBuilder();
        float h = fi.heightM;
        float w = h * 0.7f;
        // Quad 1: X-aligned
        mb.addQuad(new Vector3(-w/2, 0, 0), new Vector3(w/2, 0, 0),
                   new Vector3(w/2, h, 0), new Vector3(-w/2, h, 0),
                   pal.foliageBase, pal.foliageHighlight);
        // Quad 2: Z-aligned
        mb.addQuad(new Vector3(0, 0, -w/2), new Vector3(0, 0, w/2),
                   new Vector3(0, h,  w/2), new Vector3(0, h, -w/2),
                   pal.foliageBase, pal.foliageHighlight);
        return mb.build();
    }
}
```

---

## Wind Animation Parameters

```java
public class WindParamSeeder {

    /**
     * Seed per-instance wind animation parameters.
     * Grass and lightweight shrubs sway more than trees.
     * Wind parameters are passed to the vertex shader as uniforms.
     * (No procedural animation logic here — just the seed values.)
     */
    public WindParams seed(FloraArchetype type, long instanceSeed, float planetWindSpeed) {
        Random rng = new Random(instanceSeed);
        WindParams w = new WindParams();
        w.swayAmplitude = swayAmplitudeFor(type) * planetWindSpeed;
        w.swayFrequency = MathUtils.random(rng, 0.4f, 1.2f);
        w.phaseOffset   = rng.nextFloat() * MathUtils.PI2;  // desync from neighbours
        w.heightFalloff = heightFalloffFor(type); // how much sway decreases near base
        return w;
    }

    private float swayAmplitudeFor(FloraArchetype t) {
        return switch (t) {
            case GRASS_TUFT, GRASS_FIELD -> 0.12f;
            case ALIEN_FROND, FERN       -> 0.08f;
            case ROUND_SHRUB, SPINY_SHRUB-> 0.04f;
            case BROADLEAF_TREE          -> 0.025f;
            case GIANT_TREE              -> 0.010f;
            case CRYSTAL_SPIRE,
                 CRYSTAL_TREE            -> 0.002f; // stiff mineral; barely moves
            default                      -> 0.05f;
        };
    }

    private float heightFalloffFor(FloraArchetype t) {
        // 0 = uniform sway; 1 = sway only at tip
        return switch (t) {
            case GRASS_TUFT  -> 0.3f;  // grass sways along most of blade
            case CONIFER_TREE-> 0.8f;  // stiff trunk; only branches move
            default          -> 0.6f;
        };
    }
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Trees on > 45° slopes | Apply slope exclusion in `VegetationPlacer.place()`; never plant trees on cliff faces |
| Aquatic plants away from water | Check `chunk.isNearWater()` before placing reeds, waterilies, mangroves |
| UV coordinates on plant meshes | No UVs — stride-11 vertex colour only; all detail via position-based colour |
| Crystal structures with foliage sphere | Crystal/alien structures never get organic foliage; use pyramid/facet tips only |
| Same density in all biomes | `densityFor(biome)` must vary 16× from rainforest (0.08) to lava fields (0.003) |
| No LOD system for vegetation | Full meshes at any distance will destroy frame rate; LOD2 cross-billboards are mandatory at 120+ m |
| Wind amplitude same for all flora | Grass sways 6× more than a giant tree; use `swayAmplitudeFor(type)` |
| Alien flora using Earth biome colours | `biomeLeafColour(ALIEN_CRYSTAL)` returns pale cyan, not green; always route through `VegetationColourDeriver` |
| Full geometry generated for LOD3+ | LOD3 and LOD4 use texture/impostor; never build a procedural mesh for instances > 400 m |
| Phase offsets all zero | `phaseOffset = rng.nextFloat() * PI2` per instance; all-same-phase causes synchronised swaying |
