package com.galacticodyssey.shipbuilder;

public final class AppendageDef {

    public enum AppendageType {
        SWEPT_WING, DELTA_WING, STRAIGHT_WING,
        ENGINE_POD, DORSAL_FIN, VENTRAL_FIN
    }

    public enum Side { LEFT, RIGHT, BOTH, CENTER }

    public AppendageType type;
    public float spineT;
    public Side side;
    public float scale;

    public AppendageDef() {}

    public AppendageDef(AppendageType type, float spineT, Side side, float scale) {
        this.type = type;
        this.spineT = spineT;
        this.side = side;
        this.scale = scale;
    }

    public AppendageDef copy() {
        return new AppendageDef(type, spineT, side, scale);
    }
}
