package com.galacticodyssey.hacking.events;

import com.badlogic.gdx.math.Vector3;

public final class SecurityAlarmEvent {
    public final Vector3 location;
    public final float radius;

    public SecurityAlarmEvent(Vector3 location, float radius) {
        this.location = new Vector3(location);
        this.radius = radius;
    }
}
