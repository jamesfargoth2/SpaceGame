package com.galacticodyssey.fauna.animation;

import com.galacticodyssey.fauna.rig.CreatureRig;

public interface GaitController {
    void update(CreatureRig rig, GaitParams params);
    void reset(CreatureRig rig);
}
