package com.galacticodyssey.core.events;

public class ScanCompleteEvent {
    public final String targetId;

    public ScanCompleteEvent(String targetId) {
        this.targetId = targetId;
    }
}
