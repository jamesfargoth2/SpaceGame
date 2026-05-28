package com.galacticodyssey.shipbuilder;

public enum BuilderPhase {
    HULL_SCULPT("Hull Sculpt", 1),
    ROOM_LAYOUT("Room Layout", 2),
    MODULE_FIT("Module Fit", 3);

    public final String displayName;
    public final int order;

    BuilderPhase(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }
}
