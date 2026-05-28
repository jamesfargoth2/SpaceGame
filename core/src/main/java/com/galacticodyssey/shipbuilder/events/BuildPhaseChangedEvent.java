package com.galacticodyssey.shipbuilder.events;

import com.galacticodyssey.shipbuilder.BuilderPhase;

public class BuildPhaseChangedEvent {
    public final BuilderPhase previousPhase;
    public final BuilderPhase newPhase;
    public BuildPhaseChangedEvent(BuilderPhase previousPhase, BuilderPhase newPhase) {
        this.previousPhase = previousPhase;
        this.newPhase = newPhase;
    }
}
