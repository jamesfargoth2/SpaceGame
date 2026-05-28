package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.networking.prediction.InputBuffer;

public class PredictionComponent implements Component {

    public static final int SMOOTHING_FRAMES = 10;
    public static final float ACCEPT_THRESHOLD = 0.01f;
    public static final float HARD_SNAP_THRESHOLD = 5.0f;

    private final InputBuffer inputBuffer = new InputBuffer();
    public int nextSequenceNumber;

    public float smoothingOffsetX;
    public float smoothingOffsetY;
    public float smoothingOffsetZ;
    public int smoothingFramesRemaining;

    public int lastAcknowledgedSequence = -1;

    public int advanceSequence() {
        return nextSequenceNumber++;
    }

    public InputBuffer getInputBuffer() {
        return inputBuffer;
    }
}
