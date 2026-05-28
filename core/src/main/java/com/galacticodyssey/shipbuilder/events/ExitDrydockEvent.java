package com.galacticodyssey.shipbuilder.events;

public class ExitDrydockEvent {
    public final boolean committed;
    public ExitDrydockEvent(boolean committed) { this.committed = committed; }
}
