package com.galacticodyssey.shipbuilder.events;

import com.galacticodyssey.shipbuilder.planning.BuildAction;

public class BuildOrderQueuedEvent {
    public final BuildAction action;
    public BuildOrderQueuedEvent(BuildAction action) { this.action = action; }
}
