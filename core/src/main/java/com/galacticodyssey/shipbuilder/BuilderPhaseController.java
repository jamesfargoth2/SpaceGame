package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.shipbuilder.events.BuildPhaseChangedEvent;

import java.util.ArrayList;
import java.util.List;

public class BuilderPhaseController {
    private BuilderPhase currentPhase = BuilderPhase.HULL_SCULPT;
    private final ShipDesign design;
    private final ShipDesignValidator validator;
    private final EventBus eventBus;
    private boolean roomsInvalidated;
    private boolean modulesInvalidated;

    public BuilderPhaseController(ShipDesign design, ShipDesignValidator validator, EventBus eventBus) {
        this.design = design;
        this.validator = validator;
        this.eventBus = eventBus;
    }

    public BuilderPhase getCurrentPhase() {
        return currentPhase;
    }

    public boolean canAdvance() {
        if (currentPhase == BuilderPhase.HULL_SCULPT) {
            return design.hull.spinePoints.size() >= 3 && design.hull.crossSections.size() >= 2;
        }
        if (currentPhase == BuilderPhase.ROOM_LAYOUT) {
            List<ShipDesignValidator.ValidationError> errors = validator.validate(design, null);
            return errors.isEmpty();
        }
        return false;
    }

    public List<String> getAdvanceBlockers() {
        List<String> blockers = new ArrayList<>();
        if (currentPhase == BuilderPhase.HULL_SCULPT) {
            if (design.hull.spinePoints.size() < 3) blockers.add("Need at least 3 spine points");
            if (design.hull.crossSections.size() < 2) blockers.add("Need at least 2 cross-sections");
        }
        if (currentPhase == BuilderPhase.ROOM_LAYOUT) {
            for (ShipDesignValidator.ValidationError e : validator.validate(design, null)) {
                blockers.add(e.message);
            }
        }
        return blockers;
    }

    public boolean advance() {
        if (!canAdvance()) return false;
        BuilderPhase previous = currentPhase;
        if (currentPhase == BuilderPhase.HULL_SCULPT) {
            currentPhase = BuilderPhase.ROOM_LAYOUT;
        } else if (currentPhase == BuilderPhase.ROOM_LAYOUT) {
            currentPhase = BuilderPhase.MODULE_FIT;
        } else {
            return false;
        }
        roomsInvalidated = false;
        modulesInvalidated = false;
        eventBus.publish(new BuildPhaseChangedEvent(previous, currentPhase));
        return true;
    }

    public boolean goBack() {
        BuilderPhase previous = currentPhase;
        if (currentPhase == BuilderPhase.MODULE_FIT) {
            currentPhase = BuilderPhase.ROOM_LAYOUT;
            modulesInvalidated = true;
        } else if (currentPhase == BuilderPhase.ROOM_LAYOUT) {
            currentPhase = BuilderPhase.HULL_SCULPT;
            roomsInvalidated = true;
            modulesInvalidated = true;
        } else {
            return false;
        }
        eventBus.publish(new BuildPhaseChangedEvent(previous, currentPhase));
        return true;
    }

    public boolean areRoomsInvalidated() { return roomsInvalidated; }
    public boolean areModulesInvalidated() { return modulesInvalidated; }
}
