package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public final class ThreatDetectedEvent {
    public final Entity detector;
    public final Entity threat;
    public final Vector3 position;
    public final int squadId;

    public ThreatDetectedEvent(Entity detector, Entity threat, Vector3 position, int squadId) {
        this.detector = detector;
        this.threat = threat;
        this.position = new Vector3(position);
        this.squadId = squadId;
    }
}
