package com.galacticodyssey.core.events;

public final class LoadCompleteEvent {
    public final String saveName;
    public final long durationMillis;

    public LoadCompleteEvent(String saveName, long durationMillis) {
        this.saveName = saveName;
        this.durationMillis = durationMillis;
    }
}
