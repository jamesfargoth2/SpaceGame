package com.galacticodyssey.fauna.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.galacticodyssey.fauna.skin.CreatureSkinSpec;

public class CreatureRenderComponent implements Component {
    public Object modelInstance = null;
    public ModelInstance skinnedInstance = null;
    public CreatureSkinSpec skinSpec = null;
}
