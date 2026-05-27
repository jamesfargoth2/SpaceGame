package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.npc.PersonalityTrait;

import java.util.ArrayList;
import java.util.List;

public class NpcPersonalityComponent implements Component {
    public final List<PersonalityTrait> traits = new ArrayList<>();
    public float loyalty;
    public float greed;
    public float bravery;
}
