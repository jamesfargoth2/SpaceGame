package com.galacticodyssey.fauna.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.fauna.CreatureSpec;

/** Marks an entity as a generated creature and carries its spec + derived stats. */
public class CreatureComponent implements Component {
    public CreatureSpec spec;
    public String archetypeId;
    public float moveSpeed;
    public float meleeDamage;
}
