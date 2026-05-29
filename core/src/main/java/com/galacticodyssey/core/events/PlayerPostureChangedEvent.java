package com.galacticodyssey.core.events;

import com.galacticodyssey.player.PostureType;

public class PlayerPostureChangedEvent {
    public final PostureType previous;
    public final PostureType next;
    public PlayerPostureChangedEvent(PostureType previous, PostureType next) {
        this.previous = previous;
        this.next = next;
    }
}
