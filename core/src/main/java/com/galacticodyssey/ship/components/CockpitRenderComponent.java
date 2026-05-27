package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;

public class CockpitRenderComponent implements Component {
    public Model cockpitModel;
    public ModelInstance cockpitInstance;
    public Environment cockpitEnvironment;
    public boolean visible;
}
