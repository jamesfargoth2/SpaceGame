package com.galacticodyssey.galaxy;

import java.util.List;

public final class FactionTerritory {
    public final String factionId;
    public final double capitalX;
    public final double capitalY;
    public final List<Long> controlledSystems;
    public final List<Long> borderSystems;
    public final float influence;
    public final double expansionBiasX;
    public final double expansionBiasY;

    public FactionTerritory(String factionId, double capitalX, double capitalY,
                            List<Long> controlledSystems, List<Long> borderSystems,
                            float influence, double expansionBiasX, double expansionBiasY) {
        this.factionId = factionId;
        this.capitalX = capitalX;
        this.capitalY = capitalY;
        this.controlledSystems = List.copyOf(controlledSystems);
        this.borderSystems = List.copyOf(borderSystems);
        this.influence = influence;
        this.expansionBiasX = expansionBiasX;
        this.expansionBiasY = expansionBiasY;
    }
}
