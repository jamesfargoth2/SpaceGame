package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Component;

/** Marks an entity as a deployed ground vehicle and records its source definition id. */
public class VehicleTagComponent implements Component {
    public String definitionId;
}
