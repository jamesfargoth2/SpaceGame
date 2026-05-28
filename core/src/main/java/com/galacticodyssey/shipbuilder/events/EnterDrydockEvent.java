package com.galacticodyssey.shipbuilder.events;

import com.galacticodyssey.shipbuilder.ShipDesign;

public class EnterDrydockEvent {
    public final ShipDesign design;
    public EnterDrydockEvent(ShipDesign design) { this.design = design; }
}
