package com.galacticodyssey.networking.prediction;

import com.galacticodyssey.common.protocol.PlayerInput;

public class TimestampedInput {
    public final int sequenceNumber;
    public final PlayerInput input;
    public final PredictedState predictedState;

    public TimestampedInput(int sequenceNumber, PlayerInput input, PredictedState predictedState) {
        this.sequenceNumber = sequenceNumber;
        this.input = input;
        this.predictedState = predictedState;
    }
}
