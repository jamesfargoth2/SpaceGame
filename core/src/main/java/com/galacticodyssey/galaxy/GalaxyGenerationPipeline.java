package com.galacticodyssey.galaxy;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.data.GameSession;
import com.galacticodyssey.data.TerrainGenerator;
import com.galacticodyssey.data.names.SpaceNameGenerator;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetGenerator;
import com.galacticodyssey.planet.PlanetType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

public final class GalaxyGenerationPipeline {

    private static final int TERRAIN_VERTS = 257;
    private static final float TERRAIN_SIZE = 500f;

    private GalaxyGenerationPipeline() {}

    /** Runs all five generation phases synchronously, populating {@code session}'s result fields. */
    public static void run(GameSession session) {

        // Phase 1 — galaxy layout
        GalaxyConfig config = buildConfig(session.galaxySize, session.galaxyType);
        GalaxyManager manager = new GalaxyManager(session.seed, config);
        session.galaxy = manager;
        session.log.add("Mapping " + session.galaxySize.starCount
            + " star systems across the " + session.galaxyName + "…");

        // Phase 2 — find starting G/K main-sequence star deterministically
        Random seedRng = new Random(session.seed);
        double angle = seedRng.nextDouble() * 2 * Math.PI;
        double viewRadius = session.startingRegion.normalizedRadius * config.radiusLY;
        double viewX = Math.cos(angle) * viewRadius;
        double viewY = Math.sin(angle) * viewRadius;

        StarSystemGenerator starGen = new StarSystemGenerator(session.seed);
        GalaxyRegion targetRegion = session.startingRegion.galaxyRegion;
        SpaceNameGenerator nameGen = new SpaceNameGenerator();

        // Expand the view radius until at least one star is loaded, capped at galaxy radius.
        // Start at 5% of galaxy radius to ensure reasonable coverage even in sparse galaxies.
        List<StarPosition> candidates = new ArrayList<>();
        float loadRadius = Math.max(config.chunkSizeLY * 3f, config.radiusLY * 0.05f);
        float maxRadius = config.radiusLY * 0.6f;
        while (candidates.isEmpty() && loadRadius <= maxRadius) {
            manager.updateView(viewX, viewY, loadRadius);
            candidates = collectCandidates(manager, starGen, targetRegion);
            if (candidates.isEmpty()) loadRadius *= 2f;
        }
        // Last resort: scan galaxy core from origin (always has the highest density)
        if (candidates.isEmpty()) {
            manager.updateView(0, 0, config.radiusLY * 0.2f);
            candidates = collectCandidates(manager, starGen, GalaxyRegion.CORE);
        }

        candidates.sort(Comparator.comparingLong(s -> s.uniqueId));
        int chosenIdx = (int) (Math.abs(session.seed) % candidates.size());
        StarPosition chosenStar = candidates.get(chosenIdx);
        StarSystem chosenSystem = starGen.generate(chosenStar, targetRegion);
        session.startingSystem = chosenSystem;
        session.startingStarPosition = chosenStar;

        String starName = nameGen.starName(new Random(chosenStar.uniqueId ^ session.seed));
        session.log.add("Habitable zone identified around "
            + starName + " (" + chosenSystem.spectralClass + ")…");

        // Phase 3 — terran planet selection
        OrbitalLayoutGenerator layoutGen = new OrbitalLayoutGenerator();
        List<OrbitalSlot> slots = layoutGen.generate(chosenSystem);
        PlanetGenerator planetGen = new PlanetGenerator(session.seed);

        Planet planet = findTerranBreathable(slots, chosenSystem, planetGen);
        if (planet == null) planet = findPlanet(slots, chosenSystem, planetGen, PlanetType.TERRAN);
        if (planet == null) {
            for (OrbitalSlot slot : slots) {
                Planet p = planetGen.generate(slot, chosenSystem);
                if (p.type.hasSurface()) { planet = p; break; }
            }
        }
        if (planet == null) {
            planet = planetGen.generate(slots.get(0), chosenSystem);
        }
        session.startingPlanet = planet;

        String planetName = nameGen.planetName(new Random(planet.seed));
        String worldLabel = (planet.type == PlanetType.TERRAN) ? "Terran world" : planet.type.name() + " world";
        session.log.add(worldLabel + " " + planetName + " selected as origin point…");

        // Phase 4 — terrain seed and biome map derived from planet via SeedDeriver
        session.terrainSeed = SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN);
        session.startingBiomeMap = buildBiomeMap(planet);
        session.log.add("Surveying surface of " + planetName + "…");

        // Phase 5 — spawn coordinates from the seeded heightmap
        float[] hmap = TerrainGenerator.generateHeightmap(
            TERRAIN_VERTS, TERRAIN_VERTS, TERRAIN_SIZE, TERRAIN_SIZE, session.terrainSeed);
        float groundH = TerrainGenerator.getHeightAt(
            hmap, TERRAIN_VERTS, TERRAIN_VERTS, TERRAIN_SIZE, TERRAIN_SIZE, 0f, 0f);
        session.playerSpawnPos = new Vector3(0f, groundH + 2f, 0f);
        float shipX = 75f;
        float shipGroundH = TerrainGenerator.getHeightAt(
            hmap, TERRAIN_VERTS, TERRAIN_VERTS, TERRAIN_SIZE, TERRAIN_SIZE, shipX, 0f);
        session.shipSpawnPos = new Vector3(shipX, shipGroundH + 0.5f, 0f);
        session.log.add("Origin point confirmed. Welcome to " + session.galaxyName + ".");
    }

    private static List<StarPosition> collectCandidates(
            GalaxyManager manager, StarSystemGenerator starGen, GalaxyRegion region) {

        List<StarPosition> gk = new ArrayList<>();
        List<StarPosition> mainSeq = new ArrayList<>();
        List<StarPosition> any = new ArrayList<>();

        for (StarPosition star : manager.getLoadedStars()) {
            StarSystem sys = starGen.generate(star, region);
            any.add(star);
            if (sys.luminosityClass == LuminosityClass.MAIN_SEQUENCE) {
                mainSeq.add(star);
                if (sys.spectralClass == SpectralClass.G || sys.spectralClass == SpectralClass.K) {
                    gk.add(star);
                }
            }
        }

        if (!gk.isEmpty()) return gk;
        if (!mainSeq.isEmpty()) return mainSeq;
        return any;
    }

    /** Returns the first TERRAN planet whose atmosphere is non-null and breathable. */
    private static Planet findTerranBreathable(List<OrbitalSlot> slots, StarSystem system,
                                               PlanetGenerator gen) {
        for (OrbitalSlot slot : slots) {
            Planet p = gen.generate(slot, system);
            if (p.type == PlanetType.TERRAN && p.atmosphere != null && p.atmosphere.breathable) {
                return p;
            }
        }
        return null;
    }

    private static Planet findPlanet(List<OrbitalSlot> slots, StarSystem system,
                                     PlanetGenerator gen, PlanetType type) {
        for (OrbitalSlot slot : slots) {
            Planet p = gen.generate(slot, system);
            if (p.type == type) return p;
        }
        return null;
    }

    /** Derives a BiomeMap from the planet's physical and atmospheric properties. */
    private static BiomeMap buildBiomeMap(Planet planet) {
        long biomeSeed = SeedDeriver.domain(planet.seed, SeedDeriver.BIOME_DOMAIN);

        float seaLevel, snowLine, baseMoisture, surfaceTemp;
        EnumSet<BiomeType> allowed;

        switch (planet.type) {
            case TERRAN:
                seaLevel     = -0.05f;
                snowLine     = 0.55f;
                baseMoisture = 0.5f;
                surfaceTemp  = (planet.atmosphere != null) ? planet.atmosphere.surfaceTemp : 290f;
                allowed = EnumSet.allOf(BiomeType.class);
                break;
            case OCEAN:
                seaLevel     = 0.25f;
                snowLine     = 0.80f;
                baseMoisture = 0.85f;
                surfaceTemp  = (planet.atmosphere != null) ? planet.atmosphere.surfaceTemp : 285f;
                allowed = EnumSet.of(BiomeType.OCEAN, BiomeType.TROPICAL_FOREST,
                                     BiomeType.SWAMP, BiomeType.LAKE, BiomeType.RIVER);
                break;
            case ARID:
                seaLevel     = -0.6f;
                snowLine     = 0.90f;
                baseMoisture = 0.08f;
                surfaceTemp  = (planet.atmosphere != null) ? planet.atmosphere.surfaceTemp : 315f;
                allowed = EnumSet.of(BiomeType.DESERT, BiomeType.ARID_SHRUB,
                                     BiomeType.BADLANDS, BiomeType.STEPPE, BiomeType.ROCKY_WASTE);
                break;
            case TOXIC:
                seaLevel     = 0.0f;
                snowLine     = 0.95f;
                baseMoisture = 0.3f;
                surfaceTemp  = (planet.atmosphere != null) ? planet.atmosphere.surfaceTemp : 400f;
                allowed = EnumSet.of(BiomeType.VOLCANIC, BiomeType.BADLANDS,
                                     BiomeType.ROCKY_WASTE, BiomeType.OCEAN);
                break;
            case ICE_WORLD:
                seaLevel     = 0.30f;
                snowLine     = 0.05f;
                baseMoisture = 0.4f;
                surfaceTemp  = 200f;
                allowed = EnumSet.of(BiomeType.ICE_SHEET, BiomeType.ICE_FIELD,
                                     BiomeType.TUNDRA, BiomeType.POLAR_DESERT, BiomeType.OCEAN);
                break;
            case MOLTEN:
                seaLevel     = -0.8f;
                snowLine     = 1.00f;
                baseMoisture = 0.0f;
                surfaceTemp  = 800f;
                allowed = EnumSet.of(BiomeType.VOLCANIC, BiomeType.BADLANDS, BiomeType.ROCKY_WASTE);
                break;
            case BARREN:
                seaLevel     = -1.0f;
                snowLine     = 0.95f;
                baseMoisture = 0.0f;
                surfaceTemp  = 300f;
                allowed = EnumSet.of(BiomeType.ROCKY_WASTE, BiomeType.BADLANDS,
                                     BiomeType.DESERT, BiomeType.POLAR_DESERT);
                break;
            default:
                seaLevel     = -0.1f;
                snowLine     = 0.6f;
                baseMoisture = 0.4f;
                surfaceTemp  = 280f;
                allowed = EnumSet.allOf(BiomeType.class);
                break;
        }

        return new BiomeMap(biomeSeed, seaLevel, snowLine, baseMoisture, surfaceTemp, allowed);
    }

    private static GalaxyConfig buildConfig(GalaxySize size, GalaxyType type) {
        GalaxyConfig cfg = new GalaxyConfig();
        cfg.type = type;
        cfg.targetStarCount = size.starCount;
        // Choose radiusLY so that expected stars per chunk (at 30% average arm density) stays >= 1.
        // Formula: radiusLY = chunkSizeLY * sqrt(starCount * 1.2 / π).
        // This keeps galaxy scale proportional to star count and chunk size consistent.
        cfg.radiusLY = cfg.chunkSizeLY * (float) Math.sqrt(size.starCount * 1.2 / Math.PI);
        return cfg;
    }
}
