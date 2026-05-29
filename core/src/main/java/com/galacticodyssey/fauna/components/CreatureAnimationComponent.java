package com.galacticodyssey.fauna.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.fauna.animation.GaitController;
import com.galacticodyssey.fauna.animation.GaitParams;
import com.galacticodyssey.fauna.rig.CreatureRig;

public class CreatureAnimationComponent implements Component {
    public CreatureRig rig;
    public GaitController gaitController;
    public final GaitParams params = new GaitParams();
}
