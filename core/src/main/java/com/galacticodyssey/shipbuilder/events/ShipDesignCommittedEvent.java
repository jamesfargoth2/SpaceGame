package com.galacticodyssey.shipbuilder.events;

import com.galacticodyssey.shipbuilder.ShipDesign;

public class ShipDesignCommittedEvent {
    public final ShipDesign design;
    public ShipDesignCommittedEvent(ShipDesign design) { this.design = design; }
}
