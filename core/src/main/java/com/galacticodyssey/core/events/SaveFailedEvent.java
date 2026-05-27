package com.galacticodyssey.core.events;

public final class SaveFailedEvent {
    public final String saveName;
    public final Exception cause;

    public SaveFailedEvent(String saveName, Exception cause) {
        this.saveName = saveName;
        this.cause = cause;
    }
}
