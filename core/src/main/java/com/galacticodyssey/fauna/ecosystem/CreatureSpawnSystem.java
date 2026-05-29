package com.galacticodyssey.fauna.ecosystem;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureFactory;
import com.galacticodyssey.fauna.CreatureGenerator;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeType;

import java.util.*;

public class CreatureSpawnSystem extends EntitySystem {

    private static final float CHUNK_SIZE = 100f;
    private static final int LOAD_RADIUS = 2;

    private final FaunaDataRegistry registry;
    private final CreatureGenerator generator;
    private final CreatureFactory factory = new CreatureFactory();
    private final BiomeSpawnTable spawnTable;
    private final long worldSeed;

    private final Map<Long, ChunkPopulationRecord> populationRecords = new HashMap<>();
    private final Map<Long, List<Entity>> loadedCreatures = new HashMap<>();
    private final Set<Long> loadedChunks = new HashSet<>();
    private final Vector3 playerPos = new Vector3();
    private int lastPlayerCX = Integer.MIN_VALUE, lastPlayerCZ = Integer.MIN_VALUE;

    public CreatureSpawnSystem(int priority, FaunaDataRegistry registry,
                                CreatureGenerator generator, long worldSeed) {
        super(priority);
        this.registry = registry;
        this.generator = generator;
        this.worldSeed = worldSeed;
        this.spawnTable = new BiomeSpawnTable(registry.allSpecies());
    }

    public void setPlayerPosition(Vector3 pos) { playerPos.set(pos); }

    @Override
    public void update(float deltaTime) {
        int cx = (int) Math.floor(playerPos.x / CHUNK_SIZE);
        int cz = (int) Math.floor(playerPos.z / CHUNK_SIZE);

        if (cx == lastPlayerCX && cz == lastPlayerCZ) return;
        lastPlayerCX = cx;
        lastPlayerCZ = cz;

        Set<Long> desired = new HashSet<>();
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                desired.add(chunkKey(cx + dx, cz + dz));
            }
        }

        Iterator<Long> it = loadedChunks.iterator();
        while (it.hasNext()) {
            long key = it.next();
            if (!desired.contains(key)) {
                unloadChunk(key);
                it.remove();
            }
        }

        for (long key : desired) {
            if (!loadedChunks.contains(key)) {
                loadChunk(key, decodeX(key), decodeZ(key));
                loadedChunks.add(key);
            }
        }
    }

    private void loadChunk(long key, int cx, int cz) {
        ChunkPopulationRecord record = populationRecords.get(key);
        if (record == null) {
            record = generateInitialPopulation(cx, cz);
            populationRecords.put(key, record);
        }

        List<Entity> entities = new ArrayList<>();
        Random placeRng = new Random(SeedDeriver.forChunk(SeedDeriver.faunaDomain(worldSeed), cx, cz));
        float baseX = cx * CHUNK_SIZE;
        float baseZ = cz * CHUNK_SIZE;

        for (SpeciesPopulation pop : record.populations) {
            SpeciesDef species = registry.getSpecies(pop.speciesId);
            if (species == null || pop.count <= 0) continue;

            for (int i = 0; i < pop.count; i++) {
                float x = baseX + placeRng.nextFloat() * CHUNK_SIZE;
                float z = baseZ + placeRng.nextFloat() * CHUNK_SIZE;
                Vector3 pos = new Vector3(x, 0, z);

                CreatureSpec spec = generator.generate(species.archetypeId,
                    SeedDeriver.forId(SeedDeriver.faunaDomain(worldSeed), placeRng.nextLong()));
                Entity e = factory.create(getEngine(), spec, pos);
                entities.add(e);
            }
        }

        loadedCreatures.put(key, entities);
    }

    private void unloadChunk(long key) {
        List<Entity> entities = loadedCreatures.remove(key);
        if (entities != null) {
            ChunkPopulationRecord record = populationRecords.get(key);
            if (record != null) {
                Map<String, Integer> counts = new HashMap<>();
                for (Entity e : entities) {
                    if (getEngine().getEntities().contains(e, true)) {
                        com.galacticodyssey.fauna.components.CreatureComponent cc =
                            e.getComponent(com.galacticodyssey.fauna.components.CreatureComponent.class);
                        if (cc != null) {
                            counts.merge(cc.archetypeId, 1, Integer::sum);
                        }
                    }
                }
                for (SpeciesPopulation pop : record.populations) {
                    SpeciesDef species = registry.getSpecies(pop.speciesId);
                    if (species != null) {
                        Integer alive = counts.get(species.archetypeId);
                        if (alive != null) {
                            pop.count = alive;
                        }
                    }
                }
            }
            for (Entity e : entities) {
                getEngine().removeEntity(e);
            }
        }
    }

    private ChunkPopulationRecord generateInitialPopulation(int cx, int cz) {
        ChunkPopulationRecord record = new ChunkPopulationRecord();
        record.chunkX = cx;
        record.chunkZ = cz;
        record.biome = BiomeType.GRASSLAND;

        Random rng = new Random(SeedDeriver.forChunk(SeedDeriver.faunaDomain(worldSeed), cx, cz));
        List<BiomeSpawnTable.WeightedSpecies> eligible = spawnTable.speciesForBiome(record.biome);
        if (eligible.isEmpty()) return record;

        int speciesCount = 2 + rng.nextInt(Math.min(4, eligible.size()));
        List<BiomeSpawnTable.WeightedSpecies> shuffled = new ArrayList<>(eligible);
        Collections.shuffle(shuffled, rng);

        for (int i = 0; i < Math.min(speciesCount, shuffled.size()); i++) {
            BiomeSpawnTable.WeightedSpecies ws = shuffled.get(i);
            float K = ws.species.carryingCapacityBase * ws.weight;
            int initial = (int) (K * (0.5f + rng.nextFloat() * 0.3f));
            if (initial > 0) {
                record.populations.add(new SpeciesPopulation(ws.species.id, initial));
            }
        }

        return record;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
    private static int decodeX(long key) { return (int) (key >> 32); }
    private static int decodeZ(long key) { return (int) key; }
}
