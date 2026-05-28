package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.networking.interpolation.SnapshotBuffer;

public class InterpolationComponent implements Component {

    public static final float MAX_EXTRAPOLATION_SECONDS = 0.5f;
    public static final int BLEND_FRAMES = 5;
    public static final int RENDER_DELAY_TICKS = 2;

    private final SnapshotBuffer snapshotBuffer = new SnapshotBuffer();

    public float extrapolationTimer;
    public boolean frozen;
    public int blendFramesRemaining;

    public float blendFromX, blendFromY, blendFromZ;
    public float blendFromRotX, blendFromRotY, blendFromRotZ, blendFromRotW;

    public SnapshotBuffer getSnapshotBuffer() {
        return snapshotBuffer;
    }
}
