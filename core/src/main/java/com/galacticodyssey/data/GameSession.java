package com.galacticodyssey.data;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.GalaxyManager;
import com.galacticodyssey.galaxy.GalaxySize;
import com.galacticodyssey.galaxy.GalaxyType;
import com.galacticodyssey.galaxy.StarSystem;
import com.galacticodyssey.galaxy.StartingRegion;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameSession {

    // --- Input config (set by GameSetupScreen) ---
    public final long seed;
    public final String galaxyName;
    public final GalaxyType galaxyType;
    public final GalaxySize galaxySize;
    public final StartingRegion startingRegion;

    // --- Generated results (populated by GalaxyGenerationPipeline on worker thread) ---
    public volatile GalaxyManager galaxy;
    public volatile StarSystem startingSystem;
    public volatile Planet startingPlanet;
    public volatile long terrainSeed;
    public volatile BiomeMap biomeMap;
    public volatile float spawnLat;
    public volatile float spawnLon;
    public volatile Vector3 playerSpawnPos;
    public volatile Vector3 shipSpawnPos;

    // --- Thread coordination ---
    public volatile boolean complete = false;
    public volatile boolean failed = false;
    public volatile Throwable error = null;
    public final List<String> log = Collections.synchronizedList(new ArrayList<>());

    public GameSession(long seed, String galaxyName, GalaxyType galaxyType,
                       GalaxySize galaxySize, StartingRegion startingRegion) {
        this.seed = seed;
        this.galaxyName = galaxyName;
        this.galaxyType = galaxyType;
        this.galaxySize = galaxySize;
        this.startingRegion = startingRegion;
    }
}
