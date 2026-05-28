package com.galacticodyssey.hacking.events;

import com.badlogic.ashley.core.Entity;

public final class DataAccessedEvent {
    public final Entity player;
    public final Entity terminal;
    public final String terminalId;

    public DataAccessedEvent(Entity player, Entity terminal, String terminalId) {
        this.player = player;
        this.terminal = terminal;
        this.terminalId = terminalId;
    }
}
