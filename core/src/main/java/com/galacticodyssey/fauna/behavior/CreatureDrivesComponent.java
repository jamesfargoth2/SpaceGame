package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.Component;

public class CreatureDrivesComponent implements Component {
    public float hunger = 0.2f;
    public float energy = 1f;
    public float fear = 0f;

    public float hungerRate = 0.01f;
    public boolean moving = false;
    public boolean sprinting = false;

    public static final float ENERGY_MOVE_DRAIN = 0.02f;
    public static final float ENERGY_SPRINT_DRAIN = 0.06f;
    public static final float ENERGY_IDLE_REGEN = 0.05f;
    public static final float FEAR_DECAY = 0.1f;
}
