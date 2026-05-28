package com.galacticodyssey.planet.thermal.events;

import com.badlogic.ashley.core.Entity;

public final class ObjectConsumedByFireEvent {
    public final Entity entity;
    public final String charMaterialId; // nullable -> entity removed
    public ObjectConsumedByFireEvent(Entity entity, String charMaterialId) {
        this.entity = entity;
        this.charMaterialId = charMaterialId;
    }
}
