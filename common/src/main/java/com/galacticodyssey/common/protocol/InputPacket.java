package com.galacticodyssey.common.protocol;

public class InputPacket extends NetworkMessage {
    public PlayerInput[] inputs;
    public PlayerInput[] redundantInputs;

    public InputPacket() {}
}
