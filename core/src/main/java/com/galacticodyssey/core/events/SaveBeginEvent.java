package com.galacticodyssey.core.events;

public final class SaveBeginEvent {
    public final String saveName;

    public SaveBeginEvent(String saveName) {
        this.saveName = saveName;
    }
}
