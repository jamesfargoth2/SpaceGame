package com.galacticodyssey.data;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.GalaxyManager;
import com.galacticodyssey.galaxy.GalaxySize;
import com.galacticodyssey.galaxy.GalaxyType;
import com.galacticodyssey.galaxy.StarPosition;
import com.galacticodyssey.galaxy.StarSystem;
import com.galacticodyssey.galaxy.StartingRegion;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.shipbuilder.ShipDesign;
import com.galacticodyssey.shipbuilder.planning.BuildOrder;

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
    public volatile StarPosition startingStarPosition;
    public volatile Planet startingPlanet;
    public volatile BiomeMap startingBiomeMap;
    public volatile long terrainSeed;
    public volatile Vector3 playerSpawnPos;
    public volatile Vector3 shipSpawnPos;

    // --- Ship builder state ---
    private ShipDesign playerShipDesign;
    private BuildOrder pendingBuildOrder;

    public ShipDesign getPlayerShipDesign() { return playerShipDesign; }
    public void setPlayerShipDesign(ShipDesign design) { this.playerShipDesign = design; }

    public BuildOrder getPendingBuildOrder() { return pendingBuildOrder; }
    public void setPendingBuildOrder(BuildOrder order) { this.pendingBuildOrder = order; }

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
