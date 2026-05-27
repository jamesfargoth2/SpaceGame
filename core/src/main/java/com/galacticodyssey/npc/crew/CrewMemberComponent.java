package com.galacticodyssey.npc.crew;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import java.util.ArrayList;
import java.util.List;

public class CrewMemberComponent implements Component {
    public CrewRole role;
    public CrewRank rank = CrewRank.RECRUIT;
    public float xp;
    public float morale = 75f;
    public float loyalty = 50f;
    public MoraleState moraleState = MoraleState.GRUMBLING;
    public float wage;
    public final List<String> perkIds = new ArrayList<>();
    public Entity assignedStation;
}
