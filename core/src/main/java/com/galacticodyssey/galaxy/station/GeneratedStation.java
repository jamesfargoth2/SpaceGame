package com.galacticodyssey.galaxy.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Output of space station generation: the full module graph and metadata.
 */
public final class GeneratedStation {

    public final List<StationModule> modules;
    public final int dockingPortCount;
    public final String name;

    public GeneratedStation(List<StationModule> modules, int dockingPortCount, String name) {
        this.modules = Collections.unmodifiableList(new ArrayList<>(modules));
        this.dockingPortCount = dockingPortCount;
        this.name = name;
    }
}
