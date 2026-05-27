package com.galacticodyssey.core.events;

public final class SaveCompleteEvent {
    public final String saveName;
    public final long durationMillis;

    public SaveCompleteEvent(String saveName, long durationMillis) {
        this.saveName = saveName;
        this.durationMillis = durationMillis;
    }
}
