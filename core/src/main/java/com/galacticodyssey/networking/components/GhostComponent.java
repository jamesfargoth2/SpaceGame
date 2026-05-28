package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Component;
import java.util.UUID;

public class GhostComponent implements Component {
    public UUID owningZoneId;
    public boolean readOnly;
}
