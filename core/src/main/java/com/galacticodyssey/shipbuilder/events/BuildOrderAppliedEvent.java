package com.galacticodyssey.shipbuilder.events;

import com.galacticodyssey.shipbuilder.planning.BuildOrder;

public class BuildOrderAppliedEvent {
    public final BuildOrder order;
    public BuildOrderAppliedEvent(BuildOrder order) { this.order = order; }
}
