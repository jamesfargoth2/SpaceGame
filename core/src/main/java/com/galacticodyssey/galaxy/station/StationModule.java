package com.galacticodyssey.galaxy.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single structural module within a generated space station.
 */
public final class StationModule {

    public final String id;
    public final ModuleType type;
    public final float localX;
    public final float localY;
    public final float localZ;
    public final List<String> connectedModuleIds;
    public final boolean hasDockingPort;
    public final float dockingPortFacingX;
    public final float dockingPortFacingY;
    public final float dockingPortFacingZ;

    public StationModule(String id, ModuleType type,
                         float localX, float localY, float localZ,
                         List<String> connectedModuleIds,
                         boolean hasDockingPort,
                         float dockingPortFacingX, float dockingPortFacingY,
                         float dockingPortFacingZ) {
        this.id = id;
        this.type = type;
        this.localX = localX;
        this.localY = localY;
        this.localZ = localZ;
        this.connectedModuleIds = Collections.unmodifiableList(new ArrayList<>(connectedModuleIds));
        this.hasDockingPort = hasDockingPort;
        this.dockingPortFacingX = dockingPortFacingX;
        this.dockingPortFacingY = dockingPortFacingY;
        this.dockingPortFacingZ = dockingPortFacingZ;
    }
}
