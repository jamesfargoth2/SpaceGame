package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;

public final class RelativisticEvents {

    private RelativisticEvents() {}

    public static class DopplerTintEvent {
        public Entity entity;
        public Color tint;

        public DopplerTintEvent(Entity entity, Color tint) {
            this.entity = entity;
            this.tint = tint;
        }
    }

    public static class TimeDilationChangedEvent {
        public Entity entity;
        public float factor;

        public TimeDilationChangedEvent(Entity entity, float factor) {
            this.entity = entity;
            this.factor = factor;
        }
    }

    public static class SpeedCapEnforcedEvent {
        public Entity entity;

        public SpeedCapEnforcedEvent(Entity entity) {
            this.entity = entity;
        }
    }
}
