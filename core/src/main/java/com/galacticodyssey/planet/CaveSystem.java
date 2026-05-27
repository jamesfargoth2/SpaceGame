package com.galacticodyssey.planet;

import com.badlogic.gdx.math.Vector3;
import java.util.List;

public final class CaveSystem {
    public final long seed;
    public final CaveBiome biome;
    public final List<CaveRoom> rooms;
    public final List<CaveTunnel> tunnels;
    public final int depth;
    public final List<Vector3> entrances;

    public CaveSystem(long seed, CaveBiome biome, List<CaveRoom> rooms,
                      List<CaveTunnel> tunnels, int depth, List<Vector3> entrances) {
        this.seed = seed;
        this.biome = biome;
        this.rooms = List.copyOf(rooms);
        this.tunnels = List.copyOf(tunnels);
        this.depth = depth;
        this.entrances = List.copyOf(entrances);
    }
}
