package com.galacticodyssey.fauna.components;

import com.badlogic.ashley.core.Component;

/** Render hook-up for a creature. The GL layer fills {@link #modelInstance} from the spec. */
public class CreatureRenderComponent implements Component {
    /** com.badlogic.gdx.graphics.g3d.ModelInstance(s), set by the GL layer; null until built. */
    public Object modelInstance = null;
    /** Flat tint (RGBA packed) derived from colorSeed/biome until Cycle C shaders land. */
    public float tintR = 0.6f, tintG = 0.6f, tintB = 0.6f;
}
