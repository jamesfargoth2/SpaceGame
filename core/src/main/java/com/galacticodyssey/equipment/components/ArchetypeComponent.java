package com.galacticodyssey.equipment.components;

import com.badlogic.ashley.core.Component;

/** Tags an entity with a named archetype id used to look up its loot table. */
public class ArchetypeComponent implements Component {
    public String archetypeId;

    public ArchetypeComponent() {}

    public ArchetypeComponent(String archetypeId) {
        this.archetypeId = archetypeId;
    }
}
