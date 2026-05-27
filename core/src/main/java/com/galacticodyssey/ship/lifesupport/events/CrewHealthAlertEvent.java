package com.galacticodyssey.ship.lifesupport.events;

import com.badlogic.ashley.core.Entity;

public final class CrewHealthAlertEvent {

    public enum AlertType {
        HYPOXIA,
        CO2_TOXIC,
        DECOMPRESSION
    }

    public final Entity entity;
    public final AlertType alertType;
    public final float severity;

    public CrewHealthAlertEvent(Entity entity, AlertType alertType, float severity) {
        this.entity = entity;
        this.alertType = alertType;
        this.severity = severity;
    }
}
