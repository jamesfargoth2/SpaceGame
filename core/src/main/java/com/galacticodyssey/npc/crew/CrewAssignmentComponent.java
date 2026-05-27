package com.galacticodyssey.npc.crew;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class CrewAssignmentComponent implements Component {
    public CrewRole requiredRole;
    public Entity assignedCrew;
    public float effectivenessMultiplier;
}
