package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import java.util.List;

public final class MutinyEvent {
    public final List<Entity> mutineers;

    public MutinyEvent(List<Entity> mutineers) {
        this.mutineers = List.copyOf(mutineers);
    }
}
