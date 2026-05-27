package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;

public class PlayerModelComponent implements Component {

    public ModelInstance modelInstance;
    public AnimationController animationController;
    public AnimState currentAnim = AnimState.IDLE;
    public float modelYOffset = -0.9f;

    public enum AnimState {
        IDLE("Idle"),
        WALK("Walk"),
        RUN("Run"),
        CROUCH_IDLE("CrouchIdle"),
        CROUCH_WALK("CrouchWalk"),
        FALL("Fall"),
        JUMP("Jump");

        public final String id;
        AnimState(String id) { this.id = id; }
    }
}
