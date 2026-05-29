package com.galacticodyssey.data;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.flora.FloraGenerator;
import com.galacticodyssey.flora.data.FloraRegistry;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.WhittakerGrid;

import java.util.Random;

/**
 * Populates a terrain with biome-driven procedural objects: trees, grass, rocks,
 * water, and animals. All geometry is built with {@link ModelBuilder}.
 */
public final class WorldPopulator {

    public static final class PopulatedWorld implements Disposable {
        public final BiomeType[] biomeGrid;
        public final int[] noisePerm;
        public final Array<ModelInstance> treeInstances = new Array<>();
        public final Array<ModelInstance> rockInstances = new Array<>();
        public final Array<ModelInstance> animalInstances = new Array<>();
        public final Array<AnimalState> animalStates = new Array<>();
        public Model waterModel;
        public ModelInstance waterInstance;
        public float seaLevel;

        private final Array<Model> models = new Array<>();

        public PopulatedWorld(BiomeType[] biomeGrid, int[] noisePerm) {
            this.biomeGrid = biomeGrid;
            this.noisePerm = noisePerm;
        }

        public void addModel(Model model) {
            models.add(model);
        }

        @Override
        public void dispose() {
            for (int i = 0; i < models.size; i++) {
                models.get(i).dispose();
            }
            models.clear();
            if (waterModel != null) {
                waterModel.dispose();
                waterModel = null;
            }
        }
    }

    public static final class AnimalState {
        public final Vector3 position = new Vector3();
        public final Vector3 velocity = new Vector3();
        public float timer;
        public float directionChangeInterval;
        public final BiomeType biome;

        public AnimalState(float x, float y, float z, BiomeType biome) {
            position.set(x, y, z);
            this.biome = biome;
        }
    }

    private WorldPopulator() {}

    private static FloraRegistry floraRegistry;

    private static FloraRegistry floraRegistry() {
        if (floraRegistry == null) {
            floraRegistry = new FloraRegistry();
            floraRegistry.load("data/flora/species.json", "data/flora/palettes.json");
        }
        return floraRegistry;
    }

    public static PopulatedWorld populate(
            float[] heightmap, int vertsX, int vertsZ,
            float worldWidth, float worldDepth, long seed) {

        Random rng = new Random(seed + 7919L);
        float cellW = worldWidth / (vertsX - 1);
        float cellD = worldDepth / (vertsZ - 1);
        float halfW = worldWidth / 2f;
        float halfD = worldDepth / 2f;

        float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
        for (float h : heightmap) {
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
        }
        float heightRange = maxH - minH + 0.001f;
        float seaLevel = minH + heightRange * 0.25f;

        int[] noisePerm = createPermutation(new Random(seed + 31337L));
        BiomeType[] biomeGrid = classifyBiomes(heightmap, vertsX, vertsZ, minH, heightRange, seaLevel, seed);
        PopulatedWorld world = new PopulatedWorld(biomeGrid, noisePerm);
        world.seaLevel = seaLevel;

        ModelBuilder mb = new ModelBuilder();
        int attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

        FloraGenerator.populate(world, floraRegistry(), heightmap, vertsX, vertsZ,
            worldWidth, worldDepth, seaLevel, seed);
        placeRocks(world, mb, attrs, heightmap, biomeGrid, vertsX, vertsZ, worldWidth, worldDepth, rng, seaLevel);
        placeAnimals(world, mb, attrs, heightmap, biomeGrid, vertsX, vertsZ, worldWidth, worldDepth, rng, seaLevel);
        createWater(world, mb, worldWidth, worldDepth, seaLevel);

        return world;
    }

    public static BiomeType[] classifyBiomes(float[] heightmap, int vertsX, int vertsZ,
                                              float minH, float heightRange, float seaLevel, long seed) {
        BiomeType[] grid = new BiomeType[vertsX * vertsZ];
        Random noiseRng = new Random(seed + 31337L);

        int[] perm = createPermutation(noiseRng);

        for (int z = 0; z < vertsZ; z++) {
            for (int x = 0; x < vertsX; x++) {
                int idx = z * vertsX + x;
                float h = heightmap[idx];
                float heightFrac = (h - minH) / heightRange;

                float nx = x / (float) vertsX;
                float nz = z / (float) vertsZ;

                float tempK = 290f - heightFrac * 60f + noise2D(perm, nx * 3f, nz * 3f) * 15f;
                float moisture = 0.5f + noise2D(perm, nx * 4f + 50f, nz * 4f + 50f) * 0.4f;
                moisture = Math.max(0f, Math.min(1f, moisture));

                if (h < seaLevel) {
                    grid[idx] = tempK > 273f ? BiomeType.OCEAN : BiomeType.ICE_SHEET;
                } else if (heightFrac > 0.85f) {
                    grid[idx] = BiomeType.ICE_FIELD;
                } else {
                    grid[idx] = WhittakerGrid.classify(tempK, moisture);
                }
            }
        }
        return grid;
    }

    public static Color biomeColor(BiomeType biome, float heightFrac, float slope,
                                    float worldX, float worldZ, float height,
                                    float minH, float maxH, int[] noisePerm,
                                    BiomeType[] biomeGrid, int vertsX, int vertsZ,
                                    int gridX, int gridZ) {
        float[] base = baseBiomeRGB(biome);
        float r = base[0], g = base[1], b = base[2];

        // Layer 1: Noise variation — perturb base color by ±15% using two octaves
        float n1 = noise2D(noisePerm, worldX * 0.02f, worldZ * 0.02f);
        float n2 = noise2D(noisePerm, worldX * 0.05f + 100f, worldZ * 0.05f + 100f);
        float noiseVal = (n1 * 0.7f + n2 * 0.3f) * 0.15f;
        r += noiseVal;
        g += noiseVal;
        b += noiseVal;

        // Layer 2: Biome edge blending — lerp with different-biome neighbors
        // Also includes the cell itself in case the grid value differs from the passed biome
        // (e.g. a vertex sitting on a biome seam boundary).
        int left  = Math.max(0, gridX - 1);
        int right = Math.min(vertsX - 1, gridX + 1);
        int up    = Math.max(0, gridZ - 1);
        int down  = Math.min(vertsZ - 1, gridZ + 1);

        BiomeType[] neighbors = {
            biomeGrid[gridZ * vertsX + gridX],
            biomeGrid[gridZ * vertsX + left],
            biomeGrid[gridZ * vertsX + right],
            biomeGrid[up * vertsX + gridX],
            biomeGrid[down * vertsX + gridX]
        };

        float blendWeight = 0f;
        float blendR = 0f, blendG = 0f, blendB = 0f;
        int blendCount = 0;
        for (BiomeType nb : neighbors) {
            if (nb != biome) {
                float[] nbRGB = baseBiomeRGB(nb);
                float noiseFactor = 0.4f + noise2D(noisePerm, worldX * 0.03f + 200f, worldZ * 0.03f + 200f) * 0.2f;
                blendR += nbRGB[0];
                blendG += nbRGB[1];
                blendB += nbRGB[2];
                blendWeight += noiseFactor;
                blendCount++;
            }
        }
        if (blendCount > 0) {
            blendR /= blendCount;
            blendG /= blendCount;
            blendB /= blendCount;
            float t = Math.min(1f, blendWeight / blendCount);
            r = r * (1f - t) + blendR * t;
            g = g * (1f - t) + blendG * t;
            b = b * (1f - t) + blendB * t;
        }

        // Layer 3: Slope-driven rock — steep slopes blend toward grey-brown
        float normalY = 1f - slope;
        float rockBlend = 1f - smoothstep(0.5f, 0.75f, normalY);
        if (rockBlend > 0f) {
            r = r * (1f - rockBlend) + 0.42f * rockBlend;
            g = g * (1f - rockBlend) + 0.38f * rockBlend;
            b = b * (1f - rockBlend) + 0.32f * rockBlend;
        }

        // Layer 4: Altitude snow — above 75th percentile, blend toward white
        float heightRange = maxH - minH + 0.001f;
        float snowLine = minH + heightRange * 0.75f;
        float snowNoise = noise2D(noisePerm, worldX * 0.01f + 300f, worldZ * 0.01f + 300f) * heightRange * 0.08f;
        float snowThreshold = snowLine + snowNoise;
        if (height > snowThreshold - heightRange * 0.1f) {
            float snowBlend = smoothstep(snowThreshold - heightRange * 0.1f, snowThreshold + heightRange * 0.05f, height);
            r = r * (1f - snowBlend) + 0.92f * snowBlend;
            g = g * (1f - snowBlend) + 0.93f * snowBlend;
            b = b * (1f - snowBlend) + 0.95f * snowBlend;
        }

        return new Color(
            Math.max(0f, Math.min(1f, r)),
            Math.max(0f, Math.min(1f, g)),
            Math.max(0f, Math.min(1f, b)), 1f);
    }

    private static float[] baseBiomeRGB(BiomeType biome) {
        switch (biome) {
            case OCEAN:            return new float[]{0.05f, 0.15f, 0.45f};
            case ICE_SHEET:        return new float[]{0.85f, 0.90f, 0.95f};
            case ICE_FIELD:        return new float[]{0.80f, 0.85f, 0.92f};
            case TUNDRA:           return new float[]{0.45f, 0.50f, 0.42f};
            case POLAR_DESERT:     return new float[]{0.55f, 0.52f, 0.48f};
            case BOREAL_FOREST:    return new float[]{0.12f, 0.30f, 0.15f};
            case TEMPERATE_FOREST: return new float[]{0.15f, 0.40f, 0.12f};
            case STEPPE:           return new float[]{0.50f, 0.48f, 0.30f};
            case ROCKY_WASTE:      return new float[]{0.42f, 0.38f, 0.32f};
            case TROPICAL_FOREST:  return new float[]{0.08f, 0.42f, 0.10f};
            case GRASSLAND:        return new float[]{0.30f, 0.55f, 0.18f};
            case ARID_SHRUB:       return new float[]{0.58f, 0.52f, 0.30f};
            case DESERT:           return new float[]{0.76f, 0.68f, 0.42f};
            case SWAMP:            return new float[]{0.20f, 0.32f, 0.18f};
            case SAVANNA:          return new float[]{0.55f, 0.52f, 0.25f};
            case BADLANDS:         return new float[]{0.62f, 0.35f, 0.22f};
            case VOLCANIC:         return new float[]{0.25f, 0.15f, 0.12f};
            case LAKE:             return new float[]{0.10f, 0.25f, 0.50f};
            default:               return new float[]{0.30f, 0.30f, 0.30f};
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private static void placeRocks(PopulatedWorld world, ModelBuilder mb, int attrs,
                                    float[] heightmap, BiomeType[] biomeGrid,
                                    int vertsX, int vertsZ, float worldWidth, float worldDepth,
                                    Random rng, float seaLevel) {
        float halfW = worldWidth / 2f;
        float halfD = worldDepth / 2f;

        for (int i = 0; i < 200; i++) {
            float wx = (rng.nextFloat() - 0.5f) * worldWidth * 0.9f;
            float wz = (rng.nextFloat() - 0.5f) * worldDepth * 0.9f;
            float h = TerrainGenerator.getHeightAt(heightmap, vertsX, vertsZ, worldWidth, worldDepth, wx, wz);
            if (h < seaLevel) continue;

            int gx = Math.min(vertsX - 1, Math.max(0, (int) ((wx + halfW) / worldWidth * (vertsX - 1))));
            int gz = Math.min(vertsZ - 1, Math.max(0, (int) ((wz + halfD) / worldDepth * (vertsZ - 1))));
            BiomeType biome = biomeGrid[gz * vertsX + gx];

            float density = rockDensity(biome);
            if (rng.nextFloat() > density) continue;

            float sx = 0.3f + rng.nextFloat() * 1.5f;
            float sy = 0.2f + rng.nextFloat() * 1.0f;
            float sz = 0.3f + rng.nextFloat() * 1.5f;

            Color rockColor = rockColorForBiome(biome, rng);

            Model rockModel = mb.createBox(sx, sy, sz,
                new Material(ColorAttribute.createDiffuse(rockColor)), attrs);
            world.addModel(rockModel);

            ModelInstance instance = new ModelInstance(rockModel);
            instance.transform.setToTranslation(wx, h + sy * 0.4f, wz);
            instance.transform.rotate(Vector3.Y, rng.nextFloat() * 360f);
            world.rockInstances.add(instance);
        }
    }

    private static float rockDensity(BiomeType biome) {
        switch (biome) {
            case ROCKY_WASTE: return 0.85f;
            case BADLANDS:    return 0.7f;
            case VOLCANIC:    return 0.6f;
            case DESERT:      return 0.3f;
            case TUNDRA:      return 0.25f;
            case ICE_FIELD:   return 0.2f;
            case STEPPE:      return 0.15f;
            case GRASSLAND:   return 0.05f;
            default:          return 0.02f;
        }
    }

    private static Color rockColorForBiome(BiomeType biome, Random rng) {
        float v = rng.nextFloat() * 0.1f;
        switch (biome) {
            case VOLCANIC:  return new Color(0.20f + v, 0.12f + v, 0.10f, 1f);
            case BADLANDS:  return new Color(0.55f + v, 0.30f + v, 0.18f, 1f);
            case DESERT:    return new Color(0.65f + v, 0.58f + v, 0.40f, 1f);
            case ICE_FIELD: return new Color(0.70f + v, 0.75f + v, 0.80f, 1f);
            default:        return new Color(0.40f + v, 0.38f + v, 0.35f, 1f);
        }
    }

    private static void placeAnimals(PopulatedWorld world, ModelBuilder mb, int attrs,
                                      float[] heightmap, BiomeType[] biomeGrid,
                                      int vertsX, int vertsZ, float worldWidth, float worldDepth,
                                      Random rng, float seaLevel) {
        float halfW = worldWidth / 2f;
        float halfD = worldDepth / 2f;

        for (int i = 0; i < 60; i++) {
            float wx = (rng.nextFloat() - 0.5f) * worldWidth * 0.7f;
            float wz = (rng.nextFloat() - 0.5f) * worldDepth * 0.7f;
            float h = TerrainGenerator.getHeightAt(heightmap, vertsX, vertsZ, worldWidth, worldDepth, wx, wz);
            if (h < seaLevel + 1f) continue;

            int gx = Math.min(vertsX - 1, Math.max(0, (int) ((wx + halfW) / worldWidth * (vertsX - 1))));
            int gz = Math.min(vertsZ - 1, Math.max(0, (int) ((wz + halfD) / worldDepth * (vertsZ - 1))));
            BiomeType biome = biomeGrid[gz * vertsX + gx];

            float density = animalDensity(biome);
            if (rng.nextFloat() > density) continue;

            Color animalColor = animalColorForBiome(biome, rng);
            float bodyLength = 0.4f + rng.nextFloat() * 0.6f;
            float bodyHeight = 0.25f + rng.nextFloat() * 0.35f;
            float bodyWidth = 0.2f + rng.nextFloat() * 0.3f;

            Model animalModel = mb.createBox(bodyLength, bodyHeight, bodyWidth,
                new Material(ColorAttribute.createDiffuse(animalColor)), attrs);
            world.addModel(animalModel);

            ModelInstance instance = new ModelInstance(animalModel);
            instance.transform.setToTranslation(wx, h + bodyHeight * 0.5f, wz);
            world.animalInstances.add(instance);

            AnimalState state = new AnimalState(wx, h + bodyHeight * 0.5f, wz, biome);
            state.directionChangeInterval = 2f + rng.nextFloat() * 4f;
            state.velocity.set(
                (rng.nextFloat() - 0.5f) * 2f, 0f,
                (rng.nextFloat() - 0.5f) * 2f);
            world.animalStates.add(state);
        }
    }

    private static float animalDensity(BiomeType biome) {
        switch (biome) {
            case GRASSLAND:        return 0.7f;
            case SAVANNA:          return 0.65f;
            case TEMPERATE_FOREST: return 0.4f;
            case TROPICAL_FOREST:  return 0.35f;
            case BOREAL_FOREST:    return 0.25f;
            case STEPPE:           return 0.3f;
            case TUNDRA:           return 0.15f;
            case SWAMP:            return 0.2f;
            default:               return 0f;
        }
    }

    private static Color animalColorForBiome(BiomeType biome, Random rng) {
        float v = rng.nextFloat() * 0.1f;
        switch (biome) {
            case SAVANNA:         return new Color(0.60f + v, 0.48f + v, 0.25f, 1f);
            case TUNDRA:          return new Color(0.70f + v, 0.72f + v, 0.70f, 1f);
            case BOREAL_FOREST:   return new Color(0.40f + v, 0.30f + v, 0.20f, 1f);
            case SWAMP:           return new Color(0.25f + v, 0.30f + v, 0.18f, 1f);
            default:              return new Color(0.45f + v, 0.35f + v, 0.22f, 1f);
        }
    }

    private static void createWater(PopulatedWorld world, ModelBuilder mb,
                                     float worldWidth, float worldDepth, float seaLevel) {
        Material waterMat = new Material(
            ColorAttribute.createDiffuse(new Color(0.1f, 0.3f, 0.6f, 0.7f)),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA));

        Model waterModel = mb.createRect(
            -worldWidth / 2f, seaLevel, -worldDepth / 2f,
            -worldWidth / 2f, seaLevel, worldDepth / 2f,
            worldWidth / 2f, seaLevel, worldDepth / 2f,
            worldWidth / 2f, seaLevel, -worldDepth / 2f,
            0f, 1f, 0f,
            waterMat,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        world.waterModel = waterModel;
        world.waterInstance = new ModelInstance(waterModel);
    }

    public static void updateAnimals(PopulatedWorld world, float delta,
                                      float[] heightmap, int vertsX, int vertsZ,
                                      float worldWidth, float worldDepth) {
        Random rng = new Random();
        float halfW = worldWidth * 0.45f;
        float halfD = worldDepth * 0.45f;

        for (int i = 0; i < world.animalStates.size; i++) {
            AnimalState state = world.animalStates.get(i);
            state.timer += delta;

            if (state.timer >= state.directionChangeInterval) {
                state.timer = 0f;
                state.velocity.set(
                    (rng.nextFloat() - 0.5f) * 3f, 0f,
                    (rng.nextFloat() - 0.5f) * 3f);
            }

            state.position.x += state.velocity.x * delta;
            state.position.z += state.velocity.z * delta;

            state.position.x = Math.max(-halfW, Math.min(halfW, state.position.x));
            state.position.z = Math.max(-halfD, Math.min(halfD, state.position.z));

            float h = TerrainGenerator.getHeightAt(
                heightmap, vertsX, vertsZ, worldWidth, worldDepth,
                state.position.x, state.position.z);

            if (h < world.seaLevel + 0.5f) {
                state.velocity.scl(-1f);
                state.position.x += state.velocity.x * delta * 2f;
                state.position.z += state.velocity.z * delta * 2f;
                h = TerrainGenerator.getHeightAt(
                    heightmap, vertsX, vertsZ, worldWidth, worldDepth,
                    state.position.x, state.position.z);
            }

            state.position.y = h + 0.3f;

            ModelInstance instance = world.animalInstances.get(i);
            instance.transform.setToTranslation(state.position);
            if (state.velocity.len2() > 0.01f) {
                float angle = (float) Math.toDegrees(Math.atan2(state.velocity.x, state.velocity.z));
                instance.transform.rotate(Vector3.Y, angle);
            }
        }
    }

    // Simplex noise for biome classification (same as TerrainGenerator)
    private static final float F2 = 0.5f * ((float) Math.sqrt(3.0) - 1.0f);
    private static final float G2 = (3.0f - (float) Math.sqrt(3.0)) / 6.0f;
    private static final int[][] GRAD2 = {
        {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    static int[] createPermutation(Random rng) {
        int[] p = new int[512];
        int[] base = new int[256];
        for (int i = 0; i < 256; i++) base[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = base[i]; base[i] = base[j]; base[j] = tmp;
        }
        for (int i = 0; i < 512; i++) p[i] = base[i & 255];
        return p;
    }

    static float noise2D(int[] perm, float x, float y) {
        float s = (x + y) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);
        float t = (i + j) * G2;
        float x0 = x - (i - t);
        float y0 = y - (j - t);
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }
        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1f + 2f * G2;
        float y2 = y0 - 1f + 2f * G2;
        int ii = i & 255;
        int jj = j & 255;
        float n0 = corner(perm, x0, y0, ii, jj);
        float n1 = corner(perm, x1, y1, ii + i1, jj + j1);
        float n2 = corner(perm, x2, y2, ii + 1, jj + 1);
        return 70f * (n0 + n1 + n2);
    }

    private static float corner(int[] perm, float x, float y, int gi, int gj) {
        float t = 0.5f - x * x - y * y;
        if (t < 0) return 0;
        t *= t;
        int[] g = GRAD2[perm[perm[gi & 255] + (gj & 255)] & 7];
        return t * t * (g[0] * x + g[1] * y);
    }

    private static int fastFloor(float x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}
