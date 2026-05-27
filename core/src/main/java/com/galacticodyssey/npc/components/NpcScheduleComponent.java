package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;
import java.util.ArrayList;
import java.util.List;

public class NpcScheduleComponent implements Component {
    public final List<ScheduleEntry> entries = new ArrayList<>();
}
