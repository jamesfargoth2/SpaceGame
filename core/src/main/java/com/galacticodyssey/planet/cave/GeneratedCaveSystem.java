package com.galacticodyssey.planet.cave;

import java.util.List;

/** Output of cave system generation. */
public final class GeneratedCaveSystem {
    public final List<CaveChamber> chambers;
    public final List<BezierTunnel> tunnels;
    public final List<CaveVein> veins;
    public final List<BioluminescentPatch> bioluminescentPatches;

    public GeneratedCaveSystem(List<CaveChamber> chambers,
                               List<BezierTunnel> tunnels,
                               List<CaveVein> veins,
                               List<BioluminescentPatch> bioluminescentPatches) {
        this.chambers = chambers;
        this.tunnels = tunnels;
        this.veins = veins;
        this.bioluminescentPatches = bioluminescentPatches;
    }
}
