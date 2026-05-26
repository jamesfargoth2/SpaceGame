package com.galacticodyssey.vfx;

public final class VFXEnums {

    public enum BlendMode {
        NORMAL, ADDITIVE
    }

    public enum EmitterState {
        PLAYING, PAUSED, STOPPING
    }

    public static final int FLAG_ADDITIVE_BLEND = 1;
    public static final int FLAG_FACE_CAMERA = 2;
    public static final int FLAG_WORLD_SPACE = 4;

    private VFXEnums() {}
}
